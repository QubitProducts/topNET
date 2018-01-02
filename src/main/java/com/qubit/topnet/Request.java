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

import static com.qubit.topnet.ServerBase.HTTP_0_9;
import static com.qubit.topnet.ServerBase.HTTP_1_0;
import static com.qubit.topnet.ServerBase.HTTP_1_1;
import static com.qubit.topnet.ServerBase.getCharsetForName;
import com.qubit.topnet.events.BeforeOutputStreamSetEvent;
import com.qubit.topnet.events.BytesReadEvent;
import com.qubit.topnet.events.ConnectionClosedEvent;
import com.qubit.topnet.events.RequestFinishedEvent;
import com.qubit.topnet.exceptions.OutputStreamAlreadySetException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class Request {

  private DataHandler dataHandler;

  /**
   * @return the requestFinishedEvent
   */
  public RequestFinishedEvent getRequestFinishedEvent() {
    return requestFinishedEvent;
  }

  /**
   * @param requestFinishedEvent the requestFinishedEvent to set
   */
  public void setRequestFinishedEvent(RequestFinishedEvent requestFinishedEvent) {
    this.requestFinishedEvent = requestFinishedEvent;
  }

  private static final Logger log = Logger.getLogger(Request.class.getName());
  
  public static boolean cacheParameters = false;
  
  public static final byte[] BHTTP_0_9 = 
      new byte[]{'H', 'T', 'T', 'P', '/', '0', '.', '9'};
  public static final byte[] BHTTP_1_0 = 
      new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '0'};
  public static final byte[] BHTTP_1_1 = 
      new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '1'};
  public static final byte[] BHTTP_1_x = 
      new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', 'x'};
  
  private List<String[]> headers = new ArrayList<>();
  private String bodyStringCache;
  private final BytesStream bytesStream = new BytesStream();
  private String path;
  private String method;
  private String fullPath;
  private String queryString;
  private Throwable associatedException;
  private Object attachment;
  private Map<String, Object> attributes;
  private long createdTime;
  private Runnable writeFinishedHandler;
  // expected values, nulls are meaningful here:
  private Map<String, String> parametersMap = null;
  private List<String[]> parametersList = null;
  private Map<String, List<String>> parametersMappedList = null;
  private Map<String, String> urlParametersMap = null;
  private List<String[]> urlParametersList = null;
  private Map<String, List<String>> urlParametersMappedList = null;
  private Map<String, String> bodyParametersMap = null;
  private List<String[]> bodyParametersList = null;
  private Map<String, List<String>> bodyParametersMappedList = null;
  
  private int requestedHttpProtocol = getDefaultProtocol();
    
  private ServerBase server;
  private String encodingName = "UTF-8";
  private ConnectionClosedEvent connectionClosedEvent;
  private BytesReadEvent bytesReadEvent;
  private BeforeOutputStreamSetEvent beforeOutputStreamSetEvent;
  private RequestFinishedEvent requestFinishedEvent;
    
  public Request() {
  }
  
  public void init(ServerBase server, DataHandler dh) {
    this.server = server;
    this.encodingName = server.getUrlCharset().name();
    this.createdTime = new Date().getTime();
    this.bytesStream.setMaxBufferSize(this.server.getMaxMessageSize());
    this.dataHandler = dh;
  }

  /**
   * @return the bytesStream
   */
  public BytesStream getBytesStream() {
    return bytesStream;
  }
  
  protected void reset() {
    this.requestedHttpProtocol = getDefaultProtocol();
    
    if (this.bytesStream != null) {
      this.bytesStream.shrinkLessMore();
      this.bytesStream.reset();
    }
    
    this.headers.clear();
    this.bodyStringCache = null;
    this.dataHandler = null;
    this.path = null;
    this.method = null;
    this.fullPath = null;
    this.associatedException = null;
    this.attachment = null;
    this.path = null;
    this.queryString = null;
    

    // reset params buf
    if (cacheParameters) {
      this.parametersMap = null;
      this.parametersList = null;
      this.parametersMappedList = null;
      this.urlParametersMap = null;
      this.urlParametersList = null;
      this.urlParametersMappedList = null;
      this.bodyParametersMap = null;
      this.bodyParametersList = null;
      this.bodyParametersMappedList = null;
    }
    
    if (attributes != null) {
      this.attributes.clear();
    }
    
    this.createdTime = 0;
    this.writeFinishedHandler = null;
  }
  
  /**
   * @return the headers list
   */
  public List<String[]> getHeaders() {
    return headers;
  }
  
  public String getBodyString() throws OutputStreamAlreadySetException {
    return getBodyString(null);
  }
  
  public String getBodyString(Charset charset) throws OutputStreamAlreadySetException {
    if (this.bodyStringCache == null) {
        
      if (!(this.bytesStream instanceof BytesStream)) {
        throw new OutputStreamAlreadySetException();
      }
      
      if (charset == null) {
        String contentType = this.getHeader("Content-Type");

        if (contentType != null) {
          int idx = contentType.indexOf("charset=");
          if (idx != -1) {
            String charsetString = contentType.substring(idx + 8);
            int colonIdx = charsetString.indexOf(';');
            if (colonIdx != -1) {
              charsetString = charsetString.substring(0, colonIdx).trim();
            }
            charset = getCharsetForName(charsetString);
          } else {
            charset = Charset.defaultCharset();
          }
        } else {
          charset = Charset.defaultCharset();
        }
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

  public String getLowerCaseHeader(String name) {
    for (String[] header : this.headers) {
      if (header[0].toLowerCase().equals(name)) return header[1];
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

  /**
   * Path excluding query string.
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
   * Path including query string.
   * @return the fullPath
   */
  public String getFullPath() {
    return fullPath;
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
  
  /*
  
    URL params section
  
  */
  public Map<String, String> getUrlParameters() {
    if (cacheParameters && this.urlParametersMap != null) {
      return this.urlParametersMap;
    }
    
    MappedValues vals = new MappedValues();
    parseParameters(vals, this.getQueryString(), this.encodingName);
    
    if (cacheParameters) {
      return this.urlParametersMap = vals.getValues();
    } else {
      return vals.getValues();
    }
  }
  
  public List<String[]> getUrlParametersList() {
    if (cacheParameters && this.urlParametersList != null) {
      return this.urlParametersList;
    }
    
    ValuesList vals = new ValuesList();
    parseParameters(vals, this.getQueryString(), this.encodingName);
    
    if (cacheParameters) {
      return this.urlParametersList = vals.getValues();
    } else {
      return vals.getValues();
    }
  }
  
  public Map<String, List<String>> getUrlParametersMappedLists() {
    if (cacheParameters && this.urlParametersMappedList != null) {
      return this.urlParametersMappedList;
    }
    
    MappedValuesLists vals = new MappedValuesLists();
    parseParameters(vals, this.getQueryString(), this.encodingName);
    
    if (cacheParameters) {
      return this.urlParametersMappedList = vals.getValues();
    } else {
      return vals.getValues();
    }
  }
  
  /*
  
    Body params section
  
  */
  
  public Map<String, String> getBodyParameters() {
    try {
      if (cacheParameters && this.bodyParametersMap != null) {
        return this.bodyParametersMap;
      }
      
      MappedValues vals = new MappedValues();
      parseParameters(vals, this.getBodyString(), this.encodingName);
      
      if (cacheParameters) {
        return this.bodyParametersMap = vals.getValues();
      } else {
        return vals.getValues();
      }
      
    } catch (OutputStreamAlreadySetException ex) {
      if (cacheParameters) {
        this.bodyParametersMap = new HashMap<>();
        return this.bodyParametersMap;
      } else {
        return new HashMap<>();
      }
    }
  }
  
  public List<String[]> getBodyParametersList() {
    try {
      if (cacheParameters && this.bodyParametersList != null) {
        return this.bodyParametersList;
      }
      
      ValuesList vals = new ValuesList();
      parseParameters(vals, this.getBodyString(), this.encodingName);
      
      if (cacheParameters) {
        return this.bodyParametersList = vals.getValues();
      } else {
        return vals.getValues();
      }
      
    } catch (OutputStreamAlreadySetException ex) {
      if (cacheParameters) {
        this.bodyParametersList = new ArrayList<>();
        return this.bodyParametersList;
      } else {
        return new ArrayList<>();
      }
    }
  }
  
  public Map<String, List<String>> getBodyParametersMappedLists() {
    try {
      if (cacheParameters && this.bodyParametersMappedList != null) {
        return this.bodyParametersMappedList;
      }
      
      MappedValuesLists vals = new MappedValuesLists();
      parseParameters(vals, this.getBodyString(), this.encodingName);
      
      if (cacheParameters) {
        return this.bodyParametersMappedList = vals.getValues();
      } else {
        return vals.getValues();
      }
      
    } catch (OutputStreamAlreadySetException ex) {
      if (cacheParameters) {
        this.bodyParametersMappedList = new HashMap<>();
        return this.bodyParametersMappedList;
      } else {
        return new HashMap<>();
      }
    }
  }
  
  /*
  
    Merged params section
  
  */
  
  public Map<String, String> getParameters() {
    if (cacheParameters && this.parametersMap != null) {
      return this.parametersMap;
    }
    
    Map<String, String> urlType = this.getUrlParameters();
    Map<String, String> bodyType = this.getBodyParameters();
    
    for (Map.Entry<String, String> entrySet : bodyType.entrySet()) {
      urlType.put(entrySet.getKey(), entrySet.getValue());
    }
    
    if (cacheParameters) {
      this.parametersMap = urlType;
    }
    
    return urlType;
  }
  
  public List<String[]> getParametersList() {
    if (cacheParameters && this.parametersList != null) {
      return this.parametersList;
    }
    
    List<String[]> urlType = this.getUrlParametersList();
    List<String[]> bodyType = this.getBodyParametersList();
    
    for (String[] entry : bodyType) {
      urlType.add(entry);
    }
    
    if (cacheParameters) {
      this.parametersList = urlType;
    }
    
    return urlType;
  }
  
  public Map<String, List<String>> getParametersMappedLists() {
    if (cacheParameters && this.parametersMappedList != null) {
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
    
    if (cacheParameters) {
      this.parametersMappedList = urlType;
    }
    
    return urlType;
  }
  
  public static void parseParameters(Values results, String params, String encoding) {
    
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
            results.add(
                decodeURI(name.toString(), encoding),
                decodeURI(value.toString(), encoding));
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
        results.add(
            decodeURI(name.toString(), encoding),
            decodeURI(value.toString(), encoding));
        name.setLength(0);
        value.setLength(0);
      }
    }
  }
  
  public static String decodeURI (String input, String encoding) {
    try {
      return URLDecoder.decode(input, encoding);
    } catch (UnsupportedEncodingException ex) {
      log.log(Level.WARNING,
          "Unsupported charset set for encoding: {0} - falling to UTF.",
          encoding);
      try {
        return URLDecoder.decode(input, "UTF-8");
      } catch (UnsupportedEncodingException ex1) {
        log.log(Level.SEVERE, "Failed to decode using UTF - check platform!", ex1);
        return input;
      }
    }
  }

  /**
   * @return the server
   */
  public ServerBase getServer() {
    return server;
  }

  

  /**
   * @return the queryString
   */
  public String getQueryString() {
    return queryString;
  }

  /**
   * @return the requestedHttpProtocol
   */
  public int getRequestedHttpProtocol() {
    return requestedHttpProtocol;
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
  
  protected String analyzePathAndSplit() {
    if (this.path == null && this.fullPath != null) {
      int idx = this.fullPath.indexOf("?");
      if (idx == -1) {
        this.path = this.fullPath;
        this.queryString = "";
      } else {
        this.path = this.fullPath.substring(0, idx);
        this.queryString = this.fullPath.substring(idx + 1);
      }
    }
    return this.path;
  }
  
  /**
   * 
   * @param line bytes array
   * @param len the 'strlen' value
   * @return true if no headers should be processed
   */
  protected boolean setMethodAndPrototcolAndPathFromLine(byte[] line, int len) {
    int method_closing_idx = DataHandler.indexOf(line, len, ' ', 0);
    
    if (method_closing_idx < 1) {
      return true; // no headers
    }
    
    this.method = new String(line, 0, method_closing_idx);
    int path_idx = method_closing_idx + 1;
    int protocol_idx = DataHandler.indexOf(line, len, ' ', path_idx);
    
    if (protocol_idx != -1) {
      this.fullPath = new String(
          line,
          path_idx,
          protocol_idx - path_idx,
          this.server.getUrlCharset());
      
      byte[] requestHttpProtocolBytes = 
          Arrays.copyOfRange(line, protocol_idx + 1, len);
      // we reply with 1.0 or 1.1, whatever client tries to choose
      if (Arrays.equals(requestHttpProtocolBytes, BHTTP_1_1)) {
        // HTTP_1_0 is default with value 0
        this.requestedHttpProtocol = HTTP_1_1;
      }
      
      return false;
    } else {
      // HTTP 0.9 support.
      this.requestedHttpProtocol = HTTP_0_9;
      this.fullPath = new String(
          line,
          path_idx,
          len - method_closing_idx - 1,
          this.server.getUrlCharset());
      
      return true; // no headers
    }
  }
  
  public static int getDefaultProtocol() {
    return HTTP_1_0;
  }
  
    /**
   * @return the beforeOutputStreamSetEvent
   */
  public BeforeOutputStreamSetEvent getBeforeOutputStreamSetEvent() {
    return beforeOutputStreamSetEvent;
  }

  /**
   * @param beforeOutputStreamSetEvent the beforeOutputStreamSetEvent to set
   */
  public void setBeforeOutputStreamSetEvent(BeforeOutputStreamSetEvent beforeOutputStreamSetEvent) {
    this.beforeOutputStreamSetEvent = beforeOutputStreamSetEvent;
  }

  /**
   * @return the bytesReadEvent
   */
  public BytesReadEvent getBytesReadEvent() {
    return bytesReadEvent;
  }

  /**
   * @param bytesReadEvent the bytesReadEvent to set
   */
  public void setBytesReadEvent(BytesReadEvent bytesReadEvent) {
    this.bytesReadEvent = bytesReadEvent;
  }

  /**
   * @return the connectionClosedEvent
   */
  public ConnectionClosedEvent getConnectionClosedEvent() {
    return connectionClosedEvent;
  }

  /**
   * @param connectionClosedEvent the connectionClosedEvent to set
   */
  public void setConnectionClosedEvent(ConnectionClosedEvent connectionClosedEvent) {
    this.connectionClosedEvent = connectionClosedEvent;
  }

  /**
   * @return the dataHandler
   */
  public DataHandler getDataHandler() {
    return dataHandler;
  }
}
