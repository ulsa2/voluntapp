package db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import req.CalendarRequest;
import req.EventRequest;
import req.RegisterRequest;
import req.SessionRequest;
import req.UserRequest;
import resp.CalendarResponse;
import resp.EventResponse;
import resp.Response;
import resp.SessionResponse;
import resp.UserResponse;
import sql.SQLInsert;
import sql.SQLQuery;
import sql.SQLUpdate;
import utils.PasswordUtils;
import exception.EventNotFoundException;
import exception.InconsistentDataException;
import exception.PasswordHashFailureException;
import exception.SessionNotFoundException;
import exception.UserNotFoundException;

/**
 * Class to manage the back-end to the database, deals with any and all queries.
 * 
 * @author bs2113
 * 
 */
public class DBInterface {

  private Connection conn;

  /**
   * Builds the interface with the given connection.
   * 
   * @param conn
   *          The connection to be used for the database.
   */
  public DBInterface(Connection conn) {
    this.conn = conn;
  }

  /**
   * Retrieve user entity from database.
   * 
   * @param uq
   *          User request containing the information to look up the user.
   * @return A UserResponse with the email,password and ID of the user.
   * @throws SQLException
   *           There was an error in the database.
   * @throws UserNotFoundException
   *           Thrown if the users data was not in the database.
   * @throws InconsistentDataException
   *           Thrown when the database is shown to be in a bad inconsistent
   *           state.
   */
  public UserResponse getUser(UserRequest uq) throws SQLException,
      UserNotFoundException, InconsistentDataException {

    UserResponse rs = new UserResponse(uq.getEmail(), uq.getPassword(),
        uq.getUserId(), null, null);

    query(rs);
    rs.checkResult();
    return rs;
  }

  /**
   * Retrieve session information from a supplied session ID number. Used to
   * find out the user id, which user is logged for rights, etc.
   * 
   * @param sid
   *          The ID of the session to be looked up.
   * @return The ID of the user that it belongs to.
   * @throws SQLException
   *           Thrown when there is an error with the database interaction.
   */
  public SessionResponse getSession(String sid) throws SQLException,
      SessionNotFoundException {
    SessionResponse sr = new SessionResponse(sid);
    query(sr);
    if (!sr.isFound()) {
      throw new SessionNotFoundException("The Session could not be found");
    }
    return sr;
  }

  /**
   * Retrieve a calendar by its id.
   * 
   * @param cr
   *          CalendarRequest
   * @return CalendarResponse
   */
  public CalendarResponse getCalendar(int calendarId) {
    // TODO: implement this
    return new CalendarResponse();
  }

  /**
   * Retrieve an event by its id.
   * 
   * @param er
   *          EventRequest
   * @return EventResponse
   */
  public EventResponse getEvent(int eventId) {
    // TODO: implement this
    return new EventResponse();
  }

  /**
   * Store user entity into database.
   * 
   * @param rq
   *          RegisterRequest that contains user registration information
   * @return userId primary key generated by database
   * @throws SQLException
   *           if insertion failed
   * @throws PasswordHashFailureException
   */
  public int putUser(RegisterRequest rq) throws SQLException,
      PasswordHashFailureException {

    // Get the password in and put it in the pass variable
    UserResponse us = new UserResponse(rq.getEmail(),
        PasswordUtils.getPasswordHash(rq.getPassword()),
        UserResponse.INVALID_USER_ID, rq.getFirstName(), rq.getLastName());

    Statement stmt;
    stmt = conn.createStatement();
    stmt.executeUpdate(us.getSQLInsert(), Statement.RETURN_GENERATED_KEYS);
    ResultSet rs = stmt.getGeneratedKeys();
    if (!rs.next()) {
      throw new SQLException();
    }

    return rs.getInt("ID");
  }

  /**
   * Store a new session to the database, called within the SessionManager
   * class.
   * 
   * @param sq
   *          The SessionRequest that is to be added.
   * @return Whether or not the insertion was a success.
   * @throws SQLException
   *           Thrown when there is a problem with the database interaction.
   */
  public boolean putSession(SessionRequest sq) throws SQLException {
    SessionResponse sr = new SessionResponse(sq.getSessionId(), sq.getUserId());
    return insert(sr);
  }

