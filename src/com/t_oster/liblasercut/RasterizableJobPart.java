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
import java.awt.image.BufferedImage;
import java.util.Arrays;
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

  protected BufferedImage image;
  protected Point start = null;
  protected double resolution = Double.NaN;

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
  public abstract LaserProperty getLaserProperty();

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
   * Returns one line of the given rasterpart
   * every byte represents 8/getBitsPerRasterPixel() pixels and the value
   * corresponds to (2^getBitsPerRasterPixel() - 1) when black or 0 when white
   *
   * @param line
   * @return
   */
  public List<Byte> getRasterLine(int line)
  {
    int[] array = new int[image.getWidth()];
    image.getRGB(0, line, image.getWidth(), 1, array, 0, image.getWidth());
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
  abstract public void getRasterLine(int line, List<Byte> result);

  /**
   * bits for one pixel in getRasterLine() output
   *
   * @return 1 or 8
   */
  public abstract int getBitsPerRasterPixel();

  public VectorPart convertToVectorPart(double padding, boolean bidirectional)
  {
    return convertToVectorPart(X_AXIS, padding, bidirectional, true, false, true, false, false);
  }

  
  private boolean inrange_y(int y) {
    return y < getRasterHeight() && y >= 0;
  } 
  private boolean inrange_x(int x) {
    return x < getRasterWidth() && x >= 0;
  }
  
  private void line(VectorPart part, int x, int y) {
    part.lineto(start.x + x, start.y + y);
  }
  
  private void move(VectorPart part, int x, int y) {
    part.moveto(start.x + x, start.y + y);
  }
  
  /**
   * Converts a raster image (B&W or greyscale) into a series of vector
   * instructions suitable for printing. Lets non-raster-native cutters
   * emulate this functionality.
   *
   * @param axis gives the axis to use. 0 = Horizontal, 1 Vertical.
   * @param padding the raster padding for overscan
   * @param bidirectional cut in both directions
   * @param skipblanks if there's nothing on a line, skip that line.
   * @param skipwhiteedges if there's nothing more relevant this line, move to
   * next
   * @param orthogonal direction change at y-step. Orthogonal movement only.
   * @param start_bottom start from the bottom and move up.
   * @param start_end start from non-default position.
   * @return a VectorPart job of VectorCommands
   */
  public VectorPart convertToVectorPart(int axis, double padding, boolean bidirectional,
    boolean skipblanks, boolean skipwhiteedges, boolean orthogonal, boolean start_bottom, boolean start_end)
  {
    boolean cutForward;
    if (axis == Y_AXIS)
    {
      //use a rotated copy of the image.
      //flip x and y
      throw new UnsupportedOperationException("I lied.");
    }

    VectorPart result = new VectorPart(getLaserProperty(), resolution);
    int line, dx, dy, pos = 0, pad, end;

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
    if (orthogonal) //move to start point, if only permitted orthogonal changes.
    {
      if (start_end)
      {
        move(result, image.getWidth() - 1, line);
      }
      else
      {
        move(result, 0, line);
      }
    }

    for (; inrange_y(line); line += dy)//start main loop.
    {
      if (lineIsBlank(line) && skipblanks)
      {
        if (orthogonal)
        {
          move(result, pos, line+dy);
        }
        continue; //if are skipping lines.
      }
      if (cutForward)
      {
        pos = leftMostNonWhitePixel(line);
        end = rightMostNonWhitePixel(line);
        if (inrange_y(line+dy)) {
          end = Math.max(end,rightMostNonWhitePixel(line+dy));
        }
        dx = 1;
        pad = overscan;
      }
      else
      {
        pos = rightMostNonWhitePixel(line);
        end = leftMostNonWhitePixel(line);
        if (inrange_y(line+dy)) {
          end = Math.min(end,leftMostNonWhitePixel(line+dy));
        }
        dx = -1;
        pad = 1 - overscan;
      }

      //move to prestart
      move(result, pos + pad, line);

      //move to the first point of the scanline
      result.setProperty(getPowerSpeedFocusPropertyForColor(255));
      line(result, pos + pad, line);

      if (cutForward)
      {
        while (pos <= end)
        {
          result.setProperty(getPowerSpeedFocusPropertyForPixel(pos, line));
          pos = nextColorChangeHeadingRight(pos, line);
          line(result, pos, line);
        }

        // move to post-end
        result.setProperty(getPowerSpeedFocusPropertyForColor(255));
        line(result, pos+pad,line);
      }
      else
      {
        while (pos >= end)
        {
          result.setProperty(getPowerSpeedFocusPropertyForPixel(pos, line));
          pos = nextColorChangeHeadingLeft(pos, line);
          line(result,pos+1, line);
        }
        // move to post-end
        result.setProperty(getPowerSpeedFocusPropertyForColor(255));
        line(result, pos + pad, line);
      }

      if (bidirectional)
      {
        cutForward = !cutForward;
      }
      if (orthogonal)
      {
        result.moveto(pos, (line + dy));
      }
    }
    return result;
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
    for (int x = 0; x < getRasterWidth(); x++)
    {
      int pixel = getGreyScale(image, x, y);
      if (pixel < 255)
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the x coordinate for the left most pixel, since "start" depends on
   * what direction you are cutting in.
   *
   * @param y
   * @return x coordinate of left most non-white pixel
   */
  protected int leftMostNonWhitePixel(int y)
  {
    for (int x = 0; x < getRasterWidth(); x++)
    {
      if (getGreyScale(image, x, y) < 255)
      {
        return x;
      }
    }
    return -1;
  }

  /**
   * Finds the x coordinate for the right most pixel, since "end" depends on
   * what direction you are cutting in.
   *
   * @param y
   * @return x coordinate of right most non-white pixel
   */
  protected int rightMostNonWhitePixel(int y)
  {
    for (int x = getRasterWidth() - 1; x >= 0; x--)
    {
      if (getGreyScale(image, x, y) < 255)
      {
        return x;
      }
    }
    return getRasterWidth();
  }

  /**
   * nextColorChange logic when heading ->
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @return x coordinate of the next different color in this row
   */
  protected int nextColorChangeHeadingRight(int x, int y)
  {
    int color = getGreyScale(image, x, y);
    for (int i = x; i < getRasterWidth(); i++)
    {
      if (getGreyScale(image, i, y) != color)
      {
        return i;
      }
    }
    // rest of line is the same color, so next colour change is past end of line
    return getRasterWidth();
  }

  /**
   * nextColorChange logic when heading <-
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @return x coordinate of the next different color in this row
   */
  protected int nextColorChangeHeadingLeft(int x, int y)
  {
    int color = getGreyScale(image, x, y);
    for (int i = x; i >= 0; i--)
    {
      if (getGreyScale(image, i, y) != color)
      {
        return i;
      }
    }
    // rest of line is the same color, so next colour change is past the beginning of line
    return -1;
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

  /**
   * Calculate power/speed/focus required to laser a given pixel
   *
   * @param x x coordinate of pixel
   * @param y y coordinate of pixel
   * @return laser property appropriate for the color at this pixel
   */
  public FloatPowerSpeedFocusProperty getPowerSpeedFocusPropertyForPixel(int x, int y)
  {
    return getPowerSpeedFocusPropertyForColor(getGreyScale(image, x, y));
  }

  /**
   * Returns a power/speed/focus property appropriate for a given color.
   * 255 = white = 0% laser.
   * 0 = black = 100% laser.
   *
   * @param color 0-255 value representing the color. 0 = black and 255 = white.
   * @return laser property appropriate for this color
   */
  public abstract FloatPowerSpeedFocusProperty getPowerSpeedFocusPropertyForColor(int color);

  static int getGreyScale(BufferedImage src, int x, int y)
  {
    return src.getRGB(x, y) & 0xFF;
  }

}
