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
import com.qubit.topnet.Request;
import com.qubit.topnet.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ErrorHandler extends Handler {
  
  public static final Handler[] errorHandlers = new Handler[1024];
  
  private int code;
  private ErrorTypes type;
  
  public static ErrorHandler getHandlerForCode(int httpCode) {
    ErrorHandler errorHandler = new ErrorHandler();
    errorHandler.setCode(httpCode);
    return errorHandler;
  }

  public Handler getInstance() {
    return getHandlerForCode(this.code);
  }
  
  @Override
  public boolean process(Request request, Response response) throws Exception {
    response.setHttpCode(getCode());
    
    if (request.getAssociatedException() != null) {
      Logger.getLogger(ErrorHandler.class.getName())
                  .log(Level.SEVERE, null, request.getAssociatedException());
    }
    if (request.getAssociatedException() == null) {
      response.print(String.valueOf(getCode()));
    } else {
      response.print(code + " \n" + 
            request.getAssociatedException().getMessage());
    }
    
    if (type != null) {
      response.print(" (" + type.name() + ")\n");
    }
    
    response.print("\n");
    
    return true;
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

  /**
   * @return the type
   */
  public ErrorTypes getType() {
    return type;
  }

  /**
   * @param type the type to set
   */
  public void setType(ErrorTypes type) {
    this.type = type;
  }
  
}
