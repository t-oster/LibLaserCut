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

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class GreyRaster implements GreyscaleRaster, RasterElement.Provider
{
  public final RasterElement raster;

  public GreyRaster(int width, int height)
  {
    this(width, height, 8);
  }
  
  public GreyRaster(int width, int height, int bitDepth)
  {
    this(new RasterElement(width, height, bitDepth, 1));
  }
  
  public GreyRaster(RasterElement raster) 
  {
    this.raster = raster;
  }

  @Override
  public int getWidth()
  {
    return this.raster.getWidth();
  }

  @Override
  public int getGreyScale(int x, int y)
  {
    return this.raster.getPixel(x, y);
  }

  @Override
  public void setGreyScale(int x, int y, int grey)
  {
    this.raster.setPixel(x,y, grey);
  }

  @Override
  public int getHeight()
  {
     return this.raster.getHeight();
  }

  @Override
  public RasterElement getRaster()
  {
    return raster;
  }
}
