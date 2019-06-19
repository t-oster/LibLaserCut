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

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Properties;

public class DitherFunctions
{

  private static final int DEFAULT_BLOCKSIZE = 10;
  private static final int DEFAULT_BLOCKDISTANCE = 5;

  public static enum DitherAlgorithm
  {
    FLOYD_STEINBERG("Floyd-Steinberg"),
    AVERAGE("Average Dithering"),
    RANDOM("Random"),
    ORDERED("Ordered"),
    GRID("Grid",new String[]{"Blocksize", "Blockdistance"}),
    HALFTONE("Halftone"),
    BRIGHTENED_HALFTONE("Brightened Halftone");

    public String name;
    public String[] properties;

    private DitherAlgorithm(String name)
    {
      this.name = name;
      properties = null;
    }
    private DitherAlgorithm(String name, String[] properties) {
      this.name = name;
      this.properties = properties;
    }
  }
  
  public static BufferedImage dither(BufferedImage bi) {
    return dither(bi, DitherAlgorithm.FLOYD_STEINBERG);
  }
  
  public static BufferedImage dither(BufferedImage bi, DitherAlgorithm dither) {
    return dither(bi, dither, null);
  }
  
  public static BufferedImage dither(BufferedImage bi, DitherAlgorithm dither, Properties p)
  {
    switch (dither)
    {
      case FLOYD_STEINBERG:
        return Floyd_Steinberg(bi);
      case AVERAGE:
        return Average(bi);
      case RANDOM:
        return random(bi);
      case ORDERED:
        return ordered(bi);
      case GRID:
        if (p == null) return grid(bi);
        else {
          int blocksize = (Integer)p.getOrDefault("Blocksize", DEFAULT_BLOCKSIZE);
          int blockdistance = (Integer)p.getOrDefault("Blockdistance", DEFAULT_BLOCKDISTANCE);
          return grid(bi, blocksize, blockdistance);
        }
      case HALFTONE:
        return halftone(bi);
      case BRIGHTENED_HALFTONE:
        return brighter_halftone(bi);
    }
    throw new UnsupportedOperationException ("Not a recognized dither.");
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
  public static int getGreyScale(BufferedImage src, int x, int y)
  {
    return src.getRGB(x, y) & 0xFF;
  }

  public static void setBlack(BufferedImage target, int x, int y, boolean b)
  {
    if (b)
    {
      target.setRGB(x, y, 0);
    }
    else
    {
      target.setRGB(x, y, 0xFFFFFFFF);
    }
  }

  public static BufferedImage Floyd_Steinberg(BufferedImage src)
  {
    int width = src.getWidth();
    int height = src.getHeight();
    BufferedImage target = getBWImage(width, height);
    int[][] input = new int[2][width];

    src.getRGB(0, 1, width, 1, input[1], 0, width);
    for (int i = 0; i < width; i++)
    {
      input[1][i] &= 0xFF;
    }
    int error;
    for (int y = 0; y < height; y++)
    {
      // copy lower line to upper line
      // and read in next line from picture
      int[] t = input[0];
      input[0] = input[1];
      input[1] = t;
      src.getRGB(0, y + 1, width, 1, input[1], 0, width);

      for (int x = 0; x < width; x++)
      {
        int pixel = input[0][x];
        if ((pixel & 0xFF) >= 127)
        {
          target.setRGB(x, y, 0);
          error = pixel;
        }
        else
        {
          target.setRGB(x, y, 0xFFFFFFFF);
          error = pixel - 255;
        }
        if (x + 1 < width)
        {
          input[0][x + 1] = (input[0][x + 1] + 7 * error / 16);
          if (y + 1 < height)
          {
            input[1][x + 1] = (input[1][x + 1] + 1 * error / 16);
          }
        }
        if (y + 1 < height)
        {
          input[1][x] = (input[1][x] + 5 * error / 16);
          if (x > 0)
          {
            input[1][x - 1] = (input[1][x - 1] + 3 * error / 16);
          }
        }
      }
    }
    return target;
  }

  public static BufferedImage Average(BufferedImage src)
  {
    long lumTotal = 0;
    int width = src.getWidth();
    int height = src.getHeight();

    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        lumTotal += src.getRGB(x, y) & 0xFF;
      }
    }

