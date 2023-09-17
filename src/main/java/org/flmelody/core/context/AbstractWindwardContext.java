/*
 * Copyright (C) 2023 Flmelody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flmelody.core.context;

import java.util.List;
import java.util.Map;
import org.flmelody.core.HttpStatus;
import org.flmelody.core.MediaType;
import org.flmelody.core.WindwardRequest;
import org.flmelody.core.WindwardResponse;

/**
 * @author esotericman
 */
public abstract class AbstractWindwardContext implements WindwardContext {
  protected final WindwardRequest windwardRequest;
  protected final WindwardResponse windwardResponse;
  private Boolean closed = Boolean.FALSE;

  protected AbstractWindwardContext(
      WindwardRequest windwardRequest, WindwardResponse windwardResponse) {
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
  @Override
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
  @Override
  public List<String> getRequestParameters(String parameterName) {
    return this.windwardRequest.getQuerystring().get(parameterName);
  }

  /**
   * get path variables
   *
   * @return path variables
   */
  @Override
  public Map<String, Object> getPathVariables() {
    return this.windwardRequest.getPathVariables();
  }

  /**
   * get request body
   *
   * @return request body
   */
  @Override
  public String getRequestBody() {
    return this.windwardRequest.getRequestBody();
  }

  /**
   * get windwardRequest
   *
   * @return windwardRequest
   */
  @Override
  public WindwardRequest windwardRequest() {
    return this.windwardRequest;
  }

  /** close context */
  @Override
  public void close() {
    this.closed = Boolean.TRUE;
  }

  /**
   * check if current context is already closed
   *
   * @return is closed
   */
  @Override
  public Boolean isClosed() {
    return this.closed;
  }

  /**
   * response json
   *
   * @param data data
   * @param <T> type
   */
  @Override
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
  @Override
  public <T> void writeJson(int code, T data) {
    windwardResponse.write(code, MediaType.APPLICATION_JSON_VALUE, data);
  }

  /**
   * response plain string
   *
   * @param data strings
   */
  @Override
  public void writeString(String data) {
    writeString(HttpStatus.OK.value(), data);
  }

  /**
   * response plain string
   *
   * @param code response code
   * @param data strings
   */
  @Override
  public void writeString(int code, String data) {
    windwardResponse.write(code, MediaType.TEXT_PLAIN_VALUE, data);
  }
}
