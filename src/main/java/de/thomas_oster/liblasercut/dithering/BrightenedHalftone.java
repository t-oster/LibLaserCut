/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2016 Max Gaukler <development@maxgaukler.de>
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
package de.thomas_oster.liblasercut.dithering;


final class BrightnessCalculations
{

  public static double linearMap(double x, double x1, double x2, double y1, double y2)
  {
    /**
     * linear interpolation between (x1,y1) and (x2,y2)
     * 
     * @param x1 x-value 1
     * @param x2 x-value 1
     * @param y1 x-value 1
     * @param y2 y-value of coordinate 2
     * @param x  input x-value
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
}

/**
 * Halftone algorithm with increased brightness
 *
 * @author Max Gaukler <development@maxgaukler.de>
 */
public class BrightenedHalftone extends Halftone
{

  protected int applyBrightnessShift(int value)
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
    final double[] mappingInputValues =
    {
      0, 64, 230, 255
    };

    // mapped engraving intensities (must be unique and ascending!)
    // 0 = fully engraved ... 255 = not engraved
    final double[] mappingOutputIntensities =
    {
      0, 192, 250, 255
    };

    return (int) Math.round(BrightnessCalculations.piecewiseLinearMap(value, mappingOutputIntensities, mappingInputValues));
  }

  @Override
  protected int[][] getThresholdMatrix()
  {
    // apply brightness shift on the threshold matrix
    int[][] filter = super.getThresholdMatrix();
    for (int i = 0; i < filter.length; i++)
    {
      for (int j = 0; j < filter.length; j++)
      {
        filter[i][j] = applyBrightnessShift(filter[i][j]);
      }
    }
    return filter;
  }

  @Override
  public DitheringAlgorithm clone()
  {
    return new BrightenedHalftone();
  }

  @Override
  public String toString()
  {
    return "Brightened Halftone";
  }
}
