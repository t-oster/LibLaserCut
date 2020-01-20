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
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class RasterPart extends RasterizableJobPart
{

  LaserProperty blackPixelProperty = null;
  LaserProperty whitePixelProperty = null;

  public RasterPart(GreyscaleRaster image, LaserProperty laserProperty, Point offset, double resolution)
  {
    this.image = image;
    this.start = offset;
    this.resolution = resolution;
    this.blackPixelProperty = laserProperty;
    this.whitePixelProperty = blackPixelProperty.clone();
    whitePixelProperty.setProperty("power", 0);
  }


  @Override
  public int getBitsPerRasterPixel() {
    return 1;
  }

  /**
   * Sets one line of the given rasterpart into the given result list.
   * every byte represents 8 pixel and the value corresponds to
   * 1 when black or 0 when white
   * This method is preferred to the above method because it allows reuse of
   * an allocated List<Byte> instead of reallocating for every line.
   * @param line
   * @param result
   */
  @Override
  public void getRasterLine(int line, List<Byte> result)
  {
    RasterElement raster = ((RasterElement.Provider)image).getRaster();
    byte[] byte_array = raster.getRasterLine(line, null);
    if (result instanceof ByteArrayList) {
      ((ByteArrayList)result).clear(byte_array.length);
    } else {
       result.clear();
    }
    for (int x = 0; x < byte_array.length; x++)
    {
      result.add(byte_array[x]);
    }
  }

  public boolean isBlack(int x, int y)
  {
    RasterElement raster = ((RasterElement.Provider)image).getRaster();
    if (raster.getBitDepth() == 1)
    {
      int value = raster.getPixel(x, y);
      return value == raster.getWhite();
    }
    return raster.isBlack(x, y);
  }

  @Override
  public LaserProperty getLaserProperty()
  {
    return this.blackPixelProperty;
  }
  
  @Override
  public LaserProperty getPowerSpeedFocusPropertyForColor(int color)
  {
    return color <= 127
      ? blackPixelProperty
      : whitePixelProperty;
  }

}
