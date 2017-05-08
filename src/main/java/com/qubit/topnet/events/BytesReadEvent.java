/*
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.qubit.topnet.events;

import com.qubit.topnet.Request;
import com.qubit.topnet.Response;

/**
 *
 * @author piotr
 */
public interface BytesReadEvent {

  /**
   * Triggered when bytes are read from channel. When streamin data this is a
   * good moment to control growing buffer. topNET will fill growing bytesStream
   * uncontrolled and after reading from bytesStream you can update on it with
   * this function.
   *
   * @param request
   * @param response
   * @param finished true when reading is finished
   */
  public void handle(
      Request request,
      Response response,
      boolean finished);
}
