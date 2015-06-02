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
import utils.ServletUtils;

import com.google.gson.Gson;

import db.DBInterface;
import exception.InconsistentDataException;
import exception.InvalidActionException;

public class EventSubscriptionServlet extends HttpServlet {

  private final Gson gson;
  private final DBInterface db;
  private static final long serialVersionUID = 1L;

  public EventSubscriptionServlet(Gson gson, DBInterface db) {
    this.gson = gson;
    this.db = db;
  }

  // TODO: THIS IS ALL UNTESTED

  /**
   * Given the ID of the user (retrieved from the attribute of the request) get
   * the IDs of the events to which the user subscribed.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    int userId = ServletUtils.getUserId(request);
    if (userId == 0) {
      return;
    }

    Response subResp;
    try {
      subResp = db.getEventSubscription(userId);
    } catch (SQLException e) {
      subResp =
          new ErrorResponse("Error while retirieving the calendar IDs "
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
    // TODO: Handle exception, should the servlet methods throw them?
    int userId = ServletUtils.getUserId(request);
    if (userId == 0) {
      return;
    }
    EventSubscriptionRequest subReq =
        gson.fromJson(request.getReader(), EventSubscriptionRequest.class);
    subReq.setUserId(userId);

    Response subResp;
    try {
      subResp = db.putEventSubscription(subReq);
    } catch (SQLException e) {
      subResp =
          new ErrorResponse("Error while registering event subscription ");
      response.setStatus(HttpURLConnection.HTTP_BAD_REQUEST);
    } catch (InvalidActionException e) {
      subResp = new ErrorResponse("Tried to join a full event!");
      response.setStatus(HttpURLConnection.HTTP_BAD_REQUEST);
    }
    request.setAttribute(Response.class.getSimpleName(), subResp);
  }

  @Override
  public void doPut(HttpServletRequest request, HttpServletResponse response) {

  }

  /**
   * Given the ID of the event, delete user's subscription to the event.
   * 
   * @throws IOException
   */
  @Override
  public void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // TODO: Handle exception, should the servlet methods throw them?
    int userId = ServletUtils.getUserId(request);
    if (userId == 0) {
      return;
    }
    EventSubscriptionRequest subreq =
        gson.fromJson(request.getReader(), EventSubscriptionRequest.class);
    subreq.setUserId(userId);

    Response subResp;
    try {
      if (db.deleteEventSubscription(subreq) == 1) {
        subResp = new SuccessResponse("Unsubscribed from event");
      } else {
        subResp =
            new ErrorResponse("Unsubscribing failed, you are not subscribed");
      }
    } catch (SQLException | InconsistentDataException e) {
      subResp = new ErrorResponse("Unsubscribing went wrong, this is bad");
    }
    request.setAttribute(Response.class.getSimpleName(), subResp);
  }
}
