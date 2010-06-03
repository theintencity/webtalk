import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Abstraction of the database for this project. This is a in-memory data store for
 * various data such as chat history, user list. For a large scale project this should
 * be moved to a persistent database.
 * 
 * @author mamta
 */
public class Database implements Runnable {
	/**
	 * The limit on chat history per room.
	 */
	public static int CHAT_HISTORY_LIMIT = -1; 
	
	/**
	 * The limit on user list size per room.
	 */
	public static int USER_LIST_LIMIT    = -1;
	
	/**
	 * After this many milliseconds, the user list item expires if not refreshed.
	 */
	public static int DEFAULT_USER_EXPIRES = 60000; // one minute
	
	/**
	 * Periodically remove expired users from user list every this many milliseconds.
	 */
	public static int DEFAULT_CLEANUP_THREAD_INTERVAL = 60000; // one minute
	
	/**
	 * The types of room: "domain", "page", "none"
	 */
	public static final String CHATROOM_BY_DOMAIN = "domain";
	public static final String CHATROOM_BY_PAGE   = "page";
	public static final String CHATROOM_DISABLED  = "none";
	
	/**
	 * The single user data in user list.
	 * @author mamta
	 */
	public static class User {
		public String name;
		public String clientId;
		public long expires;
	}
	
	/**
	 * The user list for a chat room.
	 * @author mamta
	 */
	public static class UserList {
		public int version = 0;
		public Map<String, User> data = new HashMap<String, User>();
		public synchronized List<User> getList() {
			Collection<User> values = data.values();
			return (values != null && values.size() > 0 ? new LinkedList<User>(values) : null);
		}
	}
	
	/**
	 * A single chat message.
	 * @author mamta
	 */
	public static class Chat {
		public String sender;
		public String target;
		public long   timestamp;
		public String text;
	}
	
	/**
	 * The chat history for a chat room.
	 * @author mamta
	 */
	public static class ChatHistory {
		public int version = 0;
		public List<Chat> data = new LinkedList<Chat>();
		public List<Chat> getList() {
			return data;
		}
	}
	
	/**
	 * The chathistory table indexed by location and value as ChatHistory. 
	 */
	private static Map<String, ChatHistory> chathistory = new HashMap<String, ChatHistory>();
	
	/**
	 * The userlist table indexed by location and value as UserList.
	 */
	private static Map<String, UserList> userlist = new HashMap<String, UserList>();
	
	/**
	 * The userloc table stores the location (value) of a user's clientId (index).
	 */
	private static Map<String, String> userloc = new HashMap<String, String>();
	
	/**
	 * The sites table stores the configured per-page or no-chat sites' domains.
	 * The index is domain and value is true for per-page and false of no-chat.
	 */
	private static Map<String, Boolean> sites = new HashMap<String, Boolean>();
	
	/**
	 * The singletone instance of the database class.
	 */
	private static Database instance = null;
	
	/**
	 * The static method to get the singleton instance of the database class.
	 * @return
	 */
	public static Database getInstance() {
		if (instance == null) 
			instance = new Database();
		return instance;
	}
	
