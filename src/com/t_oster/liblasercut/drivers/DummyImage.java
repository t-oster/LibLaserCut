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

import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.platform.Util;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * This class implements a dummy driver that accepts laserjobs and prints debug
 * information about them.
 * You can use it to test the VisiCut GUI without having a real lasercutter.
 *
 * @author Max Gaukler <development@maxgaukler.de>, based on the LAOS driver by
 * Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class DummyImage extends LaserCutter
{

  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String SETTING_OUT = "output file";

  public String dummyImage = "dummy.png";

  @Override
  public String getModelName()
  {
    return "Dummy Image";
  }

  @Override
  public LaserProperty getLaserPropertyForRaster3dPart()
  {
    return new FloatPowerSpeedFocusProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
  {
    return new PowerSpeedFocusFrequencyProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new PowerSpeedFocusFrequencyProperty();
  }

  private void writeJob(File file, LaserJob job, ProgressListener pl) throws IllegalJobException, Exception
  {
    int width = (int) (Util.mm2inch(getBedWidth()) * 1000);
    int height = (int) (Util.mm2inch(getBedHeight()) * 1000);
    BufferedImage bout = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    DataBuffer buffer = bout.getRaster().getDataBuffer();
    DataBufferInt ibuffer = (DataBufferInt) buffer;
    int[] array = ibuffer.getData();
    Arrays.fill(array, 0xFFFFFFFF);
    int color = 0xFF000000;
    int x = 0, y = 0;
    checkJob(job);
    job.applyStartPoint();
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterPart)
      {
        RasterPart rp = ((RasterPart) p);
        p = rp.convertToVectorPart(getRasterPadding(), true);
      }
      if (p instanceof Raster3dPart)
      {
        Raster3dPart rp = (Raster3dPart) p;
        RasterBuilder rb = new RasterBuilder(rp.image, new RasterBuilder.PropertiesUpdate()
        {
          @Override
          public void update(AbstractLaserProperty properties, int pixel)
          {
            properties.addProperty("color", pixel);
          }
        }, 0, 255, 97);
        rb.setProgressListener(pl);
        rb.setOffsetPosition(rp.getMinX(), rp.getMinY());
        
        for (VectorCommand cmd : rb) {
          switch (cmd.getType())
          {
            case SETPROPERTY:
              AbstractLaserProperty prop = (AbstractLaserProperty) cmd.getProperty();
              int g = prop.getInteger("color", 0);
              color = 0xff000000 | g << 16 | g << 8 | g;
              continue;
            case LINETO:
              int cx = (int) Math.rint((cmd.getX() * (1000 / p.getDPI())));
              int cy = (int) Math.rint((cmd.getY() * (1000 / p.getDPI())));
              try
              {
                //System.out.println(x + " " + y + " > " + cx + " " + cy);
                line(array, width, color, x, y, cx, cy);
              }
              catch (ArrayIndexOutOfBoundsException e)
              {
                //must have drawn somewhere strange.
              }
              x = cx;
              y = cy;
              break;
            default:
              x = (int) Math.rint((cmd.getX() * (1000 / p.getDPI())));
              y = (int) Math.rint((cmd.getY() * (1000 / p.getDPI())));
              break;
          }
        }
      }
      if (p instanceof VectorPart)
      {
        for (VectorCommand cmd : ((VectorPart) p).getCommandList())
        {
          switch (cmd.getType())
          {
            case SETPROPERTY:
              AbstractLaserProperty prop = (AbstractLaserProperty) cmd.getProperty();
              int g = prop.getInteger("color", 0);
              color = 0xff000000 | g << 16 | g << 8 | g;
              continue;
            case LINETO:
              int cx = (int) Math.rint((cmd.getX() * (1000 / p.getDPI())));
              int cy = (int) Math.rint((cmd.getY() * (1000 / p.getDPI())));
              try
              {
                System.out.println(x + " " + y + " > " + cx + " " + cy);
                line(array, width, color, x, y, cx, cy);
              }
              catch (ArrayIndexOutOfBoundsException e)
              {
                //must have drawn somewhere strange.
              }
              x = cx;
              y = cy;
              break;
            default:
              x = (int) Math.rint((cmd.getX() * (1000 / p.getDPI())));
              y = (int) Math.rint((cmd.getY() * (1000 / p.getDPI())));
              break;
          }
        }
      }
    }
    ImageIO.write(bout, "png", file);
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    File file = new File(dummyImage);
    writeJob(file, job, pl);
    pl.progressChanged(this, 100);
    warnings.add("Dummy Image written to " + file.getCanonicalPath());
  }

  @Override
  public void saveJob(File file, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception
  {
    writeJob(new File(dummyImage), job, null);
  }

  @Override
  public int estimateJobDuration(LaserJob job)
  {
    return 0;
  }

  private List<Double> resolutions;

  @Override
  public boolean canEstimateJobDuration()
  {
    return true;
  }

  @Override
  public List<Double> getResolutions()
  {
    if (resolutions == null)
    {
      resolutions = Arrays.asList(new Double[]
      {
        500d
      });
    }
    return resolutions;
  }
  protected double bedWidth = 100;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth()
  {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth)
  {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 140;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight)
  {
    this.bedHeight = bedHeight;
  }

  private static String[] settingAttributes = new String[]
  {
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
  };

  @Override
  public String[] getPropertyKeys()
  {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute)
  {
    if (SETTING_BEDWIDTH.equals(attribute))
    {
      return this.getBedWidth();
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      return this.getBedHeight();
    }
    else if (SETTING_OUT.equals(attribute))
    {
      return this.dummyImage;
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value)
  {
    if (SETTING_BEDWIDTH.equals(attribute))
    {
      this.setBedWidth((Double) value);
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      this.setBedHeight((Double) value);
    }
    else if (SETTING_OUT.equals(attribute))
    {
      this.dummyImage = value.toString();
    }

  }

  @Override
  public LaserCutter clone()
  {
    DummyImage clone = new DummyImage();
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    return clone;
  }

  private void line(int[] array, int width, int color, int x0, int y0, int x1, int y1)
  {
    int dy = y1 - y0; //BRESENHAM LINE DRAW ALGORITHM
    int dx = x1 - x0;

    int stepx, stepy;

    if (dy < 0)
    {
      dy = -dy;
      stepy = -1;
    }
    else
    {
      stepy = 1;
    }

    if (dx < 0)
    {
      dx = -dx;
      stepx = -1;
    }
    else
    {
      stepx = 1;
    }
    if (dx > dy)
    {
      dy <<= 1;                                                  // dy is now 2*dy
      dx <<= 1;
      int fraction = dy - (dx >> 1);                         // same as 2*dy - dx
      array[width * y0 + x0] = color;

      while (x0 != x1)
      {
        if (fraction >= 0)
        {
          y0 += stepy;
          fraction -= dx;                                // same as fraction -= 2*dx
        }
        x0 += stepx;
        fraction += dy;                                    // same as fraction += 2*dy
        array[width * y0 + x0] = color;
      }

    }
    else
    {
      dy <<= 1;                                                  // dy is now 2*dy
      dx <<= 1;                                                  // dx is now 2*dx
      int fraction = dx - (dy >> 1);
      array[width * y0 + x0] = color;
      while (y0 != y1)
      {
        if (fraction >= 0)
        {
          x0 += stepx;
          fraction -= dy;
        }
        y0 += stepy;
        fraction += dx;
        array[width * y0 + x0] = color;
      }

    }
  }
}
