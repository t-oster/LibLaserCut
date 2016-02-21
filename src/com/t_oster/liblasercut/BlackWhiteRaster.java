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

import com.t_oster.liblasercut.dithering.*;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class BlackWhiteRaster extends TimeIntensiveOperation implements GreyscaleRaster
{

  public static enum DitherAlgorithm
  {
    FLOYD_STEINBERG,
    AVERAGE,
    RANDOM,
    ORDERED,
    GRID,
  }
  private int width;
  private int height;
  private byte[][] raster;

  public static DitheringAlgorithm getDitheringAlgorithm(DitherAlgorithm alg)
  {
    switch (alg)
    {
      case FLOYD_STEINBERG:
        return new FloydSteinberg();
      case AVERAGE:
        return new Average();
      case RANDOM:
        return new Random();
      case ORDERED:
        return new Ordered();
      case GRID:
        return new Grid();
      default:
        throw new IllegalArgumentException("Desired Dithering Algorithm ("+alg+") does not exist");
    }
  }
  
  public BlackWhiteRaster(GreyscaleRaster src, DitheringAlgorithm alg, ProgressListener listener)
  {
    if (listener != null)
    {
      this.addProgressListener(listener);
    }
    this.width = src.getWidth();
    this.height = src.getHeight();
    raster = new byte[(src.getWidth() + 7) / 8][src.getHeight()];
    if (listener != null)
    {
      alg.addProgressListener(listener);
    }
    alg.ditherDirect(src, this);
  }
  
  public BlackWhiteRaster(GreyscaleRaster src, DitherAlgorithm dither_algorithm, ProgressListener listener)
  {
    this(src, BlackWhiteRaster.getDitheringAlgorithm(dither_algorithm), listener);
  }

  public BlackWhiteRaster(GreyscaleRaster src, DitherAlgorithm dither_algorithm)
  {
    this(src, dither_algorithm, null);
  }
  
  public BlackWhiteRaster(GreyscaleRaster src, DitheringAlgorithm alg)
  {
    this(src, alg, null);
  }

  public BlackWhiteRaster(int width, int height, byte[][] raster)
  {
    this.width = width;
    this.height = height;
    this.raster = raster;
  }

  public BlackWhiteRaster(int width, int height)
  {
    this.width = width;
    this.height = height;
    this.raster = new byte[(width + 7) / 8][height];
  }

  public boolean isBlack(int x, int y)
  {
    int bx = x / 8;
    int ix = 7 - (x % 8);
    return ((raster[bx][y] & 0xFF) & (int) Math.pow(2,ix)) != 0;
  }

  public void setBlack(int x, int y, boolean black)
  {
    int bx = x / 8;
    int ix = 7 - (x % 8);
    raster[bx][y] = (byte) (((raster[bx][y] & 0xFF) & ~((int) Math.pow(2,ix))) | (black ? (int) Math.pow(2,ix) : 0 ));
  }

  /**
   * Returns the Byte where every bit represents one pixel 0=white and 1=black
   * NOTE THAT THE BITORDER IS [BBBBBBWW] = 0b11111100;
   * @param x the x index of the byte, meaning 0 is the first 8 pixels (0-7), 1 
   * the pixels 8-15 ...
   * @param y the y offset
   * @return 
   */
  public byte getByte(int x, int y)
  {
    return raster[x][y];
  }
  
  /**
   * Convenience function to pretend this B&W image is greyscale
   * @param x
   * @param y
   * @return 0 for black, 255 for white
   */
  public int getGreyScale(int x, int y)
  {
    return isBlack(x, y) ? 0 : 255;
  }
  
  public void setGreyScale(int x, int y, int color)
  {
    this.setBlack(x, y, color < 128);
  }

  public int getWidth()
  {
    return width;
  }

  public int getHeight()
  {
    return height;
  }
  
}
