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
public abstract class LimitsHandler {
  /**
   * check for key and data handler if not null. Key is not null when its accept
   * level timeout. Other timeout have dh as reference.
   * @param key
   * @param idle
   * @return true if channel should be closed and job ended.
   */
  public abstract boolean handleTimeout(
                              SelectionKey key, long idle,
                              DataHandler object);
  public abstract boolean handleSizeLimit(
                              SelectionKey key, long idle,
                              DataHandler object);
}
