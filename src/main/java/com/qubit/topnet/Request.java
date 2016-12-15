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

package com.qubit.topnet;

import com.qubit.topnet.exceptions.OutputStreamAlreadySetException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Request {

  private List<String[]> headers;
  private SocketChannel channel;
  private String bodyStringCache;

  private final BytesStream bytesStream = new BytesStream();
  private String path;
  private String method;
  private String pathParameters;
  private String fullPath;
  private Throwable associatedException;
  private Object attachment;

  private Map<String, Object> attributes;
  private long createdTime;
  private Runnable writeFinishedHandler;
  
  public void init(SocketChannel channel, List<String[]> headers) {
    this.headers = headers;
    this.channel = channel;
    this.createdTime = new Date().getTime();
  }

  public String getBodyString() throws OutputStreamAlreadySetException {
    if (this.bodyStringCache == null) {
        
      if (!(this.bytesStream instanceof BytesStream)) {
        throw new OutputStreamAlreadySetException();
      }
      
      Charset charset;
      String contentType = this.getHeader("Content-Type");
      
      if (contentType != null) {
        int idx = contentType.indexOf("charset=");
        String charsetString = contentType.substring(idx + 8).trim();
        charset = Charset.forName(charsetString);
      } else {
        charset = Charset.defaultCharset();
      }
      
      this.bodyStringCache = bytesStream
          .readAvailableToReadAsString(charset).toString();
    }

    return this.bodyStringCache;
  }

  public String getHeader(String name) {
    for (String[] header : this.headers) {
      if (header[0].equals(name)) return header[1];
    }
    return null;
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
  public void setMethod(String method) {
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
  public void setPathParameters(String pathParameters) {
    this.pathParameters = pathParameters;
  }

  /**
   * @param fullPath the fullPath to set
   */
  public void setFullPath(String fullPath) {
    this.fullPath = fullPath;
  }

  public void setPath(String fullPath) {
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
  public void setAssociatedException(Throwable associatedException) {
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

  /**
   * @return the bytesStream
   */
  public BytesStream getBytesStream() {
    return bytesStream;
  }
  
  protected void reset() {
    if (this.bytesStream != null) {
      this.bytesStream.shrinkLessMore();
      this.bytesStream.reset();
    }
    
    headers.clear();
    channel = null;
    bodyStringCache = null;

    path = null;
    method = null;
    pathParameters = null;
    fullPath = null;
    associatedException = null;
    attachment = null;

    if (attributes != null) {
      attributes.clear();
    }
    createdTime = 0;
    writeFinishedHandler = null;
  }
  
  /**
   * @return the headers
   */
  public List<String[]> getHeaders() {
    return headers;
  }
}