  /**
   * Store the calendar into the database.
   * 
   * @param cr
   *          CalendarRequest object submitted by client
   * @return CalendarResponse object
   * @throws SQLException
   */
  public CalendarResponse putCalendar(CalendarRequest cq) throws SQLException {
    CalendarResponse cr = new CalendarResponse(cq.getName(),
        cq.isJoinEnabled(), cq.getUserId(), cq.getInviteCode());
    cr.setCalendarID(getID(cr, CalendarResponse.CID_COLUMN));
    return cr;
  }

  /**
   * Utility method which retrieves the ID of the inserted database record.
   * 
   * @param insert
   *          SQLInsert object corresponding to the record to be added to the
   *          database
   * @param ColumnID
   * @return ID of the inserted database record
   * @throws SQLException
   */
  private int getID(SQLInsert insert, String ColumnID) throws SQLException {
    Statement stmt = conn.createStatement();
    stmt.executeUpdate(insert.getSQLInsert(), Statement.RETURN_GENERATED_KEYS);
    ResultSet rs = stmt.getGeneratedKeys();
    if (!rs.next()) {
      throw new SQLException();
    }
    return rs.getInt(ColumnID);
  }

  /**
   * Store the event into the database.
   * 
   * @param er
   *          EventRequest object submitted by client
   * @return event ID
   * @throws SQLException
   */
  public int putEvent(EventRequest ereq) throws SQLException {
    EventResponse eresp = new EventResponse(ereq.getTitle(),
        ereq.getDescription(), ereq.getLocation(), ereq.getDate(),
        ereq.getTime(), ereq.getDuration(), ereq.getMax(), -1,
        ereq.getCalendarId());
    return getID(eresp, EventResponse.EID_COLUMN);
  }

  /**
   * Updates a given users information. Uses the filled in fields in the
   * RegusterRequest to find out which ones to update, will ignore NULL fields.
   * 
   * @param userId
   *          The ID of the user to be updated.
   * @param rr
   *          The request object with the new information.
   * @return Whether or not the update with successful.
   * @throws SQLException
   *           Thrown when there is a problem with the database interaction.
   * @throws InconsistentDataException
   *           Thrown when more than one row was changed (This indicates a big
   *           problem with the information in the database).
   * @throws UserNotFoundException
   *           Thrown when the user could not be found in the database.
   */
  public boolean updateUser(int userId, RegisterRequest rr)
      throws SQLException, InconsistentDataException, UserNotFoundException {
    UserResponse ur = new UserResponse(rr.getEmail(), rr.getPassword(), userId,
        rr.getFirstName(), rr.getLastName());
    int rows = update(ur);
    if (rows == 1) {
      return true;
    } else if (rows == 0) {
      throw new UserNotFoundException(
          "The user could not be updated, as they don't exist");
    }
    throw new InconsistentDataException(
        "Update user info modified more than 1 row!");
  }

  /**
   * Functions to update event details for a given event Id.
   * 
   * @param eventId
   *          The id of the event which needs to be updated.
   * @param ereq
   *          The request containing the information that need to be put in.
   *          nulls indicate that the values will not change.
   * @return Whether or not the update was successful.
   * @throws SQLException
   *           Thrown when a database error occurs.
   * @throws EventNotFoundException
   *           Thrown when the event could not be found in the database.
   * @throws InconsistentDataException
   *           Thrown when the database is shown to be in a inconsistent state.
   *           This MUST be dealt with.
   */
  public boolean updateEvent(int eventId, EventRequest ereq)
      throws SQLException, EventNotFoundException, InconsistentDataException {
    EventResponse er = new EventResponse(ereq.getTitle(),
        ereq.getDescription(), ereq.getLocation(), ereq.getDate(),
        ereq.getTime(), ereq.getDuration(), ereq.getMax(), eventId, -1);
    return updateRowCheckHelper(er);
  }

