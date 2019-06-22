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
  
  /**
   * Finds the x coordinate for the left most pixel, since "start" depends on
   * what direction you are cutting in.
   *
   * @param y
   * @param v, seek value
   * @return x coordinate of left most non-matching pixel
   */
  protected int leftMostNotEqual(int y, int v)
  {
    for (int x = 0; x < width; x++)
    {
      int pixel = getPixel(x,y);
      if (pixel != v) return x;
    }
    return -1;
  }

    /**
   * Finds the y coordinate for the top most pixel, since "start" depends on
   * what direction you are cutting in.
   *
   * @param x
   * @param v, seek value
   * @return y coordinate of top most non-matching pixel
   */
  protected int topMostNotEqual(int x, int v)
  {
    for (int y = 0; y < height; y++)
    {
      int pixel = getPixel(x,y);
      if (pixel != v) return y;
    }
    return -1;
  }
  
  /**
   * Finds the x coordinate for the right most pixel, since "end" depends on
   * what direction you are cutting in.
   *
   * @param y, scanline to check
   * @param v, seek value
   * @return x coordinate of right most non-matching pixel
   */
  protected int rightMostNotEqual(int y, int v)
  {
    for (int x = width-1; x >= 0; x--)
    {
      int pixel = getPixel(x,y);
      if (pixel != v) return x;
    }
    return width;
  }

  /**
   * Finds the y coordinate for the bottom most pixel, since "end" depends on
   * what direction you are cutting in.
   *
   * @param x, scanline to check
   * @param v, seek value
   * @return y coordinate of bottom most non-matching pixel
   */
  protected int bottomMostNotEqual(int x, int v)
  {
    for (int y = height-1; y >= 0; y--)
    {
      int pixel = getPixel(x,y);
      if (pixel != v) return y;
    }
    return height;
  }
  
  /**
   * nextColorChange logic when heading <-
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingLeft(int x, int y, int def)
  {
    
    if (x <= -1) return def;
    if (x == 0) return -1;
    if (x == width) return width-1;
    if (width < x) return width;
    
    
    int v = getPixel(x,y);
    for (int ix = x; ix >= 0; ix--)
    {
      int pixel = getPixel(ix,y);
      if (pixel != v) return ix;
    }
    return 0;
  }

    /**
   * nextColorChange logic when heading <-
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingTop(int x, int y, int def)
  {
    
    if (y <= -1) return def;
    if (y == 0) return -1;
    if (y == height) return height-1;
    if (height < y) return height;
    
    
    int v = getPixel(x,y);
    for (int iy = y; iy >= 0; iy--)
    {
      int pixel = getPixel(x,iy);
      if (pixel != v) return iy;
    }
    return 0;
  }

  
  /**
   * nextColorChange logic when heading ->
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingRight(int x, int y, int def)
  {
    if (x < -1) return -1;
    if (x == -1) return 0;
    if (x == width-1) return width;
    if (width <= x) return def;
    
    int v = getPixel(x,y);
    for (int ix = x; ix < width; ix++)
    {
      int pixel = getPixel(ix,y);
      if (pixel != v) return ix;
    }
    return width-1;
  }
  
  
  /**
   * nextColorChange logic when heading ->
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingBottom(int x, int y, int def)
  {
    if (y < -1) return -1;
    if (y == -1) return 0;
    if (y == height-1) return height;
    if (height <= y) return def;
    
    int v = getPixel(x,y);
    for (int iy = y; iy < height; iy++)
    {
      int pixel = getPixel(x,iy);
      if (pixel != v) return iy;
    }
    return height-1;
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
  
}