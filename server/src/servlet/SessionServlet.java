package servlet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import req.UserRequest;
import resp.ErrorResponse;
import resp.Response;
import resp.SessionResponse;
import resp.SuccessResponse;
import resp.UserResponse;
import utils.PasswordUtils;

import com.google.gson.Gson;

import db.DBInterface;
import db.SessionManager;
import exception.InconsistentDataException;
import exception.PasswordHashFailureException;
import exception.UserNotFoundException;

/**
 * Provide login and logout service.
 */
@WebServlet
public class SessionServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private final Gson gson;
  private final DBInterface db;
  private final SessionManager sm;

  public SessionServlet(Gson gson, DBInterface db, SessionManager sm) {
    this.gson = gson;
    this.db = db;
    this.sm = sm;
  }

  /**
   * Creates a new session token based on user email and password.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // Handle forwarded login request from user servlet
    Integer userId = (Integer) request.getAttribute("userId");
    if (userId != null) {
      try {
        // Start a new session
        String sessionId = sm.startSession(userId);
        SessionResponse loginResponse = new SessionResponse(sessionId);

        // TODO: figure out why serialisation fails here
        request.setAttribute(Response.class.getSimpleName(), loginResponse);

        // Set the session cookie if request comes from a browser
        Cookie cookie = createSessionCookie(loginResponse);
        response.addCookie(cookie);

      } catch (SQLException e) {
        // Return an error message
        request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
            e.getMessage()));
      }
      return;
    }

    // Parse user login request
    UserRequest user = gson.fromJson(request.getReader(), UserRequest.class);

    // Handle request in a RESTful manner
    Response loginResponse = login(user);
    if (loginResponse instanceof SessionResponse) {
      // Set the session cookie if request comes from a browser
      Cookie cookie = createSessionCookie(loginResponse);
      response.addCookie(cookie);
    }

    // Pass response object to serialisation filter
    request.setAttribute(Response.class.getSimpleName(), loginResponse);
  }

  /**
   * TODO: Refreshes the current session token.
   */
  @Override
  public void doPut(HttpServletRequest request, HttpServletResponse response)
      throws IOException {}

  /**
   * Logs out the user and remove its session.
   */
  @Override
  public void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    SessionResponse session =
        (SessionResponse) request.getAttribute(SessionResponse.class
            .getSimpleName());

    // Invalidate session on server
    if (!sm.closeSession(session.getSessionId())) {
      request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
          "Unable to log out."));
      return;
    }

    request.setAttribute(Response.class.getSimpleName(), new SuccessResponse(
        "You have successfully logged out."));
  }

  private Cookie createSessionCookie(Response resp) {
    /*
     * if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
     * HttpSession jsession = request.getSession(); response.sendRedirect("/");
     * }
     */
    SessionResponse session = (SessionResponse) resp;
    Cookie cookie = new Cookie("token", session.getSessionId());
    cookie.setPath("/");
    // cookie.setHttpOnly(true);
    return cookie;
  }

  private Response login(UserRequest req) {
    // Validate login
    if (!req.isValid()) {
      return new ErrorResponse("You have entered invalid login information.");
    }

    try {
      UserResponse user = db.getUser(req);

      // Check that password matches the hashed value
      if (!PasswordUtils.validatePassword(req.getPassword(),
          user.getHashedPassword())) {
        return new ErrorResponse("You have entered a wrong password.");
      }

      // Start a new session to support multi-client login
      String token = sm.startSession(user.getUserId());

      // Successfully logged in
      return new SessionResponse(token);
    } catch (SQLException | UserNotFoundException | InconsistentDataException
        | PasswordHashFailureException e) {
      return new ErrorResponse(e.getMessage());
    }
  }
}