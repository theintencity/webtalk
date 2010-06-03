import org.restlet.Context;  
import org.restlet.data.MediaType;  
import org.restlet.data.Request;  
import org.restlet.data.Response;  
import org.restlet.resource.Representation;  
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;  
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

/**
 * The resource to handle GET /api/roomtype
 * @author mamta
 */
public class RoomTypeResource extends Resource {
	
	public RoomTypeResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		getVariants().add(new Variant(MediaType.TEXT_PLAIN));
	}  
	 
	/**
	 * Handle GET /api/roomtype with parameters location={location}. The location is
	 * actually a domain name, and the response is a string "domain", "page", or "none"
	 * indicating that the chat room location for that domain will be on a
	 * per-domain or per-page basis, or disallowed.
	 */
	@Override  
	public Representation represent(Variant variant) throws ResourceException {
		String location = (String) getRequest().getAttributes().get("location");
		String type = Database.getInstance().getChatRoomType(location);
		return new StringRepresentation(type, MediaType.TEXT_PLAIN);
	}  
}  
