/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import com.qubit.qurricane.exceptions.ResponseBuildingStartedException;
import com.qubit.qurricane.exceptions.TooLateToChangeHeadersException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Response {

  static final String OK_200 = "HTTP/1.1 200 OK\r\n";
  static final String OK_204 = "HTTP/1.1 204 No Content\r\n";
  static final String EOL = "\r\n";
  private int httpCode = 200;

  Map<String, String> headers = new HashMap<>();
  private ResponseStream responseStream;
  private boolean tooLateToChangeHeaders;
  private boolean responseTextBuldingStarted = false;

  protected InputStream getInputStream() {
    if (this.responseStream == null) {
      this.tooLateToChangeHeaders = true;
      this.responseStream = new ResponseStream(getHeadersToSend(), null);
    }
    return this.responseStream;
  }

  final static DateFormat dateFormat;
  final static Calendar calendar;

  static {
    calendar = Calendar.getInstance();
    dateFormat = new SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private InputStream getHeadersToSend() {

    StringBuilder builder = new StringBuilder();
    String customCode = this.customHttpCode();

    if (customCode != null) {
      builder.append("HTTP/1.1 ");
      builder.append(customCode);
    } else if (this.httpCode == 200) {
      builder.append(OK_200);
    } else if (this.httpCode == 200) {
      builder.append(OK_204);
    } else if (this.httpCode == 404) {
      builder.append("HTTP/1.1 404 Not Found\r\n");
    } else {
      builder.append("HTTP/1.1 ");
      builder.append(this.httpCode);
      builder.append("\r\n");
    }

    builder.append("Date: ");
    builder.append(dateFormat.format(calendar.getTime()));
    builder.append("Server: Qurricane");

    try {
      this.addHeaders(builder);
    } catch (TooLateToChangeHeadersException ex) {
      Logger.getLogger(
              Response.class.getName())
              .log(Level.SEVERE, "This should never happen.", ex);
    }

    builder.append(EOL); // headers are done

    InputStream stream
            = new ByteArrayInputStream(
                    builder.toString().getBytes(StandardCharsets.UTF_8));

    return stream;
  }

  private void addHeaders(StringBuilder builder)
          throws TooLateToChangeHeadersException {
    if (this.tooLateToChangeHeaders) {
      throw new TooLateToChangeHeadersException();
    }
    for (Map.Entry<String, String> entrySet : headers.entrySet()) {
      builder.append(entrySet.getKey());
      builder.append(": ");
      builder.append(entrySet.getValue());
      builder.append(EOL);
    }
  }

  /**
   * Function returning suffix to first line of HTTP message, suffix to:
   * "HTTP/1.1 "
   *
   * @return returned string will be used as: "HTTP/1.1 " + returned
   */
  protected String customHttpCode() {
    return null;
  }

  /**
   * @param httpCode the httpCode to set
   */
  public void setHttpCode(int httpCode) throws TooLateToChangeHeadersException {
    if (this.tooLateToChangeHeaders) {
      throw new TooLateToChangeHeadersException();
    }
    this.httpCode = httpCode;
  }

  StringBuilder responseBuilder = new StringBuilder();

  public void setResponseStream(InputStream is)
          throws ResponseBuildingStartedException {
    if (responseTextBuldingStarted) {
      throw new ResponseBuildingStartedException();
    } else {
      this.responseStream.setBodyStream(is);
    }
  }

  public void print(String str) {
    if (!responseTextBuldingStarted) {
      this.responseTextBuldingStarted = true;
    }
    responseBuilder.append(str);
  }
}
