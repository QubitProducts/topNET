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
  
  private Handler[] handlers = new Handler[1024];
  
  private Handler defaultGlobalErrorHandler;
  
  private static ErrorHandlingConfig errorHandlingConfig;

  static {
    errorHandlingConfig = new ErrorHandlingConfig();
  }
  
  /**
   * @return the errorHandlingConfig
   */
  public static ErrorHandlingConfig getErrorHandlingConfig() {
    return errorHandlingConfig;
  }

  /**
   * @param aErrorHandlingConfig the errorHandlingConfig to set
   */
  public static void setErrorHandlingConfig(ErrorHandlingConfig aErrorHandlingConfig) {
    errorHandlingConfig = aErrorHandlingConfig;
  }
  
  public void setDefaultErrorHandler(int code, Handler handler) {
    getHandlers()[code] = handler;
  }
  
  public Handler getDefaultErrorHandler(int code) {
    if (defaultGlobalErrorHandler != null) {
      return defaultGlobalErrorHandler.getInstance();
    }
    
    Handler handler = getHandlers()[code];
    
    if (handler == null) {
      handler = new ErrorHandler(code);
      getHandlers()[code] = handler;
    }
    
    return handler.getInstance();
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
