/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.errors;

import com.qubit.qurricane.DataHandler;
import com.qubit.qurricane.Handler;
import com.qubit.qurricane.Request;
import com.qubit.qurricane.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ErrorHandler extends Handler {
  
  static public final Handler[] errorHandlers = new Handler[1024];
  
  private int code;
  public ErrorHandler(int httpCode) {
    this.code = httpCode;
  }

  @Override
  public void process(Request request, Response response) throws Exception {
    response.setHttpCode(getCode());
    
    if (request.getAssociatedException() != null) {
      Logger.getLogger(ErrorHandler.class.getName())
                  .log(Level.SEVERE, null, request.getAssociatedException());
    }
    if (request.getAssociatedException() == null) {
      response.print("Qurricane says: " + getCode() + ".\n");
    } else {
      response.print("Qurricane says: " + code + ".\n" + 
            request.getAssociatedException().getMessage());
    }
  }

  /**
   * @return the code
   */
  public int getCode() {
    return code;
  }

  /**
   * @param code the code to set
   */
  public void setCode(int code) {
    this.code = code;
  }
  
}
