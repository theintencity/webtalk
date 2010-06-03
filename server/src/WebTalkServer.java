import java.io.FileInputStream;
import java.util.Properties;

import org.restlet.Application;  
import org.restlet.Component;
import org.restlet.Restlet;  
import org.restlet.Router; 
import org.restlet.data.Protocol;

/**
 * The main application server.
 * @author mamta
 */
public class WebTalkServer extends Application {  
  
	/**
	 * Assign the handlers for various routes.
	 */
    @Override  
    public Restlet createRoot() {  
        Router router = new Router(getContext());  
  
        // Defines route  
        router.attach("/api/roomtype?location={location}", RoomTypeResource.class);
        router.attach("/api/userlist/delete?location={location}", UserListResource.class);
        router.attach("/api/userlist?location={location}&since={version}", UserListResource.class);
        router.attach("/api/userlist?location={location}", UserListResource.class);
        router.attach("/api/chathistory?location={location}&since={version}&target={target}", ChatHistoryResource.class);
        router.attach("/api/chathistory?location={location}&since={version}", ChatHistoryResource.class);
        router.attach("/api/chathistory?location={location}&target={target}", ChatHistoryResource.class);
        router.attach("/api/chathistory?location={location}", ChatHistoryResource.class);
        router.attach("/download", StaticResource.class);
  
        return router;  
    }  

    /**
     * Start the server using the specified port read from properties file.
     * 
     * @param args Ignored.
     */
    public static void main(String[] args) {
        try {
        	// read the configuration from property file is available, or null.
        	int port = 8080; // default 
			FileInputStream in = new FileInputStream("webtalk.properties");
			Properties prop = new Properties();
			prop.load(in);
			in.close();
			
			port = Integer.parseInt(prop.getProperty("port", "8080"));
        	// validate certain property items.
        	if (port <= 1024 || port >= 65536) {
        		System.err.println("port must be > 1024 and < 65536. port=" + port);
        		return;
        	}
        	
        	// initialize the database
        	Database.getInstance();
        	
            // Create a new Component.
            Component component = new Component();

            // Add a new HTTP server listening on port.
            component.getServers().add(Protocol.HTTP, port);

            // Attach the application.
            component.getDefaultHost().attach(new WebTalkServer());

            // Start the component.
            component.start();
        } catch (Exception e) {
            // Something is wrong.
            e.printStackTrace();
        }
    }
}  
