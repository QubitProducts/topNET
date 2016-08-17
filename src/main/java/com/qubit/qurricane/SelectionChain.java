/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.nio.channels.SelectionKey;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class SelectionChain {
  
  static volatile SelectionChain last;
  static volatile SelectionChain first;
  
  private SelectionChain next;

  private SelectionKey key;
  
  public SelectionChain() {
    
  }
  
  /**
   * @return the next
   */
  public SelectionChain getNext() {
    return next;
  }

  /**
   * @param next the next to set
   */
  public void setNext(SelectionChain next) {
    this.next = next;
    SelectionChain.last = next;
  }

  /**
   * @return the key
   */
  public SelectionKey getKey() {
    return key;
  }

  /**
   * @param key the key to set
   */
  public void setKey(SelectionKey key) {
    this.key = key;
  }
  
  
}
