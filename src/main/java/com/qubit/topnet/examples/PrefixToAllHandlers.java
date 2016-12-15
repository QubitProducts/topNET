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

package com.qubit.topnet.examples;

import com.qubit.topnet.Handler;
import com.qubit.topnet.Request;
import com.qubit.topnet.Response;
//import java.io.ByteArrayInputStream;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class PrefixToAllHandlers extends Handler {

  @Override
  public boolean onBeforeOutputStreamIsSet(Request request, Response response) {
    // happens before processing and preparing any response.
    return true;
  }

  @Override
  public boolean process(Request request, Response response) 
          throws Exception {
    response.print("<h1>URL " + request.getFullPath() + "\n</h1>");
    return true;
  }

  @Override
  public boolean matches(String fullPath, String path, String params) {
    return params.matches(".*url.*");
  }
}
