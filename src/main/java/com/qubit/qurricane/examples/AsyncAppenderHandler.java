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
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class AsyncAppenderHandler extends Handler {

  @Override
  public boolean process(final Request request, final Response response) 
          throws Exception {
//    response.setResponseStream(new ResponseStream());
    response.prepareResponseReader();
    response.getResponseStream()
            .setBodyStream(new ResponseAppendableReader());
    response.setHttpProtocol(HTTP_1_1);
    response.setContentLength(-1);// 1 value trick
    response.setMoreDataComing(true);
    
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          ResponseAppendableReader stream
                  = (ResponseAppendableReader)
                    response.getResponseStream().getBodyStream();
          Thread.sleep(1000);
          stream.print("Hello after 1 sec!<br>\n");
          Thread.sleep(1000);
          stream.print("Hello after 2 sec!<br>\n");
          Thread.sleep(1000);
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
          stream.print("Hello after 3 sec!<br>\n");
        } catch (InterruptedException ex) {
          Logger.getLogger(AsyncAppenderHandler.class.getName())
                  .log(Level.SEVERE, null, ex);
        } finally {
          response.setMoreDataComing(false);
        }
      }
    });

    t.start();
    
    return true;
  }

  @Override
  public int getMaxIdle() {
    return 0;
  }
  
}

class ResponseAppendableReader extends InputStream {

  StringBuffer buffer = new StringBuffer();
  int current = 0;

  @Override
  public int read() throws IOException {
    if (buffer.length() <= current) {
      return -1;
    }
    return buffer.charAt(current++);
  }

  public void print(String str) {
    buffer.append(str);
  }
}
