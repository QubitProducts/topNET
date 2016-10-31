/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Handler.HTTP_1_0;
import static com.qubit.qurricane.Server.SERVER_VERSION;
import com.qubit.qurricane.exceptions.ResponseBuildingStartedException;
import com.qubit.qurricane.exceptions.TooLateToChangeHeadersException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Response {

  static private final String CRLF = "\r\n";
  static private final String OK_200 = "200 OK" + CRLF;
  static private final String OK_204 = "204 No Content" + CRLF;

  private static final ThreadLocal<ServerTime> serverTime;
  
  static final Logger log = Logger.getLogger(Response.class.getName());

  static {
    serverTime = new ThreadLocal<ServerTime>() {
      @Override
      protected ServerTime initialValue() {
        return new ServerTime();
      }
    };
  }

  private int httpCode = 200;
  Map<String, String> headers = new HashMap<>();
  private ResponseStream responseStream;
  private boolean tooLateToChangeHeaders;
  private long contentLength = -1;
  private String contentType = "text/html";
  private String charset;
  private boolean forcingNotKeepingAlive = true;
  private boolean tellingConnectionClose = true;
  private volatile boolean moreDataComing = false;

  private StringBuffer stringBuffer = null;
  private InputStream inputStreamForBody;
  private Object attachment;
  private String httpProtocol;

  public Response (String httpProtocol) {
    this.httpProtocol = httpProtocol;
  }
  
  public Response () {
    this.httpProtocol = HTTP_1_0;
  }
  
  /**
   * Called just before starting reading to output.
   *
   * @return ResponseStream reader object for this response
   */
  protected final ResponseStream getResponseReaderReadyToRead() {
    this.prepareResponseReader();
    return this.getResponseStream();
  }

  public void setResponseStream(ResponseStream responseStream)
          throws ResponseBuildingStartedException {
    if (this.responseStream != null) {
      throw new ResponseBuildingStartedException();
    }
    this.responseStream = responseStream;
  }

  public ByteArrayInputStream getHeadersToSend() {

    StringBuffer buffer = getHeadersBuffer();

    ByteArrayInputStream stream
            = new ByteArrayInputStream(
                    buffer.toString().getBytes(StandardCharsets.ISO_8859_1));

    return stream;
  }

  public StringBuffer getHeadersBuffer() {
    StringBuffer buffer
            = getHeadersBufferWithoutEOL(
                    this.httpCode,
                    this.getHttpProtocol());

    try {
      this.addHeaders(buffer);
    } catch (TooLateToChangeHeadersException ex) {
      log.log(Level.SEVERE, "This should never happen.", ex);
    }

    buffer.append(CRLF); // headers are done\

    return buffer;
  }

  public static StringBuffer getHeadersBufferWithoutEOL(
          int httpCode, String httpProtocol) {

    StringBuffer buffer = new StringBuffer();
    int httpCodeNum = httpCode;

    buffer.append(httpProtocol);
    buffer.append(" ");

    if (httpCodeNum == 200) {
      buffer.append(OK_200);
    } else if (httpCodeNum == 204) {
      buffer.append(OK_204);
    } else if (httpCodeNum == 404) {
      buffer.append("404 Not Found");
      buffer.append(CRLF);
    } else if (httpCodeNum == 400) {
      buffer.append("400 Bad Request");
      buffer.append(CRLF);
    } else if (httpCodeNum == 503) {
      buffer.append("503 Server Error");
      buffer.append(CRLF);
    } else {
      buffer.append(httpCodeNum);
      buffer.append(CRLF);
    }

    buffer.append("Date: ");
    buffer.append(serverTime.get().getCachedTime());
    buffer.append(CRLF);

    buffer.append("Server: Qurricane ");
    buffer.append(SERVER_VERSION);
    buffer.append(CRLF);

    return buffer;
  }

  private void addHeaders(StringBuffer buffer)
          throws TooLateToChangeHeadersException {
    for (Map.Entry<String, String> entrySet : headers.entrySet()) {
      buffer.append(entrySet.getKey());
      buffer.append(": ");
      buffer.append(entrySet.getValue());
      buffer.append(CRLF);
    }
  }

  public void addHeader(String name, String value)
          throws TooLateToChangeHeadersException {
    if (this.isTooLateToChangeHeaders()) {
      throw new TooLateToChangeHeadersException();
    }
    this.headers.put(name, value);
  }

  public void removeHeader(String name)
          throws TooLateToChangeHeadersException {
    if (this.isTooLateToChangeHeaders()) {
      throw new TooLateToChangeHeadersException();
    }
    this.headers.remove(name);
  }

  public String getHeader(String name) {
    return this.headers.get(name);
  }

  /**
   * @param httpCode the httpCodeNum to set
   * @throws com.qubit.qurricane.exceptions.TooLateToChangeHeadersException
   */
  public void setHttpCode(int httpCode) throws TooLateToChangeHeadersException {
    if (this.isTooLateToChangeHeaders()) {
      throw new TooLateToChangeHeadersException();
    }
    this.httpCode = httpCode;
  }

  public int getHttpCode()  {
    return this.httpCode;
  }
  
  /**
   * To print to response output - use this function - note this is textual
   * method of sending response. To send binary data, preapare input stream to
   * read and attach here.
   *
   * @param str
   * @throws ResponseBuildingStartedException
   */
  public void print(String str) throws ResponseBuildingStartedException {
    if (this.getResponseStream() != null) {
      throw new ResponseBuildingStartedException();
    }
    if (this.getStringBuffer() == null) {
      this.setStringBuffer(new StringBuffer());
    }
    this.getStringBuffer().append(str);
  }

  protected void prepareResponseReader() {
    if (this.responseStream == null) {
      if (this.getStringBuffer() != null) {
        String charsetString = this.getCharset();

        Charset _charset = Charset.defaultCharset();

        if (charsetString != null) {
          _charset = Charset.forName(getCharset());
        }

        charsetString = _charset.name();

        byte[] bytes = this.getStringBuffer().toString().getBytes(_charset);
        // @todo its copying... lets do that without it
        if (this.getContentLength() < 0) { // only if not 
          this.setContentLength(bytes.length);
        }
        
        ByteArrayInputStream bodyStream = new ByteArrayInputStream(bytes);

        try {

          if (this.isTellingConnectionClose()) {
            this.addHeader("Connection", "close");
          }

          if (this.getHeader("Content-Type") == null) {
            this.addHeader(
                    "Content-Type",
                    this.getContentType() + "; charset=" + charsetString);
          }
        } catch (TooLateToChangeHeadersException ex) {
          log.log(Level.SEVERE,
                          "This should never happen - bad implementation.", ex);
        }

        this.getStringBuffer().setLength(0);
        // @todo setter may be a good idea here
        this.responseStream = 
            prepareResponseStreamFromInputStream(bodyStream);
        
      } else {
        this.responseStream = 
            prepareResponseStreamFromInputStream(this.getStreamToReadFrom());
      }
    }
    
    if (this.getResponseStream().getHeadersStream() == null) { // only once
      this.getResponseStream().setHeadersStream(getHeadersToSend());
//      this.tooLateToChangeHeaders = true;
    }
  }

  /**
   * @return the contentLength
   */
  public long getContentLength() {
    return contentLength;
  }

  /**
   * @param contentLength the contentLength to set
   */
  public void setContentLength(long contentLength) {
    try {
      if (contentLength >= 0) {
        this.addHeader("Content-Length", Long.toString(contentLength));
      } else {
        this.removeHeader("Content-Length");
      }
      this.contentLength = contentLength;
    } catch (TooLateToChangeHeadersException ex) {
      log.warning("Trying to set content length too late: " + contentLength);
    }
    
  }

  /**
   * @return the contentType
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * @param contentType the contentType to set
   */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  /**
   * @return the charset
   */
  public String getCharset() {
    return charset;
  }

  /**
   * @param charset the charset to set
   */
  public void setCharset(String charset) {
    this.charset = charset;
  }

  /**
   * @return the forcingNotKeepingAlive
   */
  public boolean isForcingNotKeepingAlive() {
    return forcingNotKeepingAlive;
  }

  /**
   * @param forcingCloseConnection the forcingNotKeepingAlive to set
   */
  public void setForcingNotKeepingAlive(boolean forcingCloseConnection) {
    this.forcingNotKeepingAlive = forcingCloseConnection;
  }

  /**
   * @return the tellingConnectionClose
   */
  public boolean isTellingConnectionClose() {
    return tellingConnectionClose;
  }

  /**
   * @param inputStream the inputStreamForBody to set
   * @throws com.qubit.qurricane.exceptions.ResponseBuildingStartedException
   */
  public void setStreamToReadFrom(InputStream inputStream)
          throws ResponseBuildingStartedException {
    if (this.getStringBuffer() != null) {
      throw new ResponseBuildingStartedException();
    }

    this.inputStreamForBody = inputStream;
    if (this.getResponseStream() != null) {
      this.getResponseStream().setBodyStream(this.getStreamToReadFrom());
    }
  }

  public boolean waitForData() {
    return false;
  }

  /**
   * @return the moreDataComing
   */
  public boolean isMoreDataComing() {
    return moreDataComing;
  }

  /**
   * @param moreDataComing the moreDataComing to set
   */
  public void setMoreDataComing(boolean moreDataComing) {
    this.moreDataComing = moreDataComing;
  }

  /**
   * @return the attachment
   */
  public Object getAttachment() {
    return attachment;
  }

  /**
   * @param attachment the attachment to set
   */
  public void setAttachment(Object attachment) {
    this.attachment = attachment;
  }

  /**
   * @param tellingConnectionClose the tellingConnectionClose to set
   */
  public void setTellingConnectionClose(boolean tellingConnectionClose) {
    this.tellingConnectionClose = tellingConnectionClose;
  }

  /**
   * @return the responseStream
   */
  public ResponseStream getResponseStream() {
    return responseStream;
  }

  /**
   * @return the httpProtocol
   */
  public String getHttpProtocol() {
    return httpProtocol;
  }

  /**
   * @param httpProtocol the httpProtocol to set
   */
  public void setHttpProtocol(String httpProtocol) {
    this.httpProtocol = httpProtocol;
  }

  /**
   * @return the tooLateToChangeHeaders
   */
  public boolean isTooLateToChangeHeaders() {
    return tooLateToChangeHeaders;
  }

  /**
   * @param tooLateToChangeHeaders the tooLateToChangeHeaders to set
   */
  void setTooLateToChangeHeaders(boolean tooLateToChangeHeaders) {
    this.tooLateToChangeHeaders = tooLateToChangeHeaders;
  }

  /**
   * @return the stringBuffer
   */
  public StringBuffer getStringBuffer() {
    return stringBuffer;
  }

  /**
   * @param stringBuffer the stringBuffer to set
   */
  public void setStringBuffer(StringBuffer stringBuffer) {
    this.stringBuffer = stringBuffer;
  }

  public static ResponseStream prepareResponseStreamFromInputStream(
          InputStream bodyStream) {
    ResponseStream r = new ResponseStream();
    r.setBodyStream(bodyStream);
    return r;
  }

  /**
   * @return the inputStreamForBody
   */
  public InputStream getStreamToReadFrom() {
    return inputStreamForBody;
  }
}
