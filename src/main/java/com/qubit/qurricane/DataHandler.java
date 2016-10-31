/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Handler.HTTP_0_9;
import static com.qubit.qurricane.Handler.HTTP_1_0;
import static com.qubit.qurricane.Handler.HTTP_1_1;
import static com.qubit.qurricane.Handler.HTTP_1_x;
import com.qubit.qurricane.errors.ErrorHandlingConfig;
import com.qubit.qurricane.errors.ErrorTypes;
import static com.qubit.qurricane.errors.ErrorTypes.BAD_CONTENT_HEADER;
import com.qubit.qurricane.utils.Pair;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class DataHandler {

  final static Logger log = Logger.getLogger(DataHandler.class.getName());
  
  
  public static PrePostGeneralHandler postPreProcessingHandler;
  /**
   * @return the postPreProcessingHandler
   */
  public static PrePostGeneralHandler getPostPreProcessingHandler() {
    return postPreProcessingHandler;
  }

  /**
   * @param handler
   */
  public static void setPostPreProcessingHandler(PrePostGeneralHandler handler) {
    postPreProcessingHandler = handler;
  }
  
  private volatile long touch; // check if its needed volatile
  private volatile int size = 0; // check if its needed volatile

  private final Map<String, String> headers = new HashMap<>();
  
  public AtomicReference<HandlingThread> atomicRefToHandlingThread;

  private String method;
  private String fullPath;
  private String path;
  private boolean headersReady;
  protected boolean writingResponse = false;
  private boolean bodyRequired = false;

  private boolean firstLine = true;
  private String lastHeaderName = null;

  private final StringBuffer currentLine = new StringBuffer();
  private int contentLengthCounter = 0;
  private long contentLength = 0; // -1 is used to distinguish cases when no 

  private Request request;
  private Response response;
  private ErrorTypes errorOccured;
  private Throwable errorException;
  private Handler handlerUsed;

  static final String utfTextHtmlHeader
          = "Content-Type: text/html; charset=utf-8\n\r";
  static final String EOL = "\n";
  private String parameters;
  private String httpProtocol = HTTP_1_0;
  
  private boolean headersOnly = false;
  private final Server server;
  private ByteBuffer writeBuffer;
  private int writeBufSize;
  private boolean wasMarkedAsMoreDataIsComing;
  private final SocketChannel channel;
  private long acceptedTime;

  private void reset() {
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

  public DataHandler(Server server, SocketChannel channel) {
    this.channel = channel;
    this.server = server;
    this.writeBufSize = server.getDataHandlerWriteBufferSize();
    touch = System.currentTimeMillis();
  }
  
  // returns -2 when done reading -1 when data stream  to read is finished , 
  //-2 means that logically read is over, -1 that EOF stream occured 
  public int read(SocketChannel channel, ByteBuffer buf)
          throws IOException {

    buf.clear();
    int read;

    if ((read = channel.read(buf)) > 0) {
      size += read;
      
      if (read > 0) {
        this.touch();
      }
      
      if (this.flushReads(buf)) {
        read = -2; // -1: reading is finished, -2 means done
        this.handleData();
      }
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
        this.parameters = "";
      } else {
        this.path = this.fullPath.substring(0, idx);
        this.parameters = this.fullPath.substring(idx + 1);
      }
    }
    return this.path;
  }

  private byte previous = -1;

  // returns true if reading is finished. any error handling should happend after reading foinished.
  private boolean flushReads(ByteBuffer buffer)
          throws UnsupportedEncodingException {

    buffer.flip();

    if (!this.headersReady) {
      
      if (this.getRequest() == null) {
        this.request = new Request(this.getChannel(), this.headers);
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
      getResponse().setHttpProtocol(httpProtocol);
    
      if (this.errorOccured != null) {
        return true;
      }
      
      // paths must be ready by headers setup
      this.handlerUsed = 
        this.server.getHandlerForPath(this.fullPath, this.path, this.parameters);
      
      this.errorOccured = 
              this.headersAreReadySoProcessReqAndRes(this.handlerUsed);
      
      if (this.errorOccured != null) {
        return true; // stop reading now because of errors! 
      }
      
      this.handlerUsed.onBeforeOutputStreamIsSet(this.getRequest(), this.getResponse());
      
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
            OutputStream destination = getRequest().getOutputStream();
            int count = 0;
            while (count < amount) {
              destination.write(buffer.get());
              count++;
            }
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
          headersReadyHandler(this);
        }
      }
    }

    return false;
  }
  
  protected int write(SocketChannel channel)
          throws IOException {
    
    ResponseReader responseReader;
    if (this.headersOnly) {
      responseReader = this.getInputStreamForResponse()
              .getHeadersOnlyResponseReader();
    } else {
      responseReader = this.getInputStreamForResponse();
    }

    if (responseReader == null) {
      if (this.getResponse() == null) {// should never happen
        log.warning("Response set to null - check threads.");
        return -1;
      }
      if (this.getResponse().isMoreDataComing()) {
        this.wasMarkedAsMoreDataIsComing = true;
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
    
    if (!this.getResponse().isTooLateToChangeHeaders()) {
      this.getResponse().setTooLateToChangeHeaders(true);
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
      if (this.getResponse().isMoreDataComing()) {
        this.wasMarkedAsMoreDataIsComing = true;
        return 0;
      } else {
        // this check is for synchronization reasons between last read 
        // from stream and new check - it could happen that response is marked as 
        // finished and few bytes appeared still left to be read.
        // one more check will make sure all are read
        if (this.wasMarkedAsMoreDataIsComing) {
          this.wasMarkedAsMoreDataIsComing = false;
          return 0;
        } else {
          return -1;
        }
      }
    } else {
      return ch;
    }
  }

  // returns true if writing should be stopped function using it should reply 
  // asap - typically its used to repoly unsupported fullPath 
  private ErrorTypes headersAreReadySoProcessReqAndRes(Handler handler) {
    
    if (handler == null) {
      return ErrorTypes.HTTP_NOT_FOUND;
    } else {
      this.getRequest().setPath(this.path);
      this.getRequest().setFullPath(this.fullPath);
      this.getRequest().setPathParameters(this.parameters);
      this.getRequest().setMethod(this.method);
      
      // this prepares space for BODY to be written to, once headers are sorted,
      // possibly body will be written
      getRequest().makeSureOutputStreamIsReady();
      Handler tmp = handler;
      
      while(tmp != null) {
        if (tmp.supports(this.method)) {
          return null;
        }
        tmp = tmp.getNext();
      }
      
      return ErrorTypes.HTTP_NOT_FOUND;
    }
  }

  private Handler getErrorHandler(Handler handler) {
    Handler errorHandler
            = ErrorHandlingConfig.getErrorHandlingConfig()
            .getDefaultErrorHandler(getErrorCode());

    getRequest().setAssociatedException(this.errorException);

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
    }

    beforeHandlingReadyHandler(this);
    
    if (handler != null) {

      Pair<Handler, Throwable> execResult = 
              handler.doProcess(this.getRequest(), this.getResponse(), this);
      
      try {
        if (execResult != null) {
          // handle processing error, be delicate:
          this.errorOccured = ErrorTypes.HTTP_SERVER_ERROR;
          this.errorException = execResult.getRight();
          log.log(Level.WARNING, "Exception in handler.", this.errorException);

          handler = getErrorHandler(handler);

          try {
            handler.doProcess(this.getRequest(), this.getResponse(), this);
          } catch (Throwable ex) {
            log.log(Level.SEVERE, "Error in error handler.", ex);
          }

          return this.errorOccured;
        }
      } finally {
        if (this.errorOccured != null && this.handlerUsed != null) {
          if (execResult != null && execResult.getLeft() != this.handlerUsed) {
            try {
              execResult.getLeft().onError(execResult.getRight());
            } catch (Throwable t) {
              log.log(Level.SEVERE, null, t);
            }
          }
          this.handlerUsed.onError(this.errorException);
        }
        
        afterHandlingReadyHandler(this);
      }
    } else {
      afterHandlingReadyHandler(this);
    }

    return null;
  }

  private ResponseStream getInputStreamForResponse() {
    if (this.getResponse() == null) {
      log.warning("Response is null - check threads.");
      return null;
    }
    return this.getResponse().getResponseReaderReadyToRead();
  }

  private int getErrorCode() {
    if (this.errorOccured != null) {
      // @todo clean this up
      switch (this.getErrorOccured()) {
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

  private boolean canClose(boolean finishedWriting) {
    if (this.getResponse() != null && 
            this.getResponse().isForcingNotKeepingAlive()) {
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

    if (finishedWriting && this.getResponse() != null) {
      long cl = this.getResponse().getContentLength();
      if (cl == -1) {
        return true; // close unknown contents once finished reading
      }
    }
    
    return false;
  }

  public int getMaxMessageSize(int defaultMaxMessageSize) {
    if (this.handlerUsed != null) {
      int maxSize = this.handlerUsed.getMaxIncomingDataSize();
      Handler tmp = this.handlerUsed.getNext();
      
      while (tmp != null) {
        maxSize = Math.max(tmp.getMaxIncomingDataSize(), maxSize);
        tmp = tmp.getNext();
      }
      
      if (maxSize > -2) {
        return maxSize;
      }
    }

    return defaultMaxMessageSize;
  }

  public long getMaxIdle(long defaultMaxIdle) {
    if (this.getHandlerUsed() != null) {
      int maxIdle = this.getHandlerUsed().getMaxIdle();
      Handler tmp = this.getHandlerUsed().getNext();
      
      while (tmp != null) {
        maxIdle = Math.max(tmp.getMaxIdle(), maxIdle);
        tmp = tmp.getNext();
      }
      
      if (maxIdle > -1) {
        return maxIdle;
      }
    }

    return defaultMaxIdle;
  }

  /**
   * @return the request
   */
  public Request getRequest() {
    return request;
  }

  /**
   * @return the response
   */
  public Response getResponse() {
    return response;
  }

  /**
   * @return the channel
   */
  public SocketChannel getChannel() {
    return channel;
  }

  /**
   * @return the acceptedTime
   */
  public long getAcceptedTime() {
    return acceptedTime;
  }

  /**
   * @param acceptedTime the acceptedTime to set
   */
  protected void setAcceptedTime(long acceptedTime) {
    this.acceptedTime = acceptedTime;
  }
  
  protected void initLock() {
    if (atomicRefToHandlingThread == null) {
      atomicRefToHandlingThread = new AtomicReference<>();
    }
  }
  
  protected boolean canCloseOrResetAndPutBack(
          SocketChannel channel,
          boolean finishedWriting) {
    if (this.canClose(finishedWriting)) {
      return true;
    } else {
      this.reset();
      return false;
    }
  }

  protected final void connectionClosedHandler() {
    try {
      if (this.handlerUsed != null) {
        this.handlerUsed.connectionClosedHandler(this);
      }
    } catch (Throwable t) {
      log.log(Level.SEVERE, "Exception in on close handler.", t);
    } finally {
      try {
        finishedAndClosedHandler(this);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void startedAnyHandler(DataHandler dh) {
    dh.setAcceptedTime(System.currentTimeMillis());
    dh.touch();
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleStarted(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void headersReadyHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleHeadersReady(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void bodyReadyHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleBodyReady(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void beforeHandlingReadyHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleBeforeHandlingProcessing(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void afterHandlingReadyHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleAfterHandlingProcessed(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  protected final static void finishedAndClosedHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.onFinishedAndClosedHandler(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
  }
  
  /**
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * @return the errorOccured
   */
  public ErrorTypes getErrorOccured() {
    return errorOccured;
  }

  /**
   * @return the errorException
   */
  public Throwable getErrorException() {
    return errorException;
  }

  /**
   * @return the handlerUsed
   */
  public Handler getHandlerUsed() {
    return handlerUsed;
  }

  /**
   * @return the parameters
   */
  public String getParameters() {
    return parameters;
  }
}
