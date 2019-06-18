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
    HALFTONE,
    BRIGHTENED_HALFTONE
  }
  private int width;
  private int scanline;
  private int height;
  private int[] raster;

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
      case HALFTONE:
        return new Halftone();
      case BRIGHTENED_HALFTONE:
        return new BrightenedHalftone();
      default:
        throw new IllegalArgumentException("Desired Dithering Algorithm (" + alg + ") does not exist");
    }
  }

  public BlackWhiteRaster(GreyscaleRaster src, DitheringAlgorithm alg, ProgressListener listener) throws InterruptedException
  {
    this(src.getWidth(), src.getHeight());
    if (listener != null)
    {
      this.addProgressListener(listener);
    }

    if (listener != null)
    {
      alg.addProgressListener(listener);
    }
    alg.ditherDirect(src, this);
  }

  public BlackWhiteRaster(GreyscaleRaster src, DitherAlgorithm dither_algorithm, ProgressListener listener) throws InterruptedException
  {
    this(src, BlackWhiteRaster.getDitheringAlgorithm(dither_algorithm), listener);
  }

  public BlackWhiteRaster(GreyscaleRaster src, DitherAlgorithm dither_algorithm) throws InterruptedException
  {
    this(src, dither_algorithm, null);
  }

  public BlackWhiteRaster(GreyscaleRaster src, DitheringAlgorithm alg) throws InterruptedException
  {
    this(src, alg, null);
  }

  public BlackWhiteRaster(int width, int height, int[] raster)
  {
    this.width = width;
    this.height = height;
    this.raster = raster;
  }

  public BlackWhiteRaster(int width, int height)
  {
    this.width = width;
    this.height = height;
    this.scanline = (int) Math.ceil(width / 32.0);
    this.raster = new int[scanline * height];
  }

  public boolean isBlack(int x, int y)
  {
    int index = y * scanline + (x / 32);
    int ix = 31 - (x % 32);
    int mask = 1 << ix;
    return (raster[index] & mask) != 0;
  }

  public void setBlack(int x, int y, boolean black)
  {
    int index = y * scanline + (x / 32);
    int ix = 31 - (x % 32);
    int mask = 1 << ix;
    raster[index] &= ~mask;
    if (black)
    {
      raster[index] |= mask;
    }

  }

  /**
   * Returns the byte where every bit represents one pixel 0=white and 1=black
   * Note: the bit order is bigendian. Meaning the most significant digit is the
   * first pixel.
   *
   * @param x the x index of the byte, meaning 0 is the first 8 pixels (0-7), 1
   * the pixels 8-15 ...
   * @param y the y offset
   * @return
   */
 public byte getByte(int x, int y)
  {
    int index = y * scanline + (x / 4);
    int index_in_byte = (3 - (x % 4));
    int ix = (8 * index_in_byte);
    int mask = 0xFF << ix;
    return (byte) ((raster[index] >> ix) & 0xFF);
  }

  /**
   * Returns the integer where every bit represents one pixel 0=white and
   * 1=black
   * Note: the bit order is bigendian. Meaning the most significant digit is the
   * first pixel.
   *
   * If this is the last integer in a line, note that the pixels value to the
   * width point, and the remaining values are considered garbage.
   *
   * @param x the x index of the byte, meaning 0 is the first 8 pixels (0-7), 1
   * the pixels 8-15 ...
   * @param y the y offset
   * @return
   */
  public int getInteger(int x, int y)
  {
    int index = y * scanline + x;
    return raster[index];
  }

  public boolean isLineBlank(int y)
  {
    for (int i = y * scanline, ie = (y + 1) * scanline; i < ie; i++)
    {
      if (raster[i] != 0)
      {
        return false;
      }
    }
    return true;
  }

  private int mostSignificantBit(int i)
  {
    int mask = 1 << 31;
    for (int bitIndex = 31; bitIndex >= 0; bitIndex--)
    {
      if ((i & mask) != 0)
      {
        return bitIndex;
      }
      mask >>>= 1;
    }
    return -1;
  }

  private int leastSignificantBit(int i)
  {
    for (int bitIndex = 0; bitIndex < 32; bitIndex++)
    {
      if ((i & 1) != 0)
      {
        return bitIndex;
      }
      i >>>= 1;
    }
    return -1;
  }

  public int leftmostBlackPixel(int y)
  {
    for (int i = y * scanline, ie = (y + 1) * scanline; i < ie; i++)
    {
      if (raster[i] != 0)
      {
        return (i % scanline) * 32 + (31-mostSignificantBit(raster[i]));
      }
    }
    return width;
  }

  public int rightmostBlackPixel(int y)
  {
    for (int i = ((y + 1) * scanline) - 1, ie = y * scanline; i >= ie; i--)
    {
      if (raster[i] != 0)
      {
        return (i % scanline) * 32 + (31-leastSignificantBit(raster[i]));
      }
    }
    return -1;
  }

  /**
   * Convenience function to pretend this B&W image is greyscale
   *
   * @param x
   * @param y
   * @return 0 for black, 255 for white
   */
  @Override
  public int getGreyScale(int x, int y)
  {
    return isBlack(x, y) ? 0 : 255;
  }

  @Override
  public void setGreyScale(int x, int y, int color)
  {
    this.setBlack(x, y, color < 128);
  }

  @Override
  public int getWidth()
  {
    return width;
  }

  @Override
  public int getHeight()
  {
    return height;
  }

  public int getScanline()
  {
    return scanline;
  }

}