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
package com.qubit.topnet.eventonly;

import java.nio.channels.SelectionKey;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class SelectionKeyChain {

  private SelectionKeyLink first;
  private SelectionKeyLink last;

  public SelectionKeyChain() {
    first = last = null;
  }

  public int size() {
    SelectionKeyLink cur = first;
    int i = 0;
    while (cur != null) {
      cur = cur.next;
      i++;
    }
    return i;
  }

  public void add(SelectionKeyLink skl) {
    if (last == null) {
      first = last = skl;
    } else {
      last.next = skl;
      skl.previous = last;
      last = skl;
    }
  }

  /**
   * @return the first
   */
  public SelectionKeyLink getFirst() {
    return first;
  }

  /**
   * @return the last
   */
  public SelectionKeyLink getLast() {
    return last;
  }
  
  class SelectionKeyLink {
    private SelectionKey key;
    private SelectionKeyLink next;
    private SelectionKeyLink previous;
    private long acceptTime;

    public SelectionKeyLink(SelectionKey k) {
      this.key = k;
    }

    public SelectionKeyLink(SelectionKey newKey, long acceptTime) {
      this.key = newKey;
      this.acceptTime = acceptTime;
    }

    public void remove() {
      if (this == first) {
        first = first.next;
        if (first != null) {
          first.previous = null;
        } else {
          last = first;
        }
      } else {
        this.previous.next = this.next;
        if (this.next != null) {
          this.next.previous = this.previous;
        }
      }
    }

    /**
     * @return the key
     */
    public SelectionKey getKey() {
      return key;
    }

    /**
     * @return the next
     */
    public SelectionKeyLink getNext() {
      return next;
    }

    /**
     * @return the previous
     */
    public SelectionKeyLink getPrevious() {
      return previous;
    }

    /**
     * @return the acceptTime
     */
    public long getAcceptTime() {
      return acceptTime;
    }
  }
}
