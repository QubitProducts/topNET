/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import com.qubit.qurricane.errors.ErrorHandlingConfig;
import com.qubit.qurricane.errors.ErrorTypes;
import static com.qubit.qurricane.errors.ErrorTypes.BAD_CONTENT_HEADER;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class DataHandler {

  final static Logger log = Logger.getLogger(DataHandler.class.getName());
  private volatile long touch; // check if its needed volatile
  private volatile int size = 0; // check if its needed volatile

  private final Map<String, String> headers = new HashMap<>();

  private String method;
  private String fullPath;
  private String path;
  private boolean headersReady;
  public volatile boolean writingResponse = false;
  private boolean bodyRequired = false;

  private boolean firstLine = true;
  private String lastHeaderName = null;

  private final StringBuffer currentLine = new StringBuffer();
  private int contentLengthCounter = 0;
  private long contentLength = 0; // -1 is used to distinguish cases when no 

  public volatile boolean locked = false;

  private Request request;
  private Response response;
  private ErrorTypes errorOccured;
  private Throwable errorException;
  private Handler handlerUsed;

  static final String utfTextHtmlHeader
          = "Content-Type: text/html; charset=utf-8\n\r";
  static final String EOL = "\n";
  private String params;
  private String httpProtocol = HTTP_1_0;
  
  private boolean headersOnly = false;
  private final Server server;
  private ByteBuffer writeBuffer;
  private int writeBufSize;

  protected void reset() {
    size = 0;
    headers.clear();
    method = null;
    fullPath = null;
    path = null;
    headersReady = false;
    writingResponse = false;
    bodyRequired = false;
    firstLine = true;
    lastHeaderName = null;
    currentLine.setLength(0);
    contentLengthCounter = 0;
    contentLength = 0;
    request = null;
    response = null;
    errorOccured = null;
    errorException = null;
    handlerUsed = null;
    headersOnly = false;
    writeBuffer = null;
  }

  public DataHandler(Server server) {
    this.server = server;
    this.writeBufSize = server.getDataHandlerWriteBufferSize();
    touch = System.currentTimeMillis();
  }

  // returns true if should listen for write
  public synchronized int read(SelectionKey key, ByteBuffer buf)
          throws IOException {
    SocketChannel socketChannel = (SocketChannel) key.channel();

    buf.clear();
    int read;

    if ((read = socketChannel.read(buf)) > 0) {
      size += read;
      if (this.flushReads(key, buf)) {
        read = -2; // -1: reading is finished, -2 means done
        this.handleData();
      }
    }

    if (read > 0) {
      this.touch();
    }

    return read;
  }

  public long getTouch() {
    return touch;
  }

  protected void touch() {
    this.touch = System.currentTimeMillis();
  }

  /**
   * @return the size
   */
  public int getSize() {
    return size;
  }

  public String getEncoding() {
    return "UTF-8";
  }

  static final String  HTTP_0_9 = "HTTP/0.9";
  static final String  HTTP_1_0 = "HTTP/1.0";
  static final String  HTTP_1_1 = "HTTP/1.1";
  static final String  HTTP_1_x = "HTTP/1.x";
  
  private void setMethodAndPathFromLine(String line) {
    int idx = line.indexOf(" ");
    if (idx < 1) {
      return;
    }
    this.method = line.substring(0, idx);
    int sec_idx = line.indexOf(" ", idx + 1);
    if (sec_idx != -1) {
      this.fullPath = line.substring(idx + 1, sec_idx);
      this.httpProtocol = line.substring(sec_idx + 1);
      switch (this.httpProtocol) {
        case HTTP_0_9:
        case HTTP_1_0:
        case HTTP_1_1:
         break;
        case HTTP_1_x:
         this.httpProtocol = HTTP_1_0;
         break;
        default:
        this.errorOccured = ErrorTypes.BAD_CONTENT_HEADER;
        this.httpProtocol = HTTP_1_0;
      }
    } else {
      this.fullPath = line.substring(idx + 1);
    }
  }

  /**
   * @return the method
   */
  public String getMethod() {
    return method;
  }

  private String[] parseHeader(String line) {
    int idx = line.indexOf(":");
    if (idx > 0) {
      // 2 removes ": " part
      return new String[]{
        line.substring(0, idx),
        line.substring(idx + 2)
      };
    } else {
      return null;
    }
  }

  /**
   * @return the fullPath
   */
  public String getFullPath() {
    return fullPath;
  }

  private String analyzePathAndSplit() {
    if (this.path == null && this.fullPath != null) {
      int idx = this.fullPath.indexOf("?");
      if (idx == -1) {
        this.path = this.fullPath;
      } else {
        this.path = this.fullPath.substring(0, idx);
        this.params = this.fullPath.substring(idx + 1);
      }
    }
    return this.path;
  }

  private byte previous = -1;

  // returns true if reading is finished. any error handling should happend after reading foinished.
  private synchronized boolean flushReads(SelectionKey key, ByteBuffer buffer)
          throws UnsupportedEncodingException {

    buffer.flip();

    if (!this.headersReady) {
      
      if (this.request == null) {
        this.request = new Request(key, this.headers);
        this.response = new Response(this.httpProtocol);
      }

      while (buffer.hasRemaining()) {

        byte current = buffer.get();

        if (current == '\n' && previous == '\r') {

          previous = -1;
          if (this.processHeaderLine()) {
            this.errorOccured = BAD_CONTENT_HEADER;
            return true; // finish now!
          }

          if (this.headersReady) {
            break;
          }
        } else {
          if (previous != -1) {
            currentLine.append((char) previous);
          }

          previous = current;
        }
      }
    }

    if (this.headersReady) {
      // request can be processed
      if ((this.errorOccured = this.headersAreReadySoProcessReqAndRes(key))
              != null) {
        return true; // stop reading now because of errors! 
      }

      if (this.bodyRequired) {
        if (this.contentLength < 0) {
          this.errorOccured = ErrorTypes.BAD_CONTENT_LENGTH;
          return true;
        }
      }

      if (previous != -1) { // append last possible character
        currentLine.append((char) previous);
        previous = -1;
      }

      if (this.contentLength > 0) { // this also validates this.bodyRequired
        if (buffer.hasRemaining()) {

          int pos = buffer.position();
          int amount = buffer.limit() - pos;

          this.contentLengthCounter += amount;

          try {
            request.getOutputStream().write(
                    buffer.array(),
                    pos,
                    amount);
          } catch (IOException ex) {
            this.errorOccured = ErrorTypes.IO_ERROR;
            this.errorException = ex;
            log.log(Level.SEVERE, null, errorException);
            // some problem - just stop
            return true;
          }
        }

        if (this.contentLengthCounter >= this.contentLength) {
          return true;
        }

      } else {
        // stop reading now coz there is nothing to read (not bcoz of error)
        return true;
      }
    }

    buffer.clear();
    return false;
  }

  private boolean processHeaderLine() {
    String line = currentLine.toString();
    currentLine.setLength(0); // reset

    if (firstLine) {
      firstLine = false;

      bodyRequired = !(line.startsWith("GET ")
              || line.startsWith("HEAD ")
              || line.startsWith("DELETE ")
              || line.startsWith("TRACE "));
      // two birds as one

      this.setMethodAndPathFromLine(line);
      
      if (this.method.equals("HEAD")) {
        this.headersOnly = true;
      }
      
      this.analyzePathAndSplit();

      if (this.method == null) {
        this.errorOccured = ErrorTypes.HTTP_UNSET_METHOD;
        return true;
      }
    } else {

      // if at body
      if (!this.headersReady) {

        // must be headers first, check if at the end of headers - empty line
        if (!line.equals("")) {

          // check if not multiline header
          if (line.startsWith("\t")) {
            // multiline header, yuck! check if any header was read!
            if (lastHeaderName != null) {
              String tmp = this.headers.get(lastHeaderName);
              this.headers.put(lastHeaderName, tmp + "\n" + line);
            } else {
              this.errorOccured = ErrorTypes.HTTP_MALFORMED_HEADERS;
              return true; // yuck! headers malformed!! not even started and multiline ?
            }
          } else {
            String[] twoStrings = this.parseHeader(line);

            if (twoStrings != null) {
              lastHeaderName = twoStrings[0];
              if (!lastHeaderName.isEmpty()) {
                if (lastHeaderName.length() == 14) {
                  if (lastHeaderName.toLowerCase().equals("content-length")) {
                    try {
                      this.contentLength = Long.parseLong(twoStrings[1]);
                    } catch (NullPointerException | NumberFormatException ex) {
                      // just try, weird stuff ignore in this case...
                    }
                  }
                }
                headers.put(lastHeaderName, twoStrings[1]);
              }
            } else {
              this.errorOccured = ErrorTypes.HTTP_MALFORMED_HEADERS;
              return true; // yuck! header malformed!
            }
          }
        } else {
          this.headersReady = true;
        }
      }
    }

    return false;
  }

  
  
  public synchronized int write(
          SelectionKey key)
          throws IOException {

    SocketChannel channel = (SocketChannel) key.channel();
    
    ResponseReader responseReader;
    if (this.headersOnly) {
      responseReader = this.getInputStreamForResponse()
              .getHeadersOnlyResponseReader();
    } else {
      responseReader = this.getInputStreamForResponse();
    }

    if (responseReader == null) {
      if (this.response == null) {// should never happen
        log.warning("Response set to null - check threads.");
        return -1;
      }
      if (this.response.isMoreDataComing()) {
        return 0;
      } else {
        return -1;//finished reading
      }
    }

    if (writeBuffer == null) {
      this.writeBuffer = ByteBuffer.allocate(this.writeBufSize);
    } else {
      this.writeBuffer.compact();
    }
    
    int ch = 0;
    while (writeBuffer.hasRemaining() && (ch = responseReader.read()) != -1) {
      writeBuffer.put((byte) ch);
    }

    writeBuffer.flip();
    int written = channel.write(writeBuffer);

    if (written > 0) {
      this.touch();
    }


    if ((ch == -1)) {
      if (this.response.isMoreDataComing()) {
        return 0;
      } else {
        return -1;
      }
    } else {
      return ch;
    }
  }

  // returns true if writing should be stopped function using it should reply 
  // asap - typically its used to repoly unsupported fullPath 
  private ErrorTypes headersAreReadySoProcessReqAndRes(SelectionKey key) {
    response.setHttpProtocol(httpProtocol);
    
    if (this.errorOccured != null) {
      return this.errorOccured;
    }

    // paths must be ready by headers setup
    Handler handler = this.server.getHandlerForPath(this.fullPath, this.path);
    this.handlerUsed = handler;

    if (handler == null) {
      return ErrorTypes.HTTP_NOT_FOUND;
    } else {
      this.request.setPath(this.path);
      this.request.setFullPath(this.fullPath);
      this.request.setPathParameters(this.params);
      this.request.setMethod(this.method);

      handler.runPrepare(this.request, this.response);
      
      // this prepares space for BODY to be written to, once headers are sorted,
      // possibly body will be written
      request.makeSureOutputStreamIsReady();
    }

    if (!handler.supports(this.method)) {
      return ErrorTypes.HTTP_NOT_FOUND;
    }

    return null;
  }

  private Handler getErrorHandler(Handler handler) {
    Handler errorHandler
            = ErrorHandlingConfig.getErrorHandlingConfig()
            .getDefaultErrorHandler(getErrorCode());

    request.setAssociatedException(this.errorException);

    if (handler != null) {
      Handler tmp = handler.getErrorHandler();
      if (tmp != null) {
        errorHandler = tmp;
      }
    }

    return errorHandler;
  }

  public ErrorTypes handleData() {
    Handler handler = this.handlerUsed;

    if (handler == null && this.errorOccured == null) {
      this.errorOccured = ErrorTypes.HTTP_NOT_FOUND;
    }

    if (this.errorOccured != null) {
      handler = getErrorHandler(handler);
      handler.runPrepare(request, response);
      // @todo review error handling and refactor to nicer form
    }

    if (handler != null) {

      try {
        handler.process(request, response);
      } catch (Throwable t) {
        // handle processing error, be delicate:
        this.errorOccured = ErrorTypes.HTTP_SERVER_ERROR;
        this.errorException = t;
        log.log(Level.WARNING, "Exception in handler.", this.errorException);

        handler = getErrorHandler(handler);

        try {
          response = new Response(this.httpProtocol);
          handler.runPrepare(request, response);
          // @todo review error handling and refactor to nicer form.
          // runPrepare not really needed
          handler.process(request, response);
        } catch (Throwable ex) {
          Logger.getLogger(DataHandler.class.getName())
                  .log(Level.SEVERE, "Error in error handler.", ex);
        }

        return this.errorOccured;
      } finally {
        if (this.errorOccured != null && this.handlerUsed != null) {
          this.handlerUsed.onError(this.errorException);
        }
      }
    }

    return null;
  }

  private ResponseStream getInputStreamForResponse() {
    if (this.response == null) {
      log.warning("Response is null - check threads.");
      return null;
    }
    return this.response.getResponseReaderReadyToRead();
  }

  private int getErrorCode() {
    if (this.errorOccured != null) {
      // @todo clean this up
      switch (this.errorOccured) {
        case HTTP_NOT_FOUND:
          return 404;
        case HTTP_SERVER_ERROR:
          return 503;
        case BAD_CONTENT_HEADER:
        case BAD_CONTENT_LENGTH:
        case HTTP_MALFORMED_HEADERS:
        case HTTP_UNSET_METHOD:
          return 400;
        default:
          return 503;
      }
    }
    return 200;
  }

  boolean canClose(boolean finishedWriting) {
    if (this.response != null && this.response.isForcingNotKeepingAlive()) {
      return true;
    }

    String connection = this.headers.get("Connection");

    if (connection != null) {
      switch (connection) {
        case "keep-alive":
          return false;
        case "close":
          return true;
      }
    }
    
    if (!this.httpProtocol.equals(HTTP_1_1)) {
      return true;
    }

    if (finishedWriting && this.response != null) {
      int cl = this.response.getContentLength();
      if (cl == -1) {
        return true; // close unknown contents once finished reading
      }
    }
    
    return false;
  }

  int getMaxMessageSize(int defaultMaxMessageSize) {
    if (this.handlerUsed != null) {
      int maxSize = this.handlerUsed.getMaxIncomingDataSize();
      if (maxSize > -2) {
        return maxSize;
      }
    }

    return defaultMaxMessageSize;
  }

  long getMaxIdle(long defaultMaxIdle) {
    if (this.handlerUsed != null) {
      int maxIdle = this.handlerUsed.getMaxIdle();
      if (maxIdle > -1) {
        return maxIdle;
      }
    }

    return defaultMaxIdle;
  }
}
