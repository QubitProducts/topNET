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

import static com.qubit.topnet.ServerBase.DEFAULT_MAX_MESSAGE_SIZE;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author peter.fronc@qubit.com
 */
public class BytesStream {

  /**
   * @return the maxBufferSize
   */
  public long getMaxBufferSize() {
    return maxBufferSize;
  }

  /**
   * @param maxBufferSize the maxBufferSize to set
   */
  public void setMaxBufferSize(long maxBufferSize) {
    this.maxBufferSize = maxBufferSize;
  }

  private static int defaultBufferChunkSize = 2 *32 * 1024; // def cluster size
  private static int minimumBytesToKeepAfterJobShrink = 4 * 32 * 1024;
  private static long moveReadTailThreshold = 2 * defaultBufferChunkSize;
  private long maxBufferSize = DEFAULT_MAX_MESSAGE_SIZE - 1;

  // purposely static and not on server instance.
  private static boolean shrinkingBuffersAfterJob = true;

  private int singleBufferChunkSize = getDefaultBufferChunkSize();
  private BufferWrapper currentReadingBuffer;
  private BufferWrapper currentWritingBuffer;
  private int currentBufferReadPosition = 0;
  private byte[] bytesCache = null;
  private long currentSize;

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
    currentSize = 0;
    currentBufferReadPosition = 0;
  }

  private void addBuf() {
    if (this.currentWritingBuffer.getNext() == null) {
      if (currentWritingBuffer.getByteBuffer() == null) {
        currentWritingBuffer.setByteBuffer(
            ByteBuffer.allocateDirect(singleBufferChunkSize));
        currentSize += singleBufferChunkSize;
      } else if (currentSize < maxBufferSize) { // add new only if not too large
        this.currentWritingBuffer = (new BufferWrapper());
        this.currentWritingBuffer
            .setByteBuffer(ByteBuffer.allocateDirect(singleBufferChunkSize));
        currentSize += singleBufferChunkSize;
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

  /**
   * Function returns current buffer for writing (also available as
   * `this.currentWritingBuffer`).
   *
   * @return this.currentWritingBuffer
   */
  public BufferWrapper getBufferToWrite() {
    if (this.currentWritingBuffer.getByteBuffer() == null
        || !this.currentWritingBuffer.getByteBuffer().hasRemaining()) {
      this.addBuf();
    }
    return this.currentWritingBuffer;
  }

  /**
   * Reads all available bytes as string - does not change
   * 'currentBufferReadPosition'.
   *
   * @param charset
   * @return StringBuilder
   */
  public StringBuilder readAvailableBytesAsString(Charset charset) {
    BufferWrapper next = this.currentReadingBuffer;
    StringBuilder builder = new StringBuilder();

    if (next.getByteBuffer() == null) {
      return builder;
    }

    int pos = currentBufferReadPosition;
    ByteBuffer buffer;

    while (next != null) {

      buffer = next.getByteBuffer();
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

      if (next == last) {
        break;
      }

      next = next.getNext();
    }

    return builder;
  }

  public static final int NO_BYTES_LEFT_VAL = Byte.MAX_VALUE * 2;

  /**
   * Reads byte from this bytes stream and increments
   * `this.currentBufferReadPosition`.
   *
   * @return byte read or NO_BYTES_LEFT_VAL if nothing left to read.
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
   * Resets this bytes stream. Clears any buffer chunks left and sets all
   * markers to zero.
   */
  public void reset() {
    this.currentReadingBuffer = first;
    this.currentWritingBuffer = first;
    last = first;
    if (first.getByteBuffer() != null) {
      first.getByteBuffer().clear();
    }
    //this.clear();
    this.currentBufferReadPosition = 0;
  }

  private void clear() {
    BufferWrapper next = this.first;

    while (next != null) {
      if (next.getByteBuffer() != null) {
        next.getByteBuffer().clear();
      }
      next = next.getNext();
    }
  }

  //@todo convert it to numerical algo
  public long availableToRead() {
    if (currentReadingBuffer.getByteBuffer() == null) {
      return 0;
    }

    long amount = 0;
    int pos = currentBufferReadPosition;
    BufferWrapper next = currentReadingBuffer;

    while (next != null) {
      amount += (next.getByteBuffer().position() - pos);

      if (next == last) {
        break;
      }

      pos = 0;
      next = next.getNext();
    }

    return amount;
  }

  public long dataSize() {
    if (first.getByteBuffer() == null) {
      return 0;
    }

    long amount = 0;
    BufferWrapper next = first;

    while (next != null) {
      amount += next.getByteBuffer().position();

      if (next == currentWritingBuffer) {
        break;
      }

      if (next == last) {
        break;
      }
      next = next.getNext();
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

    BufferWrapper next = first;
    while (next != null) {
      ByteBuffer buffer = next.getByteBuffer();
      amount += buffer.capacity();
      if (amount > toValue) {
        if (next.getPrev() != null) {
          last = next.getPrev();
        } else {
          last = next;
        }
        last.setNext(null);
        break;
      }
      next = next.getNext();
    }

    currentSize = amount;
  }

  public void shrinkLessMore() {
    if (isShrinkingBuffersAfterJob()) {
      this.shrinkLessMore(Math.min(minimumBytesToKeepAfterJobShrink, getMaxBufferSize()));
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

  /**
   * @return the currentSize
   */
  public long getCurrentSize() {
    return currentSize;
  }

  // @todo add reusing buffer, so dropped chunks go at end of queue
  /**
   * This method will seek currentReadingBufferand will move all elements till
   * end of chain and clear them. If there is only one element and is fully read
   * (first.getByteBuffer().position() == currentBufferReadPosition) then it
   * will be cleared and currentBufferReadPosition set to zero.
   *
   * @return
   */
  protected boolean moveReadTailToEndOrClearBufferIfSpaceUnavailable() {
    
    if (currentSize > getMoveReadTailThreshold()) {
      return false;
    }

    if (first.getByteBuffer() == null) {
      return false;
    }

    // unique case there is one buf only and must be reset
    if (first == last) {
      if (first.getByteBuffer().position() == currentBufferReadPosition) {
        first.getByteBuffer().clear();
        currentBufferReadPosition = 0;
        return true;
      }
      return false;
    }

    // larger than one, check if its first (we tail only passed elemnts)
    // means that next elements are available
    if (first == currentReadingBuffer) {
      return false;
    }

    BufferWrapper next = first;

    while (next != null) {
      if (next == currentReadingBuffer) {

        BufferWrapper toReset = first;

        last.setNext(first); // order is critical
        first.setPrev(last);
        first = currentReadingBuffer;
        last = currentReadingBuffer.getPrev();
        last.setNext(null);
        first.setPrev(null);

        do {
          toReset.getByteBuffer().clear();
        } while ((toReset = toReset.getNext()) != null);

        return true;
      }
      next = next.getNext();
    }

    return false;
  }

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
   * @param aMinimumBytesToKeepAfterJobShrink the
   * minimumBytesToKeepAfterJobShrink to set
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

  /**
   * @return the moveReadTailThreshold
   */
  public static long getMoveReadTailThreshold() {
    return moveReadTailThreshold;
  }

  /**
   * @param aMoveReadTailThreshold the moveReadTailThreshold to set
   */
  public static void setMoveReadTailThreshold(long aMoveReadTailThreshold) {
    moveReadTailThreshold = aMoveReadTailThreshold;
  }

}
