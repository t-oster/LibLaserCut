/**
 * This file is part of VisiCut. Copyright (C) 2012 Thomas Oster
 * <thomas.oster@rwth-aachen.de> RWTH Aachen University - 52062 Aachen, Germany
 *
 * VisiCut is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * VisiCut is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with VisiCut. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.*;
import java.io.BufferedOutputStream;
import java.util.*;
import java.lang.System;

/**
 * This class implements a driver for the LAOS Lasercutter board. Currently it
 * supports the simple code and the G-Code, which may be used in the future.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class Dummy extends LaserCutter {
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  @Override
  public String getModelName() {
    return "Dummy";
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);

    BufferedOutputStream out;
    pl.taskChanged(this, "checking job");
    checkJob(job);
    pl.taskChanged(this, "sending");
    pl.taskChanged(this, "sent.");
    System.out.println("dummy-driver got LaserJob: ");
     for (JobPart p : job.getParts())
        {
          if (p instanceof VectorPart)
          {
            System.out.println("VectorPart");
            for (VectorCommand cmd : ((VectorPart) p).getCommandList())
            {
              if (cmd.getType() == VectorCommand.CmdType.SETPROPERTY)
              {
                if (!(cmd.getProperty() instanceof PowerSpeedFocusFrequencyProperty))
                {
                  throw new IllegalJobException("This driver expects Power,Speed,Frequency and Focus as settings");
                }
                System.out.println(((PowerSpeedFocusFrequencyProperty) cmd.getProperty()).toString());
              }
            }
          }
          if (p instanceof RasterPart)
          {
            RasterPart rp = ((RasterPart) p);
            if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
            {
              throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
            }
            System.out.println(((PowerSpeedFocusProperty) rp.getLaserProperty()).toString());

          }
          if (p instanceof Raster3dPart)
          {
            Raster3dPart rp = (Raster3dPart) p;
            if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
            {
              throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
            }
            System.out.println(((PowerSpeedFocusProperty) rp.getLaserProperty()).toString());
          }
        System.out.println("end of job.");
        pl.progressChanged(this, 100);
      }
  }
  
  @Override
  public int estimateJobDuration(LaserJob job)
  {
      return 1234;
  }
  
  private List<Double> resolutions;
  
  @Override
  public boolean canEstimateJobDuration() {
        return true;
    }

  @Override
  public List<Double> getResolutions() {
    if (resolutions == null) {
      resolutions = Arrays.asList(new Double[]{
                500d
              });
    }
    return resolutions;
  }
  protected double bedWidth = 250;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth() {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth) {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 280;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight() {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight) {
    this.bedHeight = bedHeight;
  }
  private static String[] settingAttributes = new String[]{
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT
  };

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.getBedHeight();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight((Double) value);
    }
  }

  @Override
  public LaserCutter clone() {
    Dummy clone = new Dummy();
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    return clone;
  }
}
