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

package com.qubit.topnet.errors;

import com.qubit.topnet.Handler;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ErrorHandlingConfig {

  private Handler[] handlers = new Handler[1024];

  private Handler defaultGlobalErrorHandler;

  public void setDefaultErrorHandler(int code, Handler handler) {
    getHandlers()[code] = handler;
  }

  public Handler getDefaultErrorHandler(int code, ErrorTypes type) {
    if (defaultGlobalErrorHandler != null) {
      return defaultGlobalErrorHandler.getInstance();
    }

    Handler handler = getHandlers()[code];

    if (handler == null) { // initialize
      handler = ErrorHandler.getHandlerForCode(code);
      getHandlers()[code] = handler;
    }

    Handler tmp = handler.getInstance();
    
    if (type != null && tmp instanceof ErrorHandler) {
      ((ErrorHandler)tmp).setType(type);
    }
    return tmp;
  }
  
  /**
   * @return the defaultGlobalErrorHandler
   */
  public Handler getDefaultGlobalErrorHandler() {
    return this.defaultGlobalErrorHandler;
  }

  /**
   * @param defaultGlobalErrorHandler the defaultGlobalErrorHandler to set
   */
  public void setDefaultGlobalErrorHandler(Handler defaultGlobalErrorHandler) {
    this.defaultGlobalErrorHandler = defaultGlobalErrorHandler;
  }

  /**
   * @return the handlers
   */
  public Handler[] getHandlers() {
    return handlers;
  }

  /**
   * @param handlers the handlers to set
   */
  public void setHandlers(Handler[] handlers) {
    this.handlers = handlers;
  }
}
