/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
public class PrefixToAllHandlers extends Handler {

  @Override
  public boolean prepare(Request request, Response response) {
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
