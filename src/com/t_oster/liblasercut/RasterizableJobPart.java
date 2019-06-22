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

package com.t_oster.liblasercut;

import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Util;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Common functions useful when rasterizing an image.
 *
 * @author Michael Adams <zap@michaeladams.org>
 */
abstract public class RasterizableJobPart extends JobPart
{

  public static final int X_AXIS = 0;
  public static final int Y_AXIS = 1;

  public RasterElement image;
  protected LaserProperty property = null;

  protected Point start = null;
  protected double resolution = Double.NaN;

  public RasterizableJobPart(RasterElement image, LaserProperty laserProperty, Point offset, double resolution)
  {
    this.image = image;
    this.resolution = resolution;
    this.property = laserProperty;
    this.start = offset;
  }

  @Override
  public double getDPI()
  {
    return resolution;
  }

  /**
   * The initial laser settings to start a rasterization job with.
   *
   * @return LaserProperty
   */
  public LaserProperty getProperty()
  {
    return property;
  }

  /**
   * The initial laser settings to start a rasterization job with.
   *
   * @return LaserProperty
   */
  public LaserProperty getLaserProperty()
  {
    return property;
  }

  /**
   * Gets the height of the associated raster image
   *
   * @return height in pixels
   */
  public int getRasterHeight()
  {
    return this.image.getHeight();
  }

  /**
   * Returns one line of the given rasterpart every byte represents
   * 8/getBitsPerRasterPixel() pixels and the value corresponds to
   * (2^getBitsPerRasterPixel() - 1) when black or 0 when white
   *
   * @param line
   * @return
   */
  public List<Byte> getRasterLine(int line)
  {
    byte[] array = image.getRasterLine(line, null);
    Byte[] bytearray = new Byte[array.length];
    for (int i = 0, ie = array.length; i < ie; i++)
    {
      bytearray[i] = (byte) (array[i] & 0xFF);
    }
    return Arrays.asList(bytearray);
  }

  /**
   * write output of getRasterLine(line) into the given result list
   *
   * @param line
   * @param result
   */
  public void getRasterLine(int line, List<Byte> result)
  {
    byte[] array = image.getRasterLine(line, null);
    for (int i = 0, ie = array.length; i < ie; i++)
    {
      result.set(i, (byte) (array[i] & 0xFF));
    }
  }

  public List<Byte> getInvertedRasterLine(int line)
  {
    List<Byte> rasterLine = getRasterLine(line);
    Collections.reverse(rasterLine);
    return rasterLine;
  }

  public void getInvertedRasterLine(int line, List<Byte> result)
  {
    getRasterLine(line, result);
    Collections.reverse(result);
  }

  /**
   * bits for one pixel in getRasterLine() output
   *
   * @return image bit depth.
   */
  public int getBitsPerRasterPixel()
  {
    return image.getBitDepth();
  }

  private boolean inrange_y(int y)
  {
    return y < getRasterHeight() && y >= 0;
  }

  private boolean inrange_x(int x)
  {
    return x < getRasterWidth() && x >= 0;
  }

  public VectorPart convertToVectorPart3D(LaserProperty.Provider provider, double padding, boolean bidirectional)
  {
    return RasterizableJobPart.this.convertToVectorPart(provider, X_AXIS, padding, bidirectional, true, 255, false, false, false);
  }

  public VectorPart convertToVectorPart(double padding, boolean bidirectional)
  {
    return RasterizableJobPart.this.convertToVectorPart(null, X_AXIS, padding, bidirectional, true, 0, false, false, false);
  }

  public VectorPart convertToVectorPart(LaserProperty.Provider provider, double padding, boolean bidirectional)
  {
    return RasterizableJobPart.this.convertToVectorPart(provider, X_AXIS, padding, bidirectional, true, 0, false, false, false);
  }

