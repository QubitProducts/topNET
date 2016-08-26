/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ResponseStream extends InputStream {
  private final InputStream headersStream;
  
  public static int RESPONSE_BUF_SIZE = 4096;

  volatile boolean closed = false;
  
  ByteBuffer buffer = ByteBuffer.allocate(RESPONSE_BUF_SIZE);
  private InputStream bodyStream;
  
  ResponseStream(InputStream headersToSend, InputStream stream) {
    this.headersStream = headersToSend;
    this.bodyStream = stream;
  }

  @Override
  public int read() throws IOException {
    int ch;
    if ((ch = headersStream.read()) != -1) {
      return ch;
    } else {
      if (this.getBodyStream() != null) {
        return getBodyStream().read();
      } else {
        return -1;
      }
    }
  }

  /**
   * @return the bodyStream
   */
  public InputStream getBodyStream() {
    return bodyStream;
  }

  /**
   * @param bodyStream the bodyStream to set
   */
  public boolean setBodyStream(InputStream bodyStream) {
    if (this.bodyStream != null) {
      this.bodyStream = bodyStream;
      return true;
    }
    
    return false;
  }
  
  
}
