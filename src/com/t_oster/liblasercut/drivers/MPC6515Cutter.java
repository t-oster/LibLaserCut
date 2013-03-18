/**
 * This file is part of VisiCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.Raster3dPart;
import com.t_oster.liblasercut.RasterPart;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class MPC6515Cutter extends LaserCutter
{

  private static Double[] SUPPORTED_RESOLUTIONS = new Double[]{200d,500d,1000d};
  private static final String BED_WIDTH = "bed width";
  private static final String BED_HEIGHT = "bed height";
  private static String[] PROPERTIES = new String[]{
    BED_WIDTH,
    BED_HEIGHT,
  };
 
  private double bedWidth = 300;
  private double bedHeight = 210;
  
  @Override
  public double getBedWidth()
  {
    return bedWidth;
  }

  public void setBedWidth(double bedWidth)
  {
    this.bedWidth = bedWidth;
  }

  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  public void setBedHeight(double bedHeight)
  {
    this.bedHeight = bedHeight;
  }

  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    pl.taskChanged(this, "checking...");
    checkJob(job);
    pl.progressChanged(this, 20);
    pl.taskChanged(this, "buffering...");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    BufferedOutputStream out = new BufferedOutputStream(buffer);
    //TODO: write MOL header to out
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterPart)
      {
        throw new IllegalJobException("The MPC6515 currently does not support engraving");
      }
      else if (p instanceof Raster3dPart)
      {
        throw new IllegalJobException("The MPC6515 currently does not support 3d engraving");
      }
      if (p instanceof VectorPart)
      {
        double resolution = p.getDPI();
        for (VectorCommand c : ((VectorPart) p).getCommandList())
        {
          switch (c.getType())
          {
            case LINETO:
            {
              //TODO: WRITE IN MOL FORMAT
              out.write(10);
              out.write(c.getX());//x in dots (relative to resolution)
              out.write(c.getY());//y in dots (relative to resolution)
              break;
            }
            case MOVETO:
            {
              //TODO: WRITE IN MOL FORMAT
              out.write(20);
              out.write(c.getX());//x in dots (relative to resolution)
              out.write(c.getY());//y in dots (relative to resolution)
              break;
            }
            case SETPROPERTY:
            {
              //TODO: WRITE IN MOL FORMAT
              PowerSpeedFocusFrequencyProperty prop = (PowerSpeedFocusFrequencyProperty) c.getProperty();
              out.write(10);
              out.write(prop.getPower());//power in 0-100
              out.write(prop.getSpeed());//speed in 0-100
              out.write((int) prop.getFocus());//focus in mm
              out.write(prop.getFrequency());//frequency in Hz
              break;
            }
          }
        }
      }
    }
    //TODO: write MOL footer to out
    pl.progressChanged(this, 80);
    pl.taskChanged(this, "sending...");
    //TODO: contents of buffer.toByteArray() to laser-cutter
    pl.progressChanged(this, 100);
  }

  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(SUPPORTED_RESOLUTIONS);
  }

  @Override
  public String getModelName()
  {
    return "MPC6515";
  }

  @Override
  public LaserCutter clone()
  {
    MPC6515Cutter o = new MPC6515Cutter();
    for (String k : this.getPropertyKeys())
    {
      o.setProperty(k, this.getProperty(k));
    }
    return o;
  }

  @Override
  public String[] getPropertyKeys()
  {
    return PROPERTIES;
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (BED_WIDTH.equals(key))
    {
      setBedWidth((Double) value);
    }
    else if (BED_HEIGHT.equals(key))
    {
      setBedHeight((Double) value);
    }
    else
    {
      System.err.println("ERROR: Unknown property '"+key+"' for MPC6151 driver");
    }
  }

  @Override
  public Object getProperty(String key)
  {
    if (BED_WIDTH.equals(key))
    {
      return getBedWidth();
    }
    else if (BED_HEIGHT.equals(key))
    {
      return getBedHeight();
    }
    else
    {
      System.err.println("ERROR: Unknown property '"+key+"' for MPC6151 driver");
    }
    return null;
  }

}
