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
import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class IModelaMill extends LaserCutter
{

  private void writeInitializationCode(PrintStream out)
  {
    out.println("%");
    out.println("G90");//absolute positioning
    out.println("G21");//select mm as input unit
  }
  
  private void writeFinalizationCode(PrintStream out)
  {
    out.println("M02");//END_OF_PROGRAM
    out.println("%");
  }
  
  private void writeVectorCode(VectorPart p, PrintStream out)
  {
    double dpi = p.getDPI();
    boolean spindleOn = false;
    for (VectorCommand c : p.getCommandList())
    {
      switch (c.getType())
      {
        case MOVETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = Util.px2mm(c.getY(), dpi);
          if (spindleOn)
          {
            out.println("M05");//stop spindle
            spindleOn = false;
          }
          out.printf("G00 X%f Y%f\n", x, y);
          break;
        }
        case LINETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = Util.px2mm(c.getY(), dpi);
          if (!spindleOn)
          {
            out.println("M03");//start spindle
            spindleOn = true;
          }
          out.printf("G01 X%f Y%f\n", x, y);
          break;
        }
        case SETPROPERTY:
        {
          break;
        }
      }
    }
  }

  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new IModelaProperty();
  }
  
  
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    pl.taskChanged(this, "checking...");
    checkJob(job);
    pl.progressChanged(this, 20);
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    pl.taskChanged(this, "generating...");
    writeInitializationCode(out);
    double all = job.getParts().size();
    int i = 1;
    for (JobPart p : job.getParts())
    {
      if (p instanceof VectorPart)
      {
        pl.progressChanged(this, (int) (20+30*i++/all));
        writeVectorCode((VectorPart) p, out);
      }
    }
    writeFinalizationCode(out);
    pl.progressChanged(this, 50);
    pl.taskChanged(this, "sending...");
    sendGCode(result.toByteArray());
    pl.progressChanged(this, 100);
    pl.taskChanged(this, "done");
    warnings.add("WARNING: THE iModela driver only outputs to command line");
  }

  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]{1000d});
  }

  @Override
  public double getBedWidth()
  {
    return 100;
  }

  @Override
  public double getBedHeight()
  {
    return 100;
  }

  @Override
  public String getModelName()
  {
    return "ROLAND iModela";
  }

  @Override
  public LaserCutter clone()
  {
    IModelaMill result = new IModelaMill();
    for (String k:this.getPropertyKeys())
    {
      result.setProperty(k, this.getProperty(k));
    }
    return result;
  }

  @Override
  public String[] getPropertyKeys()
  {
    return new String[0];
  }

  @Override
  public void setProperty(String key, Object value)
  {
  }

  @Override
  public Object getProperty(String key)
  {
    return null;
  }

  private void sendGCode(byte[] toByteArray)
  {
    try
    {
      System.out.write(toByteArray);
    }
    catch (IOException ex)
    {
      Logger.getLogger(IModelaMill.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

}
