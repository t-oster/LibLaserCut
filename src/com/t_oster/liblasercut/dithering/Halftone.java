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

package com.t_oster.liblasercut.dithering;

/**
 * 8x8 cluster ordered dithering, which produces circles of varying diameter.
 * This pattern is more robust against inaccuracies from material variations
 * or mechanical vibration of the lasercutter.
 */
public class Halftone extends Ordered
{

  @Override
  protected int[][] getThresholdMatrix()
  {
    // 8x8 cluster ordered dithering
    // values and scaling given in http://caca.zoy.org/study/part2.html
    int[][] filter =
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

    for (int i = 0; i < filter.length; i++)
    {
      for (int j = 0; j < filter.length; j++)
      {
        filter[i][j] = (1 + filter[i][j]) * 256 / (1 + filter.length * filter.length);
      }
    }
    return filter;
  }

  @Override
  public DitheringAlgorithm clone()
  {
    return new Halftone();
  }

  @Override
  public String toString()
  {
    return "Halftone";
  }
}
