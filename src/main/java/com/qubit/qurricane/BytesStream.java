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

/**
 *
 * @author piotr
 */
public class BytesStream {

  private static int defaultBufferChunkSize = 64 * 1024;
  private static int minimumBytesToKeepAfterJobShrink = 256 * 1024;
  public static boolean doNotShrinkBuffersAfterJob = false;

  /**
   * @return the defaultBufferChunkSize
   */
  public static int getDefaultBufferChunkSize() {
    return defaultBufferChunkSize;
  }

  /**
   * @param aDefaultBufferChunkSize the defaultBufferChunkSize to set
   */
  public static void setDefaultBufferChunkSize(int aDefaultBufferChunkSize) {
    defaultBufferChunkSize = aDefaultBufferChunkSize;
  }

  /**
   * @return the minimumBytesToKeepAfterJobShrink
   */
  public static int getMinimumBytesToKeepAfterJobShrink() {
    return minimumBytesToKeepAfterJobShrink;
  }

  /**
   * @param aMinimumBytesToKeepAfterJobShrink the minimumBytesToKeepAfterJobShrink to set
   */
  public static void setMinimumBytesToKeepAfterJobShrink(int aMinimumBytesToKeepAfterJobShrink) {
    minimumBytesToKeepAfterJobShrink = aMinimumBytesToKeepAfterJobShrink;
  }
  
  public int bufferElementSize = getDefaultBufferChunkSize();
  private BufferWrapper currentBufferReading;
  private BufferWrapper currentBufferWriting;
  private int currentBufferReadPosition = 0;
  
  BufferWrapper last;
  BufferWrapper first;
  
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


  public BytesStream() {
    this.init();
  }

  private void addBuf() {
    if (this.currentBufferWriting.getNext() == null) {
      if (currentBufferWriting.getByteBuffer() == null) {
        currentBufferWriting.setByteBuffer(
            ByteBuffer.allocateDirect(bufferElementSize));
      } else {
        this.currentBufferWriting = (new BufferWrapper());
        this.currentBufferWriting
            .setByteBuffer(ByteBuffer.allocateDirect(bufferElementSize));
        
        this.last.setNext(this.currentBufferWriting);
        this.currentBufferWriting.setPrev(this.last);
        this.last = this.currentBufferWriting;
      }
    } else {
      this.currentBufferWriting = this.currentBufferWriting.getNext();
      this.last = this.currentBufferWriting;
      this.currentBufferWriting.getByteBuffer().clear();
    }
  }

  public ByteBuffer getNotEmptyCurrentBuffer() {
    if (this.currentBufferWriting.getByteBuffer() == null
        || !this.currentBufferWriting.getByteBuffer().hasRemaining()) {
      this.addBuf();
    }
    return this.currentBufferWriting.getByteBuffer();
  }
  
  byte[] bytes = null;
  
  public StringBuilder readAvailableToReadAsString(Charset charset) {
    BufferWrapper start = this.currentBufferReading;
    StringBuilder builder = new StringBuilder();
    
    if (start.getByteBuffer() == null) {
      return builder;
    }
    
    int pos = currentBufferReadPosition;
    ByteBuffer buffer;
    
    while (start != null) {
      
      buffer = start.getByteBuffer();
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
      
      if (start == last) {
        break;
      }
      
      start = start.getNext();
    }
    
    return builder;
  }

  public byte read() {
    ByteBuffer buf = currentBufferReading.getByteBuffer();
    if (currentBufferReadPosition < buf.position()) {
      
      return buf.get(currentBufferReadPosition++);
    
    } else if (!buf.hasRemaining()) {
      
      if (this.currentBufferReading.getNext() != null) {
        
        currentBufferReading = this.currentBufferReading.getNext();
        currentBufferReadPosition = 0;
        return this.read();
        
      } else {
        
        return -1;
        
      }
    } else {
      
      return -1;
      
    }
  }
  
  public void reset() {
    this.currentBufferReading = first;
    this.currentBufferWriting = first;
    last = first;
    this.clear();
    this.currentBufferReadPosition = 0;
  }

  private void clear() {
    BufferWrapper start = this.first;
    
    while (start != null) {
      if (start.getByteBuffer() != null) {
        start.getByteBuffer().clear();
      }
      start = start.getNext();
    }
  }
  
  public long leftToRead() {
    if (currentBufferReading.getByteBuffer() == null) {
      return 0;
    }
    
    long amount = 0;
    int pos = currentBufferReadPosition;
    BufferWrapper start = currentBufferReading;
    
    while (start != null) {
      amount += (start.getByteBuffer().position() - pos);
      
      if (start == last) {
        break;
      }
      
      if (start == last) {
        break;
      }
      pos = 0;
      start = start.getNext();
    }
    
    return amount;
  }
  
  public long dataSize() {
    if (first.getByteBuffer() == null) {
      return 0;
    }
    
    long amount = 0;
    BufferWrapper start = first;
    
    while (start != null) {
      amount += start.getByteBuffer().position();
      
      if (start == currentBufferWriting) {
        break;
      }
      
      if (start == last) {
        break;
      }
      start = start.getNext();
    }
    return amount;
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
    if (first.getByteBuffer() == null) {
      return;
    }
    
    if (first == last) {
      return;
    }
    
    long amount = 0;
    
    BufferWrapper start = first;
    while (start != null) {
      ByteBuffer buffer = start.getByteBuffer();
      amount += buffer.capacity();
      if (amount > toValue) {
        if (start.getPrev() != null) {
          last = start.getPrev();
        } else {
          last = start;
        }
        last.setNext(null);
        break;
      }
      start = start.getNext();
    }
  }
  
  public void dropAlreadyRead() {
    first = this.currentBufferReading;
    first.setPrev(null);
  }
  
  public void shrinkLessMore() {
    if (doNotShrinkBuffersAfterJob) {
      return;
    }
    this.shrinkLessMore(minimumBytesToKeepAfterJobShrink);
  }
  
  /**
   * @return the currentBufferReading
   */
  public BufferWrapper getCurrentBufferReading() {
    return currentBufferReading;
  }

  /**
   * @return the currentBufferWriting
   */
  public BufferWrapper getCurrentBufferWriting() {
    return currentBufferWriting;
  }

  /**
   * 
   */
  private void init() {
    currentBufferReading = new BufferWrapper();
    currentBufferWriting = currentBufferReading;
    first = currentBufferReading;
    last = currentBufferReading;
    currentBufferReadPosition = 0;
  }

}
