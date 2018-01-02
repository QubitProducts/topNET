/*
 * topNET
 * Fast HTTP AbstractServer Solution.
 * Copyright 2016, Qubit Group <www.qubit.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  
 * If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
 * 
 * Author: Peter Fronc <peter.fronc@qubitdigital.com>
 */

package com.qubit.topnet;

import static com.qubit.topnet.Request.getDefaultProtocol;
import static com.qubit.topnet.ServerBase.HTTP_0_9;
import static com.qubit.topnet.ServerBase.HTTP_1_0;
import static com.qubit.topnet.ServerBase.getCharsetForName;
import static com.qubit.topnet.ServerTime.getCachedTime;
import com.qubit.topnet.exceptions.ResponseBuildingStartedException;
import com.qubit.topnet.exceptions.TooLateToChangeHeadersException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Response {
  static private final Logger log = Logger.getLogger(Response.class.getName());

  public static String serverName = "topNET/2.0.12";
  
  private static final String CRLF = "\r\n";
  private static final String OK_200 = "200 OK" + CRLF;
  private static final String OK_204 = "204 No Content" + CRLF;
  public static final char[] HTTP_0_9_CHARS = "HTTP/0.9".toCharArray();
  public static final char[] HTTP_1_0_CHARS = "HTTP/1.0".toCharArray();
  public static final char[] HTTP_1_1_CHARS = "HTTP/1.1".toCharArray();
  public static final char[] HTTP_1_X_CHARS = "HTTP/1.x".toCharArray();

  private static void appendServerHeader(StringBuilder buffer) {
    if (Response.serverName != null) {
      buffer.append("Server: ");
      buffer.append(Response.serverName);
      buffer.append(CRLF);
    }
  }
  
  /**
   * @return the serverName
   */
  public static String getServerName() {
    return serverName;
  }

  /**
   * @param aServerName the serverName to set
   */
  public static void setServerName(String aServerName) {
    serverName = aServerName;
  }
  
  private int httpCode = 200;
  List<String[]> headers = new ArrayList<>();
  private ResponseStream responseStream;
  private ReadableByteChannel channelToReadFrom;
  private boolean tooLateToChangeHeaders;
  private long contentLength = -1;
  private String contentType = "text/html";
  private String charset;
  private boolean forcingClosingAfterRequest = false;
  private boolean tellingConnectionClose = false;
  private volatile boolean moreDataComing = false;
  private boolean readingChannelResponseOnly = false;

  private StringBuilder stringBuffer = null;
  private InputStream streamToReadFrom;
  private Object attachment;
  private int httpProtocol = HTTP_1_0;

  public void init (int httpProtocol) {
    this.httpProtocol = httpProtocol;
  }
  
  public Response () {
    this.httpProtocol = getDefaultProtocol();
  }
  
  public static ResponseStream prepareResponseStreamFromInputStream(
          InputStream bodyStream, ReadableByteChannel channel) {
    ResponseStream r = new ResponseStream();
    
    r.setByteChannel(channel);
    r.setBodyStream(bodyStream);
    
    return r;
  }

  public void reset() {
    if (this.headers != null) {
      this.headers.clear();
    }
    
    this.httpCode = 200;
    this.responseStream = null;
    this.tooLateToChangeHeaders = false;
    this.contentLength = -1;
    this.contentType = "text/html";
    this.charset = null;
    this.forcingClosingAfterRequest = false;
    this.tellingConnectionClose = false;
    this.moreDataComing = false;
    this.stringBuffer = null;
    this.streamToReadFrom = null;
    this.channelToReadFrom = null;
    this.readingChannelResponseOnly = false;
    this.attachment = null;
    this.httpProtocol = 1;
  }
  
  /**
   * @return the headers
   */
  public List<String[]> getHeaders() {
    return headers;
  }
  
  /**
   * Called just before starting reading to output.
   *
   * @return ResponseStream reader object for this response
   */
  public final ResponseStream getResponseReaderReadyToRead() {
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
    StringBuilder buffer = getHeadersBufferToResponse();

    ByteArrayInputStream stream
            = new ByteArrayInputStream(
                    buffer.toString().getBytes(StandardCharsets.ISO_8859_1));

    return stream;
  }

  public StringBuilder getHeadersBufferToResponse() {
    
    // normal cases:
    StringBuilder buffer = new StringBuilder();
    String baseHeaders = getHeadersBufferWithoutEOL(this.httpCode,
                                                    this.getHttpProtocol());

    buffer.append(baseHeaders);
    
    try {
      this.addStandardHeadersToBuffer(buffer);
    } catch (TooLateToChangeHeadersException ex) {
      log.log(Level.SEVERE, "This should never happen.", ex);
    }

    buffer.append(CRLF); // headers are done\

    return buffer;
  }

  private static String[] headersBufferWithoutEOLCache = new String[1024 << 2];
  
  public static String getHeadersBufferWithoutEOL(
          int httpCode, int httpProtocol) {
    int index = (httpCode << 2) | httpProtocol;
    
    if (index < headersBufferWithoutEOLCache.length) {
      if (headersBufferWithoutEOLCache[index] != null) {
        return headersBufferWithoutEOLCache[index];
      }
    }

    StringBuilder buffer =
        prepareHTTPResponseFirstLine(httpCode, httpProtocol);
    
    Response.appendServerHeader(buffer);

    if (index < headersBufferWithoutEOLCache.length) {
      headersBufferWithoutEOLCache[index] = buffer.toString();
    }
    
    return headersBufferWithoutEOLCache[index];
  }

  private void addStandardHeadersToBuffer(StringBuilder buffer)
          throws TooLateToChangeHeadersException {
    // add date!
    buffer.append("Date: ");
    buffer.append(getCachedTime());
    buffer.append(CRLF);
    
    for (String[] header : headers) {
      buffer.append(header[0]);
      buffer.append(": ");
      buffer.append(header[1]);
      buffer.append(CRLF);
    }
  }

  public void addHeader(String name, String value)
          throws TooLateToChangeHeadersException {
    if (this.isTooLateToChangeHeaders()) {
      throw new TooLateToChangeHeadersException();
    }
    this.headers.add(new String[]{name, value});
  }

  public void removeHeader(String name)
          throws TooLateToChangeHeadersException {
    if (this.isTooLateToChangeHeaders()) {
      throw new TooLateToChangeHeadersException();
    }
    
    for (Iterator<String[]> it = headers.iterator(); it.hasNext();) {
      String[] header = it.next();
      if (header[0].equals(name)) {
        it.remove();
      }
    }
    
  }

  public List<String> getHeaders(String name) {
    List<String> ret = new ArrayList<>();
    for (String[] header : this.headers) {
      if (header[0].equals(name)) {
        ret.add(header[1]);
      }
    }
    return ret;
  }
  
  public String getHeader(String name) {
    for (String[] header : headers) {
      if (header[0].equals(name)) {
        return header[1];
      }
    }
    return null;
  }

  /**
   * @param httpCode the httpCodeNum to set
   * @throws com.qubit.topnet.exceptions.TooLateToChangeHeadersException
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
    if (this.stringBuffer == null) {
      this.stringBuffer = new StringBuilder();
    }
    this.stringBuffer.append(str);
  }

  protected void prepareResponseReader() {
    if (this.responseStream == null) {
      if (this.stringBuffer != null) {

        Charset _charset = Charset.defaultCharset();

        if (this.getCharset() != null) {
          _charset = getCharsetForName(getCharset());
        }

        this.setCharset(_charset.name());

        byte[] bytes = this.stringBuffer.toString().getBytes(_charset);
        // @todo its copying... lets do that without it
        if (this.getContentLength() < 0) { // only if not 
          this.setContentLength(bytes.length);
        }
        
        ByteArrayInputStream bodyStream = new ByteArrayInputStream(bytes);

        try {

          if (this.isTellingConnectionClose() || 
              ServerBase.isTellingConnectionClose()) {
            this.addHeader("Connection", "close");
          }

        } catch (TooLateToChangeHeadersException ex) {
          log.log(Level.SEVERE,
                 "This should never happen - bad implementation.", ex);
        }
//@todo 
//        this.stringBuffer.setLength(0);
        // @todo setter may be a good idea here
        this.responseStream = 
            prepareResponseStreamFromInputStream(bodyStream, null);
        
      } else {
        if (channelToReadFrom == null && streamToReadFrom == null) {
          this.setContentLength(0);
        }
        this.responseStream = 
            prepareResponseStreamFromInputStream(
                streamToReadFrom, channelToReadFrom);
      }
    }
    
    // http 0.9 case
    if (this.httpProtocol == HTTP_0_9) {
      // skip headers
      this.getResponseStream().setReadingBody(true);
    } else {
      if (this.getResponseStream().getHeadersStream() == null) {
        this.buildContentTypeWithCharset();        
        this.prepareContentLengthHeader();  // only once
        this.getResponseStream().setHeadersStream(getHeadersToSend());
      }
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
    this.contentLength = contentLength;
  }

  public void prepareContentLengthHeader() {
    try {
      if (this.contentLength >= 0) {
        this.addHeader("Content-Length", Long.toString(this.contentLength));
      } else {
        this.removeHeader("Content-Length");
      }
    } catch (TooLateToChangeHeadersException ex) {
      log.warning("Content length header set too late.");
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
   * @return the forcingClosingAfterRequest
   */
  public boolean isForcingClosingAfterRequest() {
    return forcingClosingAfterRequest;
  }

  /**
   * @param forcingCloseConnection the forcingClosingAfterRequest to set
   */
  public void setForcingClosingAfterRequest(boolean forcingCloseConnection) {
    this.forcingClosingAfterRequest = forcingCloseConnection;
  }

  /**
   * @return the tellingConnectionClose
   */
  public boolean isTellingConnectionClose() {
    return tellingConnectionClose;
  }

  /**
   * @param inputStream the inputStreamForBody to set
   * @throws com.qubit.topnet.exceptions.ResponseBuildingStartedException
   */
  public void setStreamToReadFrom(InputStream inputStream)
          throws ResponseBuildingStartedException {
    if (this.stringBuffer != null) {
      throw new ResponseBuildingStartedException();
    }

    this.streamToReadFrom = inputStream;
    if (this.getResponseStream() != null) {
      this.getResponseStream().setBodyStream(this.getStreamToReadFrom());
    }
  }

  public void setChannelToReadFrom(ReadableByteChannel channel)
      throws ResponseBuildingStartedException {
    if (this.stringBuffer != null) {
      throw new ResponseBuildingStartedException();
    }

    this.channelToReadFrom = channel;
    if (this.getResponseStream() != null) {
      this.getResponseStream().setByteChannel(this.channelToReadFrom);
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
   * Response can carry attachment - this is a plain object carried with
   * response so may be used by other handlers. Normally its null.
   * @return the attachment
   */
  public Object getAttachment() {
    return attachment;
  }

  /**
   * Response can carry attachment - this is a plain object carried with
   * response so may be used by other handlers.
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
  public int getHttpProtocol() {
    return httpProtocol;
  }

  /**
   * @param httpProtocol the httpProtocol to set
   */
  public void setHttpProtocol(int httpProtocol) {
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
  public void setTooLateToChangeHeaders(boolean tooLateToChangeHeaders) {
    this.tooLateToChangeHeaders = tooLateToChangeHeaders;
  }

  /**
   * @return the stringBuffer
   */
  public StringBuilder getStringBuffer() {
    return stringBuffer;
  }

  /**
   * @param stringBuffer the stringBuffer to set
   */
  public void setStringBuffer(StringBuilder stringBuffer) {
    this.stringBuffer = stringBuffer;
  }

  public static StringBuilder prepareHTTPResponseFirstLine(int httpCode, int httpProtocol) {
    StringBuilder buffer = new StringBuilder();
    int httpCodeNum = httpCode;

    switch (httpProtocol) {
      case 0:
        buffer.append(HTTP_0_9_CHARS);
        break;
      case 1:
        buffer.append(HTTP_1_0_CHARS);
        break;
      case 2:
        buffer.append(HTTP_1_1_CHARS);
        break;
      case 3:
        buffer.append(HTTP_1_X_CHARS);
        break;
      default:
        buffer.append(HTTP_1_0_CHARS);
        break;
    }
    
    buffer.append(' ');

    switch (httpCodeNum) {
      case 200:
        buffer.append(OK_200);
        break;
      case 204:
        buffer.append(OK_204);
        break;
      case 404:
        buffer.append("404 Not Found");
        buffer.append(CRLF);
        break;
      case 400:
        buffer.append("400 Bad Request");
        buffer.append(CRLF);
        break;
      case 503:
        buffer.append("503 Server Error");
        buffer.append(CRLF);
        break;
      default:
        buffer.append(httpCodeNum);
        buffer.append(CRLF);
        break;
    }
    
    return buffer;
  }
  
  public void setErrorResponse(int code, String message) 
      throws TooLateToChangeHeadersException  {
    this.setHttpCode(code);
    // override any previous stream preparation.
    this.responseStream = null;
    this.stringBuffer = null;
    
    try {
      // print message
      this.print(message);
    } catch (ResponseBuildingStartedException ex) {
      log.log(Level.WARNING,
          "Setting error message failed. \n It typically means incorrect usage of setErrorResponse.", ex);
    }
  }

  public boolean buildContentTypeWithCharset() {
    if (this.getHeader("Content-Type") == null) {
      StringBuilder sb = new StringBuilder(this.getContentType());
      if (this.getCharset() != null) {
        sb.append("; charset=");
        sb.append(this.getCharset());
      }
      this.addHeader("Content-Type", sb.toString());
      return true;
    }
    return false;
  }
  
  /**
   * Normally response will have attached reader stream to read
   * response from (see ResponseStream), additionally channel may 
   * be provided with this setter. To force the passed channel as response 
   * only (reader will skip input streams - including headers) use 
   * `setReadingChannelResponseOnly`.
   * @return the channelToReadFrom
   */
  public ReadableByteChannel getChannelToReadFrom() {
    return channelToReadFrom;
  }

  /**
   * @return the inputStreamForBody
   */
  public InputStream getStreamToReadFrom() {
    return streamToReadFrom;
  }
  
  /**
   * If channel response is set, this property will cause reading 
   * only from `this.channelToReadFrom` set `with setChannelToReadFrom()`
   *
   * @return the readingChannelResponseOnly
   */
  public boolean isReadingChannelResponseOnly() {
    return readingChannelResponseOnly;
  }

  /**
   * If channel response is set, this property will cause reading only from
   * `this.channelToReadFrom` set `with setChannelToReadFrom()`
   *
   * @param readingChannelResponseOnly the readingChannelResponseOnly to set, 
   *  if true, only channel will be read.
   */
  public void setReadingChannelResponseOnly(boolean readingChannelResponseOnly) {
    this.readingChannelResponseOnly = readingChannelResponseOnly;
  }

}
