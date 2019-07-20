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

import java.util.Arrays;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class RasterElement
{

  private byte[] imageData;
  private int stride;
  private int width;
  private int height;
  private int bitDepth;
  private int samplesPerPixel;
  
  
  public RasterElement(int width, int height)
  {
    this(width,height,1,1);
  }
    
  public RasterElement(int width, int height, int bitDepth)
  {
    this(width,height,bitDepth,1);
  }
    
  public RasterElement(int width, int height, int bitDepth, int samplesPerPixel) {
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
    long value = 0;
    for (int i = startPosInBytes; i <= endPosInBytes; i++) {
      value <<= 8;
      value |= (imageData[i] & 0xFF);
    }
    int unusedBitsRightOfSample = (8 - (endPosInBits + 1) % 8) % 8;
    long maskSampleBits = (1L << pixelLengthInBits) - 1;
    long pixel = (value >> unusedBitsRightOfSample) & maskSampleBits;
    if (!set) return (int)pixel;
    
    value &= ~(maskSampleBits << unusedBitsRightOfSample);
    value |= (replace & maskSampleBits) << unusedBitsRightOfSample;
    for (int i = endPosInBytes; i >= startPosInBytes; i--) {
      imageData[i] = (byte)(value & 0xff);
      value >>= 8;
    }
    return (int)pixel;
  }
 
 public byte[] getRasterLine(int y, byte[] bytes) {
    if ((bytes == null) || (bytes.length < stride)) {
        return Arrays.copyOfRange(imageData, y * stride, (y+1) * stride);
    }
    System.arraycopy(imageData, y * stride, bytes, 0, stride);
    return bytes;
 }
 
 public byte getByte(int x, int line) {
    return imageData[(line * stride) + x];
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

  
  boolean isBlack(int x, int y) {
    int value = getPixel(x,y);
    return value == getBlack();
  }
  
  boolean isWhite(int x, int y) {
    int value = getPixel(x,y);
    
    return value == getWhite();
  }
  
  int getBlack() {
    return 0;
  }
  
  int getWhite() {
    return (1 << bitDepth) - 1;
  }
  
  public int getWidth()
  {
    return width;
  }

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
  
  public interface Provider {
    public RasterElement getRaster();
  }
  
}