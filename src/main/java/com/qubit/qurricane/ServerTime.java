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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class ServerTime {
  DateFormat dateFormat;

  public ServerTime () {
    dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  
  static volatile private long lastRead = 0;
  String cachedTime = null;
  
  public String getCachedTime() {
    long now = new Date().getTime();
    if (cachedTime == null || (lastRead + 777) < now) {
      lastRead = now;
      cachedTime = getTime();
    }
    return cachedTime;
  }
  
  public String getTime() {
    return dateFormat.format(new Date());
  }
}
