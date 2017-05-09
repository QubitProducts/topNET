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

import com.qubit.topnet.errors.ErrorTypes;
import com.qubit.topnet.utils.Pair;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class Handler {
  
  static final Logger log = Logger.getLogger(Handler.class.getName());
  
  private Handler next;

  // consider per thread instance handlers sets
  /**
   * Function defining how handler instance is created by topNET engine for 
   * each request processing chain.
   * By default this function will create new instance of handler class.
   * 
   * It is important to understand that handlers processing chain is build of 
   * objects returned by this function.
   * 
   * This function is shared among all worker threads in topNET.
   * 
   * @return instance of handler to be used in request processing.
   */
  public Handler getInstance() {
    try {
      return this.getClass().newInstance();
    } catch (InstantiationException | IllegalAccessException ex) {
      log.log(Level.SEVERE, "Bad handlers implementation!", ex);
    }
    return null;
  }

  protected Handler() {}

  /**
   * Function used by topNET engine to prepare output stream.
   * This function is reponsible for triggering {@link onBeforeOutputStreamIsSet(Request request, Response response)} event.
   * 
   * @param request
   * @param response 
   */
  protected final void triggerOnBeforeOutputStreamIsSet(
                                                Request request,
                                                Response response) {
    if (this.init(request, response)) {
      
      Handler tmp = this.getNext();
      
      if (tmp != null) {
        tmp.init(request, response);
      }
    }
  }

  /**
   * Important pre output stream setting event handling function.
   * This function is a last moment before default output stream for request
   * data is set. If output stream for request will be set - the default
   * string buffer will not be used.
   * 
   * Please not that once you have set your own stream reading code - 
   * it is recommended clear stream buffer after reading large portions of data
   * (normally grown buffers are truncated by topNET engine after response is 
   * sent).
   * 
   * Headers are ready at init time, body is not and output stream is not 
   * set - stream can be set now or by using BeforeOutputStreamSetEvent 
   * (same time).
   * 
   * This event is triggered on all handlers in the chain till one of handlers
   * return false.
   * 
   * @param request the request
   * @param response the response
   * @return true if next handler in chain can change the stream.
   */
  public boolean init(Request request, Response response) {
    return true;
  }
  
  //@todo zmien nazwy na onBodyReady, onHeadersReady, onBeforeReadingBody
  /**
   * Default handling caller for this handler, it loops through handling
   * chain and calling {@link process(Request request, Response response, DataHandler dh)}
   * on each.
   * @param request the request object
   * @param response the reponse object
   * @param dh data handler object instance used by working thread to handle request and response.
   * @return Handling result pair of last handler used and error reference if any.
   */
  public final Pair<Handler, Throwable> 
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
        // pass errors out for handling.
        return new Pair<>(tmp, t);
      }
    }
    // no errors
    return null;
  }
  
  // request is ready, with full body, unless different stream been passed
  public boolean process(Request request, Response response, DataHandler dh) 
                 throws Exception {
    return this.process(request, response);
  }
         
  /**
   * Core processing function for requests.
   * 
   * 
   * @param request
   * @param response
   * @return true only if handling should be passed to next handler in chain. 
   * Next handler in chain is referenced bu {@link getNext()}
   * @throws Exception 
   */
  public abstract boolean process(Request request, Response response) 
      throws Exception;

  /**
   * Function used by topNET to check if handler should be included in 
   * processing chain for given request. This function is used only if handler 
   * instance is registered with {@link ServerBase#registerMatchingHandler(Handler handler)}.
   * 
   * @param fullPath
   * @param path
   * @param params
   * @return 
   */
  public boolean matches(String fullPath, String path, String params) {
    return true;
  }

  public InputStream getErrorInputStream(ErrorTypes errorOccured) {
    return null;
  }

  public Handler getErrorHandler() {
    return null;
  }

  /**
   * Returns -2 by default - which means that this handler lets server default
   * value to be used. To set no size limit - set -1. Any 0+ value will cause
   * incoming data size limit to be applied.
   * 
   * See {@link ServerBase#setMaxMessageSize(int) }
   * @return the maxIncomingDataSize
   */
  public long getMaxIncomingDataSize() {
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
   * Returns next handler to be used in handling chain or null if none is to be used.
   * @return the next
   */
  public Handler getNext() {
    return next;
  }

  /**
   * Important handling chain setter. Use this function to specify which handler 
   * should start handling request next. Handler specified by {@link getNext()}
   * will be used only if {@link process(Request request, Response response) }
   * return true.
   * 
   * When you set next handler to be invoked right after current handler - 
   * remember to not to use shared instance between threads unless its desired.
   * topNET is strongly multi-threaded, if you use handler directly referenced from 
   * {@link ServerBase#matchingPathHandlers} or {@link ServerBase#plainPathHandlers}
   * it's instance may be used by many threads at the same time - if they also 
   * use setNext() function processing chains may be unpredictable.
   * 
   * If you take handler from shared location, call {@link Handler#getInstance()}
   * to get handler reference.
   * 
   * 
   * @param next the next to set
   */
  public void setNext(Handler next) {
    this.next = next;
  }

}
