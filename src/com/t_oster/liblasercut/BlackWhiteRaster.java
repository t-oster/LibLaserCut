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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

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
  
  public BufferedImage image;
  public byte[] imageData;
  public int scanline;

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

    public BlackWhiteRaster() {
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
    this(BlackWhiteRaster.getBWImage(width, height));
  }
  
  public BlackWhiteRaster(BufferedImage image) {
    if (image.getType() != BufferedImage.TYPE_BYTE_BINARY) throw new UnsupportedOperationException("Only permitted 1 Bit Color.");
    this.image = image;
    this.imageData = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
    scanline = (image.getWidth() + 7) / 8;
  }

  public boolean isBlack(int x, int y)
  {
    int bx = x / 8;
    int ix = 7 - (x % 8);
    int value = imageData[y * scanline + bx];
    int mask = 1 << ix;
    return (value & mask) != 0;
  }

  public void setBlack(int x, int y, boolean black)
  {
    int bx = x / 8;
    int ix = 7 - (x % 8);
    
    int index = y * scanline + bx;
    int mask = 1 << ix;
    imageData[index] &= ~mask;
    if (black)
    {
      imageData[index] |= mask;
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
    int index = y * scanline + x;
    return imageData[index];
  }
 
 public byte[] getRasterLine(int y, byte[] bytes) {
    if ((bytes == null) || (bytes.length < scanline)) {
        return Arrays.copyOfRange(imageData, y * scanline, (y+1) * scanline);
    }
    System.arraycopy(imageData, y * scanline, bytes, 0, scanline);
    return bytes;
 }
 
  public boolean isLineBlank(int y)
  {
    for (int i = y * scanline, ie = (y + 1) * scanline; i < ie; i++)
    {
      if (imageData[i] != 0)
      {
        return false;
      }
    }
    return true;
  }

  private int mostSignificantBit(byte i)
  {
    int mask = 1 << 7;
    for (int bitIndex = 7; bitIndex >= 0; bitIndex--)
    {
      if ((i & mask) != 0)
      {
        return bitIndex;
      }
      mask >>>= 1;
    }
    return -1;
  }

  private int leastSignificantBit(byte i)
  {
    for (int bitIndex = 0; bitIndex < 8; bitIndex++)
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
    int offset = y * scanline;
    for (int i = 0; i < scanline; i++)
    {
      if (imageData[offset + i] != 0)
      {
        return (i * 8) + (7-mostSignificantBit(imageData[offset + i]));
      }
    }
    return image.getWidth();
  }

  public int rightmostBlackPixel(int y)
  {
    int offset = y * scanline;
    for (int i = scanline-1; i >= 0; i--)
    {
      if (imageData[offset + i] != 0)
      {
        return (i * 8) + (7-leastSignificantBit(imageData[offset + i]));
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
    return image.getWidth();
  }

  @Override
  public int getHeight()
  {
    return image.getHeight();
  }

  public static BufferedImage getBWImage(int w, int h)
  {
    byte[] v = new byte[]
    {
      (byte) 0, (byte) 0xFF
    };
    IndexColorModel cm = new IndexColorModel(1, v.length, v, v, v);
    WritableRaster wr = cm.createCompatibleWritableRaster(w, h);
    return new BufferedImage(cm, wr, false, null);
  }
}