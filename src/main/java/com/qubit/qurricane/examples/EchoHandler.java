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

package com.qubit.qurricane.examples;

import com.qubit.qurricane.Handler;
import com.qubit.qurricane.Request;
import com.qubit.qurricane.Response;
//import java.io.ByteArrayInputStream;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class EchoHandler extends Handler {

  @Override
  public boolean prepare(Request request, Response response) {
    // happens before processing and preparing any response.
    return true;
  }

  @Override
  public boolean process(Request request, Response response) 
          throws Exception {
    response.print("Hello World! Echo:\n" + request.getBodyString());
    // using stream example (if you start using print, streaming will fail):
//    ByteArrayInputStream is = new ByteArrayInputStream(
//    request.getBodyString().getBytes());
//    response.setStreamToReadFrom(is);
    response.setForcingNotKeepingAlive(false);
    response.setTellingConnectionClose(false);
    return true;
  }
  
}
