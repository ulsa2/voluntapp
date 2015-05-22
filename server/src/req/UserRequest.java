package req;

import org.apache.commons.validator.routines.EmailValidator;

/**
 * Deserialized JSON object of an API request to login a user.
 */
public class UserRequest implements Request {

  private String email;
  private String password;

  @Override
  public boolean isValid() {
    return (email != null && EmailValidator.getInstance().isValid(email))
        && (password != null && password.length() >= 6);
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }
}