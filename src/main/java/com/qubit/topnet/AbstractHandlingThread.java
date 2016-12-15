/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qubit.topnet;

import java.util.List;

/**
 *
 * @author piotr
 */
public abstract class AbstractHandlingThread extends Thread {

  abstract public long getJobsAdded();

  abstract public long getJobsRemoved();

  abstract public List<DataHandler> getValidJobs();

}
