/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane.examples;

import com.qubit.qurricane.Handler;
import com.qubit.qurricane.Request;
import com.qubit.qurricane.Response;
import com.qubit.qurricane.ResponseStream;
import com.qubit.qurricane.exceptions.ResponseBuildingStartedException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class AsyncAppenderHandler extends Handler {

  public AsyncAppenderHandler() {
  }

  @Override
  public void prepare(Request request, Response response) {
    try {
      response.setResponseStream(new ResponseAppendableReader());
      response.setMoreDataComing(true);
      response.setForcingNotKeepingAlive(true);
    } catch (ResponseBuildingStartedException ex) {
      Logger.getLogger(AsyncAppenderHandler.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  @Override
  public void process(final Request request, final Response response) throws Exception {

    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          ResponseAppendableReader stream = 
                  (ResponseAppendableReader) response.getResponseStream();
          Thread.sleep(1000);
          stream.print("Hello after 1 sec!");
          Thread.sleep(2000);
          stream.print("Hello after 3 sec!");
          
        } catch (InterruptedException ex) {
          Logger.getLogger(AsyncAppenderHandler.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
          response.setMoreDataComing(false);
        }
      }
    });

    t.start();
  }

}

class ResponseAppendableReader extends ResponseStream {

  StringBuffer buffer = new StringBuffer();
  int current = 0;
  
  @Override
  public int readBody() throws IOException {
    if (buffer.length() <= current) {
      return -1;
    }
    return buffer.charAt(current++);
  }

  public void print(String str) {
    buffer.append(str);
  }
}
