/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     LibLaserCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LibLaserCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with LibLaserCut.  If not, see <http://www.gnu.org/licenses/>.
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class IModelaMill extends LaserCutter
{

  private static String HOSTNAME = "Hostname/IP";
  private static String PORT = "port";
  
  private String hostname = "localhost";
  private int port = 5000;
  
  private void writeInitializationCode(PrintStream out)
  {
    out.println("%");
    out.println("G90");//absolute positioning
    out.println("G21");//select mm as input unit
    out.println("M03");//start spindle
  }
  
  private void writeFinalizationCode(PrintStream out)
  {
    out.println("M05");//stop spindle
    out.println("M02");//END_OF_PROGRAM
    out.println("%");
  }
  
  private void writeVectorCode(VectorPart p, PrintStream out)
  {
    double dpi = p.getDPI();
    boolean headDown = false;
    double olddepth = 0;
    double depth = 0;
    int spindleSpeed = -1;
    double feedRate = -1;
    int tool = -1;
    for (VectorCommand c : p.getCommandList())
    {
      switch (c.getType())
      {
        case MOVETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = Util.px2mm(c.getY(), dpi);
          if (headDown)
          {
            out.println("G00 Z0");
            headDown = false;
          }
          out.print(String.format(Locale.ENGLISH, "G00 X%f Y%f\n", x, y));
          break;
        }
        case LINETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = Util.px2mm(c.getY(), dpi);
          if (!headDown || depth != olddepth)
          {
            out.print(String.format(Locale.ENGLISH, "G01 Z%f\n", depth));
            headDown = true;
            olddepth = depth;
          }
          out.print(String.format(Locale.ENGLISH, "G01 X%f Y%f\n", x, y));
          break;
        }
        case SETPROPERTY:
        {
          IModelaProperty pr = (IModelaProperty) c.getProperty();
          depth = pr.getDepth();
          if (pr.getSpindleSpeed() != spindleSpeed)
          {
            spindleSpeed = pr.getSpindleSpeed();
            out.print(String.format(Locale.ENGLISH, "S%d\n", spindleSpeed));
          }
          if (pr.getFeedRate() != feedRate)
          {
            feedRate = pr.getFeedRate();
            out.print(String.format(Locale.ENGLISH, "F%f\n", feedRate));
          }
          if (pr.getTool() != tool)
          {
            tool = pr.getTool();
            out.print(String.format(Locale.ENGLISH, "M06T0\n"));//return current tool
            out.print(String.format(Locale.ENGLISH, "M06T%d\n", tool));
          }
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
    sendGCode(result.toByteArray(), pl, warnings);
    pl.progressChanged(this, 100);
    pl.taskChanged(this, "done");
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
    return new String[]{HOSTNAME, PORT};
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (HOSTNAME.equals(key))
    {
      hostname = (String) value;
    }
    else if (PORT.equals(key))
    {
      port = (Integer) value;
    }
  }

  @Override
  public Object getProperty(String key)
  {
    if (HOSTNAME.equals(key))
    {
      return hostname;
    }
    else if (PORT.equals(key))
    {
      return (Integer) port;
    }
    return null;
  }

  private void sendGCode(byte[] gcode, ProgressListener pl, List<String> warnings) throws IOException, URISyntaxException
  {
    pl.taskChanged(this, "connecting...");
    if ("stdout".equals(hostname))
    {
      pl.taskChanged(this, "sending...");
      System.out.write(gcode);
    }
    else if (hostname.startsWith("file://"))
    {
      PrintStream w = new PrintStream(new FileOutputStream(new File(new URI(hostname))));
      pl.taskChanged(this, "sending...");
      w.write(gcode);
    }
    else
    {
      Socket s = new Socket();
      s.connect(new InetSocketAddress(hostname, port), 3000);
      pl.taskChanged(this, "sending...");
      s.getOutputStream().write(gcode);
      s.close();
    }
  }

}