    BufferedImage target = getBWImage(width, height);
    int thresh = (int) (lumTotal / height / width);
    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        setBlack(target,x,y,getGreyScale(src,x,y) < thresh);
      }
    }
    return target;
  }

  public static BufferedImage random(BufferedImage src)
  {
    int width = src.getWidth();
    int height = src.getHeight();
    java.util.Random r = new java.util.Random();
    BufferedImage target = getBWImage(width, height);
    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        setBlack(target,x,y,getGreyScale(src,x,y) < r.nextInt(256));
      }
    }
    return target;
  }

  private static int[][] getOrderedMatrix()
  {
    return new int[][]
    {
      {
        16, 144, 48, 176
      },
      {
        208, 80, 240, 112
      },
      {
        64, 192, 32, 160
      },
      {
        256, 128, 224, 96
      },
    };
  }

  private static int[][] getHalftoneMatrix()
  {
    // 8x8 cluster ordered dithering
    // values and scaling given in http://caca.zoy.org/study/part2.html
    return new int[][]
    {
      {
        24, 10, 12, 26, 35, 47, 49, 37
      },
      {
        8, 0, 2, 14, 45, 59, 61, 51
      },
      {
        22, 6, 4, 16, 43, 57, 63, 53
      },
      {
        30, 20, 18, 28, 33, 41, 55, 39
      },
      {
        34, 46, 48, 36, 25, 11, 13, 27
      },
      {
        44, 58, 60, 50, 9, 1, 3, 15
      },
      {
        42, 56, 62, 52, 23, 7, 5, 17
      },
      {
        32, 40, 54, 38, 31, 21, 19, 29
      },
    };
  }

  private static int[][] getScaledHalftoneMatrix()
  {
    int[][] filter = getHalftoneMatrix();
    for (int i = 0; i < filter.length; i++)
    {
      for (int j = 0; j < filter[i].length; j++)
      {
        filter[i][j] = (1 + filter[i][j]) * 256 / (1 + filter.length * filter.length);
      }
    }
    return filter;
  }

  public static BufferedImage ordered(BufferedImage src)
  {
    return ordered(src, getOrderedMatrix());
  }

  public static BufferedImage halftone(BufferedImage src)
  {
    return ordered(src, getScaledHalftoneMatrix());
  }

  public static BufferedImage brighter_halftone(BufferedImage src)
  {
    return ordered(src, getBrighterHalftoneMatrix());
  }

  public static BufferedImage ordered(BufferedImage src, int[][] filter)
  {
    int width = src.getWidth();
    int height = src.getHeight();
    BufferedImage target = getBWImage(width, height);
    int nPatWid = filter.length;

    int x = 0;
    int y = 0;
    int pixelcount = 0;

    for (y = 0; y < (height - nPatWid); y = y + nPatWid)
    {

      for (x = 0; x < (width - nPatWid); x = x + nPatWid)
      {

        for (int xdelta = 0; xdelta < nPatWid; xdelta++)
        {
          for (int ydelta = 0; ydelta < nPatWid; ydelta++)
          {
            setBlack(target, x + xdelta, y + ydelta, getGreyScale(src, x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
      for (int xdelta = 0; xdelta < nPatWid; xdelta++)
      {
        for (int ydelta = 0; ydelta < nPatWid; ydelta++)
        {

          if (((x + xdelta) < width) && ((y + ydelta) < height))
          {
            setBlack(target, x + xdelta, y + ydelta, getGreyScale(src, x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
    }

    // y is at max; loop through x
    for (x = 0; x < (width); x = x + nPatWid)
    {
      for (int xdelta = 0; xdelta < nPatWid; xdelta++)
      {
        for (int ydelta = 0; ydelta < nPatWid; ydelta++)
        {

          if (((x + xdelta) < width) && ((y + ydelta) < height))
          {
            setBlack(target, x + xdelta, y + ydelta, getGreyScale(src, x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
    }
    return target;
  }

  public static BufferedImage grid(BufferedImage src)
  {
    return grid(src, DEFAULT_BLOCKSIZE, DEFAULT_BLOCKDISTANCE);
  }

  public static BufferedImage grid(BufferedImage src, int blocksize, int blockdistance)
  {

    long lumTotal = 0;
    int width = src.getWidth();
    int height = src.getHeight();
    BufferedImage target = getBWImage(width, height);

    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        lumTotal += getGreyScale(src, x, y);
      }
    }

    int thresh = (int) (lumTotal / height / width);
    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        setBlack(target, x, y,
          (y % (blocksize + blockdistance) <= blocksize
          && x % (blocksize + blockdistance) <= blocksize
          && getGreyScale(src, x, y) < thresh));
      }
    }
    return target;
  }

  public static int[][] getBrighterHalftoneMatrix()
  {
    // apply brightness shift on the threshold matrix
    int[][] filter = DitherFunctions.getScaledHalftoneMatrix();
    for (int i = 0; i < filter.length; i++)
    {
      for (int j = 0; j < filter.length; j++)
      {
        filter[i][j] = applyBrightnessShift(filter[i][j]);
      }
    }
    return filter;
  }

  public static double linearMap(double x, double x1, double x2, double y1, double y2)
  {
    /**
     * linear interpolation between (x1,y1) and (x2,y2)
     *
     * @param x1 x-value 1
     * @param x2 x-value 1
     * @param y1 x-value 1
     * @param y2 y-value of coordinate 2
     * @param x input x-value
     * @return interpolated value of y for x
     */
    return (x - x1) / (x2 - x1) * (y2 - y1) + y1;
  }

  public static double piecewiseLinearMap(double value, double[] x, double[] y)
  {
    /**
     * piecewise linear interpolation between (x,y) coordinate pairs
     *
     * @param x x-values in ascending order
     * @param y y-values y=f(x)
     * @return interpolated value f(value); y[0] or y[last] if x out of bounds
     */
    // too small or too large input values: clamp to y[0] or y[last]
    if (value < x[0])
    {
      return y[0];
    }
    if (value > x[x.length - 1])
    {
      return y[x.length - 1];
    }

    // linear interpolation inbetween.
    // find matching x-range, return interpolated value
    int i;
    for (i = 1; i < x.length; i++)
    {
      if ((x[i - 1] <= value) && (value <= x[i]))
      {
        break;
      }
    }
    return linearMap(value, x[i - 1], x[i], y[i - 1], y[i]);
  }

  /**
   * Halftone algorithm with increased brightness
   *
   * @author Max Gaukler <development@maxgaukler.de>
   */
  protected static int applyBrightnessShift(int value)
  {
    // apply brightness correction on a single threshold matrix entry

    // piecewise linear curve
    // Please note: The mapping is applied on the threshold matrix,
    // not on the image values. Therefore, the inverse function of the
    // transformation is used:
    // Let y=f(x) be the applied brightness transformation function
    // and x=f_inverse(y) its inverse.
    // The pixel is black if (f(value) > threshold),
    // which is equivalent to (value > f_inverse(threshold)).
    // list of image grayscale intensity values (0=black ... 255=white)
    double[] mappingInputValues =
    {
      0, 64, 230, 255
    };

    // mapped engraving intensities (must be unique and ascending!)
    // 0 = fully engraved ... 255 = not engraved
    double[] mappingOutputIntensities =
    {
      0, 192, 250, 255
    };

    return (int) Math.round(piecewiseLinearMap(value, mappingOutputIntensities, mappingInputValues));
  }

}
