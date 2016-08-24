/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import com.qubit.qurricane.errors.ErrorTypes;
import static com.qubit.qurricane.errors.ErrorTypes.BAD_CONTENT_HEADER;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class DataHandler {

  private volatile long touch; // check if its needed volatile
  private volatile long size = 0; // check if its needed volatile

  private final Map<String, String> headers = new HashMap<>();

  private String method;
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
  private ErrorTypes errorOccured = null;
  private Throwable errorException;
  
  public DataHandler() {
    touch = System.currentTimeMillis();
  }

  // returns true if should listen for write
  public synchronized int read(SelectionKey key, ByteBuffer buf) 
          throws IOException {
    SocketChannel socketChannel = (SocketChannel) key.channel();

    buf.clear();
    int read;

    if ((read = socketChannel.read(buf)) > 0) {
      if (this.flushReads(key, buf)) {
        read = -2; // -1: reading is finished, -2 means done
      }
    }
    
    if (read == -1) {
      socketChannel.close();
    }

    this.touch();

    return read;
  }

  private void processsError() {
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
  public long getSize() {
    return size;
  }

  public String getEncoding() {
    return "UTF-8";
  }

  private void setMethodAndPath(String line) {
    int idx = line.indexOf(" ");
    this.method = line.substring(0, idx);
    int sec_idx = line.indexOf(" ", idx + 1);
    if (sec_idx != -1) {
      this.path = line.substring(idx + 1, sec_idx);
    } else {
      this.path = line.substring(idx + 1);
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
      return new String[]{
        line.substring(0, idx),
        line.substring(idx + 2)
      };
    } else {
      return null;
    }
  }

  /**
   * @return the path
   */
  public String getPath() {
    return path;
  }

  static final Map<String, Handler> handlers;
  
  static {
    handlers = new HashMap<>();
  }
  
  private static Handler getHandlerForPath(String path) {
    return handlers.get(path);
  }

  private void onInvalidPath() {
    
  }
  
  private byte previous = -1;
  
  // returns true if reading is finished
  protected synchronized boolean flushReads(SelectionKey key, ByteBuffer buffer) 
          throws UnsupportedEncodingException {
    
    buffer.flip();

    if (!this.headersReady) {

      while (buffer.hasRemaining()) {

        byte current = buffer.get();

        if (current == '\n' && previous == '\r') {

          previous = -1;
          if (this.processHeaderLine()) {
            this.errorOccured = BAD_CONTENT_HEADER;
            return true; // finish now!
          }

          if (this.headersReady) {
            if (this.bodyRequired) {
              try {
                this.contentLength
                        = Long.parseLong(this.headers.get("Content-Length"));
              } catch (NullPointerException | NumberFormatException ex) {
                this.errorOccured = ErrorTypes.BAD_CONTENT_LENGTH;
                return true;
              }
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
      if (this.headersAreReadySoProcessReqAndRes(key) != null) {
        return true;
      }
      
      if (previous != -1) { // append last possible character
        currentLine.append((char) previous);
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
            return true;
          }
        }

        if (this.contentLengthCounter >= this.contentLength) {
          return true;
        }

      } else {
        // ignore rest of request if content length is unset!
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

      bodyRequired = line.startsWith("POST ") || line.startsWith("PUT ")
              || line.startsWith("PATCH ");
      // two birds as one
      if (bodyRequired
              || line.startsWith("GET ")
              || line.startsWith("OPTIONS ") // @todo review
              || line.startsWith("DELETE ")) {

        this.setMethodAndPath(line);

      } else {
        this.errorOccured = ErrorTypes.HTTP_UNKNOWN_METHOD;
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
              headers.put(twoStrings[0], twoStrings[1]);
              lastHeaderName = twoStrings[0];
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
  
  public synchronized boolean write(
          SelectionKey key,
          ByteBuffer buffer) 
          throws UnsupportedEncodingException, IOException {
    
    SocketChannel channel = (SocketChannel) key.channel();
    buffer.clear();
    
    InputStream currentSource = this.response.getInputStream();
    
    int ch = 0;
    while(buffer.hasRemaining() && (ch = currentSource.read()) != -1) {
      buffer.putInt(ch);
    }
    
    buffer.flip();
    channel.write(buffer);
    
    return ch == -1;
  }

  // returns true if writing should be stopped function using it should reply 
  // asap - typically its used to repoly unsupported path 
  private ErrorTypes headersAreReadySoProcessReqAndRes(SelectionKey key) {
    this.response = new Response();
    
    if (handlers == null || handlers.isEmpty()) {
      this.onInvalidPath();
      return ErrorTypes.HTTP_NOT_FOUND;
    }

    Handler handler = getHandlerForPath(this.path);
    
    if (handler == null) {
      return ErrorTypes.HTTP_NOT_FOUND;
    }
    
    if (handler.supports(this.method)) {
      this.request = new Request(key, headers, null);
      handler.init(this.request, this.response);
      this.request.setStreamSet(true);
      request.setMethod(this.method);
      request.setPath(this.path);
      handler.process(request, response);
    } else {
      return ErrorTypes.HTTP_NOT_FOUND;
    }
    
    return null;
  }
}
