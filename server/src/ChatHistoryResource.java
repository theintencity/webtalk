import java.io.IOException;
import java.net.URLDecoder;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Representation;  
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;  
import org.restlet.resource.Variant;

/**
 * The resource to handle GET and POST /api/chathistory
 * 
 * @author mamta
 */
public class ChatHistoryResource extends Resource {
	
	public ChatHistoryResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}  
	 
	@Override
	public boolean allowPost() {
		return true;
	}
	
	/**
	 * Handle GET /api/chathistory with parameters such as
	 * location={location}, since={version} or target={target}
	 * It returns the chat history of the given chat room location if modified since the 
	 * supplied version. If the target is specified, the chat history is for the 
	 * given target, and may include any target specific chat messages. If the
	 * chat history for the chat room location is not modified since the supplied
	 * version, it returns "304 Not Modified" response. 
	 * 
	 * The response object has version and chathistory properties, e.g.,
	 * {"version":4,"chathistory":[...]}
	 * Each item in the chathistory array is an object, e.g.,
	 * {"sender":"User 1","timestamp":123456789,"text":"Hello There"}
	 */
	@SuppressWarnings("deprecation")
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		try {
			String location = (String) getRequest().getAttributes().get("location");
			String target = (String) getRequest().getAttributes().get("target");
			if (target != null)
				target = URLDecoder.decode(target);
			Database.ChatHistory value = Database.getInstance().getChatHistory(location);
			String version = (String) getRequest().getAttributes().get("version");
			if (value != null && String.valueOf(value.version).equals(version)) {
				throw new ResourceException(Status.REDIRECTION_NOT_MODIFIED, "Not Modified");
			}
			
			JSONObject object = new JSONObject();
			JSONArray list = new JSONArray();
			if (value != null) {
				for (Iterator<Database.Chat> it=value.data.iterator(); it.hasNext(); ) {
					Database.Chat msg = it.next();
					if (target == null && msg.target == null 
						|| (target != null && (msg.target == null || target.equalsIgnoreCase(msg.target)))) {
						JSONObject obj = new JSONObject();
						obj.put("sender", msg.sender);
						obj.put("timestamp", msg.timestamp);
						obj.put("text", msg.text);
						list.put(obj);
					}
				}
			}
			object.put("chathistory", list);
			object.put("version", (value != null ? value.version : 0));
			
			JsonRepresentation result = new JsonRepresentation(object);
			return result;
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Exception");
		}
	}  

	/**
	 * Handle POST /api/chathistory with parameters such as location={location} and target={target}.
	 * The request body should have an object of the form
	 * {"sender":"User 2","timestamp":123456789,"text":"Hello again"}
	 * The target parameter is optional, and if present, makes this chat message only available
	 * to the supplied target user name. If the target user is not part of this chat room
	 * location, and error "404 Target User Not Found" is returned.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void acceptRepresentation(Representation entity) throws ResourceException {
		
		if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON, true))
			throw new ResourceException(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Not Acceptable");
		
		try {
			String location = (String) getRequest().getAttributes().get("location");
			String target = (String) getRequest().getAttributes().get("target");
			if (target != null) 
				target = URLDecoder.decode(target);
			if (target != null && !Database.getInstance().hasUserList(location, target))
				throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Target User Not Found");
			
			JSONObject obj = (new JsonRepresentation(entity)).toJsonObject();
			Database.Chat msg = new Database.Chat();
			msg.sender = obj.getString("sender");
			msg.target = target;
			msg.timestamp = obj.getLong("timestamp");
			msg.text = obj.getString("text");
			Database.getInstance().addChatHistory(location, msg);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing Content");
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing JSON");
		}
	}
}  