	/**
	 * Create a new database object, initialize the properties and populate the sites table
	 * using webtalk.properties file.
	 */
	private Database() {
		try {
			FileInputStream in = new FileInputStream("webtalk.properties");
			Properties prop = new Properties();
			prop.load(in);
			in.close();
			
        	Database.CHAT_HISTORY_LIMIT = Integer.parseInt(prop.getProperty("chatHistoryLimit", "30"));
        	Database.USER_LIST_LIMIT = Integer.parseInt(prop.getProperty("userListLimit", "30"));
        	Database.DEFAULT_USER_EXPIRES = Integer.parseInt(prop.getProperty("userExpires", "60000"));
        	Database.DEFAULT_CLEANUP_THREAD_INTERVAL = Integer.parseInt(prop.getProperty("cleanupThreadInterval", "60000"));
        	
			String[] perpage = prop.getProperty("perpage").split("[, ]+");
			String[] nochat = prop.getProperty("nochat").split("[, ]+");
			for (int i=0; i<perpage.length; ++i) {
				sites.put(perpage[i].trim(), new Boolean(true));
			}
			for (int i=0; i<nochat.length; ++i) {
				sites.put(nochat[i].trim(), new Boolean(false));
			}
		} catch (FileNotFoundException e) {
			System.err.println("File Not Found: domain.properties");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		new Thread(this).start();
	}
	
	/**
	 * The thread method periodically calls the removeExpiredUserList to remove expired
	 * user items from userlist of all chat rooms.
	 */
	public void run() {
		while (true) {
			try {
				Thread.sleep(DEFAULT_CLEANUP_THREAD_INTERVAL);  // run every minute
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			removeExpiredUserList();
		}
	}
	
	/**
	 * Get the ChatHistory for the given chat room location.
	 * 
	 * @param location
	 * @return ChatHistory
	 */
	public synchronized ChatHistory getChatHistory(String location) {
		return chathistory.get(location);
	}
	
	/**
	 * Add the Chat message to the given chat room location. It creates a ChatHistory
	 * if missing for the location in chathistory table. It adds the Chat to the list
	 * of messages in ChatHistory, and increments the version of ChatHistory. If the 
	 * list of messages is more than the chat history size limit per location, it
	 * removes oldest messages.
	 * 
	 * @param location
	 * @param data
	 */
	public synchronized void addChatHistory(String location, Chat data) {
		ChatHistory value = chathistory.get(location);
		if (value == null) {
			value = new ChatHistory();
			chathistory.put(location, value);
		}
		
		value.data.add(data);
		value.version += 1;
		
		if (CHAT_HISTORY_LIMIT >= 0) {
			while (value.data.size() > CHAT_HISTORY_LIMIT) {
				value.data.remove(0);
			}
		}
	}
	
	/**
	 * Get the UserList associated with the given chat room location.
	 * 
	 * @param location
	 * @return UserList
	 */
	public synchronized UserList getUserList(String location) {
		return userlist.get(location);
	}
	
	/**
	 * Add the user to the given chat room location. It also removes the user from his
	 * old location if any using the userloc table. It creates a new UserList if missing
	 * in userlist table for this location. It adds the supplied user data at index of
	 * its clientId in the UserList. It also sets the expires value in future, so that
	 * the user data will expire if not refreshed. If the method is called to refresh the
	 * existing data, it does not increment the UserList version. Otherwise if this method
	 * adds a new user data or if this method changes the name of the user data, it 
	 * increments the version. Finally, it makes sure that the user list if limited to
	 * the configured size.
	 * 
	 * @param location
	 * @param data
	 */
	public synchronized void addUserList(String location, User data) {
		// update userloc
		String oldLocation = userloc.get(data.clientId);
		userloc.put(data.clientId, location);
		
		// add to the new location in userlist
		UserList value = userlist.get(location);
		if (value == null) {
			value = new UserList();
			userlist.put(location, value);
		}
		
		// update expiration
		data.expires = (new Date()).getTime() + DEFAULT_USER_EXPIRES;
		if (!value.data.containsKey(data.clientId) || data.name == null || 
			!data.name.equals(value.data.get(data.clientId).name)) 
			value.version += 1;
		value.data.put(data.clientId, data);
		
		// then remove the user from his old location
		if (oldLocation != null && !oldLocation.equals(location)) {
			UserList users = userlist.get(oldLocation);
			if (users != null) {
				users.data.remove(data.clientId);
				users.version += 1;
			}
		}
	}
	
	/**
	 * Remove the user data from any existing chat room location that this user
	 * belongs to. It also increments the associated UserList's version.
	 * 
	 * @param data
	 */
	public synchronized void removeUserList(User data) {
		// update userloc
		String oldLocation = userloc.get(data.clientId);
		userloc.remove(data.clientId);
		
		// then remove the user from his old location
		if (oldLocation != null) {
			UserList users = userlist.get(oldLocation);
			if (users != null) {
				users.data.remove(data.clientId);
				users.version += 1;
			}
		}
	}
	
	/**
	 * Remove all expired user data from UserList of all the chat room locations in
	 * the userlist table. It updates the UserList version if a user data is removed
	 * from that UserList. It updates both the userlist and userloc tables.
	 */
	public synchronized void removeExpiredUserList() {
		long now = (new Date()).getTime();
		
		for (Iterator<String> it=userlist.keySet().iterator(); it.hasNext(); ) {
			String location = it.next();
			UserList users = userlist.get(location);
			boolean modified = false;
			
			List<User> toRemove = new LinkedList<User>();
			for (Iterator<User> it2=users.data.values().iterator(); it2.hasNext(); ) {
				User data = it2.next();
				if (data.expires < now) {
					toRemove.add(data);
				}
			}
			
			for (Iterator<User> it2=toRemove.iterator(); it2.hasNext(); ) {
				User data = it2.next();
				users.data.remove(data.clientId);
				modified = true;
				if (location != null && location.equals(userloc.get(data.clientId))) {
					userloc.remove(data.clientId);
				}
			}
			
			if (modified)
				users.version += 1;
		}
	}
	
	/**
	 * Check whether the given user is member of the userlist of the given chat room
	 * location. It compares the user by name, without using the clientId. This method
	 * is used rarely only for sending targetted chat messages to a specific user --
	 * if the specific user by name doesn't exist, the service returns an error.
	 * 
	 * @param location
	 * @param user
	 * @return true or false
	 */
	public synchronized boolean hasUserList(String location, String user) {
		UserList users = userlist.get(location);
		if (users == null)
			return false;
		for (Iterator<User> it=users.data.values().iterator(); it.hasNext(); ) {
			User data = it.next();
			if (user.equalsIgnoreCase(data.name))
				return true;
		}
		
		return false;
	}
	
	/**
	 * Get the chat room type: "domain", "page" or "none", for the given
	 * chat room domain. 
	 * 
	 * @param domain The chat room domain.
	 * @return
	 */
	public synchronized String getChatRoomType(String domain) {
		if (!sites.containsKey(domain)) // default is enabled
			return CHATROOM_BY_DOMAIN;
		
		Boolean enabled = sites.get(domain);
		return (enabled.booleanValue() ? CHATROOM_BY_PAGE : CHATROOM_DISABLED);
	}
}
