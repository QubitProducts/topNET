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
    response.setMoreDataComing(true);
    response.setContentLength(-1);// 1 value trick
    ResponseAppendableReader source = new ResponseAppendableReader();
    
    if (response.getStringBuffer() != null) {
      source.print(response.getStringBuffer().toString());
      response.setStringBuffer(null);
    }
    
    response.setStreamToReadFrom(source);
    
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          ResponseAppendableReader stream
                  = (ResponseAppendableReader)
                    response.getStreamToReadFrom();
          Thread.sleep(1000);
          stream.print("Hello after 1 sec!<br>\n");
          Thread.sleep(1000);
          stream.print("Hello after 2 sec!<br>\n");
          Thread.sleep(1000);
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
  
  public void print(StringBuffer str) {
    buffer.append(str);
  }
}
