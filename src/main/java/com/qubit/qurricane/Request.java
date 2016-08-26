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
public class Request {
  private final Map<String, String> headers;
  private OutputStream outputStream;
  private final SelectionKey key;
  private String bodyStringCache;
  
  private String path;
  private String method;
  private String pathParameters;
  private String fullPath;
  private Throwable passedException;
  private Throwable associatedException;
  
  protected Request (SelectionKey key, Map<String, String> headers) {
    this.headers = headers;
    this.key = key;
  }
  
  byte[] byteArray = null;
  
  public byte[] getBodyBytes() throws OutputStreamAlreadySetException {
    if (!(this.outputStream instanceof ByteArrayOutputStream)) {
      throw new OutputStreamAlreadySetException();
    }
    
    if (byteArray == null) {
      byteArray = ((ByteArrayOutputStream)outputStream).toByteArray();
    }
    
    return byteArray;
  }
  
  public String getBodyString() throws OutputStreamAlreadySetException {
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
  
  public String getHeader(String name) {
    return headers.get(name);
  }

  public Set<String> getHeadersKeySet() {
    return headers.keySet();
  }
  
  /**
   * @return the outputStream
   */
  protected OutputStream getOutputStream() {
    return outputStream;
  }

  /**
   * @param streamSet the streamSet to set
   */
  protected void makeSureOutputStreamIsReady() {
    if (this.outputStream == null) {
      this.outputStream = new ByteArrayOutputStream();
    }
  }

  /**
   * @param outputStream the outputStream to set
   */
  public void setOutputStream(OutputStream outputStream) 
          throws OutputStreamAlreadySetException {
    if (outputStream != null) {
      // ignore nonsense, or throw...
      throw new OutputStreamAlreadySetException();
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

  /**
   * @return the pathParameters
   */
  public String getPathParameters() {
    return pathParameters;
  }

  /**
   * @return the fullPath
   */
  public String getFullPath() {
    return fullPath;
  }

  /**
   * @param pathParameters the pathParameters to set
   */
  protected void setPathParameters(String pathParameters) {
    this.pathParameters = pathParameters;
  }

  /**
   * @param fullPath the fullPath to set
   */
  protected void setFullPath(String fullPath) {
    this.fullPath = fullPath;
  }
  
  protected void setPath(String fullPath) {
    this.fullPath = fullPath;
  }

  /**
   * @return the associatedException
   */
  public Throwable getAssociatedException() {
    return associatedException;
  }

  /**
   * @param associatedException the associatedException to set
   */
  protected void setAssociatedException(Throwable associatedException) {
    this.associatedException = associatedException;
  }
  
}
