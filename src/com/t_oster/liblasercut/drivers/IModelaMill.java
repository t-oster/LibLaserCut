/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 **/

package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.Raster3dPart;
import com.t_oster.liblasercut.RasterPart;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Point;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 * 
 * 
 * some technical details about the iModela IM-01:
 * max. part dimensions (x,y,z): (86mm, 55mm, 26mm)
 * operating speed x and y axis: 6 to 240 mm/min
 * operating speed z axis: 6 to 180 mm/min
 * software resolution: 0.001mm/step (in NC-code mode), 0.01mm/step (RML-1 mode)
 * mechanical resolution: 0.000186mm/step
 * 
 * This driver controls the mill using NC codes.
 * Reference: http://icreate.rolanddg.com/iModela/download/dl/manual/NC_CODE_EN.pdf
 * 
 * Currently, this driver just engraves/cuts material in 2D. 2.5D data is not supported by VisiCut (yet).
 * 
 * 
 */
public class IModelaMill extends LaserCutter
{

  private static String HOSTNAME = "Hostname/IP";
  private static String PORT = "port";
  private static String BED_WIDTH = "bed width";
  private static String BED_HEIGHT = "bed height";
  private static String FLIP_YAXIS = "flip y axis";
  private static String HOME_ON_END = "move home after job";

  private Map<String, Object> properties = new LinkedHashMap<String, Object>();
  public IModelaMill()
  {
    properties.put(BED_WIDTH, (Double) 85d);
    properties.put(BED_HEIGHT, (Double) 55d);
    properties.put(HOSTNAME, "file:///dev/usb/lp0");
    properties.put(PORT, (Integer) 5000);
    properties.put(HOME_ON_END, (Boolean) true);
    properties.put(FLIP_YAXIS, (Boolean) false);
  }
  
  private boolean spindleOn = false;
  private void setSpindleOn(PrintStream out, boolean spindleOn)
  {
    if (spindleOn != this.spindleOn)
    {
      this.spindleOn = spindleOn;
      out.println(spindleOn ? "M03" : "M05");//start/stop spindle
    }
  }
  
  private void writeInitializationCode(PrintStream out)
  {
    out.println("%");
    out.println("O00000001");//program number 00000001 - can be changed to any number, must be 8 digits
    out.println("G90");//absolute positioning
    out.println("G21");//select mm as input unit
  }
  
  private void writeFinalizationCode(PrintStream out)
  {
    this.setSpindleOn(out, false);
    out.println("G0 Z0");//head up
    if ((Boolean) properties.get(HOME_ON_END))
    {
      out.println("G0 X0 Y0");//go back to home
    }
    out.println("M02");//END_OF_PROGRAM
    out.println("%");
  }
  
  //all depth values are positive, 0 is top
  private double movedepth = 0;
  private double linedepth = 0;
  private double headdepth = 0;
  private double spindleSpeed = 0;
  private double feedRate = 0;
  private int tool = 0;
  //is applied to next G command
  private String parameters = "";
  
  private void moveHead(PrintStream out, double depth)
  {
    if (headdepth > depth)
    {//move up fast
      out.println(String.format(Locale.ENGLISH, "G00 Z%f%s\n", -depth, parameters));
      parameters = "";
    }
    else if (headdepth < depth)
    {//move down slow
      out.println(String.format(Locale.ENGLISH, "G01 Z%f%s\n", -depth, parameters));
      parameters = "";
    }
    headdepth = depth;
  }
  
  private void move(PrintStream out, double x, double y)
  {
    moveHead(out, movedepth);
    //TODO: check if last command was also move and lies on the 
    //same line. If so, replace the last move command
    out.print(String.format(Locale.ENGLISH, "G00 X%f Y%f%s\n", x, properties.get(FLIP_YAXIS) == Boolean.TRUE ? getBedHeight()-y : y, parameters));
    parameters = "";
  }
  
  private void line(PrintStream out, double x, double y)
  {
    setSpindleOn(out, true);
    moveHead(out, linedepth);
    //TODO: check if last command was also line and lies on the 
    //same line. If so, replace the last move command
    out.print(String.format(Locale.ENGLISH, "G01 X%f Y%f%s\n", x, properties.get(FLIP_YAXIS) == Boolean.TRUE ? getBedHeight()-y : y, parameters));
    parameters = "";
  }
  
