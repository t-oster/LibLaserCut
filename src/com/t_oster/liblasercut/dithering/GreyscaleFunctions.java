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

package com.t_oster.liblasercut.dithering;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

/**
 * This is a helper static class that converts any BufferedImage into a
 * greyscale
 * It allows a choice between several different algorithms.
 *
 * Regardless of the incoming type of bufferedimage it will export a
 * 8 bit greyscale.
 *
 */
public class GreyscaleFunctions
{

  public enum GreyscaleAlgorithm
  {
    DECOMPOSE_MIN,
    DECOMPOSE_MAX,
    DESATURATED,
    INTENSITY,
    LUMINANCE,
    LUMA
  }

  public static BufferedImage greyscale(BufferedImage bi, GreyscaleAlgorithm greyscale) {
    return greyscale(bi,greyscale,0,false);
  }
  
  public static BufferedImage greyscale(BufferedImage bi, GreyscaleAlgorithm greyscale, int shift, boolean invert)
  {
    switch (greyscale)
    {
      case DECOMPOSE_MIN:
        return decomposeMin(bi,shift,invert);
      case DECOMPOSE_MAX:
        return decomposeMax(bi,shift,invert);
      case DESATURATED:
        return desaturated(bi,shift,invert);
      case LUMINANCE:
        return luminance(bi,shift,invert);
      case LUMA:
        return luma(bi,shift,invert);
    }
    return bi;
  }

  public static BufferedImage wrapBytesAsGreyscale(byte[] data, int w, int h)
  {
    DataBufferByte buffer = new DataBufferByte(data, data.length);
    int[] bands = new int[] {0};
    WritableRaster raster = WritableRaster.createInterleavedRaster(buffer, w, h, w, 1, bands, null);
    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
    ComponentColorModel cm = new ComponentColorModel(cs, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    return new BufferedImage(cm, raster, false, null);
  }

  public static BufferedImage decomposeMin(BufferedImage bi) { return decomposeMin(bi,0,false); }
  public static BufferedImage decomposeMin(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getDecomposionMin(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

  public static BufferedImage decomposeMax(BufferedImage bi) { return decomposeMax(bi,0,false); }
  public static BufferedImage decomposeMax(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getDecomposionMax(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

  public static BufferedImage desaturated(BufferedImage bi) { return desaturated(bi,0,false); }
  public static BufferedImage desaturated(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getDesaturated(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

  public static BufferedImage intensity(BufferedImage bi) { return intensity(bi,0,false); }
  public static BufferedImage intensity(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getIntensity(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

  public static BufferedImage luminance(BufferedImage bi) { return luminance(bi,0,false); }
  public static BufferedImage luminance(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getLuminance(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

  public static BufferedImage luma(BufferedImage bi) { return luma(bi,0,false); }
  public static BufferedImage luma(BufferedImage bi, int shift, boolean invert)
  {
    int width = bi.getWidth();
    int height = bi.getHeight();
    int[] rgbArray = new int[width * height];
    rgbArray = bi.getRGB(0, 0, width, height, rgbArray, 0, width);
    byte[] grey = new byte[rgbArray.length];
    for (int i = 0, q = rgbArray.length; i < q; i++)
    {
      int c = rgbArray[i];
      double value = 1d - ColorFunctions.getLuma(c);
      value = value * 0xFF;
      value += shift;
      if (invert) value = 0xFF - value;
      value = ColorFunctions.crimp((int)value);
      grey[i] = (byte)value;
    }
    return wrapBytesAsGreyscale(grey, width, height);
  }

}
