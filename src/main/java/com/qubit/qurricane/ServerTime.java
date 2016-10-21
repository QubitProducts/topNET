/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
