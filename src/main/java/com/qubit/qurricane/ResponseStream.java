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
