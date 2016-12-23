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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Logger;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class ServerTime {
  static private final Logger log = Logger.getLogger(ServerTime.class.getName());
  
  DateFormat dateFormat;

  public ServerTime () {
    dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  
  static volatile private long lastRead = 0;
  static private volatile String cachedTime = null;
  
  private static final ThreadLocal<ServerTime> serverTime;

  static {
    serverTime = new ThreadLocal<ServerTime>() {
      @Override
      protected ServerTime initialValue() {
        return new ServerTime();
      }
    };
  }
  
  public static String getCachedTime() {
    long now = new Date().getTime();
    if (cachedTime == null || (lastRead + 499) < now) {
      lastRead = now;
      cachedTime = serverTime.get().getTime();
      log.info(cachedTime);
    }
    return cachedTime;
  }
  
  public String getTime() {
    return dateFormat.format(new Date());
  }
}
