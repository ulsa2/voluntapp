package req;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;

import db.CodeGenerator;

public class CalendarSubscriptionRequest implements Request {

  private String joinCode;
  private String targetUserEmail;
  private String role;

  /**
   * Fields excluded from deserialisation.
   */
  private transient int userId;

  /**
   * No-arg constructor for compatibility with gson serialiser.
   */
  public CalendarSubscriptionRequest() {
  }

  @Override
  public boolean isValid() {
    return joinCode != null && joinCode.length() == CodeGenerator.CODE_LENGTH
        && StringUtils.isAlphanumeric(joinCode);
  }

  public String getJoinCode() {
    return joinCode;
  }
  
  public String getTargetUserEmail() {
    return targetUserEmail;
  }

  public String getRole() {
    return role;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  @VisibleForTesting
  protected void setJoinCode(String joinCode) {
    this.joinCode = joinCode;
  }
}
