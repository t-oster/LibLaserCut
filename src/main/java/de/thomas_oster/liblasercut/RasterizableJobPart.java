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
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.platform.Point;
import java.util.List;

/**
 * Common functions useful when rasterizing an image.
 * @author Michael Adams <zap@michaeladams.org>
 */
abstract public class RasterizableJobPart extends JobPart
{
  protected GreyscaleRaster image;
  protected Point start = null;
  protected boolean cutDirectionleftToRight = true;
  protected double resolution = Double.NaN;

  @Override
  public double getDPI()
  {
      return resolution;
  }
  
  /**
   * The initial laser settings to start a rasterization job with.
   * @return LaserProperty
   */
  public abstract LaserProperty getLaserProperty();
  
  /**
   * Gets the height of the associated raster image
   * @return height in pixels
   */
  public int getRasterHeight() {
    return this.image.getHeight();
  }
  
    /**
   * Returns one line of the given rasterpart
   * every byte represents 8/getBitsPerRasterPixel() pixels and the value
   * corresponds to (2^getBitsPerRasterPixel() - 1) when black or 0 when white
   * @param line
   * @return
   */
  public List<Byte> getRasterLine(int line)
  {
    ByteArrayList b = new ByteArrayList(image.getWidth());
    getRasterLine(line, b);
    return b;
  }

  /**
   * write output of getRasterLine(line) into the given result list
   * @param line
   * @param result
   */
  abstract public void getRasterLine(int line, List<Byte> result);

  /**
   * bits for one pixel in getRasterLine() output
   * @return 1 or 8
   */
  public abstract int getBitsPerRasterPixel();

  /**
   * Gets the width of the associated raster image 
   * @return width in pixels
   */
  public int getRasterWidth() {
    return this.image.getWidth();
  }
  
  /**
   * Determines whether an entire line in an image is blank; i.e. can it be skipped?
   * @param y
   * @return true if the line is blank
   */
  public boolean lineIsBlank(int y)
  {
    for (int x=0; x<getRasterWidth(); x++)
      if (image.getGreyScale(x, y) < 255)
        return false;
    return true;
  }
  
  /**
   * Toggle the direction cutting is done in. Left to right by default; when changed
   * then "start" of the line is the right-most side, and "end" is the left-most
   * side.
   */
  public void toggleRasteringCutDirection()
  {
    cutDirectionleftToRight = !cutDirectionleftToRight;
  }

  
  /**
   * Adds any required compensation when cutting.
   * Fixes off-by-one errors when cutting in reverse direction, since
   * the pixel finding methods here always refer to the bottom left corner of 
   * a pixel, but when cutting in reverse direction, we need to take pixel width
   * into account.
   * @return amount to adjust coordinates by
   */
  public int cutCompensation()
  {
    return cutDirectionleftToRight ? 0 : 1;
  }
  
  /**
   * Given an coordinate, and knowing the direction we are cutting in, decide
   * if we have finished cutting a row of the image.
   * @param x x coordinate of last pixel cut
   * @param y y coordinate of last pixel cut
   * @return true if we have finished cutting a line
   */
  public boolean hasFinishedCuttingLine(int x, int y)
  {
    return cutDirectionleftToRight 
      ? (x > rightMostNonWhitePixel(y)) 
      : (x < leftMostNonWhitePixel(y));
  }
  
  /**
   * Finds the x coordinate of the first pixel that needs lasering
   * @param y
   * @return x coordinate to start lasering from
   */
  public int firstNonWhitePixel(int y)
  {
    return cutDirectionleftToRight
      ? leftMostNonWhitePixel(y)
      : rightMostNonWhitePixel(y);
  }
  
  /**
   * Finds the x coordinate for the left most pixel, since "start" depends on
   * what direction you are cutting in.
   * @param y
   * @return x coordinate of left most non-white pixel
   */
  protected int leftMostNonWhitePixel(int y)
  {
    for (int x=0; x<getRasterWidth(); x++)
      if (image.getGreyScale(x, y) < 255)
        return x;
    return getRasterWidth();
  }
  
  /**
   * Finds the end of the line; points after this pixel are all blank
   * @param y
   * @return x coordinate to end lasering at
   */
  public int lastNonWhitePixel(int y)
  {
    return cutDirectionleftToRight
      ? rightMostNonWhitePixel(y)
      : leftMostNonWhitePixel(y);
  }
  
  /**
   * Finds the x coordinate for the right most pixel, since "end" depends on
   * what direction you are cutting in.
   * @param y
   * @return x coordinate of right most non-white pixel
   */
  protected int rightMostNonWhitePixel(int y)
  {
    for (int x=getRasterWidth()-1; x >= 0; x--)
      if (image.getGreyScale(x, y) < 255)
        return x;
    return 0;
  }
  
  /**
   * Given a pixel in a row of an image, finds the next pixel that has a different
   * color. If no more color changes take place, returns the last interesting pixel.
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChange(int x, int y)
  {
    return cutDirectionleftToRight
      ? nextColorChangeHeadingRight(x, y)
      : nextColorChangeHeadingLeft(x, y);
  }
  
  /**
   * nextColorChange logic when heading ->
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @return x coordinate of the next different color in this row
   */
  protected int nextColorChangeHeadingRight(int x, int y)
  {
    int color = image.getGreyScale(x, y);
    for (int i=x; i<getRasterWidth(); i++)
      if (image.getGreyScale(i, y) != color)
        return i;
    // rest of line is the same color, so next colour change is past end of line
    return getRasterWidth();
  }
  
  /**
   * nextColorChange logic when heading <-
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @return x coordinate of the next different color in this row
   */
  protected int nextColorChangeHeadingLeft(int x, int y)
  {
    int color = image.getGreyScale(x, y);
    for (int i=x; i>=0; i--)
      if (image.getGreyScale(i, y) != color)
        return i;
    // rest of line is the same color, so next colour change is past the beginning of line
    return -1;
  }
  
  /**
   * Returns the upper left point of the given raster
   * @return
   */
  public Point getRasterStart()
  {
    return getStartPosition(0);
  }

  /**
   * Returns the start position of the first column (x=0) for a given line
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
    return start.y+image.getHeight();
  }

  public GreyscaleRaster getImage()
  {
    return image;
  }
  
  /**
   * Calculate power/speed/focus required to laser a given pixel
   * @param x x coordinate of pixel
   * @param y y coordinate of pixel
   * @return laser property appropriate for the color at this pixel
   */
  public LaserProperty getPowerSpeedFocusPropertyForPixel(int x, int y)
  {
    return getPowerSpeedFocusPropertyForColor(image.getGreyScale(x, y));
  }
  
  /**
   * Returns a power/speed/focus property appropriate for a given color.
   * 255 = white = 0% laser.
   * 0 = black = 100% laser.
   * @param color 0-255 value representing the color. 0 = black and 255 = white.
   * @return laser property appropriate for this color
   */
  public abstract LaserProperty getPowerSpeedFocusPropertyForColor(int color);
}
