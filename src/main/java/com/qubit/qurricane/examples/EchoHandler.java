/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.examples;

import com.qubit.qurricane.Handler;
import com.qubit.qurricane.Request;
import com.qubit.qurricane.Response;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class EchoHandler extends Handler {

  @Override
  public void process(Request request, Response response) throws Exception {
    response.print("Hello World! Echo:\n" + request.getBodyString());
  }
  
}
