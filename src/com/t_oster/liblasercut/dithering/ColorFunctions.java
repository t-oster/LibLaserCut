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

public class ColorFunctions
{

  public static int setAlpha(int rgb, int a)
  {
    return ((0x00FFFFFF & rgb) | ((a & 0xFF) << 24));
  }

  public static int setAlpha(int rgb, double a)
  {
    return setAlpha(rgb, (int) (a * 255));
  }

  public static int getAlpha(int v)
  {
    return ((v >> 24) & 0xff);
  }

  public static int getRed(int v)
  {
    return ((v >> 16) & 0xff);
  }

  public static int getGreen(int v)
  {
    return ((v >> 8) & 0xff);
  }

  public static int getBlue(int v)
  {
    return ((v) & 0xff);
  }

  public static int getRGB(int r, int g, int b)
  {
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  public static int getRGB(int r, int g, int b, int a)
  {
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  public static double getsAlpha(int v)
  {
    return ((double) ((v >> 24) & 0xff)) / 255d;
  }

  public static double getsRed(int v)
  {
    return ((double) ((v >> 16) & 0xff)) / 255d;
  }

  public static double getsGreen(int v)
  {
    return ((double) ((v >> 8) & 0xff)) / 255d;
  }

  public static double getsBlue(int v)
  {
    return ((double) ((v) & 0xff)) / 255d;
  }

  public static int crimp(int v)
  {
    if (v > 0xff)
    {
      v = 0xff;
    }
    if (v < 0)
    {
      v = 0;
    }
    return v;
  }


  static double min(double v0, double v1, double v2)
  {
    return Math.min(Math.min(v0, v1), v2);
  }

  static double max(double v0, double v1, double v2)
  {
    return Math.max(Math.max(v0, v1), v2);
  }

  public static double getBrightness(int c)
  {
    int r = getRed(c);
    int g = getGreen(c);
    int b = getBlue(c);
    int cmax = (r > g) ? r : g;
    if (b > cmax)
    {
      cmax = b;
    }
    return ((double) cmax) / 255.0d;
  }

  public static double getDecomposionMax(int c)
  {
    int r = getRed(c);
    int g = getGreen(c);
    int b = getBlue(c);
    int cmax = (r > g) ? r : g;
    if (b > cmax)
    {
      cmax = b;
    }
    return ((double) cmax) / 255.0d;
  }

  public static double getDecomposionMin(int c)
  {
    int r = getRed(c);
    int g = getGreen(c);
    int b = getBlue(c);
    int cmin = (r < g) ? r : g;
    if (b < cmin)
    {
      cmin = b;
    }
    return ((double) cmin) / 255.0d;
  }

  public static double getDesaturated(int c)
  {
    int r = getRed(c);
    int g = getGreen(c);
    int b = getBlue(c);
    int cmax = (r > g) ? r : g;
    if (b > cmax)
    {
      cmax = b;
    }
    int cmin = (r < g) ? r : g;
    if (b < cmin)
    {
      cmin = b;
    }
    return ((double) (cmax + cmin) / 2d) / 255.0d;
  }

  public static double getValue(int rgb)
  {
    return 1d - getBrightness(rgb);
  }

  public static double getIntensity(int c)
  {
    double r = getsRed(c);
    double g = getsGreen(c);
    double b = getsBlue(c);
    return (r + g + b) / 3d;
  }

  public static double getLuminance(int c)
  {
    double r = getsRed(c) * 0.3d;
    double g = getsGreen(c) * 0.59;
    double b = getsBlue(c) * 0.11;
    return r + g + b;
  }

  public static double getLuma(int c)
  {
    double r = getsRed(c) * 0.2126d;
    double g = getsGreen(c) * 0.7152;
    double b = getsBlue(c) * 0.0722;
    return r + g + b;
  }
}
