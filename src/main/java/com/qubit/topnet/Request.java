/*
 * topNET
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

  private List<String[]> headers = new ArrayList<>();
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
  private Map<String, String> parametersMap = null;
  private List<String[]> parametersList = null;
  private Map<String, List<String>> parametersMappedList = null;
  private Map<String, String> urlParametersMap = null;
  private List<String[]> urlParametersList = null;
  private Map<String, List<String>> urlParametersMappedList = null;
  private Map<String, String> bodyParametersMap = null;
  private List<String[]> bodyParametersList = null;
  private Map<String, List<String>> bodyParametersMappedList = null;
  
  
  public Request() {}
  
  public void init(SocketChannel channel) {
    this.channel = channel;
    this.createdTime = new Date().getTime();
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
   * @return the headers list
   */
  public List<String[]> getHeaders() {
    return headers;
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
          .readAvailableBytesAsString(charset).toString();
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
  protected void setPathParameters(String pathParameters) {
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
  
  /*
  
    URL params section
  
  */
  public Map<String, String> getUrlParameters() {
    if (this.urlParametersMap != null) {
      return this.urlParametersMap;
    }
    
    MappedValues vals = new MappedValues();
    parseParameters(vals, this.getPathParameters());
    
    return this.urlParametersMap = vals.getValues();
  }
  
  public List<String[]> getUrlParametersList() {
    if (this.urlParametersList != null) {
      return this.urlParametersList;
    }
    
    ValuesList vals = new ValuesList();
    parseParameters(vals, this.getPathParameters());
    
    return this.urlParametersList = vals.getValues();
  }
  
  public Map<String, List<String>> getUrlParametersMappedLists() {
    if (this.urlParametersMappedList != null) {
      return this.urlParametersMappedList;
    }
    
    MappedValuesLists vals = new MappedValuesLists();
    parseParameters(vals, this.getPathParameters());
    
    return this.urlParametersMappedList = vals.getValues();
  }
  
  /*
  
    Body params section
  
  */
  
  public Map<String, String> getBodyParameters() {
    try {
      if (this.bodyParametersMap != null) {
        return this.bodyParametersMap;
      }
      
      MappedValues vals = new MappedValues();
      parseParameters(vals, this.getBodyString());
      
      return this.bodyParametersMap = vals.getValues();
    } catch (OutputStreamAlreadySetException ex) {
      this.bodyParametersMap = new HashMap<>();
      return this.bodyParametersMap;
    }
  }
  
  public List<String[]> getBodyParametersList() {
    try {
      if (this.bodyParametersList != null) {
        return this.bodyParametersList;
      }
      
      ValuesList vals = new ValuesList();
      parseParameters(vals, this.getBodyString());
      
      return this.bodyParametersList = vals.getValues();
    } catch (OutputStreamAlreadySetException ex) {
      this.bodyParametersList = new ArrayList<>();
      return this.bodyParametersList;
    }
  }
  
  public Map<String, List<String>> getBodyParametersMappedLists() {
    try {
      if (this.bodyParametersMappedList != null) {
        return this.bodyParametersMappedList;
      }
      
      MappedValuesLists vals = new MappedValuesLists();
      parseParameters(vals, this.getBodyString());
      
      return this.bodyParametersMappedList = vals.getValues();
    } catch (OutputStreamAlreadySetException ex) {
      this.bodyParametersMappedList = new HashMap<>();
      return this.bodyParametersMappedList;
    }
  }
  
  /*
  
    Merged params section
  
  */
  
  public Map<String, String> getParameters() {
    if (this.parametersMap != null) {
      return this.parametersMap;
    }
    
    Map<String, String> urlType = this.getUrlParameters();
    Map<String, String> bodyType = this.getBodyParameters();
    
    for (Map.Entry<String, String> entrySet : bodyType.entrySet()) {
      urlType.put(entrySet.getKey(), entrySet.getValue());
    }
    
    return this.parametersMap = urlType;
  }
  
  public List<String[]> getParametersList() {
    if (this.parametersList != null) {
      return this.parametersList;
    }
    
    List<String[]> urlType = this.getUrlParametersList();
    List<String[]> bodyType = this.getBodyParametersList();
    
    for (String[] entry : bodyType) {
      urlType.add(entry);
    }
    
    return this.parametersList = urlType;
  }
  
  public Map<String, List<String>> getParametersMappedLists() {
    if (this.parametersMappedList != null) {
      return this.parametersMappedList;
    }
    
    Map<String, List<String>> urlType = this.getUrlParametersMappedLists();
    Map<String, List<String>> bodyType = this.getBodyParametersMappedLists();
    
    for (Map.Entry<String, List<String>> entrySet : bodyType.entrySet()) {
      String key = entrySet.getKey();
      List<String> val = entrySet.getValue();
      
      List<String> fromUrl = urlType.get(key);
      if (fromUrl == null) {
        urlType.put(key, val);
      } else {
        fromUrl.addAll(val);
      }
    }
    
    return this.parametersMappedList = urlType;
  }
  
  public static void parseParameters(Values results, String params) {
    
    if (params == null) return;
    
    int len = params.length();
    
    if (len == 0) return;
    
    StringBuilder name = new StringBuilder();
    StringBuilder value = new StringBuilder();
    
    boolean nameNow = true;
    
    for (int i = 0; i < len; i++) {
      char ch = params.charAt(i);
      
      if (ch == '&') {
        
        { // clearing block
          if (name.length() > 0 || value.length() > 0) {
            results.add(name.toString(), value.toString());
            name.setLength(0);
            value.setLength(0);
          }
        }
        
        nameNow = true;
        continue;
      }
      
      if (nameNow && ch == '=') {
        nameNow = false;
      } else {
        if (nameNow) {
          name.append(ch);
        } else {
          value.append(ch);
        }
      }
    }
    
    { // clearing block
      if (name.length() > 0 || value.length() > 0) {
        results.add(name.toString(), value.toString());
        name.setLength(0);
        value.setLength(0);
      }
    }
    
  }

  public static interface Values {
    public void add(String name, String value);
  }

  public static class MappedValues implements Values {
    Map<String, String> map = new HashMap<>();
    
    @Override
    public void add(String name, String value) {
      map.put(name, value);
    }
    
    public Map<String, String> getValues() {
      return map;
    }
  }
  
  public static class MappedValuesLists implements Values {
    Map<String, List<String>> map = new HashMap<>();
    
    @Override
    public void add(String name, String value) {
      List<String> values = map.get(name);
      if (values != null) {
        values.add(value);
      } else {
        values = new ArrayList<>();
        values.add(value);
        map.put(name, values);
      }
    }
    
    public Map<String, List<String>> getValues() {
      return map;
    }
  }
  
  public static class ValuesList implements Values {
    List<String[]> list = new ArrayList<>();
    
    @Override
    public void add(String name, String value) {
      list.add(new String[]{name, value});
    }
    
    public List<String[]> getValues() {
      return list;
    }
  }
}