  private void applyProperty(PrintStream out, IModelaProperty pr)
  {
    linedepth = pr.getDepth();
    if (pr.getSpindleSpeed() != spindleSpeed)
    {
      spindleSpeed = pr.getSpindleSpeed();
      parameters += String.format(Locale.ENGLISH, " S%f\n", spindleSpeed);
    }
    if (pr.getFeedRate() != feedRate)
    {
      feedRate = pr.getFeedRate();
      parameters += String.format(Locale.ENGLISH, " F%f\n", feedRate);
    }
    if (pr.getTool() != tool)
    {
      tool = pr.getTool();
      //TODO: Maybe stop spindle and move to some location?
      out.print(String.format(Locale.ENGLISH, "M06T0\n"));//return current tool
      out.print(String.format(Locale.ENGLISH, "M06T%d\n", tool));
    }
  }
  
  /*
   * Returns the percentage of black pixels in a square rectangle with
   * side length toolDiameter
   * arount x/y in the given raster
   */
  private double getBlackPercent(RasterPart p, int cx, int cy, int toolDiameter)
  {
    double count = toolDiameter*toolDiameter;
    double black = 0;
    for (int x = Math.max(cx-toolDiameter/2, 0); x < Math.min(cx+toolDiameter/2, p.getRasterWidth()); x++)
    {
      for (int y = Math.max(cy-toolDiameter/2, 0); y < Math.min(cy+toolDiameter/2, p.getRasterHeight()); y++)
      {
        if (p.isBlack(x, y))
        {
          black++;
        }
      }
    }
    return black/count;
  }
  
  private double getAverageGrey(Raster3dPart p, int cx, int cy, int toolDiameter)
  {
    double count = toolDiameter*toolDiameter;
    double value = 0;
    for (int y = Math.max(cy-toolDiameter/2, 0); y < Math.min(cy+toolDiameter/2, p.getRasterHeight()); y++)
    {
      List<Byte> line = p.getRasterLine(y);
      for (int x = Math.max(cx-toolDiameter/2, 0); x < Math.min(cx+toolDiameter/2, p.getRasterWidth()); x++)
      {
      
        value += line.get(x);
      }
    }
    return (value/count)/255;
  }
  
  private void writeRasterCode(RasterPart p, PrintStream out)
  {
    double dpi = p.getDPI();
    //how many pixels(%) have to be black until we move the head down
    double treshold = 0.7;
    IModelaProperty prop = (IModelaProperty) p.getLaserProperty();
    int toolDiameterInPx = (int) Util.mm2px(prop.getToolDiameter(), dpi);
    applyProperty(out, prop);
    boolean leftToRight = true;
    Point offset = p.getRasterStart();
    move(out, Util.px2mm(offset.x, dpi), Util.px2mm(offset.y, dpi));
    for (int y = 0; y < p.getRasterHeight(); y+= toolDiameterInPx/2)
    {
      for (int x = leftToRight ? 0 : p.getRasterWidth() - 1; 
        (leftToRight && x < p.getRasterWidth()) || (!leftToRight && x >= 0); 
        x += leftToRight ? 1 : -1)
      {
        if (getBlackPercent(p, x, y, toolDiameterInPx)<treshold)
        {
          //skip intermediate move commands
          while((leftToRight && x+1 < p.getRasterWidth()) || (!leftToRight && x-1 >= 0) && getBlackPercent(p, leftToRight ? x+1 : x-1, y, toolDiameterInPx) < treshold)
          {
            x+= leftToRight ? 1 : -1;
          }
          move(out, Util.px2mm(offset.x+x, dpi), Util.px2mm(offset.y+y, dpi));
        }
        else
        {
          //skip intermediate line commands
          while((leftToRight && x+1 < p.getRasterWidth()) || (!leftToRight && x-1 >= 0) && getBlackPercent(p, leftToRight ? x+1 : x-1, y, toolDiameterInPx) >= treshold)
          {
            x+= leftToRight ? 1 : -1;
          }
          line(out, Util.px2mm(offset.x+x, dpi), Util.px2mm(offset.y+y, dpi));
        }
      }
      //invert direction
      leftToRight = !leftToRight;
    }
  }
  
