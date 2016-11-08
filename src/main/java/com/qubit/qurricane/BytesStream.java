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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author piotr
 */
class BytesStream {

  public final static int BUF_SIZE_DEF = 1024;
  
  public int bufferElementSize = BUF_SIZE_DEF;
  private ByteBuffer currentBufferReading;
  int currentBufferReadIndex = 0;
  int currentBufferReadPosition = 0;
  private final List<ByteBuffer> buffers = new ArrayList<>();
  private ByteBuffer currentBufferWriting;

  /**
   * @return the bufferElementSize
   */
  public int getBufferElementSize() {
    return bufferElementSize;
  }

  /**
   * @param aBufferElementSize the bufferElementSize to set
   */
  public void setBufferElementSize(int aBufferElementSize) {
    bufferElementSize = aBufferElementSize;
  }


  public BytesStream() {}

  int currBufAt = -1;

  private void addBuf() {
    if ((currBufAt + 1) == buffers.size()) {
      this.currentBufferWriting = ByteBuffer.allocateDirect(bufferElementSize);
      if (this.currentBufferReading == null) {
        this.currentBufferReading = this.currentBufferWriting;
      }
      buffers.add(this.currentBufferWriting);
      currBufAt++;
    } else {
      this.currentBufferWriting = this.buffers.get(++currBufAt);
      this.currentBufferWriting.clear();
    }
  }

  /**
   * @return the buffers
   */
  public List<ByteBuffer> getBuffers() {
    return buffers;
  }

  public ByteBuffer getNotEmptyCurrentBuffer() {
    if (this.currentBufferWriting == null
        || !this.currentBufferWriting.hasRemaining()) {
      this.addBuf();
    }
    return this.currentBufferWriting;
  }
  
  byte[] bytes = null;
  
  public StringBuilder readAvailableToReadAsString(Charset charset) {
    StringBuilder builder = new StringBuilder();
    
    int pos = currentBufferReadPosition;
    for (int i = currentBufferReadIndex; i < buffers.size(); i++) {
      ByteBuffer buffer = buffers.get(i);
      int amount = buffer.position() - pos;
      if (buffer.hasArray()) {
        builder.append(new String(buffer.array(), pos, amount, charset));
      } else {
        if (bytes == null || bytes.length != buffer.capacity()) {
          bytes = new byte[amount];
        }
        for (int c = 0; c < amount; c++) {
          bytes[c] = buffer.get(pos + c);
        }
        builder.append(new String(bytes, 0, amount, charset));
      }
      pos = 0;// next buffer starts from 0
    }
    return builder;
  }

  public int read() {
    if (this.currentBufferReading == null) {
      return -1;
    } else if (currentBufferReadPosition < currentBufferReading.position()) {
      return (int) currentBufferReading.get(currentBufferReadPosition++);
    } else if (currentBufferReading.position() == currentBufferReading.limit()) {
      if (this.buffers.size() > currentBufferReadIndex + 1) {
        currentBufferReading = this.buffers.get(++currentBufferReadIndex);
        currentBufferReadPosition = 0;
        return this.read();
      } else {
        return -1;
      }
    } else {
      return -1;
    }
  }

  private void reset() {
    if (buffers.isEmpty()) {
      currentBufferReading = null;
      currentBufferWriting = null;
      currBufAt = -1;
    } else {
      currentBufferReading = currentBufferWriting = buffers.get(0);
      currBufAt = 0;
    }
    currentBufferReadIndex = 0;
    currentBufferReadPosition = 0;
  }

  public void clear() {
    reset();
    for (ByteBuffer buffer : buffers) {
      buffer.clear();
    }
  }
  
  public long leftToRead() {
    long amount = (currentBufferReading.position() - currentBufferReadPosition);
    int i = currentBufferReadIndex + 1;
    while (i < buffers.size()) {
      amount += buffers.get(i++).position();
    }
    return amount;
  }
  
  public long dataSize() {
    long amount = 0;
    for (ByteBuffer buffer : buffers) {
      amount += buffer.position();
      if (buffer == currentBufferWriting) {
        break;
      }
    }
    return amount;
  }
  
  /**
   * @return the currentBufferReadIndex
   */
  public int getCurrentBufferReadIndex() {
    return currentBufferReadIndex;
  }

  /**
   * @param currentBufferReadIndex the currentBufferReadIndex to set
   */
  public void setCurrentBufferReadIndex(int currentBufferReadIndex) {
    this.currentBufferReadIndex = currentBufferReadIndex;
  }

  /**
   * @return the currentBufferReadPosition
   */
  public int getCurrentBufferReadPosition() {
    return currentBufferReadPosition;
  }

  /**
   * @param currentBufferReadPosition the currentBufferReadPosition to set
   */
  public void setCurrentBufferReadPosition(int currentBufferReadPosition) {
    this.currentBufferReadPosition = currentBufferReadPosition;
  }
  
  public void shrinkLessMore(long toValue) {
    long amount = 0;
    int len = buffers.size();
    for (int i = 0; i < len; i++) {
      ByteBuffer buffer = buffers.get(i);
      amount += buffer.capacity();
      if (amount > toValue && (i + 1) < len) {
        for (int j = len - 1; j > i; j--) {
          buffers.remove(j);
        }
        break;
      }
    }
  }
  
  public void dropAlreadyRead() {
    for (int i = 0; i < currentBufferReadIndex; i++) {
      buffers.remove(i);
    }
  }
  
  /**
   * @return the currentBufferWriting
   */
  public ByteBuffer getCurrentBufferWriting() {
    return currentBufferWriting;
  }

  /**
   * @param currentBufferWriting the currentBufferWriting to set
   */
  public void setCurrentBufferWriting(ByteBuffer currentBufferWriting) {
    this.currentBufferWriting = currentBufferWriting;
  }

  static final int MAX_SHRINK = 4 * 64 * 1024;
  void shrinkLessMore() {
   this.shrinkLessMore(MAX_SHRINK);
  }
}
