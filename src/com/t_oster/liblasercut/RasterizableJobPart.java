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

/**
 * Common functions useful when rasterizing an image.
 * @author Michael Adams <zap@michaeladams.org>
 */
abstract public class RasterizableJobPart extends JobPart
{
  protected GreyscaleRaster image;
  protected Point start = null;
  
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
   * Finds the x coordinate of the first pixel that needs lasering
   * @param y
   * @return x coordinate to start lasering from
   */
  public int firstNonWhitePixel(int y)
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
    int color = image.getGreyScale(x, y);
    for (int i=x; i<getRasterWidth(); i++)
      if (image.getGreyScale(i, y) != color)
        return i;
    // whole line is the same color, so go to end of line
    return lastNonWhitePixel(y);
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
  
  /**
   * Calculate power/speed/focus required to laser a given pixel
   * @param x x coordinate of pixel
   * @param y y coordinate of pixel
   * @return laser property appropriate for the color at this pixel
   */
  public FloatPowerSpeedFocusProperty getPowerSpeedFocusPropertyForPixel(int x, int y)
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
  public abstract FloatPowerSpeedFocusProperty getPowerSpeedFocusPropertyForColor(int color);
}
