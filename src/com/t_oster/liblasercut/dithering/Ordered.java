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

import com.t_oster.liblasercut.BlackWhiteRaster;
import com.t_oster.liblasercut.GreyscaleRaster;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class Ordered extends DitheringAlgorithm
{

  @Override
  protected void doDithering(GreyscaleRaster src, BlackWhiteRaster target)
  {
    int width = src.getWidth();
    int height = src.getHeight();
    int nPatWid = 4;
    int[][] filter =
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
            this.setBlack(src, target, x + xdelta, y + ydelta, src.getGreyScale(x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
      for (int xdelta = 0; xdelta < nPatWid; xdelta++)
      {
        for (int ydelta = 0; ydelta < nPatWid; ydelta++)
        {

          if (((x + xdelta) < width) && ((y + ydelta) < height))
          {
            this.setBlack(src, target, x + xdelta, y + ydelta, src.getGreyScale(x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
      setProgress((100 * pixelcount++) / (height));
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
            this.setBlack(src, target, x + xdelta, y + ydelta, src.getGreyScale(x + xdelta, y + ydelta) < filter[xdelta][ydelta]);
          }
        }
      }
    }
  }

  @Override
  public DitheringAlgorithm clone() {
    return new Ordered();
  }

  @Override
  public String toString()
  {
    return "Ordered";
  }
}
