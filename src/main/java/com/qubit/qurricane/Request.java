/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import com.qubit.qurricane.exceptions.OutputStreamAlreadySetException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class Request {
  private final Map<String, String> headers;
  private OutputStream outputStream;
  private final SelectionKey key;
  private String bodyStringCache;
  
  private String path;
  private String method;
  
  protected Request (SelectionKey key, Map<String, String> headers, OutputStream os) {
    this.headers = headers;
    this.key = key;
    if (os == null) {
      outputStream = new ByteArrayOutputStream();
    } else {
      outputStream = os;
    }
  }
  
  byte[] byteArray = null;
  
  byte[] getBodyBytes() throws OutputStreamAlreadySetException {
    if (this.isStreamSet()) {
      throw new OutputStreamAlreadySetException();
    }
    
    if (byteArray == null) {
      byteArray = ((ByteArrayOutputStream)outputStream).toByteArray();
    }
    
    return byteArray;
  }
  
  String getBodyString() throws OutputStreamAlreadySetException {
    if (this.bodyStringCache == null) {
      Charset charset = null;
      try {
        String contentType = this.getHeader("Content-Type");
        if (contentType != null) {
        int idx = contentType.indexOf("charset=");
          String charsetString = contentType.substring(idx + 8).trim();
          charset = Charset.forName(charsetString);
        } else {
          charset = Charset.defaultCharset();
        }
      } finally {}

      this.bodyStringCache = new String(this.getBodyBytes(), charset);
    }
    
    return this.bodyStringCache;
  }
  
  String getHeader(String name) {
    return headers.get(name);
  }

  Set<String> getHeadersKeySet() {
    return headers.keySet();
  }
  
  /**
   * @return the outputStream
   */
  protected OutputStream getOutputStream() {
    return outputStream;
  }
  
  private boolean streamSet = false;

  /**
   * @return the streamSet
   */
  public boolean isStreamSet() {
    return streamSet;
  }

  /**
   * @param streamSet the streamSet to set
   */
  protected void setStreamSet(boolean streamSet) {
    this.streamSet = streamSet;
  }

  /**
   * @param outputStream the outputStream to set
   */
  public void setOutputStream(OutputStream outputStream) 
          throws OutputStreamAlreadySetException {
    if (outputStream == null) {
      // ignore nonsense, or throw...
      return;
    }
    
    if (this.isStreamSet()) {
      throw new OutputStreamAlreadySetException();
    }
    
    if (this.outputStream != outputStream) {
      this.setStreamSet(true);
    }
    
    this.outputStream = outputStream;
  }

  /**
   * @return the key
   */
  public SelectionKey getKey() {
    return key;
  }
  
  public SocketChannel getSocketChannel() {
    return (SocketChannel) getKey().channel();
  }

  /**
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * @param path the path to set
   */
  protected void setPath(String path) {
    this.path = path;
  }

  /**
   * @return the method
   */
  public String getMethod() {
    return method;
  }

  /**
   * @param method the method to set
   */
  protected void setMethod(String method) {
    this.method = method;
  }
  
}
