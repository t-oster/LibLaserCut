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

  protected RasterElement raster;

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

  public BlackWhiteRaster(int width, int height)
  {
    this(width, height, 1, 1);
  }

  public BlackWhiteRaster(int width, int height, int bitDepth)
  {
    this(width, height, bitDepth, 1);
  }

  public BlackWhiteRaster(int width, int height, int bitDepth, int samplesPerPixel)
  {
    this.raster = new RasterElement(width, height, bitDepth, samplesPerPixel);
  }

  public int getPixel(int x, int y)
  {
    return raster.getPixel(x, y);
  }

  public int setPixel(int x, int y, int v)
  {
    return raster.setPixel(x, y, v);
  }

  public byte[] getRasterLine(int y, byte[] bytes)
  {
    return raster.getRasterLine(y, bytes);
  }

  int getBlack()
  {
    return raster.getBlack();
  }

  int getWhite()
  {
    return raster.getWhite();
  }

  public int getWidth()
  {
    return raster.getWidth();
  }

  public int getHeight()
  {
    return raster.getHeight();
  }

  public byte[] getImageData()
  {
    return raster.getImageData();
  }

  public int getBitDepth()
  {
    return raster.getBitDepth();
  }

  public int getSamplesPerPixel()
  {
    return raster.getSamplesPerPixel();
  }

  public byte getByte(int x, int line)
  {
    return raster.getByte(x, line);
  }

  public void setBlack(int x, int y, boolean black)
  {
    if (raster.getBitDepth() == 1)
    {
      if (black)
      {
        setPixel(x, y, getWhite());
      }
      else
      {
        setPixel(x, y, getBlack());
      }
    }
    else
    {
      if (black)
      {
        setPixel(x, y, getBlack());
      }
      else
      {
        setPixel(x, y, getWhite());
      }
    }
  }

  public boolean isBlack(int x, int y)
  {
    if (raster.getBitDepth() == 1)
    {
      int value = getPixel(x, y);
      return value == getWhite();
    }
    return raster.isBlack(x, y);
  }

  @Override
  public int getGreyScale(int x, int y)
  {
    return getPixel(x, y);
  }

  @Override
  public void setGreyScale(int x, int y, int grey)
  {
    setPixel(x, y, grey);
  }

}
