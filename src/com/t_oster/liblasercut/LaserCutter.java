/**
 * This file is part of VisiCut.
 * Copyright (C) 2011 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 * 
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *    VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.t_oster.liblasercut;

import com.t_oster.liblasercut.platform.Util;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public abstract class LaserCutter implements Cloneable
{

  /**
   * Checks the given job. It throws exceptions if
   * - job size is bigger than laser bed size
   * - job resolution is not supported
   * This method is supposed to be used (in addition of own sanity checks)
   * as a sanity check inside the sendJob mehtod
   * 
   * @param job
   * @throws IllegalJobException 
   */
  protected void checkJob(LaserJob job) throws IllegalJobException
  {
    boolean pass = false;
    for (int i : this.getResolutions())
    {
      if (i == job.getResolution())
      {
        pass = true;
        break;
      }
    }
    if (!pass)
    {
      throw new IllegalJobException("Resoluiton of " + job.getResolution() + " is not supported");
    }
    if (job.containsVector())
    {
      double w = Util.px2mm(job.getVectorPart().getWidth(), job.getResolution());
      double h = Util.px2mm(job.getVectorPart().getHeight(), job.getResolution());

      if (w > this.getBedWidth() || h > this.getBedHeight())
      {
        throw new IllegalJobException("The Job is too big (" + w + "x" + h + ") for the Laser bed (" + this.getBedHeight() + "x" + this.getBedHeight() + ")");
      }
    }
    if (job.containsRaster())
    {
      double w = Util.px2mm(job.getRasterPart().getWidth(), job.getResolution());
      double h = Util.px2mm(job.getRasterPart().getHeight(), job.getResolution());

      if (w > this.getBedWidth() || h > this.getBedHeight())
      {
        throw new IllegalJobException("The Job is too big (" + w + "mm x" + h + "mm) for the Laser bed (" + this.getBedWidth() + "mm x" + this.getBedHeight() + "mm)");
      }
    }
    if (job.contains3dRaster())
    {
      double w = Util.px2mm(job.getRaster3dPart().getWidth(), job.getResolution());
      double h = Util.px2mm(job.getRaster3dPart().getHeight(), job.getResolution());

      if (w > this.getBedWidth() || h > this.getBedHeight())
      {
        throw new IllegalJobException("The Job is too big (" + w + "x" + h + ") for the Laser bed (" + this.getBedHeight() + "x" + this.getBedHeight() + ")");
      }
    }
  }
  
  /**
   * Performs sanity checks on the LaserJob and sends it to the Cutter
   * @param job
   * @param pl A ProgressListener to give feedback about the progress
   * @throws IllegalJobException if the Job didn't pass the SanityCheck
   * @throws Exception  if there is a Problem with the Communication or Queue
   */
  public abstract void sendJob(LaserJob job, ProgressListener pl) throws IllegalJobException, Exception;

  /**
   * If you lasercutter supports autofocus, override this method,
   * to let programs like VisiCut know, that they don't need to focus.
   * @return 
   */
  public boolean isAutoFocus()
  {
    return false;
  }
  
  /**
   * This calls sendJob(job, pl) with a default progress listener, which
   * just dumps everythong on the command line
   * @param job
   * @throws IllegalJobException
   * @throws Exception 
   */
  public void sendJob(LaserJob job) throws IllegalJobException, Exception
  {
    this.sendJob(job, new ProgressListener(){
      @Override
      public void progressChanged(Object source, int percent)
      {
        System.out.println(""+percent+"%");
      }
      @Override
      public void taskChanged(Object source, String taskName)
      {
        System.out.println(taskName+"...");
      }  
    });
  }
  
  /**
   * Returns the available Resolutions in DPI
   * @return 
   */
  public abstract List<Integer> getResolutions();

  /**
   * Returns the Maximum width of a LaserJob in mm
   * @return 
   */
  public abstract double getBedWidth();

  /**
   * Returns the Maximum height of a LaserJob in mm
   * @return 
   */
  public abstract double getBedHeight();
  
  /**
   * Returns a List of Attributes, needed for 
   * configuring the Lasercutter (eg. IP, Port...)
   * @return 
   */
  public abstract List<String> getSettingAttributes();
  
  /**
   * Returns the <value> of the setting <attribute>
   * @param attribute
   * @return 
   */
  public abstract String getSettingValue(String attribute);
  
  /**
   * Sets the setting named <attribute> to <value>
   * @param attribute
   * @param value 
   */
  public abstract void setSettingValue(String attribute, String value);
  
  /**
   * Returns an estimated time, how long the job would take
   * in seconds
   * @param job
   * @return 
   */
  public abstract int estimateJobDuration(LaserJob job);
  
  public abstract String getModelName();
  
  @Override
  public abstract LaserCutter clone();
}
