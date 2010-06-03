import java.io.File;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;

/**
 * Resource to handle GET /download
 * @author mamta
 */
public class StaticResource  extends Resource {
	
	public StaticResource(Context context, Request request, Response response) {  
		super(context, request, response);  
		
		getVariants().add(new Variant(MediaType.APPLICATION_ALL));
	}  
	
	/**
	 * Handle GET /api/download/somefile to download the index.html and webtalk.xpi
	 * files. It redirects /api/download to /api/download/ and assumes
	 * /api/download/ is same as /api/download/index.html. It uses correct
	 * content-type in the response, so that the Firefox  browser can recognize .xpi
	 * as an extension, and install it.
	 */
	@Override
	public Representation represent(Variant variant) throws ResourceException {
		String filename = getRequest().getResourceRef().getRemainingPart();
		
		if ("".equals(filename)) {
			getResponse().setLocationRef("/download/");
			throw new ResourceException(Status.REDIRECTION_TEMPORARY, "Redirection");
		}
		
		File file = new File("download" + filename);
		if (file.isDirectory()) {
			file = new File(file.getPath() + (file.getPath().endsWith("/") ? "" : "/") + "index.html");
		}
		if (!file.exists())
			throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "File Not Found");
		
		MediaType type = MediaType.APPLICATION_OCTET_STREAM;
		int index = file.getName().lastIndexOf('.');
		if (index >= 0) {
			String ext = file.getName().substring(index+1).toLowerCase();
			if ("html".equals(ext))
				type = MediaType.TEXT_HTML;
			else if ("xpi".equals(ext))
				type = new MediaType("application/x-xpinstall");
		}
		
		return new FileRepresentation(file, type);
	}

}
