/*
 * Qurrican
 * Fast HTTP Server Solution.
 * Copyright 2016, Qubit Group
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

import com.qubit.qurricane.exceptions.OutputStreamAlreadySetException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Request {

  private final Map<String, String> headers;
  private OutputStream outputStream;
  private final SocketChannel channel;
  private String bodyStringCache;

  private String path;
  private String method;
  private String pathParameters;
  private String fullPath;
  private Throwable associatedException;
  private Object attachment;

  private Map<String, Object> attributes;
  private final long createdTime;
  private Runnable writeFinishedHandler;

  protected Request(SocketChannel channel, Map<String, String> headers) {
    this.headers = headers;
    this.channel = channel;
    this.createdTime = new Date().getTime();
  }

  byte[] byteArray = null;

  public byte[] getBodyBytes() throws OutputStreamAlreadySetException {
    if (!(this.outputStream instanceof ByteArrayOutputStream)) {
      throw new OutputStreamAlreadySetException();
    }

    if (byteArray == null) {
      byteArray = ((ByteArrayOutputStream) outputStream).toByteArray();
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
      } finally {
      }

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

  public SocketChannel getChannel() {
    return channel;
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
   * @return the attributes
   */
  public Map getAttributes() {
    if (this.attributes == null) {
      this.attributes = new HashMap<>();
    }
    return this.attributes;
  }

  public Object getAttribute(String name) {
    return this.getAttributes().get(name);
  }
  
  public void setAttribute(String name, Object obj) {
    this.getAttributes().put(name, obj);
  }

  /**
   * @return the createdTime
   */
  public long getCreatedTime() {
    return createdTime;
  }

  public void onWriteFinished(Runnable runnable) {
    writeFinishedHandler = runnable;
  }

  /**
   * @return the writeFinishedHandler
   */
  public Runnable getWriteFinishedHandler() {
    return writeFinishedHandler;
  }
}
