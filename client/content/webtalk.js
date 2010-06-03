var webtalk = {
  // whether the web talk sidebar is active or not?
  active: true, 
  
  // the client id that is used to identify this client.
  // this is generated randomly for each browser instance.
  clientId: null,
  
  // the name of this which is used in displaying.
  // this is read and stored in extension preferences.
  myname: null,
  
  // location is url of the currently opened web page.
  location: null,
  
  // room type of this location: "domain", "page", or "none".
  type: null,
  
  // timer that periodically fetches chathistory and userlist
  timer: null,
  
  // last timestamp when refresh was done
  lastRefresh: 0,
  
  // roomtype cache indexed by domain and value as "domain", "page" or "none"
  roomtype: {},
  
  // versions of userlist and chathistory of last fetch.
  userlistVersion: 0,
  chathistoryVersion: 0,
  
  // config items that can be set by chat input {webtalk.item=value}
  // server is the web service url, and interval is the refresh interval.
  config: {
    service: "",
    interval: 5000
  },
  
  // author email for feedback
  author: "mamta_singh02@yahoo.com",
  
  // This is the first entry point to the extension. It is invoked onload of the 
  // main window. It initializes some properties, and installs the progress listener
  // to receive the location change event.
  // see https://developer.mozilla.org/en/Code_snippets/Progress_Listeners
  onLoad: function() {
    this.initialized = true;
    this.strings = document.getElementById("webtalk-strings");
    this.clientId = "" + Math.floor(Math.random() * 10000000000);
    this.config.service = this.strings.getString("webtalkURL");
    
    gBrowser.addProgressListener(this, Components.interfaces.nsIWebProgress.NOTIFY_LOCATION);
  },

  // When the window is unloaded, remove the progress listener.
  onUnload: function() {
    gBrowser.removeProgressListener(this);
  },

  // The progress listener interface method to initialize.
  QueryInterface: function(aIID) {  
    alert(aIID);
    if (aIID.equals(Components.interfaces.nsIWebProgressListener) ||  
        aIID.equals(Components.interfaces.nsISupportsWeakReference) ||  
        aIID.equals(Components.interfaces.nsISupports))
      return this;  
    throw Components.results.NS_NOINTERFACE;  
  },  
  
  // The main progress listener handler when location changes. It just invokes
  // another locationChanged method.
  onLocationChange: function(aProgress, aRequest, aURI) {  
    webtalk.locationChanged(window.content.document.wrappedJSObject);  
  },  
  
  // following progress listener handlers are not called since I filter on
  // the location event only. But they are required to be defined.
  onStateChange: function(a, b, c, d) {},  
  onProgressChange: function(a, b, c, d, e, f) {},  
  onStatusChange: function(a, b, c, d) {},  
  onSecurityChange: function(a, b, c) {},
  
  // This is the second entry point in this script. This is invoked by the main
  // XUL (firefoxOverlay.xul) whenever the webtalk sidebar is enabled or disabled.
  // It sets the active property, and if active invokes the locationChanged method.
  // If not active it leaves the user from existing room.
  onToggleSidebar: function() {
    try {
      var sidebarWindow = document.getElementById("sidebar").contentWindow;
      webtalk.active = (sidebarWindow.location.href == "chrome://webtalk/content/sidebar.xul");
    } catch (e) { }
    
    if (webtalk.active) {
      webtalk.populateMyName();
      webtalk.locationChanged(window.content.document.wrappedJSObject, true);
      webtalk.focusChatInput();
    } else {
      webtalk.leaveRoom();
      webtalk.location = null;
    }
  },
  
  // This is the main event handler and is invoked whenever the location of the 
  // browser tab changes or when the webtalk sidebar is enabled. When the location
  // changes, first it determines the roomtype (either from cache or web service).
  // If the roomtype and previous location demand a change in chat room, it joins 
  // the new chat room.
  // @param obj the document object with domain and location properties.
  // @param forceJoin if true then join the room, otherwise join only if the location
  // change demands a new chat room.
  locationChanged: function(obj, forceJoin) {
    if (!webtalk.active)
      return;
      
    var location = obj.domain;
    // 2. method to invoke after room type is decided
    var callback = function() {
      var type = webtalk.type;
      if (type == "none") {
        // disable the chat room
        webtalk.error("Chat room disabled for " + location);
        webtalk.setChatHistory([]);
        webtalk.setUserList([]);
        webtalk.leaveRoom();
        webtalk.location = null;
        return;
      }
      else if (type == "page") {
        // the chat room location is per-page, hence include full url parts
        location = obj.location.hostname + obj.location.pathname + obj.location.search;
      }
      // otherwise chat room location is per-domain (default)
      
      // join the chat room location if the location changed, or 
      // enabling the extension forced the join. The join happens only
      // if the webtalk sidebar is active.
      if (location != null && (location != webtalk.location || forceJoin)) {
        webtalk.location = location;
        if (webtalk.active)
          webtalk.joinRoom();
      }
    };
    
    // 1. determine the room type
    // 1.a if not found in cache, use GET /api/roomtype web service
    if (typeof(webtalk.roomtype[location]) == "undefined") {
      var request = webtalk.getRequest();
      request.onreadystatechange = function() {
        if (request.readyState == 4) {
          if (request.status == 200) {
            webtalk.roomtype[location] = request.responseText;
            webtalk.type = webtalk.roomtype[location];
            callback();
          } else {
            webtalk.error("GET /roomtype failed (" + webtalk.getStatus(request) + ")");
          }
        }
      }
      var url = webtalk.config.service + "/roomtype?location=" + escape(location);
      request.open("GET", url, true);
      request.send(null);
    } else {
    // 1.b if found in cache use that
      webtalk.type = webtalk.roomtype[location];
      callback();
    }
  },
  
  // Join the chat room for the location. It is invoked only if a location change
  // demands a new chat room. It makes sure that my name is populated. 
  // It then resets the versions because we are in a new chat room. It then does
  // 1. post my user data to chat room user list
  // 2. get the chat room user list
  // 3. get the chat room chat history
  // 4. start the timer to periodically refresh user list and chat history
  joinRoom: function() {
    if (webtalk.myname == null)
      webtalk.populateMyName();
    webtalk.userlistVersion = 0;
    webtalk.chathistoryVersion = 0;
    webtalk.postUserList();
    setTimeout(function() { webtalk.loadUserList(); }, 200);
    webtalk.loadChatHistory();
    
    if (webtalk.timer == null) {
      webtalk.timer = setInterval(webtalk.timerHandler, webtalk.config.interval);
    }
    webtalk.lastRefresh = (new Date()).getTime();
  },
  
  // Leave any existing chat room. First clear any refresh timer. Then reset any
  // versions. Finally remove my user data from the chat room user list.
  leaveRoom: function() {
    if (webtalk.timer != null) {
      clearInterval(webtalk.timer);
      webtalk.timer = null;
    }
    webtalk.chathistoryVersion = 0;
    webtalk.userlistVersion = 0;
    if (webtalk.location != null) {
      webtalk.deleteUserList();
    }
  },
  
  // The periodic refresh timer does
  // 1. post my user data to chat room user list (to refresh)
  // 2. get chat room user list
  // 3. get chat room chat history.
  // It skips the steps if it is invoked too soon. It also correctly handles
  // 304 not modified response in steps 2 and 3.
  timerHandler: function() {
    var now = (new Date()).getTime();
    if ((now - webtalk.lastRefresh) > (webtalk.config.interval-1000)) {
      if (webtalk.type != null && webtalk.type != "none") {
        webtalk.lastRefresh = (new Date()).getTime();
        webtalk.postUserList();
        setTimeout(function() { webtalk.loadUserList(); }, 200);
        webtalk.loadChatHistory();
      }
    }
  },
  
  // Get the AJAX object for making HTTP request.
  // Since this is Firefox extension, no need for ActiceXObject Microsoft/Msxml2.XMLHTTP
  getRequest: function() {
    try {
      return new XMLHttpRequest();
    } catch (e) {
      alert("Your browser is too old and does not support AJAX");
      return false;
    }
  },
  
  // Get the status text from the request object. This is convenient method because
  // sometimes the request.status or request.statusText give exception.
  getStatus: function(request) {
    var status = "N/A";
    var statusText = "N/A";
    try{ status = request.status; } catch(e) {}
    try{ statusText = request.statusText} catch(e) {}
    return status + " " + statusText;
  },
  
  // Post a new chat message to the chat room chat history. It also handles any
  // special commands such as
  // {webtalk.property} -- display the value of the property, e.g., service, interval
  // {webtalk.property=value} -- set the value of the property.
  // @user 2:private message targetted to user 2.
  // The method ensures that my name is set, otherwise it skips the posting of chat
  // messages.
  postChatHistory: function(text) {
    // handle {webtalk.property} and {webtalk.property=value} commands
    var command = text.match(/^\{webtalk\.([^=\}]+)(=([^\}]*))?\}$/);
    if (command) {
      webtalk.handleChatCommand(command);
      return;
    }
    
    if (webtalk.location == null)
      return;
    
    timestamp = (new Date()).getTime();
    if (!webtalk.myname) {
      // ensure my name is set
      webtalk.changeMyName();
      if (!webtalk.myname) {
        alert("Cannot send message without setting your screen name");
        return;
      }
    }
    
    var request = webtalk.getRequest();
    request.onreadystatechange = function() {
      if (request.readyState == 4) {
        if (request.status == 200) {
          // nothing
        } else {
          webtalk.error("POST /chathistory failed (" + webtalk.getStatus(request) + ")");
        }
      }
    }
    
    var url = webtalk.config.service + "/chathistory?location=" + webtalk.getLocation();
    var target = text.match(/^@([^:]+):/)
    if (target) {
      url += "&target=" + escape(target[1].toString());
      text = text.substr(target[1].length+2);
    }
    request.open("POST", url, true);
    request.setRequestHeader("Content-Type", "application/json");
    request.send('{"sender":"' + webtalk.myname + '","timestamp":' + timestamp + ',"text":"' + text + '"}');
  }, 
   
  // Get the chat room chat history. The supplied callback function is invoked
  // on success with an array of chat messages. In case of 304 response, it doesn't
  // invoke the callback. For any other failure it displays the error message.
  getChatHistory: function(callback) {
    var request = webtalk.getRequest();
    request.onreadystatechange = function() {
      if (request.readyState == 4) {
        if (request.status == 200) {
          obj = eval("(" + request.responseText + ")");
          webtalk.chathistoryVersion = obj.version;
          callback(obj.chathistory);
        } else if (request.status == 304) {
          // chat history is not modified
        } else {
          webtalk.chathistoryVersion = 0;
          webtalk.error("GET /chathistory failed (" + webtalk.getStatus(request) + ")");
        }
      }
    }
    
    var url = webtalk.config.service + "/chathistory?location=" + webtalk.getLocation() + "&since=" + webtalk.chathistoryVersion;
    if (webtalk.myname)
      url += "&target=" + escape(webtalk.myname);
    request.open("GET", url, true);
    request.send(null);
  },
  
  // Load the chat room's chat history using getChatHistory and set the result to
  // the user interface using setChatHistory methods. It formats the response
  // so that the chat history is displayed with correct order: if same user sent
  // multiple messages, they are ordered chronologically for that user, and
  // each user messages are ordered in reverse chronological by user name. This makes
  // the most recent comment first, but still allows readability for multiple messages
  // from the same user.
  loadChatHistory: function() {
    if (webtalk.location == null)
      return;
    
    webtalk.getChatHistory(function(data) {
      var msgs = [];
      for (var s in data) {
        var obj = data[s];
        var obj_date = (new Date(obj.timestamp)).toLocaleDateString();
        var msg;
        if (msgs.length > 0 && msgs[0].sender == obj.sender && msgs[0].date == obj_date) {
          msg = msgs[0];
        } else {
          msg = {"sender": obj.sender, "date": obj_date, "text": obj.sender + " [" + obj_date + "]:\n"};
          msgs.unshift(msg);
        }
        msg.text += "  " + obj.text + "\n";
      }
      webtalk.setChatHistory(msgs);
    });
  },
  
  // Set the user interface chat history. An empty chat history is indicated
  // explicitly.
  setChatHistory: function(msgs) {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var chathistory = sidebarDocument.getElementById("chathistory");
    var text = msgs ? msgs.map(function(obj) { return obj.text; }).join("") : "";
    chathistory.value = text || "<empty>";
  },
  
  // Post my user data to the chat room's user list using POST /api/userlist 
  // web service. The web service implicitly removes my user data from my previous
  // chat room's user list.
  postUserList: function() {
    if (webtalk.location == null)
      return;
    
    var request = webtalk.getRequest();
    request.onreadystatechange = function() {
      if (request.readyState == 4) {
        if (request.status == 200) {
          // nothing
        } else {
          webtalk.error("POST /userlist failed (" + webtalk.getStatus(request) + ")");
        }
      }
    }
  
    var url = webtalk.config.service + "/userlist?location=" + webtalk.getLocation();
    request.open("POST", url, true);
    request.setRequestHeader("Content-Type", "application/json");
    request.send('{"name":"' + webtalk.myname + '","clientId":"' + webtalk.clientId + '"}');
  },
  
  // Delete my user data from chat room's user list using POST /api/userlist/delete
  // web service.
  deleteUserList: function() {
    if (webtalk.location == null)
      return;
 
    var request = webtalk.getRequest();
    request.onreadystatechange = function() {
      if (request.readyState == 4) {
        if (request.status == 200) {
          // nothing
        } else {
          // nothing
        }
      }
    }
 
    var url = webtalk.config.service + "/userlist/delete?location=" + webtalk.getLocation();
    request.open("POST", url, true);
    request.setRequestHeader("Content-Type", "application/json");
    request.send('{"name":"' + webtalk.myname + '","clientId":"' + webtalk.clientId + '"}');
  },
 
  // Get the chat room's user list and invoke the supplied callback function on
  // success with the list of user data. It doesn't call the callback if 304 is
  // received. It indicates the error message for any other error response.
  getUserList: function(callback) {
    var request = webtalk.getRequest();
    request.onreadystatechange = function() {
      if (request.readyState == 4) {
        if (request.status == 200) {
          obj = eval("(" + request.responseText + ")");
          webtalk.userlistVersion = obj.version;
          callback(obj.userlist);
        } else if (request.status == 304) {
          // user list is not modified.
        } else {
          webtalk.userlistVersion = 0;
          webtalk.error("GET /userlist failed (" + webtalk.getStatus(request) + ")");
        }
      }
    }
  
    var url = webtalk.config.service + "/userlist?location=" + webtalk.getLocation() + "&since=" + webtalk.userlistVersion;
    request.open("GET", url, true);
    request.send(null);
  },
  
  // Load the chat room's user list by getUserList to get the data and then setUserList
  // to set the user interface with the data.
  loadUserList: function() {
    if (webtalk.location == null)
      return;
    
    webtalk.getUserList(function(data) {
      var users = [];
      for (var s in data)
        users.push(data[s]);
      webtalk.setUserList(users);
    });
  },
  
  // Set the users list in the user interface. It explicitly indicates an empty 
  // list. It also sorts the users list by name.
  setUserList: function(users) {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var userlist = sidebarDocument.getElementById("userlist");
    var text = users ? users.map(function(obj) { return obj.name; }).sort().join("\n") : "";
    userlist.value = text || "<empty>";
  },
  
  // Get my name from preferences and set it in user interface, as well as myname 
  // property. 
  populateMyName: function() {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var prefManager = Components.classes["@mozilla.org/preferences-service;1"]
                      .getService(Components.interfaces.nsIPrefBranch);
    var name = "";
    try {
      name = prefManager.getCharPref("extensions.webtalk.myname");
    } catch (e) { }
    if (name == null)
      name = "";
    var mynameText = sidebarDocument.getElementById("mynameText");
    mynameText.value = (name == "" ? "Set My Name" : name);
    webtalk.myname = name;
  },
  
  // Prompt the user to change my name property. On success, update the preferences,
  // user interface as well as myname property. 
  changeMyName: function() {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var mynameText = sidebarDocument.getElementById("mynameText");
    var name = prompt("Enter your screen name: ", mynameText.value);
    if (name != null && name != "Set My Name") {
      mynameText.value = (name == "" ? "Set My Name" : name);
      webtalk.myname = name;
      webtalk.postUserList();
      setTimeout(function() { webtalk.loadUserList(); }, 200);
      var prefManager = Components.classes["@mozilla.org/preferences-service;1"]
      .getService(Components.interfaces.nsIPrefBranch);
      prefManager.setCharPref("extensions.webtalk.myname", name);
    }
    webtalk.focusChatInput();
  },
  
  // Display an error message in the input box.
  error: function(msg) {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var input = sidebarDocument.getElementById("input");
    setTimeout(function() { input.value = "ERROR: " + msg; input.setSelectionRange(0, input.value.length); }, 100);
  },
  
  // Display a message in the input box.
  message: function(msg) {
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    var input = sidebarDocument.getElementById("input");
    setTimeout(function() { input.value = msg; input.setSelectionRange(0, input.value.length); }, 100);
  },
  
  // Invoked when user clicks on the refresh link, to refresh the user list
  // and chat history by re-joining the chat room after resetting the version.
  refresh: function() {
    webtalk.userlistVersion = 0;
    webtalk.chathistoryVersion = 0;
    webtalk.joinRoom();
  },
  
  // invoke the mail client to send email to the author when user
  // clicks on the feedback link
  feedback: function() {
    window.content.document.wrappedJSObject.location = "mailto:" + webtalk.author;
  },
  
  // show the /download page when the user clicks on the help link.
  help: function() {
    window.content.document.wrappedJSObject.location = webtalk.config.service + "/../download";
  },
  
  // Handle the special commands in input box to show or set the properties.
  // The "interval" and "service" properties are defined.
  handleChatCommand: function(command) {
    if (command[1]) {
      if (typeof(command[3])!="undefined") {
        value = (command[1] == "interval" ? int(command[3]) : command[3]);
        webtalk.config[command[1]] = value;
      }
      webtalk.message(command[1] + "=" + webtalk.config[command[1]]);
    }
  },
  
  // Get the escaped location value which can be supplied in the location
  // parameter in various web services. The built-in escape function does not
  // change the "/" character, but this interferes with parameter identification
  // of restlet, hence I explicitly escape "/" character.
  getLocation: function() {
    return webtalk.location ? escape(webtalk.location).replace(/\//g, '%2f') : '';
  },

  // Set the keyboad focus to the input box.
  focusChatInput: function() {
    // set the focus to input
    var sidebarDocument = document.getElementById("sidebar").contentDocument;
    if (sidebarDocument != null) {
      var input = sidebarDocument.getElementById("input");
      if (input != null) {
        input.focus();
      }
    }
  },
  
  mustBeLast: function() { }
};

window.addEventListener("load", function(e) { webtalk.onLoad(e); }, false);
window.addEventListener("unload", function(e) { webtalk.onUnload(e); }, false);
