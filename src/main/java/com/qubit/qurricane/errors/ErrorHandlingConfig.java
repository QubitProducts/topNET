/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.errors;

import com.qubit.qurricane.Handler;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ErrorHandlingConfig {
  
  static Handler[] handlers = new Handler[1024];
  
  private static Handler defaultGlobalErrorHandler;
  
  public static void setDefaultErrorHandler(int code, Handler handler) {
    handlers[code] = handler;
  }
  
  public static Handler getDefaultErrorHandler(int code) {
    
    if (defaultGlobalErrorHandler != null) {
      return defaultGlobalErrorHandler.getInstance();
    }
    
    Handler handler = handlers[code];
    
    if (handler == null) {
      handler = new ErrorHandler(code);
      handlers[code] = handler;
    }
    
    return handler.getInstance();
  }

  /**
   * @return the defaultGlobalErrorHandler
   */
  public Handler getDefaultGlobalErrorHandler() {
    return ErrorHandlingConfig.defaultGlobalErrorHandler;
  }

  /**
   * @param defaultGlobalErrorHandler the defaultGlobalErrorHandler to set
   */
  public void setDefaultGlobalErrorHandler(Handler defaultGlobalErrorHandler) {
    ErrorHandlingConfig.defaultGlobalErrorHandler = defaultGlobalErrorHandler;
  }
}
