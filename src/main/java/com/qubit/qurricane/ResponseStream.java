/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class ResponseStream implements ResponseReader {

  private InputStream headersStream;
  private InputStream bodyStream;
  private long bytesRead = 0;

  public ResponseStream() {
  }

  /**
   * Main reading function. It returns integer != -1 if there is anything to
   * read.
   *
   * @return
   * @throws IOException
   */
  public int read() throws IOException {
    int ch;
    
    if ((ch = getHeadersStream().read()) == -1) {
      ch = readBody();
    }
    
    if (ch > -1) {
      bytesRead++;
    }
    
    return ch;
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

  private ResponseReader headerResponseReader;
  public ResponseReader getHeadersOnlyResponseReader() {
    if (headerResponseReader == null) {
      headerResponseReader = new ResponseReader() {
        @Override
        public int read() throws IOException {
          return ResponseStream.this.headersStream.read();
        }
      };
    }
    
    return headerResponseReader;
  }

  /**
   * @return the bytesRead
   */
  public long getBytesRead() {
    return bytesRead;
  }
}
