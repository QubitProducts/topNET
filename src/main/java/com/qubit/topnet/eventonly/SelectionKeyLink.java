/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet.eventonly;

import java.nio.channels.SelectionKey;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class SelectionKeyLink {
  
  public static SelectionKeyLink first;
  public static SelectionKeyLink last;
  
  static {
    first = last = null;
  }
  
  public SelectionKey key;
  public SelectionKeyLink next;
  public SelectionKeyLink previous;
  public long acceptTime;
  
  public SelectionKeyLink(SelectionKey k) {
    this.key = k;
  }

  SelectionKeyLink(SelectionKey newKey, long acceptTime) {
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
  
  public static int size () {
    SelectionKeyLink cur = first;
    int i =0;
    while(cur != null){
      cur = cur.next;
      i++;
    }
    return i;
  }
  
  public static void add(SelectionKeyLink skl) {
    if (last == null) {
      first = last = skl;
    } else {
      last.next = skl;
      skl.previous = last;
      last = skl;
    }
  }
}