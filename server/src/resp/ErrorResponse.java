package resp;

/**
 * An error response indicating that the request has failed. Contains an error
 * message and a status code that can be used to trigger different error
 * handling behaviour on the client.
 */
public class ErrorResponse extends Response {

  /**
   * User friendly error message.
   */
  private String message;

  /**
   * No-arg constructor for compatibility with gson serialiser.
   */
  public ErrorResponse() {
    this(null);
  }

  /**
   * Constructs an error response with the given message. Sets status code to 1.
   * 
   * @param message user friendly error message
   */
  public ErrorResponse(String message) {
    this.message = message;
    statusCode = 1;
  }
}