  private void writeRaster3dCode(Raster3dPart p, PrintStream out)
  {
    double dpi = p.getDPI();
    IModelaProperty prop = (IModelaProperty) p.getLaserProperty();
    int toolDiameterInPx = (int) Util.mm2px(prop.getToolDiameter(), dpi);
    applyProperty(out, prop);
    boolean leftToRight = true;
    Point offset = p.getRasterStart();
    move(out, Util.px2mm(offset.x, dpi), Util.px2mm(offset.y, dpi));
    for (int y = 0; y < p.getRasterHeight(); y+= toolDiameterInPx/2)
    {
      for (int x = leftToRight ? 0 : p.getRasterWidth() - 1; 
        (leftToRight && x < p.getRasterWidth()) || (!leftToRight && x >= 0); 
        x += leftToRight ? 1 : -1)
      {
        //scale the depth according to the average grey value
        linedepth = getAverageGrey(p, x, y, toolDiameterInPx)*prop.getDepth();
        //skip intermediate line commands
        while((leftToRight && x+1 < p.getRasterWidth()) || (!leftToRight && x-1 >= 0) && getAverageGrey(p, leftToRight ? x+1 : x-1, y, toolDiameterInPx) == linedepth)
        {
          x+= leftToRight ? 1 : -1;
        }
        line(out, Util.px2mm(offset.x+x, dpi), Util.px2mm(offset.y+y, dpi));
      }
      //invert direction
      leftToRight = !leftToRight;
    }
  }
  
  private void writeVectorCode(VectorPart p, PrintStream out)
  {
    double dpi = p.getDPI();
    for (VectorCommand c : p.getCommandList())
    {
      switch (c.getType())
      {
        case MOVETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = getBedHeight() - Util.px2mm(c.getY(), dpi); //mill origin is bottom left, so we have to mirror y coordinates
          move(out, x, y);
          break;
        }
        case LINETO:
        {
          double x = Util.px2mm(c.getX(), dpi);
          double y = getBedHeight() - Util.px2mm(c.getY(), dpi); //mill origin is bottom left, so we have to mirror y coordinates
          line(out, x, y);
          break;
        }
        case SETPROPERTY:
        {
          IModelaProperty pr = (IModelaProperty) c.getProperty();
          applyProperty(out, pr);
          break;
        }
      }
    }
  }

  @Override
  public LaserProperty getLaserPropertyForRaster3dPart()
  {
    return new IModelaProperty();
  }
  
  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new IModelaProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
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
        writeVectorCode((VectorPart) p, out);
      }
      else if (p instanceof RasterPart)
      {
        writeRasterCode((RasterPart) p, out);
      }
      else if (p instanceof Raster3dPart)
      {
        writeRaster3dCode((Raster3dPart) p, out);
      }
      pl.progressChanged(this, (int) (20+30*i++/all));
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
    // software resolution in NC-Code mode: 0.001mm/step = 0.000036 inches/step
    // means 1000 steps per mm
   return Arrays.asList(new Double[]{100d, 200d, 300d, 400d, 500d, 1000d, 1200d, Util.dpmm2dpi(1000d)});
  }

  @Override
  public double getBedWidth()
  {
    if (properties.get(BED_WIDTH) == null)
    {
      properties.put(BED_WIDTH, (Double) 85d);
    }
    return (Double) properties.get(BED_WIDTH);
  }

  @Override
  public double getBedHeight()
  {
    if (properties.get(BED_HEIGHT) == null)
    {
      properties.put(BED_HEIGHT, (Double) 55d);
    }
    return (Double) properties.get(BED_HEIGHT);
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
    return properties.keySet().toArray(new String[0]);
  }

  @Override
  public void setProperty(String key, Object value)
  {
    properties.put(key, value);
  }

  @Override
  public Object getProperty(String key)
  {
    return properties.get(key);
  }

  private void sendGCode(byte[] gcode, ProgressListener pl, List<String> warnings) throws IOException, URISyntaxException
  {
    String hostname = (String) properties.get(HOSTNAME);
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
      w.close();
    }
    else if (hostname.startsWith("printer://"))
    {
        String printername = hostname.substring(10);
        try
        {
            File tempFile = File.createTempFile(printername, ".txt");
            PrintStream w = new PrintStream(new FileOutputStream(tempFile));
            pl.taskChanged(this, "sending...");
            
            w.write(gcode);
            System.out.println("tempFile: "+ tempFile.getAbsolutePath());
            
            Runtime.getRuntime().exec("/usr/bin/lp -d "+printername+" "+tempFile.getAbsolutePath());
        }
        catch(IOException ex)
        {
            System.err.println("Cannot create temp file: " + ex.getMessage());
            
        }
        
    }
    else
    {
      Socket s = new Socket();
      s.connect(new InetSocketAddress(hostname, (Integer) properties.get(PORT)), 3000);
      pl.taskChanged(this, "sending...");
      s.getOutputStream().write(gcode);
      s.close();
    }
  }

}
