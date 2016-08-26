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
  
  public static void setDefaultErrorHandler(int code, Handler handler) {
    handlers[code] = handler;
  }
  
  public static Handler getDefaultErrorHandler(int code) {
    Handler handler = handlers[code];
    
    if (handler == null) {
      handler = new DefaultErrorHandler(code);
      handlers[code] = handler;
    }
    
    return handler;
  }
}
