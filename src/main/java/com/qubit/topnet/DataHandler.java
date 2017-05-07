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

import static com.qubit.topnet.BytesStream.NO_BYTES_LEFT_VAL;
import static com.qubit.topnet.ServerBase.HTTP_1_1;
import com.qubit.topnet.errors.ErrorHandlingConfig;
import com.qubit.topnet.errors.ErrorTypes;
import com.qubit.topnet.utils.Pair;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public final class DataHandler {

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

  public AtomicReference<AbstractHandlingThread> atomicRefToHandlingThread;
  public boolean writingResponse = false;
  public volatile AbstractHandlingThread owningThread = null;

  private volatile long touch; // check if its needed volatile
  private volatile int size = 0; // check if its needed volatile

  private boolean headersReady;
  private boolean bodyRequired = false;

  private boolean firstLine = true;
  private String lastHeaderName = null;
  private String lastHeaderValue = null;

  private final byte[] currentHeaderLine;
  private long contentLength = 0; // -1 is used to distinguish cases when no

  private Request request;
  private Response response;
  
  private BufferWrapper currentResponseLoadingBuffer;
  private BufferWrapper currentResponseUnloadingBuffer;
  
  private ErrorTypes errorOccured;
  private Throwable errorException;
  private Handler handlerUsed;
  private volatile SocketChannel channel = null;
  private SelectionKey selectionKey = null;

  private boolean headersOnly = false;
  private volatile ServerBase server;
  private final int maxFromContentSizeBufferChunkSize;
  private boolean wasMarkedAsMoreDataIsComing;

  private long acceptedTime;
  private long requestStartedTime = 0;
  
  private boolean bufferSizeCalculatedForWriting = false;
  private boolean reqInitialized = false;
  
  int currentBufferWrittenIndex = 0;
  int currentReadingPositionInWrittenBufByWrite = 0;
  
  private int currentHeaderLineLength = 0;

  public DataHandler(ServerBase server, SelectionKey key) {
    this.init(server, key);
    this.currentHeaderLine = new byte[server.getDefaultHeaderSizeLimit()];
    this.maxFromContentSizeBufferChunkSize = 
        server.getMaxFromContentSizeBufferChunkSize();
  }

  public DataHandler(ServerBase server, SocketChannel channel) {
    this.init(server, channel);
    this.currentHeaderLine = new byte[server.getDefaultHeaderSizeLimit()];
    this.maxFromContentSizeBufferChunkSize = 
        server.getMaxFromContentSizeBufferChunkSize();
  }

  public void init(ServerBase server, SocketChannel channel) {
    this.channel = channel;
    this.server = server;
    touch = System.currentTimeMillis();
  }

  public void init(ServerBase server, SelectionKey key) {
    this.channel = (SocketChannel) key.channel();
    this.selectionKey = key;
    this.server = server;
    touch = System.currentTimeMillis();
  }

  public void reset() {
    resetForNewRequest();
    server = null;
    selectionKey = null;
  }

  protected void resetForNewRequest() {
    this.size = 0;
    this.headersReady = false;
    this.writingResponse = false;
    this.bodyRequired = false;
    this.firstLine = true;
    this.lastHeaderName = null;
    this.lastHeaderValue = null;
    this.currentHeaderLineLength = 0;
    this.contentLength = 0;

    if (this.request != null) {
      this.request.reset();
    }

    if (this.response != null) {
      this.response.reset();
    }

    this.errorOccured = null;
    this.errorException = null;
    this.handlerUsed = null;
    this.headersOnly = false;
    this.bufferSizeCalculatedForWriting = false;
    this.currentBufferWrittenIndex = 0;
    this.reqInitialized = false;
    this.wasMarkedAsMoreDataIsComing = false;
    this.acceptedTime = 0;
    this.setRequestStartedTime(0);
  }

  // returns -2 when done reading -1 when data stream  to read is finished , 
  //-2 means that logically read is over, -1 that EOF stream occured 
  public int read()
      throws IOException {

    if (this.request == null) {
      this.request = new Request();
      this.response = new Response();
      this.request.init(this.channel, this.server);
      this.response.init(this.request.getRequestedHttpProtocol());
      this.reqInitialized = true;
    } else if (!this.reqInitialized) {
      this.reqInitialized = true;
      this.request.init(this.channel, this.server);
      this.response.init(this.request.getRequestedHttpProtocol());
      this.request.getBytesStream().reset();
    }

    int read = 0;
    int currentSum = 0;

    BytesStream bs = request.getBytesStream();
    ByteBuffer buf = bs.getBufferToWrite().getByteBuffer();

    while (read != -2 && (read = this.channel.read(buf)) > 0) {

      if (read > 0) {
        currentSum += read;
        this.size += read;
      } else if (read == -1) {
        // connection closed!
        return -1;
      }

      // flush only settles headers and check content length to know when to 
      // stop. Data is collected in dynamic buffer chain.
      if (this.flushReads(bs)) {
        read = -2; // -1: reading is finished, -2 means done
        this.handleData();
      } else {
        bs.moveReadTailToEndOrClearBufferIfSpaceUnavailable();
        buf = bs.getBufferToWrite().getByteBuffer();
      }
    }

    if (currentSum > 0) {
      this.touch();
      if (this.getRequestStartedTime() == 0) {
        this.setRequestStartedTime(this.touch);
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

  public static String[] parseHeader(byte[] line, int len, Charset headerCharset) {
    int idx = DataHandler.indexOf(line, len, ':', 0);
    if (idx > 0) {

      int valueIdx = idx;
      while (valueIdx < len
          && (line[valueIdx + 1] == ' ' || line[valueIdx + 1] == '\t')) {
        valueIdx++;
      }

      return new String[]{
        new String(line, 0, idx, headerCharset),
        new String(line, valueIdx + 1, len - (valueIdx + 1), headerCharset)
      };
    } else {
      return null;
    }
  }

  // returns true if reading is finished. any error 
  // handling should happend after reading finished.
  private boolean flushReads(BytesStream byteStream)
      throws UnsupportedEncodingException {

    if (!this.headersReady) {
      int current;

      while ((current = byteStream.readByte()) != NO_BYTES_LEFT_VAL) {
        if (current == '\n'
            && currentHeaderLine[currentHeaderLineLength - 1] == '\r') {

          currentHeaderLineLength -= 1;
          ErrorTypes error;

          if ((error = this.processHeaderLine()) != null) {
            this.errorOccured = error;
            this.response.setForcingClosingAfterRequest(true);
            return true; // finish now!
          }

          if (this.headersReady) {
            if (this.contentLength > 0
                && this.contentLength > byteStream.getSingleBufferChunkSize() * 2) {
              byteStream.setSingleBufferChunkSize(
                  this.calculateBufferSizeByContentSize(this.contentLength));
            }

            { // headers summary block
              
              // headers just finished
              if (this.server.getProtocol() > 0) {
                response.setHttpProtocol(server.getProtocol());
              } else {
                response.setHttpProtocol(
                    this.request.getRequestedHttpProtocol());
              }

              if (this.errorOccured != null) {
                return true;
              }

              // paths must be ready by headers setup
              this.handlerUsed
                  = this.server.getHandlerForPath(
                      this.request.getFullPath(),
                      this.request.getPath(),
                      this.request.getQueryString());

              if (this.handlerUsed == null) {
                this.errorOccured = ErrorTypes.HTTP_NOT_FOUND;
                return true;
              }

              this.handlerUsed
                  .triggerOnBeforeOutputStreamIsSet(this.request, this.response);

              if (this.bodyRequired) {
                if (this.contentLength < 0) {
                  this.response.setForcingClosingAfterRequest(true);
                  this.errorOccured = ErrorTypes.BAD_CONTENT_LENGTH;
                  return true;
                }
              }
            }

            break;
          }
        } else {
          //server.getDefaultHeaderSizeLimit() === currentHeaderLine.length
          if (currentHeaderLineLength < currentHeaderLine.length) {
            currentHeaderLine[currentHeaderLineLength++] = (byte) current;
          } else {
            this.response.setForcingClosingAfterRequest(true);
            this.errorOccured = ErrorTypes.HTTP_HEADER_TOO_LARGE;
            return true;
          }
        }
      }
    }

    if (this.headersReady) {
      if (this.contentLength > 0) {
        // this also validates this.bodyRequired
        if (request.getBytesStream().availableToRead() >= this.contentLength) {
          handlerUsed.onBytesRead(true);
          return true;
        }
        handlerUsed.onBytesRead(false);
      } else {
        handlerUsed.onBytesRead(true);
        // stop reading now coz there is nothing to read if cntnt is zero
        return true;
      }
    }

    // we want handlerUsed.onBytesRead to be called for the content only.
    return false;
  }

  private ErrorTypes processHeaderLine() {
    byte[] line = currentHeaderLine;
    int lineLen = currentHeaderLineLength;
    currentHeaderLineLength = 0; // reset

    if (firstLine) {
      //order here defined is for a reason
      firstLine = false;

      bodyRequired = this.checkIfBodyRequired(line, lineLen);
      // two birds as one
      // true if no headers should be read!
      boolean noHeaders = this.request.setMethodAndPathFromLine(line, lineLen);

      // check nonsense:
      if (this.request.getMethod() == null) {
        return ErrorTypes.HTTP_MALFORMED_HEADERS;
      }

      if (this.request.getMethod().equals("HEAD")) {
        this.headersOnly = true;
      }

      this.request.analyzePathAndSplit();

      if (noHeaders) {
        this.headersReady = true;
        headersReadyHandler(this);
      }
    } else {

      // if at body
      if (!this.headersReady) {

        // must be headers first, check if at the end of headers - empty line
        if (lineLen > 0) {

          // check if not multiline header, headers use single byte encoding
          if (line[0] == ('\t') || line[0] == (' ')) { // tab or space
            // multiline header, check if any header was read!
            if (lastHeaderName != null) {
              // @todo improve performance here
              if (lineLen > 1) {// lastHeaderValue never null
                lastHeaderValue = lastHeaderValue + "\n"
                    + new String(
                        line, 1, lineLen - 1, this.server.getHeaderCharset());
              }
            } else {
              return ErrorTypes.HTTP_MALFORMED_HEADERS;
            }
          } else {

            // header flushing block.
            if (lastHeaderName != null) {
              this.putHeader(lastHeaderName, lastHeaderValue);
              lastHeaderName = lastHeaderValue = null;
            }

            String[] twoStrings
                = DataHandler.parseHeader(
                    line, lineLen, this.server.getHeaderCharset());

            if (twoStrings != null) {
              lastHeaderName = twoStrings[0];
              if (!lastHeaderName.isEmpty()) {
                lastHeaderValue = twoStrings[1]; //never null
                if (lastHeaderName.length() == 14) {//optimisation
                  if (lastHeaderName.toLowerCase().equals("content-length")) {
                    try {
                      this.contentLength
                          = Long.parseLong(lastHeaderValue.trim(), 10);
                    } catch (NullPointerException | NumberFormatException ex) {
                      // just try, weird stuff ignore in this case...
                    }
                  }
                }
              }
            } else {
              return ErrorTypes.HTTP_MALFORMED_HEADERS;
            }
          }
        } else {
          // header flushing block.
          if (lastHeaderName != null) {
            this.putHeader(lastHeaderName, lastHeaderValue);
            lastHeaderName = lastHeaderValue = null;
          }
          this.headersReady = true;
          headersReadyHandler(this);
        }
      }
    }

    return null;
  }

  public int write() throws IOException {
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

    BytesStream bytesStream = request.getBytesStream();

    // adjust dynamicly buf size for future reads
    if (!this.bufferSizeCalculatedForWriting) {
      this.bufferSizeCalculatedForWriting = true;
      if (response.getContentLength() > 0) {
        long bufSize = response.getContentLength() - bytesStream.dataSize();
        if (bufSize <= BytesStream.getDefaultBufferChunkSize()) {
          bufSize = BytesStream.getDefaultBufferChunkSize();
        } else {
          bufSize = this.calculateBufferSizeByContentSize(bufSize);
        }
        bytesStream.setSingleBufferChunkSize((int) bufSize);
      }
    }

    int written = 0;
    int readResult = 0;
    int writtenFromBuffer;

    do {

      writtenFromBuffer = 0;

      // load data to send
      readResult = this.loadIntoWholeBuffer(responseReader, bytesStream);

      // flip em all
      this.flipAll(bytesStream);

      // writing to socket section
      do {
        ByteBuffer writeBuffer = this.currentResponseUnloadingBuffer.getByteBuffer();

        writtenFromBuffer = this.channel.write(writeBuffer);
        written += writtenFromBuffer;

        // buffer must be filled and fully read 
        if (!writeBuffer.hasRemaining()) { // emptied
          if (bytesStream.getCurrentWritingBuffer().getByteBuffer()
              == writeBuffer) { // last buf that was to read
            this.currentResponseUnloadingBuffer = null; // exit
            break;
          } else {
            // get next buf in queue
            this.currentResponseUnloadingBuffer = 
                this.currentResponseUnloadingBuffer.getNext();

            if (this.currentResponseUnloadingBuffer == null) { // last ? exit
              break;
            }
          }
        }

      } while (writtenFromBuffer > 0);

    } while (writtenFromBuffer > 0 );

    if (written > 0) {
      this.touch();
    }

    // if nothing in output buff and nothjing to read from provider
    if (this.currentResponseUnloadingBuffer == null && readResult == -1) {

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

  private void flipAll(BytesStream bytesStream) {
    
    if (this.currentResponseUnloadingBuffer == null) {

      BufferWrapper curBuf = bytesStream.getFirst();
      
      do {
        curBuf.getByteBuffer().flip();
      } while (bytesStream.getCurrentWritingBuffer() != curBuf
               && (curBuf = curBuf.getNext()) != null);
      
      this.currentResponseUnloadingBuffer = bytesStream.getFirst();
    }
  }

  // alternative to previous, larger loads but slower response
  private int loadIntoWholeBuffer(
                  ResponseReader responseReader,
                  BytesStream bytesStream)
            throws IOException {

    if (this.currentResponseUnloadingBuffer == null) {
      bytesStream.reset(); /// reset no needed
      this.currentResponseLoadingBuffer = bytesStream.getFirst();
      if (this.currentResponseLoadingBuffer == null) {
        bytesStream.getBufferToWrite();
        this.currentResponseLoadingBuffer = bytesStream.getFirst();
      }
    }

    int readResult = 0;
    int amount = 0;
    
    // loading section into buffer
    while (this.currentResponseLoadingBuffer != null) {
      ByteBuffer writeBuffer = 
          this.currentResponseLoadingBuffer.getByteBuffer();
      
      // fill buffer with data
      int pos = writeBuffer.position();

      if (!response.isReadingChannelResponseOnly()) {
        while (writeBuffer.hasRemaining()
            && (readResult = responseReader.read()) != -1) {
          writeBuffer.put((byte) readResult);
        }
      }

      if (readResult == -1) {
        if (responseReader.getByteChannel() != null) {
          readResult = responseReader.getByteChannel().read(writeBuffer);
        }
      }

      amount += writeBuffer.position() - pos;

      // nothig to read or max limit gained for loading
      if (readResult == -1 || server.getMaxResponseBufferFillSize() <= amount) {
        this.currentResponseLoadingBuffer = null;
        break;
      } else if (!writeBuffer.hasRemaining()) { // buffer filled
        this.currentResponseLoadingBuffer = bytesStream.getBufferToWrite();
        if (!this.currentResponseLoadingBuffer.getByteBuffer().hasRemaining()) {
          this.currentResponseLoadingBuffer = null; // buffer at max
          break;
        }
      }
    }
    return readResult;
  }

  /**
   * @unused
   * Simple writer.
   *
   * @return
   * @throws IOException
   */
  private int writeSimple() throws IOException {
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

    BytesStream bytesStream = request.getBytesStream();

    int written = 0;
    int readResult = 0;
    int writtenFromBuffer;

    BufferWrapper buf = bytesStream.getFirst();
    if (buf == null) {
      bytesStream.getBufferToWrite();
      buf = bytesStream.getFirst();
    }
    ByteBuffer writeBuffer = buf.getByteBuffer();

    boolean cleared;

    do {
      cleared = false;
      writtenFromBuffer = 0;

      // loading section into buffer
      // fill buffer with data
      if (!this.response.isReadingChannelResponseOnly()) {
        while (writeBuffer.hasRemaining()
            && (readResult = responseReader.read()) != -1) {
          writeBuffer.put((byte) readResult);
        }
      }

      if (readResult == -1) {
        if (responseReader.getByteChannel() != null) {
          readResult = responseReader.getByteChannel().read(writeBuffer);
        }
      }

      // flip it
      int oldPos = writeBuffer.position();
      writeBuffer.position(currentReadingPositionInWrittenBufByWrite);
      writeBuffer.limit(oldPos);

      // writing to socket section
      writtenFromBuffer = this.channel.write(writeBuffer);
      written += writtenFromBuffer;

      currentReadingPositionInWrittenBufByWrite = writeBuffer.position();

      if (currentReadingPositionInWrittenBufByWrite == oldPos) {
        // all written from buffer, reset it
        cleared = true;
        currentReadingPositionInWrittenBufByWrite = 0;
        writeBuffer.clear();
      } else {
        writeBuffer.position(oldPos);
      }

    } while (writtenFromBuffer > 0);

    if (written > 0) {
      this.touch();
    }

    if (cleared && readResult == -1) {
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
          // Reading input is over, stream was used so close it:
          DataHandler.closeResponseReaderStream(this.response);
          return this.getFinishedWritingResponse();
        }
      }
    } else {
      return written;
    }
  }

  private Handler getErrorHandler(Handler handler) {
    Handler errorHandler
        = this.server.getErrorHandlingConfig()
            .getDefaultErrorHandler(getErrorCode(), this.errorOccured);

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
      Pair<Handler, Throwable> execResult
          = handler.doProcess(this.request, this.response, this);

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
        case BAD_CONTENT_LENGTH:
        case HTTP_MALFORMED_HEADERS:
        case HTTP_HEADER_TOO_LARGE:
        case HTTP_UNSET_METHOD:
        case HTTP_BAD_REQUEST:
          return 400;
        case IO_ERROR:
        case HTTP_SERVER_ERROR:
        case HTTP_UNKNOWN_ERROR:
          return 503;
        default:
          return 503;
      }
    }
    return 200;
  }

  private boolean canClose(boolean finishedWriting) {
    if (this.response == null) {
      return true;
    }

    if (this.response.isForcingClosingAfterRequest()) {
      return true;
    }

    if (finishedWriting) {
      long cl = this.response.getContentLength();
      if (cl == -1) {
        return true;
      }
    }

    if (this.request.getRequestedHttpProtocol() == HTTP_1_1) {
      return false;
    }

    String connection = this.getHeader("Connection");

    if (connection != null) {
      switch (connection) {
        case "keep-alive":
          return false;
      }
    }

    return true;
  }

  public long getMaxMessageSize(long defaultMaxMessageSize) {
    if (this.handlerUsed != null) {
      long maxSize = this.handlerUsed.getMaxIncomingDataSize();
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

  public boolean finishedOrWaitForMoreRequests(boolean finishedWriting) {
    if (this.canClose(finishedWriting)) {
      return true;
    } else {
      this.resetForNewRequest();
      return false;
    }
  }

  public final void requestFinishedHandler() {
    try {
      if (this.handlerUsed != null) {
        this.handlerUsed.requestFinishedHandler(this);
      }
    } catch (Throwable t) {
      log.log(Level.SEVERE, "Exception in on close handler.", t);
    } finally {
      finishedRequestHandler(this);
    }

    if (this.getRequest() != null) {
      if (this.getRequest().getWriteFinishedHandler() != null) {
        try {
          this.getRequest().getWriteFinishedHandler().run();
        } catch (Throwable e) {
          log.log(Level.SEVERE, "Error running write finishing handler.", e);
        }
      }
    }
  }

  public final void connectionClosedHandler() {
    try {
      if (this.handlerUsed != null) {
        this.handlerUsed.connectionClosedHandler(this);
      }
    } catch (Throwable t) {
      log.log(Level.SEVERE, "Exception in on close handler.", t);
    } finally {
      try {
        finishedAndClosedHandler(this);
      } finally {
        cleanupAfterProcessing();
      }
    }
  }

  public final void setAcceptAndRunHandleStarted(Long ts) {
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

  public final static void bodyReadyHandler(DataHandler dh) {
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

  protected final static void finishedRequestHandler(DataHandler dh) {
    if (postPreProcessingHandler != null) {
      try {
        postPreProcessingHandler.requestFinishedHandler(dh);
      } catch (Throwable t) {
        log.log(Level.SEVERE, null, t);
      }
    }
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

  private void markWriting() throws ClosedChannelException {
    if (!this.writingResponse) {
      this.bufferSizeCalculatedForWriting = false;
      request.getBytesStream().reset(); //--> now done in write function
      this.registerForWriting(); // if it is in that mode!
      this.writingResponse = true; // running writing
    }
  }

  private int getFinishedWritingResponse() throws ClosedChannelException {
    this.cleanupAfterProcessing();
    this.registerForReading();
    return -1;
  }

  private int calculateBufferSizeByContentSize(long cl) {
    return Math.min(this.maxFromContentSizeBufferChunkSize, (int) (cl / 2));
  }

  private String getHeader(String name) {
    for (String[] header : this.request.getHeaders()) {
      if (header[0].equals(name)) {
        return header[1];
      }
    }
    return null;
  }

  private void putHeader(String lastHeaderName, String string) {
    this.request.getHeaders().add(new String[]{lastHeaderName, string});
  }

  private boolean checkIfBodyRequired(byte[] line, int idx) {
    // strictly private method and we assume line length much longer than 
    // checks provided in here (its obvious)
    if (line[0] == 'G'
        && // add exclusions
        line[1] == 'E'
        && line[2] == 'T') {
      return false;
    } else if (line[0] == 'H'
        && line[1] == 'E'
        && line[2] == 'A'
        && line[3] == 'D') {
      return false;
    } else if (line[0] == 'D'
        && line[1] == 'E'
        && line[2] == 'L'
        && line[3] == 'E'
        && line[4] == 'T'
        && line[5] == 'E') {
      return false;
    } else if (line[0] == 'T'
        && line[1] == 'R'
        && line[2] == 'A'
        && line[3] == 'C'
        && line[4] == 'E') {
      return false;
    }

    return true;
  }

  public static int indexOf(byte[] line, int len, char c, int from) {
    for (int i = from; i < len; i++) {
      if (line[i] == c) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @return the selectionKey
   */
  public SelectionKey getSelectionKey() {
    return selectionKey;
  }

  public void registerForWriting() throws ClosedChannelException {
    if (this.selectionKey == null) {
      return;
    }

    this.channel.register(
        this.server.getChannelSelector(),
        OP_WRITE,
        this.owningThread);
  }

  private void registerForReading() throws ClosedChannelException {
    if (this.selectionKey == null) {
      return;
    }
    if (this.owningThread != null && this.server != null) {
      this.channel.register(
          this.server.getChannelSelector(),
          OP_READ,
          this.owningThread);
    }
  }

  /**
   * @return the requestStartedTime
   */
  public long getRequestStartedTime() {
    return requestStartedTime;
  }

  /**
   * @param requestStartedTime the requestStartedTime to set
   */
  public void setRequestStartedTime(long requestStartedTime) {
    this.requestStartedTime = requestStartedTime;
  }

  public long getContentLength() {
    return this.contentLength;
  }

  private static void closeResponseReaderStream(Response resp) {
    try {
      // cleanup and close input stream
      if (resp.getResponseStream() != null) {
        resp.getResponseStream().close();
      }
    } catch (IOException ex) {
      log.log(Level.SEVERE, null, ex);
    }
  }

  private void cleanupAfterProcessing() {
    if (this.writingResponse) {
      this.writingResponse = false; // finished writing
      this.currentResponseUnloadingBuffer = null;
      // this.currentResponseLoadingBuffer = null; // unnecessary as 
      // currentResponseUnloadingBuffer equal null will cause it
      // Reading input is over, stream was used so close it:
      DataHandler.closeResponseReaderStream(this.response);
      // requerst uses growing buffer to store data and for larger inputs
      // handlers should controll handlingh and closing.
    } else {
      if (handlerUsed != null) {
        handlerUsed.onBytesRead(false);
      }
    }
  }
}
