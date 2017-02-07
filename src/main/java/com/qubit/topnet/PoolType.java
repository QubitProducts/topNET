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

/**
 *
 * @author peter.fronc@qubit.com
 */
public enum PoolType {
  /**
   * Pool of handling threads where each thread has static array with atomic 
   * references to it's jobs.
   */
  POOL,
  /**
   * Pool type where each thread maintains concurrent queue of it's jobs.
   */
  QUEUE,
  /**
   * Same as {@link QUEUE} type but all threads share same concurrent queue.
   */
  QUEUE_SHARED
}
