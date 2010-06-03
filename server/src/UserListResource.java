import java.io.IOException;
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
 * Resource to handle GET and POST /api/userlist and POST /api/userlist/delete
 * 
 * @author mamta
 *
 */
public class UserListResource extends Resource {  
  
	public UserListResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}  
	
	/**
	 * Since Javascript from the client has difficulty invoking the HTTP DELETE method
	 * directly, it maps POST /api/userlist/delete to DELETE /api/userlist request.
	 */
	@Override 
	public void handlePost() {
		Request request = getRequest();
		String lastSegment = request.getResourceRef().getLastSegment();
		if ("delete".equals(lastSegment)) {
			super.handleDelete();
		}
		else {
			super.handlePost();
		}
	}
	
	@Override
	public boolean allowPost() {
		return true;
	}
	
	@Override
	public boolean allowDelete() {
		return true;
	}
	
	/**
	 * Handle GET /api/userlist with parameters such as location={location} and
	 * since={version} and return the user list for the supplied chat room location
	 * if it is modified since the supplied version. If the user list is not
	 * modified, then it returns "304 Not Modified". The returns object has
	 * version and userlist properties, e.g.,
	 * {"version":5,"userlist":[...]} where each item in the array represents a
	 * user of the form {"clientId":123456,"name":"User 1"}.
	 */
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		try {
			String location = (String) getRequest().getAttributes().get("location");
			Database.UserList value = Database.getInstance().getUserList(location);
			String version = (String) getRequest().getAttributes().get("version");
			if (value != null && String.valueOf(value.version).equals(version)) {
				throw new ResourceException(Status.REDIRECTION_NOT_MODIFIED, "Not Modified");
			}
			
			JSONObject object = new JSONObject();
			JSONArray list = new JSONArray();
			if (value != null) {
				long now = System.currentTimeMillis();
				for (Iterator<Database.User> it=value.getList().iterator(); it.hasNext(); ) {
					Database.User data = it.next();
					if (data.expires > now) {
						JSONObject obj = new JSONObject();
						obj.put("name", data.name);
						obj.put("clientId", data.clientId);
						list.put(obj);
					}
				}
			}
			object.put("userlist", list);
			object.put("version", (value != null ? value.version : 0));
			
			JsonRepresentation result = new JsonRepresentation(object);
			return result;
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, "Exception");
		}
	}  

	/**
	 * Handle POST /api/userlist with parameter location={location}. The request body
	 * should contain the user object of the form {"clientId":12345,"name":"User 2"}
	 * which gets added to the user list of the given chat room location.
	 */
	@Override
	public void acceptRepresentation(Representation entity) throws ResourceException {
		
		if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON, true))
			throw new ResourceException(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Not Acceptable");
		
		try {
			String location = (String) getRequest().getAttributes().get("location");
			JSONObject obj = (new JsonRepresentation(entity)).toJsonObject();
			Database.User data = new Database.User();
			data.name = obj.getString("name");
			data.clientId = obj.getString("clientId");
			Database.getInstance().addUserList(location, data);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing Content");
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing JSON");
		}
	}
	
	/**
	 * Handle DELETE /api/userlist with request body containing user data of the form
	 * {"clientId":12345,"name":"User 3"} and remove the given user from the user list
	 * of the chat room that he is currently in.
	 */
	@Override
	public void removeRepresentations() throws ResourceException {
		Representation entity = getRequest().getEntity();
		if (!entity.getMediaType().equals(MediaType.APPLICATION_JSON, true))
			throw new ResourceException(Status.CLIENT_ERROR_NOT_ACCEPTABLE, "Not Acceptable");
		
		try {
			//String location = (String) getRequest().getAttributes().get("location");
			JSONObject obj = (new JsonRepresentation(entity)).toJsonObject();
			Database.User data = new Database.User();
			data.name = obj.getString("name");
			data.clientId = obj.getString("clientId");
			Database.getInstance().removeUserList(data);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing Content");
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Error Parsing JSON");
		}
	}
}  
