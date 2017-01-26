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
package com.qubit.topnet;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author peter.fronc@qubit.com
 */
public class BytesStream {

  private static int defaultBufferChunkSize = 64 * 1024;
  private static int minimumBytesToKeepAfterJobShrink = 256 * 1024;
  // purposely static and not on server instance.
  private static boolean shrinkingBuffersAfterJob = true;

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

  /**
   * @return the shrinkingBuffersAfterJob
   */
  public static boolean isShrinkingBuffersAfterJob() {
    return shrinkingBuffersAfterJob;
  }

  /**
   * @param aShrinkingBuffersAfterJob the shrinkingBuffersAfterJob to set
   */
  public static void setShrinkingBuffersAfterJob(boolean aShrinkingBuffersAfterJob) {
    shrinkingBuffersAfterJob = aShrinkingBuffersAfterJob;
  }
  
  private int singleBufferChunkSize = getDefaultBufferChunkSize();
  private BufferWrapper currentReadingBuffer;
  private BufferWrapper currentWritingBuffer;
  private int currentBufferReadPosition = 0;
  private byte[] bytesCache = null;
  
  private BufferWrapper last;
  private BufferWrapper first;
  
  public BytesStream() {
    this.init();
  }
  
  private void init() {
    currentReadingBuffer = new BufferWrapper();
    currentWritingBuffer = currentReadingBuffer;
    first = currentReadingBuffer;
    last = currentReadingBuffer;
    currentBufferReadPosition = 0;
  }

  private void addBuf() {
    if (this.currentWritingBuffer.getNext() == null) {
      if (currentWritingBuffer.getByteBuffer() == null) {
        currentWritingBuffer.setByteBuffer(
            ByteBuffer.allocateDirect(singleBufferChunkSize));
      } else {
        this.currentWritingBuffer = (new BufferWrapper());
        this.currentWritingBuffer
            .setByteBuffer(ByteBuffer.allocateDirect(singleBufferChunkSize));
        
        this.last.setNext(this.currentWritingBuffer);
        this.currentWritingBuffer.setPrev(this.last);
        this.last = this.currentWritingBuffer;
      }
    } else {
      this.currentWritingBuffer = this.currentWritingBuffer.getNext();
      this.last = this.currentWritingBuffer;
      this.currentWritingBuffer.getByteBuffer().clear();
    }
  }

  public ByteBuffer getBufferToWrite() {
    if (this.currentWritingBuffer.getByteBuffer() == null
        || !this.currentWritingBuffer.getByteBuffer().hasRemaining()) {
      this.addBuf();
    }
    return this.currentWritingBuffer.getByteBuffer();
  }
  
  /**
   * Reads all available bytes as string - does not change 'currentBufferReadPosition'.
   * @param charset
   * @return StringBuilder
   */
  public StringBuilder readAvailableBytesAsString(Charset charset) {
    BufferWrapper start = this.currentReadingBuffer;
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
        if (bytesCache == null || bytesCache.length != buffer.capacity()) {
          bytesCache = new byte[amount];
        }
        
        for (int c = 0; c < amount; c++) {
          bytesCache[c] = buffer.get(pos + c);
        }
        
        builder.append(new String(bytesCache, 0, amount, charset));
      }
      
      pos = 0;// next buffer starts from 0
      
      if (start == last) {
        break;
      }
      
      start = start.getNext();
    }
    
    return builder;
  }

  public static final int NO_BYTES_LEFT_VAL = Byte.MAX_VALUE * 2;
  
  /**
   * Reads byte from this bytes stream and increments 
   * `this.currentBufferReadPosition`.
   * @return byte read  or NO_BYTES_LEFT_VAL if nothing left to read.
   */
  public int readByte() {
    //@todo refactor to use buf function and read into array
    ByteBuffer buf = currentReadingBuffer.getByteBuffer();
    
    if (currentBufferReadPosition < buf.position()) {
      
      return buf.get(currentBufferReadPosition++);
    
    } else if (!buf.hasRemaining()) {
      
      if (this.currentReadingBuffer.getNext() != null) {
        
        currentReadingBuffer = this.currentReadingBuffer.getNext();
        currentBufferReadPosition = 0;
        return this.readByte();
        
      } else {
        
        return NO_BYTES_LEFT_VAL;
        
      }
    } else {
      
      return NO_BYTES_LEFT_VAL;
      
    }
  }
  
  /**
   * Resets this bytes stream. 
   * Clears any buffer chunks left and sets all markers to zero.
   */
  public void reset() {
    this.currentReadingBuffer = first;
    this.currentWritingBuffer = first;
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
  
  //@todo convert it to numerical algo
  public long availableToRead() {
    if (currentReadingBuffer.getByteBuffer() == null) {
      return 0;
    }
    
    long amount = 0;
    int pos = currentBufferReadPosition;
    BufferWrapper start = currentReadingBuffer;
    
    while (start != null) {
      amount += (start.getByteBuffer().position() - pos);
      
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
      
      if (start == currentWritingBuffer) {
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
    first = this.currentReadingBuffer;
    first.setPrev(null);
  }
  
  public void shrinkLessMore() {
    if (isShrinkingBuffersAfterJob()) {
      this.shrinkLessMore(minimumBytesToKeepAfterJobShrink);
    }
  }
  
  /**
   * @return the currentReadingBuffer
   */
  public BufferWrapper getCurrentReadingBuffer() {
    return currentReadingBuffer;
  }

  /**
   * @return the currentWritingBuffer
   */
  public BufferWrapper getCurrentWritingBuffer() {
    return currentWritingBuffer;
  }
  
  public BufferWrapper getFirst() {
    return first;
  }
  
  public BufferWrapper getLast() {
    return last;
  }
  
  /**
   * @return the singleBufferChunkSize
   */
  public int getSingleBufferChunkSize() {
    return singleBufferChunkSize;
  }

  /**
   * @param aBufferElementSize the singleBufferChunkSize to set
   */
  public void setSingleBufferChunkSize(int aBufferElementSize) {
    singleBufferChunkSize = aBufferElementSize;
  }
}
