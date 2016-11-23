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

import com.qubit.qurricane.errors.ErrorTypes;
import com.qubit.qurricane.utils.Pair;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class Handler {

  
  public static final char[] HTTP_0_9 = "HTTP/0.9".toCharArray();
  public static final char[] HTTP_1_0 = "HTTP/1.0".toCharArray();
  public static final char[] HTTP_1_1 = "HTTP/1.1".toCharArray();
  public static final char[] HTTP_1_x = "HTTP/1.x".toCharArray();
  
  static final Logger log = Logger.getLogger(Handler.class.getName());
  private Handler next;

  public Handler getInstance() {
    try {
      return this.getClass().newInstance();
    } catch (InstantiationException | IllegalAccessException ex) {
      log.log(Level.SEVERE, null, ex);
    }
    return null;
  }

  protected Handler() {}

  protected void triggerOnBeforeOutputStreamIsSet(Request request, Response response) {
    if (this.onBeforeOutputStreamIsSet(request, response)) {
      Handler tmp = this.getNext();
      while(tmp != null) {
        if (!tmp.onBeforeOutputStreamIsSet(request, response)) {
          break;
        } else {
          tmp = tmp.getNext();
        }
      }
    }
  }

  public boolean onBeforeOutputStreamIsSet(
                                    Request request,
                                    Response response) {
    return true;
  }
  //@todo zmien nazwy na onBodyReady, onHeadersReady, onBeforeReadingBody
  protected Pair<Handler, Throwable> 
        doProcess(Request request, Response response, DataHandler dh) {
    Handler tmp = this;
    
    while(tmp != null) {
      try {
        if (!tmp.process(request, response, dh)) {
          break;
        } else {
          tmp = tmp.getNext();
        }
      } catch (Throwable t) {
        return new Pair<>(tmp, t);
      }
    }
    
    return null;
  }
  
  // request is ready, with full body, unless different stream been passed
  public boolean process(Request request, Response response, DataHandler dh) 
                 throws Exception {
    return this.process(request, response);
  }
         
  // request is ready, with full body, unless different stream been passed
  public abstract boolean
         process(Request request, Response response) throws Exception;

  public boolean supports(String method) {
    return true;
  }

  public boolean matches(String fullPath, String path, String params) {
    return true;
  }

  public InputStream getErrorInputStream(ErrorTypes errorOccured) {
    return null;
  }

  protected Handler getErrorHandler() {
    return null;
  }

  /**
   * Returns -2 by default - which means that this handler lets server default
   * value to be used. To set no size limit - set -1. Any 0+ value will cause
   * incoming data size limit to be applied.
   *
   * @return the maxIncomingDataSize
   */
  public int getMaxIncomingDataSize() {
    return -2;
  }

  /**
   * Max idle defines maximum miliseconds amount for peer to not to return any
   * reads.
   *
   * Return -1 to let the server to decide on max idle times.
   *
   * Default value is -1
   *
   * @return the maxIdle
   */
  public int getMaxIdle() {
    return -1;
  }
  
  public void onError(Throwable t) {}

  /**
   * @return the next
   */
  public Handler getNext() {
    return next;
  }

  /**
   * @param next the next to set
   */
  public void setNext(Handler next) {
    this.next = next;
  }
  
  public final void closeConnectionImmediately (Request request) {
    Server.close(request.getChannel());
  }

  protected void connectionClosedHandler(DataHandler dataHandler) {
    
  }

  /**
   * Triggered when bytes are read from channel.
   */
  public void onBytesRead() {
    
  }
}
