package req;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import resp.EventResponse;
import resp.EventSubscriptionResponse;
import resp.Response;
import utils.EventStatus;

/**
 * Deserialized JSON object of an API request to create a calendar.
 */
public class CalendarRequest implements Request {

  /**
   * Length of time interval (in miliseconds). Events in the specified time
   * interval [startDate, startDate + TIME_INTERVAL] are going to be returned on
   * calendar query.
   */
  public static final int TIME_INTERVAL = 14 * 24 * 60 * 60 * 1000;

  /**
   * Calendar details sent by the client.
   */
  private String name;
  private boolean joinEnabled;
  private Timestamp startDate;

  /**
   * Fields excluded from deserialisation.
   */
  private transient int calendarId;
  private transient String inviteCode;
  private transient int userId;
  private transient boolean isAdmin = false;

  /**
   * No-arg constructor for compatibility with gson serialiser.
   */
  public CalendarRequest() {
  }

  /**
   * Constructor called by calendar servlet to get user calendars.
   * 
   * @param userId
   * @param calendarId
   */
  public CalendarRequest(int userId, int calendarId) {
    this.userId = userId;
    this.calendarId = calendarId;
  }

  /* Constructor added for testing. */
  public CalendarRequest(int userId, Timestamp startDate, int calendarId) {
    this.userId = userId;
    this.startDate = startDate;
    this.calendarId = calendarId;
  }

  @Override
  public boolean isValid() {
    return (name != null && !name.isEmpty());
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public void setInviteCode(String inviteCode) {
    this.inviteCode = inviteCode;
  }

  public String getName() {
    return name;
  }

  public boolean isJoinEnabled() {
    return joinEnabled;
  }

  public String getInviteCode() {
    return inviteCode;
  }

  public int getUserId() {
    return userId;
  }

  public Integer getCalendarId() {
    return calendarId;
  }

  public Timestamp getStartDate() {
    return startDate;
  }

  public CalendarEventsQuery getCalendarEventsQuery() {
    return new CalendarEventsQuery();
  }

  public void setCalendarId(int calendarId) {
    this.calendarId = calendarId;
  }

  public class CalendarEventsQuery extends Response {
    private ResultSet rs;
    private List<EventResponse> events = new ArrayList<EventResponse>();
    private boolean isPriviledged;
    
    /*
     * WITH x AS (SELECT "EID", COUNT(*) FROM "EVENT_USER" GROUP BY "EID")
     * SELECT "EVENT"."EID", "TITLE", "DESCRIPTION", "LOCATION", "DATE", "TIME",
     * "DURATION", "count", "MAX_ATTENDEES", EXISTS (SELECT "UID" FROM
     * "EVENT_USER" WHERE "UID"=76 AND "EID"="EVENT"."EID") FROM x RIGHT OUTER
     * JOIN "EVENT" ON x."EID" = "EVENT"."EID" WHERE ("DATE" + "TIME", "DATE" +
     * "TIME" + "DURATION") OVERLAPS ('1000-03-03', '3000-03-03') AND
     * "EVENT"."EID" IN (SELECT "EID" FROM "CALENDAR_EVENT" WHERE "CID"=31);
     */

    public String getSQLQuery() {
      String visibleEvents = (isPriviledged) ? 
          /* If user is privileged (admin/editor) allow him to see all
           * non-deleted events. */
          String.format("\"%s\"!='%s'::\"%s\"", EventResponse.ACTIVE_COLUMN, 
              EventStatus.DELETED.getName(), EventStatus.STATUS_ENUM_NAME) :
          /* If the user is a basic user, show him only the active events. */
          String.format("\"%s\"='%s'::\"%s\"", EventResponse.ACTIVE_COLUMN, 
              EventStatus.ACTIVE.getName(), EventStatus.STATUS_ENUM_NAME);
      return String.format(
              "WITH x AS (SELECT \"%s\", COUNT(*) FROM \"EVENT_USER\" GROUP BY \"%s\")"
                  + "SELECT  \"EVENT\".\"EID\", \"%s\", \"%s\", \"%s\", \"%s\", \"%s\", \"%s\", \"count\", \"%s\", \"%s\","
                  + "EXISTS (SELECT \"%s\" FROM \"EVENT_USER\" WHERE \"%s\"=? AND \"%s\"=\"EVENT\".\"%s\")"
                  + "FROM x RIGHT OUTER JOIN \"EVENT\" ON x.\"%s\" = \"EVENT\".\"%s\" "
                  + "WHERE (\"DATE\" + \"TIME\", \"DATE\" + \"TIME\" + \"DURATION\") "
                  + "OVERLAPS (?, ?) AND " + visibleEvents + " AND "
                  + "\"EVENT\".\"EID\" IN (SELECT \"EID\" FROM \"CALENDAR_EVENT\" WHERE \"CID\"=?);",
              EventSubscriptionResponse.EID_COLUMN,
              EventSubscriptionResponse.EID_COLUMN, EventResponse.TITLE_COLUMN,
              EventResponse.DESC_COLUMN, EventResponse.LOCATION_COLUMN,
              EventResponse.DATE_COLUMN, EventResponse.TIME_COLUMN,
              EventResponse.DURATION_COLUMN, EventResponse.MAX_ATTEDEE_COLUMN,
              EventResponse.ACTIVE_COLUMN,
              EventSubscriptionResponse.EID_COLUMN,
              EventSubscriptionResponse.UID_COLUMN,
              EventSubscriptionResponse.EID_COLUMN, EventResponse.EID_COLUMN,
              EventSubscriptionResponse.EID_COLUMN, EventResponse.EID_COLUMN
              );
    }
    
    /*
     * WITH y AS (WITH x AS (SELECT "CREATOR", "EID" FROM "CALENDAR" JOIN "CALENDAR_EVENT" ON "CALENDAR"."ID" = "CALENDAR_EVENT"."CID")
SELECT x."CREATOR", "EVENT"."EID" FROM "EVENT" JOIN x ON "EVENT"."EID" = x."EID")

UPDATE "EVENT" SET "CREATOR" = z."CREATOR"
FROM y AS z
WHERE "EVENT"."EID"=z."EID";
     * (non-Javadoc)
     * @see resp.Response#formatSQLQuery(java.sql.PreparedStatement)
     */

    @Override
    public void formatSQLQuery(PreparedStatement prepare) throws SQLException {
      Timestamp endDate = new Timestamp(startDate.getTime() + TIME_INTERVAL);
      prepare.setInt(1, userId);
      prepare.setTimestamp(2, startDate);
      prepare.setTimestamp(3, endDate);
      prepare.setInt(4, calendarId);
    }

    @Override
    public void setResult(ResultSet result) {
      this.rs = result;
      try {
        EventResponse resp = new EventResponse();
        resp.setResult(result);
        while (resp.isFound()) {
          resp.setCalendarId(calendarId);
          resp.setCurrentCount(rs.getString("count"));
          resp.setJoined(rs.getBoolean("exists"));
          events.add(resp);
          resp = new EventResponse();
          resp.setResult(result);
        }
      } catch (SQLException e) {
        System.err.println("Error getting the result while creating "
            + "calendarResponse object.");
        return;
      }
    }

    public List<EventResponse> getEvents() {
      return events;
    }
    
    public void setPriviledge() {
      isPriviledged = true;
    }
  }
}
