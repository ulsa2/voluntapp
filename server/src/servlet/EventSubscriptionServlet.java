package servlet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import req.EventSubscriptionRequest;
import resp.ErrorResponse;
import resp.Response;
import resp.SuccessResponse;
import utils.AuthLevel;
import utils.CalendarEventIdQuery;
import utils.ServletUtils;

import chat.DynamicUpdate;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import db.DBInterface;
import exception.InconsistentDataException;
import exception.InvalidActionException;

/**
 * End point for handling event subscription actions. Handle all user event
 * joining actions. Implements standard 4 methods for fetching, creating,
 * updating and deleting event subscriptions.
 * 
 * @author nc1813
 * 
 */
public class EventSubscriptionServlet extends HttpServlet {

  private final Gson gson;
  private final DBInterface db;
  private static final long serialVersionUID = 1L;

  public EventSubscriptionServlet(Gson gson, DBInterface db) {
    this.gson = gson;
    this.db = db;
  }

  /**
   * Given the ID of the user (retrieved from the attribute of the request) get
   * the IDs of the events to which the user subscribed.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    int userId = ServletUtils.getUserId(request);

    Response subResp;
    try {
      subResp = db.getEventSubscription(userId);
    } catch (SQLException e) {
      subResp = new ErrorResponse("Error while retirieving the calendar IDs "
          + "from the database." + e.getMessage());
    }
    request.setAttribute(Response.class.getSimpleName(), subResp);
  }

  /**
   * Given the ID of an event, register the user's subscription to the event.
   * 
   * @throws IOException
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    int userId = ServletUtils.getUserId(request);

    /* Get and validate the event ID. */
    String eventIdString = request.getPathInfo().substring(1);
    if (eventIdString == null) {
      request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
          "No event ID was specified."));
      return;
    }
    int eventId = Integer.parseInt(eventIdString);

    /*
     * Check if the user is subscribed to the calendar corresponding to the
     * given event.
     */
    int calendarId = db.getCalendarId(new CalendarEventIdQuery(eventId));
    if (db.authoriseUser(userId, calendarId) == AuthLevel.NONE) {
      request
          .setAttribute(
              Response.class.getSimpleName(),
              new ErrorResponse(
                  "You cannot join the specified event, you are not subscribed to this calendar."));
      return;
    }

    /* Disallow joining the past events. */
    try {
      if (db.isPastEvent(eventId)) {
        request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
            "You cannot join events that have already finished."));
        return;
      }
    } catch (SQLException e) {
      request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
          "Data base error occured." + e.getMessage()));
      return;
    }

    /* Register user subscription. */
    Response subResp;
    try {
      subResp = db.putEventSubscription(eventId, userId);
      // Send dynamic update to the online subscribers
      DynamicUpdate.sendEventJoin(calendarId, new EventSubscriptionRequest(
          userId, eventId));
    } catch (SQLException e) {
      subResp = new ErrorResponse("Error while registering event subscription.");
      response.setStatus(HttpURLConnection.HTTP_BAD_REQUEST);
    } catch (InvalidActionException e) {
      subResp = new ErrorResponse("Tried to join a full event.");
      response.setStatus(HttpURLConnection.HTTP_BAD_REQUEST);
    }
    request.setAttribute(Response.class.getSimpleName(), subResp);
  }

  /**
   * Unsupported method.
   */
  @Override
  public void doPut(HttpServletRequest request, HttpServletResponse response) {
    request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
        "PUT method is not supported."));
  }

  /**
   * Given the ID of the event, delete user's subscription to the event. If the
   * user is an admin of the calendar on which the event occurs, allow the users
   * to delete the event subscriptions of other users.
   */
  @Override
  public void doDelete(HttpServletRequest request, HttpServletResponse response) {
    int userId = ServletUtils.getUserId(request);
    int userToDelete;

    /* Get and validate the event ID. */
    String eventIdString = request.getPathInfo().substring(1);
    if (eventIdString == null) {
      request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
          "No event ID was specified."));
      return;
    }
    int eventId = Integer.parseInt(eventIdString);

    /*
     * Check if the user is an admin of the calendar, if yes then serialise the
     * submitted user IDs.
     */
    int calendarId = db.getCalendarId(new CalendarEventIdQuery(eventId));
    if (db.authoriseUser(userId, calendarId) == AuthLevel.ADMIN) {
      try {
        EventSubscriptionRequest req = gson.fromJson(request.getReader(),
            EventSubscriptionRequest.class);
        /*
         * If admin sent payload, delete specified user's subscription,
         * otherwise delete admin's subscription.
         */
        userToDelete = (req != null) ? req.getUserId() : userId;
      } catch (JsonSyntaxException | JsonIOException | IOException e) {
        request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
            "Could not serialize JSON object."));
        return;
      }
    } else {
      userToDelete = userId;
    }

    /* Prevent deleting subscription from past events. */
    try {
      if (db.isPastEvent(eventId)) {
        request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
            "You cannot delete subscription to a past event."));
        return;
      }
    } catch (NumberFormatException | SQLException e1) {
      request.setAttribute(Response.class.getSimpleName(), new ErrorResponse(
          "Data base error occured."));
    }

    /* Delete event subscription(s). */
    Response resp = null;
    try {
      if (db.deleteEventSubscription(eventId, userToDelete)) {
        resp = new SuccessResponse("Unsubscribed from event");
        // Send dynamic update to the online subscribers
        DynamicUpdate.sendEventUnJoin(calendarId, new EventSubscriptionRequest(
            userToDelete, eventId));
      } else {
        resp = new ErrorResponse("Unsubscribing failed, you are not subscribed");
      }
    } catch (SQLException e) {
      resp = new ErrorResponse("Unsubscribing went wrong, this is bad");
    } catch (InconsistentDataException e) {
      resp = new ErrorResponse(e.getMessage());
    }
    request.setAttribute(Response.class.getSimpleName(), resp);
  }
}
