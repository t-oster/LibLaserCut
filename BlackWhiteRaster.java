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
  
  private byte[] imageData;
  private int stride;
  private int width;
  private int height;
  private int bitDepth;
  private int samplesPerPixel;
  

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
    this(width,height,1,1);
  }
  
  
  public BlackWhiteRaster(int width, int height, int bitDepth)
  {
    this(width,height,bitDepth,1);
  }
  
  
  public BlackWhiteRaster(int width, int height, int bitDepth, int samplesPerPixel) {
    this.width = width;
    this.height = height;
    this.bitDepth = bitDepth;
    this.samplesPerPixel = samplesPerPixel;
    this.stride = (int)Math.ceil(bitDepth * samplesPerPixel * ((float)width) / 8.0);
    this.imageData  = new byte[stride * height];
  }
  
  
  public int getPixel(int x, int y) {
    return getPixel(x,y,0,false);
  }
  public int setPixel(int x, int y, int v) {
    return getPixel(x,y,v,true);
  }
  
  private int getPixel(int x, int y, int replace, boolean set) {
    int offset = stride * y;
    int pixelLengthInBits = samplesPerPixel * bitDepth;
    int startPosInBits = (offset * 8) + x * pixelLengthInBits;
    int endPosInBits = startPosInBits + pixelLengthInBits - 1;
    int startPosInBytes = startPosInBits / 8;
    int endPosInBytes = endPosInBits / 8;
    int value = 0;
    for (int i = startPosInBytes; i <= endPosInBytes; i++) {
      value <<= 8;
      value |= (imageData[i] & 0xFF);
    }
    int unusedBitsRightOfSample = (8 - (endPosInBits + 1) % 8) % 8;
    int maskSampleBits = (1 << pixelLengthInBits) - 1;
    int pixel = (value >> unusedBitsRightOfSample) & maskSampleBits;
    if (!set) return pixel;
    
    value &= ~(maskSampleBits << unusedBitsRightOfSample);
    value |= (replace & maskSampleBits) << unusedBitsRightOfSample;
    for (int i = endPosInBytes; i >= startPosInBytes; i--) {
      imageData[i] = (byte)(value & 0xff);
      value >>= 8;
    }
    return pixel;
  }
 
  public boolean isBlack(int x, int y)
  {
    int pixel = getPixel(x,y);
    return (pixel != 0);
  }

  public void setBlack(int x, int y, boolean black)
  {
    if (black) {
    setPixel(x,y,-1);
    }
    else {
      setPixel(x,y,0);
    }
  }

  /**
   * In bitDepth 1.
   * Returns byte where every bit represents one pixel 0=white and 1=black
   * Note: the bit order is bigendian. Meaning the most significant digit is the
   * first pixel.
   *
   * In bitDepth 8.
   * Returns the byte representing the specific grey.
   * 
   * @param x the x index of the byte, meaning 0 is the first 8 pixels (0-7), 1
   * the pixels 8-15...
   * @param y the y offset
   * @return
   */
 public byte getByte(int x, int y)
  {
    int index = y * stride + x;
    return imageData[index];
  }
 
 public byte[] getRasterLine(int y, byte[] bytes) {
    if ((bytes == null) || (bytes.length < stride)) {
        return Arrays.copyOfRange(imageData, y * stride, (y+1) * stride);
    }
    System.arraycopy(imageData, y * stride, bytes, 0, stride);
    return bytes;
 }
 
  public boolean isLineBlank(int y)
  {
    for (int i = y * stride, ie = (y + 1) * stride; i < ie; i++)
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
    int offset = y * stride;
    for (int i = 0; i < stride; i++)
    {
      if (imageData[offset + i] != 0)
      {
        return (i * 8) + (7-mostSignificantBit(imageData[offset + i]));
      }
    }
    return width;
  }

  public int rightmostBlackPixel(int y)
  {
    int offset = y * stride;
    for (int i = stride-1; i >= 0; i--)
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
    return getPixel(x,y);
  }

  @Override
  public void setGreyScale(int x, int y, int color)
  {
    setPixel(x,y, color);
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

  public byte[] getImageData()
  {
    return imageData;
  }

  public int getBitDepth()
  {
    return bitDepth;
  }

  public int getSamplesPerPixel()
  {
    return samplesPerPixel;
  }
  
}