  /**
   * Deletes an event (marks as inactive) from (in) the database.
   * 
   * @param eventId
   *          Event to be marked
   * @return Whether the marking update was successful or not
   * @throws EventNotFoundException
   *           When the eventId given was not found in the database
   * @throws InconsistentDataException
   *           When the deletion modified more than one row. This should never
   *           happen.
   * @throws SQLException
   *           Thrown when there is a error in interacting with the database
   */
  public boolean deleteEvent(int eventId) throws EventNotFoundException,
      InconsistentDataException, SQLException {
    EventResponse er = new EventResponse(null, null, null, null, null, null,
        null, eventId, -1, true);
    return updateRowCheckHelper(er);
  }

  /**
   * Helper to perform the update for a response /w a row check This should only
   * be used when you only expect ONE row to be changed by the query.
   * 
   * @param r
   *          Response representing the query to be run.
   * @return Whether or not the update was successful.
   * @throws EventNotFoundException
   *           See delete or update event.
   * @throws InconsistentDataException
   *           See delete or update event.
   * @throws SQLException
   *           See delete or update event.
   */
  private boolean updateRowCheckHelper(Response r)
      throws EventNotFoundException, InconsistentDataException, SQLException {
    int rows = update(r);
    if (rows == 1) {
      return true;
    }
    if (rows == 0) {
      throw new EventNotFoundException(
          "The event could not be altered, as it doesn't exist");
    }
    throw new InconsistentDataException(
        "Altering event info modified more than 1 row!");
  }

  /**
   * Used to log the user one, by deleting the session information.
   * 
   * @param sid
   *          The session ID to be removed.
   * @return Whether or not the deleting was successful.
   * @throws SQLException
   *           Thrown when there is an error with the database interaction.
   */
  public boolean deleteSession(String sid) throws SQLException {
    SessionResponse sr = new SessionResponse(sid, UserResponse.INVALID_USER_ID);
    int rowsChanged = update(sr);
    // If this it not 1 we may have a problem and wish to log it/
    return rowsChanged == 1;
  }

  /**
   * Function to run the SQLUpdate on the database, used for insertion
   * operations.
   * 
   * @param insertion
   *          The query to be executed.
   * @return Whether or not the insertion was successful.
   * @throws SQLException
   *           Thrown when there is an error with the database interaction.
   */
  private boolean insert(SQLInsert insertion) throws SQLException {
    Statement stmt;
    stmt = conn.createStatement();
    stmt.executeUpdate(insertion.getSQLInsert());
    return true;
  }

  /**
   * Function to run the SQLUpdate on the database, used for update/delete
   * operations. Returns 1 if the query is skipped due to it having no effect.
   * 
   * @param query
   *          The query to be executed.
   * @return How many rows were affected by the update.
   * @throws SQLException
   *           Thrown when there is an error with the database interaction.
   */
  private int update(SQLUpdate query) throws SQLException {
    Statement stmt;
    stmt = conn.createStatement();
    String q = query.getSQLUpdate();
    if (q != null) {
      int result = stmt.executeUpdate(q);
      query.checkResult(result);
      return result;
    }
    // The query is pointless, return 1 to signal success
    return 1;
  }

  /**
   * Function to run the SQLQuery on the database, used for querying operations.
   * 
   * This function returns results by setting them to fields within the SQLQuery
   * object that was passed in.
   * 
   * @param query
   *          The query to be executed.
   * @return Whether the query was successful.
   * @throws SQLException
   *           Thrown when there is an error with the database interaction.
   */
  private boolean query(SQLQuery query) throws SQLException {
    Statement stmt;
    stmt = conn.createStatement();
    ResultSet result = stmt.executeQuery(query.getSQLQuery());
    query.setResult(result);
    return true;
  }

  /**
   * Performs a database update to refresh a session.
   * 
   * @param userId
   *          The user whose session needs to be refreshed.
   * @return Whether the update was successful.
   * @throws SQLException
   *           Thrown when there is an error interacting with the database.
   */
  public boolean updateSession(int userId, String sessionId) throws SQLException {
    SessionResponse sr = new SessionResponse(sessionId, userId);
    Statement stmt;
    stmt = conn.createStatement();
    stmt.executeUpdate(sr.getSQLRefresh());
    return true;
  }
}
