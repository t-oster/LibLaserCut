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
 *@author Thomas Oster <thomas.oster@rwth-aachen.de>
 * 
 * BlackWhiteRaster is a 1 bit raster with 1 considered black and 0 considered white.
 * 
 */
public class BlackWhiteRaster extends TimeIntensiveOperation implements GreyscaleRaster, RasterElement.Provider
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
    //create a delegate class for all the rastering.
    //BlackWhiteRasters are required to be 1 bit and 1 sample per color.
    this.raster = new RasterElement(width, height, 1, 1);
  }
  
  /**
   * Gets the pixel in the form of an integer where bitDepth of the pixel is relevant.
   * In 1 bit rasters, like BlackWhiteRaster, this is 0 or 1.
   * 
   * @param x location horizontal to set the pixel
   * @param y location vertical to set the pixel
   * @return the bit of the set pixel either 1 for black, 0 for white.
   **/
  public int getPixel(int x, int y)
  {
    return raster.getPixel(x, y);
  }
  
  /**
   * Sets the physical bits within the raster.
   * Only the rightmost bitDepth bits of the integer are used.
   * For 1 bit rasters like BlackWhiteRaster this should typically be 1 or 0
   * 1 traditionally means black in this type of raster.
   * 
   * The delegated function works for all bitDepth values (n greater than 32). 
   * Here it just sets the one bit that matters. So 3 is 0b11 and sets the lowest
   * bit as as 1. 4 is 0b100 and sets the lowest bit to be 0. Only 1 and 0 should
   * be used.
   * 
   * @param x location horizontal to set the pixel
   * @param y location vertical to set the pixel
   * @param v binary value
   **/
  public int setPixel(int x, int y, int v)
  {
    return raster.setPixel(x, y, v);
  }

  public byte[] getRasterLine(int y, byte[] bytes)
  {
    return raster.getRasterLine(y, bytes);
  }


  @Override
  public int getWidth()
  {
    return raster.getWidth();
  }

  @Override
  public int getHeight()
  {
    return raster.getHeight();
  }
  
  @Override
  public RasterElement getRaster() {
    return raster;
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

  /**
   * Sets black according to the definitions used in GreyRaster and BlackWhiteRaster
   * In BlackWhiteRaster this was 1 for Black. This is maintained. For 8 bit images
   * the more traditional value definition is used.
   * 
   * @param x location horizontal to set the pixel
   * @param y location vertical to set the pixel
   * @param black are we setting black or white.
  **/
  public void setBlack(int x, int y, boolean black)
  {
    if (raster.getBitDepth() == 1)
    {
      if (black)
      {
        setPixel(x, y, raster.getWhite());
      }
      else
      {
        setPixel(x, y, raster.getBlack());
      }
    }
    else
    {
      if (black)
      {
        setPixel(x, y, raster.getBlack());
      }
      else
      {
        setPixel(x, y, raster.getWhite());
      }
    }
  }

  /**
   * Gets black according to the definitions used in GreyRaster and BlackWhiteRaster
   * In BlackWhiteRaster this is any pixel equal to 1.
   * In 8-bit greys this refers to pixel values equal to 0.
   * 
   * @param x location horizontal to set the pixel
   * @param y location vertical to set the pixel
   * @return is the pixel black?
  **/
  public boolean isBlack(int x, int y)
  {
    if (raster.getBitDepth() == 1)
    {
      int value = getPixel(x, y);
      return value == raster.getWhite();
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