/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import static com.qubit.qurricane.Server.BUF_SIZE;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

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
  private boolean bodyRequired = false;

  private boolean firstLine = true;
  private String lastHeaderName = null;
  private final ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
  private final StringBuffer currentLine = new StringBuffer();
  private int contentLengthCounter = 0;
  private long contentLength = 0; // -1 is used to distinguish cases when no 

  public volatile boolean locked = false;
  
  public DataHandler() {
    touch = System.currentTimeMillis();
  }

  // returns true if should listen for write
  public int read(SelectionKey key, ByteBuffer buf) throws IOException {

    SocketChannel socketChannel = (SocketChannel) key.channel();

    buf.clear();
    int read;

    if ((read = socketChannel.read(buf)) > 0) {
      if (this.flushReads(key, buf)) {
        read = -2; // -1: reading is finished
      }
    }
    
    if (read == -1) {
      socketChannel.close();
      this.processError();
    }

    this.touch();

    return read;
  }

  public void processError() {

  }

  public void processRequest() {
  }

  protected void finish() {
  }

  public long getTouch() {
    return touch;
  }

  public void touch() {
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
   * @return the headers
   */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /**
   * @return the path
   */
  public String getPath() {
    return path;
  }

  private boolean hasHandlersForMethod(List<Handler> handlers) {
    return true;
  }

  private void onInvalidMethod() {

  }

  private List<Handler> hasHandlersForPath() {
    return null;
  }

  private void onInvalidPath() {

  }

  // returns true if write listening should be enabled
  protected synchronized boolean flushReads(SelectionKey key, ByteBuffer buffer) 
          throws UnsupportedEncodingException {
    byte previous = -1;
    buffer.flip();

    if (!this.headersReady) {

      while (buffer.hasRemaining()) {

        byte current = buffer.get();

        if (current == '\n' && previous == '\r') {

          previous = -1;
          this.processHeaderLine();

          if (this.headersReady) {
            if (this.bodyRequired) {
              try {
                this.contentLength
                        = Long.parseLong(this.headers.get("Content-Length"));
              } catch (NullPointerException | NumberFormatException ex) {
                this.processError(); // bad headers
                return false;
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

      if (this.contentLength > 0) {
        if (buffer.hasRemaining()) {

          int pos = buffer.position();
          int amount = buffer.limit() - pos;

          this.contentLengthCounter += amount;

          bodyBuffer.write(
                  buffer.array(),
                  pos,
                  amount);
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

  private void processHeaderLine() {
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

        List<Handler> handlers = this.hasHandlersForPath();

        if (handlers == null || handlers.isEmpty()) {
          this.onInvalidPath();
          /// return;
        }

        if (!this.hasHandlersForMethod(handlers)) {
          this.onInvalidMethod();
          /// return;
        }

        // method and path is fine
      } else {
        this.processError();
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
              this.processError();
              return; // yuck! headers malformed!! not even started and multiline ?
            }

          } else {

            String[] twoStrings = this.parseHeader(line);
            if (twoStrings != null) {
              getHeaders().put(twoStrings[0], twoStrings[1]);
              lastHeaderName = twoStrings[0];
            } else {
              this.processError();
              return; // yuck! header malformed!
            }
          }
        } else {
          this.headersReady = true;
        }
      }
    }
  }

  String defaultHeaders = 
          "HTTP/1.1 200 OK\r\nCache-control: no-cache\r\n\r\n";
  int i = 0;
  byte[] str = null;
  boolean write(SelectionKey key, ByteBuffer buffer) 
          throws UnsupportedEncodingException, IOException {
    
    SocketChannel channel = (SocketChannel) key.channel();
    buffer.clear();
    
    if (str == null) {
      str = (defaultHeaders + bodyBuffer.toString()).getBytes();
    }
    
    while(buffer.hasRemaining() && i < str.length) {
      buffer.put(str[i]);
      i++;
    }
    
    buffer.flip();
    channel.write(buffer);
    
    if (i == str.length) {
      return true;
    }
    
    return false;
  }
}
