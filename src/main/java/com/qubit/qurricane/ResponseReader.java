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
public class ResponseReader {
  private InputStream headersStream;
  
  public static int RESPONSE_BUF_SIZE = 4096;  
  
  private ByteBuffer buffer = ByteBuffer.allocate(RESPONSE_BUF_SIZE);
  private InputStream bodyStream;
  
  public ResponseReader() {
  }

  /**
   * Main reading function. It returns integer != -1 if there is anything to read.
   * @return
   * @throws IOException 
   */
  public int read() throws IOException {
    int ch;
    if ((ch = getHeadersStream().read()) != -1) {
      return ch;
    } else {
      return readBody();
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
   * @return 
   */
  public void setBodyStream(InputStream bodyStream) {
    this.bodyStream = bodyStream;
  }

  public int readBody() throws IOException {
    if (this.getBodyStream() != null) {
      return getBodyStream().read();
    } else {
      return -1;
    }
  }

  /**
   * @return the headersStream
   */
  public InputStream getHeadersStream() {
    return headersStream;
  }

  /**
   * @param headersStream the headersStream to set
   */
  public void setHeadersStream(InputStream headersStream) {
    this.headersStream = headersStream;
  }
  
  
}
