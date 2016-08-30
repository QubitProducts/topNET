/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.qurricane;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
class ServerTime {
  DateFormat dateFormat;
  Calendar calendar;

  public ServerTime () {
    calendar =  Calendar.getInstance();
    
    dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  
  public Object getTime() {
    return dateFormat.format(calendar.getTime());
  }
  
}