  /**
   * Converts a raster image (B&W or greyscale) into a series of vector
   * instructions suitable for printing. Lets non-raster-native cutters emulate
   * this functionality.
   *
   * @param provider provides laser properties for adding to the vector part.
   * @param axis gives the axis to use. 0 = Horizontal, 1 Vertical.
   * @param padding the raster padding for overscan
   * @param bidirectional cut in both directions
   * @param skipblanks if there's nothing on a line, skip that line.
   * @param skipvalue if there's nothing more relevant this line, move to next
   * @param orthogonal direction change at y-step. Orthogonal movement only.
   * @param start_bottom start from the bottom and move up.
   * @param start_end start from non-default position.
   * @return a VectorPart job of VectorCommands
   */
  public VectorPart convertToVectorPart(LaserProperty.Provider provider, int axis, double padding, boolean bidirectional,
    boolean skipblanks, int skipvalue, boolean orthogonal, boolean start_bottom, boolean start_end)
  {
    boolean cutForward;
    if (axis == Y_AXIS)
    {
      //use a rotated copy of the image.
      //flip x and y
      throw new UnsupportedOperationException("I lied. You can't do that.");
    }

    VectorPart result = new VectorPart(property, resolution);
    int line, dy, pos = 0, begin, end, right, left;

    if (start_end)
    {
      cutForward = false;
      pos = image.getWidth() - 1;
    }
    else
    {
      cutForward = true;
      pos = 0;
    }

    if (start_bottom)
    {
      line = image.getHeight() - 1;
      dy = -1;
    }
    else
    {
      line = 0;
      dy = 1;
    }

    int overscan = Math.round((float) Util.mm2px(padding, resolution));

    //START MAIN LOOP//
    for (; inrange_y(line); line += dy)
    {
      if (orthogonal)
      {
        result.moveto(start.x + pos, start.y + line);
      }
      left = image.leftMostNotEqual(line, skipvalue);
      if ((left == -1) && (skipblanks))
      {
        continue; //if are skipping lines.
      }
      right = image.rightMostNotEqual(line, skipvalue);

      //START CUT INITIALIZATION//
      if (cutForward)
      {
        begin = left - overscan;
        end = right + overscan;

        if (inrange_y(line + dy))
        {
          end = Math.max(end, image.rightMostNotEqual(line + dy, skipvalue));
        }
      }
      else
      {
        begin = right + overscan;
        end = left - overscan;

        if (inrange_y(line + dy))
        {
          end = Math.min(end, image.leftMostNotEqual(line + dy, skipvalue));
        }
      }

      //START PRECUT//
      if (provider != null)
      {
        result.setProperty(provider.getLaserProperty(skipvalue));
        result.lineto(start.x + pos, start.y + line);
      }
      else
      {
        result.moveto(start.x + pos, start.y + line);
      }

      //START CUT//
      while (((cutForward) && (pos < end)) || ((!cutForward) && (end < pos)))
      {
        int pixel = skipvalue;
        if (inrange_x(pos) && inrange_y(line)) {
          pixel = image.getPixel(pos, line);
        }
        if (provider != null)
        {
          result.setProperty(provider.getLaserProperty(pixel));
        }
        
        if (cutForward)
        {
          pos = image.nextColorChangeHeadingRight(pos, line, end);
        }
        else
        {
          pos = image.nextColorChangeHeadingLeft(pos, line, end);
        }

        if ((provider == null) && (pixel == skipvalue))
        {
          result.moveto(start.x + pos, start.y + line);
        }
        else
        {
          result.lineto(start.x + pos, start.y + line);
        }
      }

      if (bidirectional)
      {
        cutForward = !cutForward;
      }
    }
    return result;
  }

  public boolean isBlack(int x, int y)
  {
    return image.isBlack(x, y);
  }

  /**
   * Gets the width of the associated raster image
   *
   * @return width in pixels
   */
  public int getRasterWidth()
  {
    return this.image.getWidth();
  }

  /**
   * Determines whether an entire line in an image is blank; i.e. can it be
   * skipped?
   *
   * @param y
   * @return true if the line is blank
   */
  public boolean lineIsBlank(int y)
  {
    return image.isLineBlank(y);
  }

  /**
   * Returns the upper left point of the given raster
   *
   * @return
   */
  public Point getRasterStart()
  {
    return getStartPosition(0);
  }

  /**
   * Returns the start position of the first column (x=0) for a given line
   *
   * @param y y coordinate of the row in question
   * @return Point representing start of this row
   */
  public Point getStartPosition(int y)
  {
    Point start = this.start.clone();
    start.y += y;
    return start;
  }

  @Override
  public double getMinX()
  {
    return this.start.x;
  }

  @Override
  public double getMaxX()
  {
    return this.start.x + this.image.getWidth();
  }

  @Override
  public double getMinY()
  {
    return start.y;
  }

  @Override
  public double getMaxY()
  {
    return start.y + image.getHeight();
  }

}
