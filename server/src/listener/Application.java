package listener;

import java.util.EnumSet;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.postgresql.ds.PGConnectionPoolDataSource;

import servlet.CalendarServlet;
import servlet.CalendarSubscriptionServlet;
import servlet.StaticFileServlet;
import servlet.EventServlet;
import servlet.EventSubscriptionServlet;
import servlet.SavedEventServlet;
import servlet.SessionServlet;
import servlet.UserServlet;
import servlet.ValidationServlet;
import utils.DataSourceProvider;

import com.google.gson.Gson;

import db.CodeGenerator;
import db.DBInterface;
import db.SessionManager;
import filter.AuthorizationFilter;
import filter.JsonFilter;

/**
 * Main application context that maps servlets to their respective URI and
 * injects appropriate dependencies to servlet constructors, for eg. json
 * serialiser and database interface. Uses database connection pool managed by
 * container instead of single connection.
 */
@WebListener
public class Application implements ServletContextListener {

  public static final Logger logger = Logger.getLogger("Logs'R'Us");

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    ServletContext context = sce.getServletContext();

    // Using connection pool managed by servlet container
    PGConnectionPoolDataSource source = DataSourceProvider.getSource();

    // Reusable objects for all servlets (must be thread safe).
    Gson gson = new Gson();
    DBInterface db = new DBInterface(source);
    SessionManager sm = new SessionManager(db);
    CodeGenerator cg = new CodeGenerator();

    // Initialise filters
    context.addFilter(AuthorizationFilter.class.getSimpleName(),
        new AuthorizationFilter(db)).addMappingForUrlPatterns(
        EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), true,
        "/api/*");
    context.addFilter(JsonFilter.class.getSimpleName(), new JsonFilter(gson))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true,
            "/api/*");

    // Instantiate servlets and add mappings
    context.addServlet(StaticFileServlet.class.getName(), new StaticFileServlet(db))
        .addMapping("");
    context.addServlet(UserServlet.class.getName(), new UserServlet(gson, db))
        .addMapping("/api/user");
    context.addServlet(SessionServlet.class.getName(),
        new SessionServlet(gson, db, sm)).addMapping("/api/session");
    context.addServlet(CalendarServlet.class.getName(),
        new CalendarServlet(gson, db)).addMapping("/api/calendar",
        "/api/calendar/*");
    context
        .addServlet(EventServlet.class.getName(), new EventServlet(gson, db))
        .addMapping("/api/event", "/api/event/*");
    context.addServlet(CalendarSubscriptionServlet.class.getName(),
        new CalendarSubscriptionServlet(gson, db)).addMapping(
        "/api/subscription/calendar", "/api/subscription/calendar/*");
    context.addServlet(EventSubscriptionServlet.class.getName(),
        new EventSubscriptionServlet(gson, db)).addMapping(
        "/api/subscription/event", "/api/subscription/event/*");
    context.addServlet(SavedEventServlet.class.getName(),
        new SavedEventServlet(db)).addMapping(
            "/api/save/event", "/api/save/event/*");
    context.addServlet(ValidationServlet.class.getName(),
        new ValidationServlet(gson, db, cg)).addMapping("/validate");

  }

  @Override
  public void contextDestroyed(ServletContextEvent arg0) {
  }

}
