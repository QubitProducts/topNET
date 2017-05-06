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

import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public abstract class ResponseReader extends InputStream {
  
  private ReadableByteChannel byteChannel;
  
  /**
   * @return the byteChannel
   */
  public ReadableByteChannel getByteChannel() {
    return byteChannel;
  }

  /**
   * @param byteChannel the byteChannel to set
   */
  public void setByteChannel(ReadableByteChannel byteChannel) {
    this.byteChannel = byteChannel;
  }
}
