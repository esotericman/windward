package org.flmelody.core;

import java.util.List;
import org.flmelody.core.exception.NoRequestBodyException;
import org.flmelody.core.exception.ValidationException;
import org.flmelody.util.JacksonUtil;
import org.flmelody.util.ValidationUtil;

/**
 * http context
 *
 * @author esotericman
 */
public class WindwardContext {
  private final WindwardRequest windwardRequest;
  private final WindwardResponse windwardResponse;
  private Boolean closed = Boolean.FALSE;

  public WindwardContext(WindwardRequest windwardRequest, WindwardResponse windwardResponse) {
    this.windwardRequest = windwardRequest;
    this.windwardResponse = windwardResponse;
  }

  /**
   * get parameter by name and type
   *
   * @param parameterName parameterName
   * @param <P> class type
   * @return parameter
   */
  public <P> P getRequestParameter(String parameterName) {
    List<String> parameters = this.windwardRequest.getQuerystring().get(parameterName);
    if (parameters == null || parameters.isEmpty()) {
      return null;
    }
    //noinspection unchecked
    return (P) parameters.get(0);
  }

  /**
   * get parameter as list
   *
   * @param parameterName parameterName
   * @return parameters list
   */
  public List<String> getRequestParameters(String parameterName) {
    return this.windwardRequest.getQuerystring().get(parameterName);
  }

  /**
   * get request body
   *
   * @return request body
   */
  public String getRequestBody() {
    return this.windwardRequest.getRequestBody();
  }

  /**
   * get windwardRequest
   *
   * @return windwardRequest
   */
  public WindwardRequest windwardRequest() {
    return this.windwardRequest;
  }

  /** close context */
  public void close() {
    this.closed = Boolean.TRUE;
  }

  /**
   * check if current context is already closed
   *
   * @return is closed
   */
  public Boolean isClosed() {
    return this.closed;
  }

  /**
   * read request body into new object possibly
   *
   * @param clazz objects class
   * @param <I> objects type
   * @return object
   */
  public <I> I readJson(Class<I> clazz) {
    if (windwardRequest.getRequestBody() == null) {
      return JacksonUtil.toObject("{}", clazz);
    }
    return JacksonUtil.toObject(windwardRequest.getRequestBody(), clazz);
  }

  /**
   * bind request body to specific class. and return instance of the class
   *
   * @param clazz objects class
   * @param groups validate group
   * @param <I> objects type
   * @return object
   * @throws NoRequestBodyException if request body is null
   * @throws ValidationException validated failed
   */
  public <I> I bindJson(Class<I> clazz, Class<?>... groups)
      throws NoRequestBodyException, ValidationException {
    if (windwardRequest.getRequestBody() == null) {
      throw new NoRequestBodyException();
    }
    return ValidationUtil.validate(windwardRequest.getRequestBody(), clazz, groups);
  }

  /**
   * response json
   *
   * @param data data
   * @param <T> type
   */
  public <T> void writeJson(T data) {
    writeJson(HttpStatus.OK.value(), data);
  }

  /**
   * response json
   *
   * @param code response code
   * @param data data
   * @param <T> type
   */
  public <T> void writeJson(int code, T data) {
    windwardResponse.write(code, MediaType.APPLICATION_JSON_VALUE, data);
  }

  /**
   * response plain string
   *
   * @param data strings
   */
  public void writeString(String data) {
    writeString(HttpStatus.OK.value(), data);
  }

  /**
   * response plain string
   *
   * @param code response code
   * @param data strings
   */
  public void writeString(int code, String data) {
    windwardResponse.write(code, MediaType.TEXT_PLAIN_VALUE, data);
  }
}
