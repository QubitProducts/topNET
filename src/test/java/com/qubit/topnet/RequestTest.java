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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Fronc <peter.fronc@qubitdigital.com>
 */
public class RequestTest {

  public RequestTest() {
  }

  /**
   * Test of parseParameters method, of class Request.
   */
  @Test
  public void testParseParameters_map() {
    System.out.println("parseParameters");
    String params = "";
    Map<String, String> expResult = new HashMap<>();
    Request.MappedValues valuesStore = new Request.MappedValues();

    Request.parseParameters(valuesStore, params, "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "1234");
    Request.parseParameters(valuesStore, "abc=1234", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "1234");
    Request.parseParameters(valuesStore, "abc=1234&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "1234");
    Request.parseParameters(valuesStore, "&abc=1234&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "1234");
    Request.parseParameters(valuesStore, "&&&abc=1234&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "=1234");
    Request.parseParameters(valuesStore, "&&&abc==1234&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "==1234");
    Request.parseParameters(valuesStore, "&&&abc===1234&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "==1234");
    Request.parseParameters(valuesStore, "&&&&abc===1234&&&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "");
    expResult.put("", "=1234");
    Request.parseParameters(valuesStore, "&&&&abc=&==1234&&&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());

    expResult.put("abc", "");
    expResult.put("", "=1234");
    expResult.put("y", "=4567");
    assertEquals(expResult.size(), 3);
    Request.parseParameters(valuesStore, "&&&&abc=&==1234&y==4567&&", "UTF-8");
    assertEquals(expResult, valuesStore.getValues());
  }

  @Test
  public void testParseParameters_mapList() {
    System.out.println("parseParameters");
    Map<String, List<String>> expResult = new HashMap<>();
    Request.MappedValuesLists valuesStore = new Request.MappedValuesLists();

    expResult.put("abc", Arrays.asList(new String[]{""}));
    expResult.put("", Arrays.asList(new String[]{"=1234"}));
    expResult.put("y", Arrays.asList(new String[]{"=4567"}));
    
    assertEquals(expResult.size(), 3);
    
    Request.parseParameters(valuesStore, "&&&&abc=&==1234&y==4567&&", "UTF-8");
    
    for (Entry<String, List<String>> entry : expResult.entrySet()) {
      assertEquals(entry.getValue(), valuesStore.getValues().get(entry.getKey()));
    }
    
  }


  /**
   * Test of getBodyParameters method, of class Request.
   */
  @Test
  public void testGetBodyParameters() {
    System.out.println("getBodyParameters");
    Request instance = new Request();
    instance.getBytesStream().setSingleBufferChunkSize(16000);
    ByteBuffer buf = null;
    
    Map<String, String> expResult = null;
    
    expResult = new HashMap<>();
    instance.reset();
    buf = instance.getBytesStream().getBufferToWrite();
    buf.put("abc=1234".getBytes());
    expResult.put("abc", "1234");
    assertEquals(expResult, instance.getBodyParameters());

    
    expResult = new HashMap<>();
    instance.reset();
    buf = instance.getBytesStream().getBufferToWrite();
    buf.put("&abc=1234&&&".getBytes());
    expResult.put("abc", "1234");
    assertEquals(expResult, instance.getBodyParameters());

    expResult = new HashMap<>();
    instance.reset();
    buf = instance.getBytesStream().getBufferToWrite();
    buf.put("&&&&abc=&==1234&&&".getBytes());
    expResult.put("abc", "");
    expResult.put("", "=1234");
    assertEquals(expResult, instance.getBodyParameters());
    
    expResult = new HashMap<>();
    instance.reset();
    buf = instance.getBytesStream().getBufferToWrite();
    buf.put("&&&&abc=&==1234%26!%40%10%25&&&".getBytes());
    expResult.put("abc", "");
    expResult.put("", "=1234&!@%");
    assertEquals(expResult, instance.getBodyParameters());
  }

  /**
   * Test of getBodyParametersList method, of class Request.
   */
  @Test
  public void testGetBodyParametersList() {
    System.out.println("getBodyParametersList");
    Request instance = new Request();
    List expResult = null;
    List result = instance.getBodyParametersList();
    assertEquals(expResult, result);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

  /**
   * Test of getBodyParametersMappedLists method, of class Request.
   */
  @Test
  public void testGetBodyParametersMappedLists() {
    System.out.println("getBodyParametersMappedLists");
    Request instance = new Request();
    Map<String, List<String>> expResult = null;
    Map<String, List<String>> result = instance.getBodyParametersMappedLists();
    assertEquals(expResult, result);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

  /**
   * Test of getParameters method, of class Request.
   */
  @Test
  public void testGetParameters() {
    System.out.println("getParameters");
    Request instance = new Request();
    Map<String, String> expResult = null;
    Map<String, String> result = instance.getParameters();
    assertEquals(expResult, result);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

  /**
   * Test of getParametersList method, of class Request.
   */
  @Test
  public void testGetParametersList() {
    System.out.println("getParametersList");
    Request instance = new Request();
    List expResult = null;
    List result = instance.getParametersList();
    assertEquals(expResult, result);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

  /**
   * Test of getParametersMappedLists method, of class Request.
   */
  @Test
  public void testGetParametersMappedLists() {
    System.out.println("getParametersMappedLists");
    Request instance = new Request();
    Map<String, List<String>> expResult = null;
    Map<String, List<String>> result = instance.getParametersMappedLists();
    assertEquals(expResult, result);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

  /**
   * Test of parseParameters method, of class Request.
   */
  @Test
  public void testParseParameters() {
    System.out.println("parseParameters");
    Request.Values results = null;
    String params = "";
    Request.parseParameters(results, params, "UTF-8");
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

}
