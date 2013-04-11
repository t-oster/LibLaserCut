/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
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
 */
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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class MPC6515Cutter extends LaserCutter
{

  private static Double[] SUPPORTED_RESOLUTIONS = new Double[]
  {
    200d, 500d, 1000d
  };
  private static final String BED_WIDTH = "bed width";
  private static final String BED_HEIGHT = "bed height";
  private static String[] PROPERTIES = new String[]
  {
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

  private void molWriteHeader(RandomAccessFile out) throws IOException
  {
    out.seek(0);
    // 0: Size of entire file in bytes (must be patched later)
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 4: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000022));
    // 8: Unknown, fixed value
    out.write(0x02);
    // 9: Unknown, fixed value
    out.write(0x02);
    // a: Unknown, fixed value
    out.write(0x01);
    // b: Unknown, fixed value
    out.write(0x04);
    // c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000001));
    // 10: This word changes when the size of the file changes, i.e. when lines are added
    // This line corresponds to the number of "move relative" commands in the entire file counting
    // "unknown 07" and "unknown 09" as well. Is that coincidence? What would this value be good for?
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 14: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000001));
    // 18: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 1c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 20: Describing the stepper resolution in x and y
    out.writeInt(Integer.reverseBytes(-20833));
    out.writeInt(Integer.reverseBytes(-20833));
    // 28: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00481095));
    // 2c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x471a0000));
    // Unknown bunch of zeros, likely reserved for later use
    out.seek(0x00000070);
    // 70: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(1));
    // 74: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(2));
    // 78: Unknown, fixed value, maybe the number of blocks in this file?
    out.writeInt(Integer.reverseBytes(3));
    // 7c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(5));
  }

  private void molWriteFloat(RandomAccessFile out, float v) throws IOException
  {
    // LEETRO float: [eeeeeeee|smmmmmmm|mmmmmmm0|00000000]
    //     IEEE 754: [seeeeeee|emmmmmmm|mmmmmmmm|mmmmmmmm]
    int i = Float.floatToRawIntBits(v);

    int ieeeMantissa = (i & 0x7fffff);
    int ieeeExponent = ((i >> 23) & 0xff);
    int ieeeSign = ((i >> 31) & 1);

    int c30Mantissa = ieeeMantissa;
    int c30Exponent = (ieeeExponent == 0) ? -128 : ieeeExponent - 127;
    int c30Sign = ieeeSign; // ??? float = -float

    i = (c30Mantissa & 0x7fffff) | ((c30Sign & 1) << 23) | ((c30Exponent & 0xff) << 24);

    out.writeInt(Integer.reverseBytes(i));
  }

  // unkonwn command 01
  private void molCmd01(RandomAccessFile out, int a, float b) throws IOException
  {
    out.write(8);
    out.write(1);
    out.write(0x60);
    out.write(0x80);
    out.writeInt(Integer.reverseBytes(2));
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
  }

  // unkonwn command 01
  private void molCmd01(RandomAccessFile out, int a, float b, float c, float d, float e, float f) throws IOException
  {
    out.write(8);
    out.write(1);
    out.write(0x60);
    out.write(0x80);
    out.writeInt(Integer.reverseBytes(6));
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
    molWriteFloat(out, c);
    molWriteFloat(out, d);
    molWriteFloat(out, e);
    molWriteFloat(out, f);
  }

  // unkonwn command 05
  private void molCmd05(RandomAccessFile out) throws IOException
  {
    out.write(8);
    out.write(5);
    out.write(0x20);
    out.write(0);
  }

  // unkonwn command 07
  private void molCmd07(RandomAccessFile out) throws IOException
  {
    out.write(8);
    out.write(7);
    out.write(0x20);
    out.write(0);
  }

  // unkonwn command 08
  private void molCmd08(RandomAccessFile out) throws IOException
  {
    out.write(8);
    out.write(8);
    out.write(0x20);
    out.write(0);
  }

  // unkonwn command 0e
  private void molCmd0e(RandomAccessFile out, float a, float b, float c) throws IOException
  {
    out.write(6);
    out.write(0x0e);
    out.write(0);
    out.write(3);
    molWriteFloat(out, a);
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }

  private void molWriteConfig(RandomAccessFile out) throws IOException
  {
    //The configuration chunk still has a bunch of unknown commands, few of them seem to change
    //for a single machine configuration.  
    out.seek(0x200);

    // 200: Unknown, fixed value
    molCmd05(out);
    // 204: Unknown, fixed value, the default driver for the X-axis is #4 (maybe related?)
    molCmd01(out, 4, 4.0f);
    // 214: Unknown, fixed value, the default driver for the Y-axis is #3 (maybe related?)
    molCmd01(out, 3, 3.0f);
    // 224: Unknown, fixed value
    molCmd08(out);
    // 228: Unknown, fixed value
    molCmd07(out);
    // 22c: Unknown
    // arg1 is unknown, but repeats in 'unknown 11'
    // arg2 is the start speed for all head movements as describen in the settings
    // arg3 is the maximum speed for moving around "quickly"
    // arg4 is the acceleration value to get to the above speed (space acc)
    // arg5 is the value for acceleration from the settings
    // arg6 is unknown and nowhere to be found, probably the slow acceleration default
    molCmd01(out, 603, 5.0f, 200.0f, 700.0f, 500.0f, 350.0f);
    // 24c: Set the Stepper sizes
    // arg1 is the number of steps required in X direction
    // arg2 is the same for Y
    // arg3 is likely the same in Z direction
    molCmd0e(out, 208.333f, 208.333f, 800.0f);
    /*
     Unknown, fixed value, maybe explicitly switching the laser off?
     0000025c: : unknown 06:  0

     Object origin. Cutting a 100x100 object on a 900x600 table would move the laser head
     to the top right corner of a centered work piece. (772 see "move rel")
     00000264: : unknown 07: 772, x:500.005mm, y:350.002mm

     Motion parameters:
     arg1 is the initial speed
     arg2 is the maximum speed 
     arg3 is the acceleration
     00000274: : unknown 08 a:1041(5%), b:41666(200%), c:145833(700%)

     Object size. Our test object is 100x100, so this command moves to the bottom left corner.
     00000284: : unknown 09: 772, x:-100mm, y:-100mm

     Unknown, fixed value
     00000294: : unknown 10:  3, 2.000000, 2.000000

     Unknown, fixed value, 603 also appears at 0x0000022c
     000002a8: : unknown 11:  603

     Unknown, fixed value
     000002b4: : unknown 12

     */
  }

  private void molFixHeader(RandomAccessFile out) throws IOException
  {
    // Size of entire file in bytes (must be patched later)
    out.seek(0x00000000);
    out.writeInt(Integer.reverseBytes((int) out.length()));
    // 10: This word changes when the size of the file changes, i.e. when lines are added
    // This line corresponds to the number of "move relative" commands in the entire file counting
    // "unknown 07" and "unknown 09" as well. Is that coincidence? What would this value be good for?
    out.seek(0x00000010);
    out.writeInt(Integer.reverseBytes(0x00000000/*FIXME*/));
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    // This is a first try in Java. All data is saved into ~/RUN.MOL until we figure out the USB communication

    // - delete the file
    try
    {
      File f = new File("/Users/matt/RUN.MOL");
      if (f.exists())
      {
        f.delete();
      }
    }
    catch (Exception e)
    {
    }

    // - create a random access file (some data can only be patched after the entire file was written)
    File file;
    file = new File("/Users/matt/RUN.MOL");
    RandomAccessFile out = new RandomAccessFile(file, "rw");

    // - write the standard header
    molWriteHeader(out);

    // - write the configuration block
    molWriteConfig(out);

    /*
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
     */

    // - fix all positions that require patching
    molFixHeader(out);

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
      System.err.println("ERROR: Unknown property '" + key + "' for MPC6151 driver");
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
      System.err.println("ERROR: Unknown property '" + key + "' for MPC6151 driver");
    }
    return null;
  }
}