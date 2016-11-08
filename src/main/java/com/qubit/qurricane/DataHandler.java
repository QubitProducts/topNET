/*
 * Qurrican
 * Fast HTTP Server Solution.
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
package com.qubit.qurricane;

import static com.qubit.qurricane.BytesStream.BUF_SIZE_DEF;
import static com.qubit.qurricane.Handler.HTTP_0_9;
import static com.qubit.qurricane.Handler.HTTP_1_0;
import static com.qubit.qurricane.Handler.HTTP_1_1;
import static com.qubit.qurricane.Handler.HTTP_1_x;
import com.qubit.qurricane.errors.ErrorHandlingConfig;
import com.qubit.qurricane.errors.ErrorTypes;
import static com.qubit.qurricane.errors.ErrorTypes.BAD_CONTENT_HEADER;
import com.qubit.qurricane.utils.Pair;
import java.io.IOException;
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
  
  public static GeneralGlobalHandlingHooks postPreProcessingHandler;
  /**
   * @return the postPreProcessingHandler
   */
  public static GeneralGlobalHandlingHooks getPostPreProcessingHandler() {
    return postPreProcessingHandler;
  }

  /**
   * @param handler
   */
  public static void setGeneralGlobalHandlingHooks(GeneralGlobalHandlingHooks handler) {
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

  private final StringBuilder currentLine = new StringBuilder();
  private long contentLength = 0; // -1 is used to distinguish cases when no 

  private Request request;
  private Response response;
  private ErrorTypes errorOccured;
  private Throwable errorException;
  private Handler handlerUsed;

  static final String UTF_TEXT_HTML_HEADER
          = "Content-Type: text/html; charset=utf-8\n\r";
  static final String EOL = "\n";
  private String parameters;
  private String httpProtocol = HTTP_1_0;
  
  private boolean headersOnly = false;
  private volatile Server server;
  private final int maxGrowningBufferChunkSize;
  private boolean wasMarkedAsMoreDataIsComing;
  private volatile SocketChannel channel;
  private long acceptedTime;
  private boolean bufferSizeCalculatedForWriting = false;
  protected volatile HandlingThread owningThread = null;
  private boolean reqInitialized = false;
  private int previous = -1;
  int currentBufferWrittenIndex = 0;
  int currentReadingPositionInWrittenBufByWrite = 0;

  protected void reset() {
    resetForNewRequest();
    server = null;
  }

  protected void resetForNewRequest() {
    previous = -1;
    parameters = null;
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
    contentLength = 0;
    if (request != null) {
      request.reset();
    }
    if (response != null) {
      response.reset();
    }
    errorOccured = null;
    errorException = null;
    handlerUsed = null;
    headersOnly = false;
    bufferSizeCalculatedForWriting = false;
    currentBufferWrittenIndex = 0;
    currentReadingPositionInWrittenBufByWrite = 0;
    reqInitialized = false;
    httpProtocol = HTTP_1_0;
    
    wasMarkedAsMoreDataIsComing = false;
    acceptedTime = 0;
  }
  
  public DataHandler(Server server, SocketChannel channel) {
    this.init(server, channel);
    this.maxGrowningBufferChunkSize = server.getDataHandlerWriteBufferSize();
  }
  
  // returns -2 when done reading -1 when data stream  to read is finished , 
  //-2 means that logically read is over, -1 that EOF stream occured 
  public int read()
          throws IOException {

    if (this.request == null) {
      this.request = new Request();
      this.response = new Response();
    }
    
    if (!this.reqInitialized) {
      this.reqInitialized = true;
      this.request.init(this.channel, this.headers);
      this.response.init(this.httpProtocol);
      this.request.getBytesStream().clear();
    }
    
    int read = 0;
    int currentSum = 0;
    
    ByteBuffer buf = this.request.getBytesStream().getNotEmptyCurrentBuffer();
    
    while (read != -2 && (read = this.channel.read(buf)) > 0) {
      this.size += read;
      
      if (read > 0) {
        currentSum += read;
        this.touch();
      }
      
      if (this.flushReads(this.request.getBytesStream())) {
        read = -2; // -1: reading is finished, -2 means done
        this.handleData();
      }
    }

    if (read < 0) {
      return read;
    } else {
      return currentSum;
    }
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

  // returns true if reading is finished. any error handling should happend after reading foinished.
  private boolean flushReads(BytesStream stream)
          throws UnsupportedEncodingException {

    if (!this.headersReady) {
      int current;
      
      while ((current = stream.read()) != -1) {
        if (current == '\n' && previous == '\r') {

          previous = -1;
          if (this.processHeaderLine()) {
            this.errorOccured = BAD_CONTENT_HEADER;
            return true; // finish now!
          }

          if (this.headersReady) {
            if (this.contentLength > stream.getBufferElementSize() * 2) {
              stream.setBufferElementSize(
                  this.calculateBufferSizeByContentSize(this.contentLength));
            }
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
      response.setHttpProtocol(httpProtocol);
    
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
      
      this.handlerUsed.triggerOnBeforeOutputStreamIsSet(
                                                  this.request,
                                                  this.response);
      
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

      if (this.contentLength > 0) {
        handlerUsed.onBytesRead();
        // this also validates this.bodyRequired
        if (request.getBytesStream().leftToRead() >= this.contentLength) {
          return true;
        }
      } else {
        // stop reading now coz there is nothing to read (not bcoz of error)
        return true;
      }
    }

//    buffer.clear();
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
  
  protected int write() throws IOException {
    this.markWriting();
    
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
        return this.getFinishedWritingResponse();
      }
      if (this.response.isMoreDataComing()) {
        this.wasMarkedAsMoreDataIsComing = true;
        return 0;
      } else {
        return this.getFinishedWritingResponse();
      }
    }

    
    if (!this.response.isTooLateToChangeHeaders()) {
      this.response.setTooLateToChangeHeaders(true);
    }
    
    ByteBuffer writeBuffer ;
    BytesStream bs = request.getBytesStream();
    
    if (!this.bufferSizeCalculatedForWriting) {
      this.bufferSizeCalculatedForWriting = true;
      if (response.getContentLength() > 0) {
        long bufSize = response.getContentLength() - bs.dataSize();
        if (bufSize <= BUF_SIZE_DEF) {
          bufSize = BUF_SIZE_DEF;
        } else {
          bufSize = this.calculateBufferSizeByContentSize(bufSize);
        }
        bs.setBufferElementSize((int)bufSize);
      }
    }
    
    int written = 0;
    boolean touchIt = false;
    int ch = 0;
    int writtenFromBuffer = 0;
    
    do {
      writtenFromBuffer = 0;
      
      if (currentReadingPositionInWrittenBufByWrite == 0) {
        writeBuffer = bs.getNotEmptyCurrentBuffer();
      } else {
        writeBuffer = bs.getCurrentBufferWriting();
        // havent finished reading from buffer, and will wait here
      }
      
      while (writeBuffer.hasRemaining() && (ch = responseReader.read()) != -1) {
        writeBuffer.put((byte) ch);
      }
      
      // store old position
      int tmpPos = writeBuffer.position();
      
      writeBuffer.limit(tmpPos);
      writeBuffer.position(currentReadingPositionInWrittenBufByWrite);
      
      if (currentReadingPositionInWrittenBufByWrite < tmpPos) {
        writtenFromBuffer = this.channel.write(writeBuffer);
        currentReadingPositionInWrittenBufByWrite += writtenFromBuffer;
        written += writtenFromBuffer;
        
        if (!touchIt && writtenFromBuffer > 0) {
          touchIt = true;
        }
      }
      
      //bring back buffer for writing
      writeBuffer.position(tmpPos);
      writeBuffer.limit(writeBuffer.capacity());
      
      if (!writeBuffer.hasRemaining()) {
        currentReadingPositionInWrittenBufByWrite = 0;
      }
      
    } while (writtenFromBuffer > 0);

    if (touchIt) {
      this.touch();
    }
    
    if ((ch == -1)) {
      if (this.response.isMoreDataComing()) {
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
          return this.getFinishedWritingResponse();
        }
      }
    } else {
      return written;
    }
  }

  // returns true if writing should be stopped function using it should reply 
  // asap - typically its used to repoly unsupported fullPath 
  private ErrorTypes headersAreReadySoProcessReqAndRes(Handler handler) {
    
    if (handler == null) {
      return ErrorTypes.HTTP_NOT_FOUND;
    } else {
      this.request.setPath(this.path);
      this.request.setFullPath(this.fullPath);
      this.request.setPathParameters(this.parameters);
      this.request.setMethod(this.method);
      
      // this prepares space for BODY to be written to, once headers are sorted,
      // possibly body will be written
//      request.makeSureOutputStreamIsReady();
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
    }

    beforeHandlingReadyHandler(this);
    
    if (handler != null) {
      // @todo review if ctually pair usage is necesary... this passing and 
      // processing can be put into the handle method.
      Pair<Handler, Throwable> execResult = 
              handler.doProcess(this.request, this.response, this);
      
      try {
        if (execResult != null) {
          // handle processing error, be delicate:
          this.errorOccured = ErrorTypes.HTTP_SERVER_ERROR;
          this.errorException = execResult.getRight();
          log.log(Level.WARNING, "Exception in handler.", this.errorException);

          handler = getErrorHandler(handler);

          try {
            handler.doProcess(this.request, this.response, this);
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
    if (this.response == null) {
      log.warning("Response is null - check threads.");
      return null;
    }
    return this.response.getResponseReaderReadyToRead();
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
    if (this.response != null && 
            this.response.isForcingNotKeepingAlive()) {
      return true;
    }

    if (finishedWriting && this.response != null) {
      long cl = this.response.getContentLength();
      if (cl == -1) {
        return true; // close unknown contents once finished reading
      }
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
  
  protected boolean finishedOrWaitForMoreRequests(boolean finishedWriting) {
    if (this.canClose(finishedWriting)) {
      return true;
    } else {
      this.resetForNewRequest();
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
  
  protected final void setAcceptAndRunHandleStarted(Long ts) {
    if (ts == null) {
      this.setAcceptedTime(System.currentTimeMillis());
    } else {
      this.setAcceptedTime(ts);
    }
    this.touch();
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.handleStarted(this);
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

  private void markWriting() {
    if (!this.writingResponse) {
      this.bufferSizeCalculatedForWriting = false;
      request.getBytesStream().clear();
      this.writingResponse = true; // running writing
    }
  }

  private int getFinishedWritingResponse() {
    this.writingResponse = false; // finished writing
    return -1;
  }
  
  private int calculateBufferSizeByContentSize(long cl) {
    return Math.min(this.maxGrowningBufferChunkSize, (int) (cl / 2));
  }

  protected void init(Server server, SocketChannel channel) {
    this.channel = channel;
    this.server = server;
    touch = System.currentTimeMillis();
  }
}
