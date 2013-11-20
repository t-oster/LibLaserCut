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
public class Grid extends DitheringAlgorithm
{

  private static String[] properties = new String[]{"Blocksize", "Blockdistance"};

  @Override
  public String[] getPropertyKeys()
  {
    return properties;
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (properties[0].equals(key))
    {
      this.blocksize = (Integer) value;
    }
    else if (properties[1].equals(key))
    {
      this.blockdistance = (Integer) value;
    }
    else
    {
      throw new IllegalArgumentException("No such key "+key);
    }
  }

  @Override
  public Object getProperty(String key)
  {
    if (properties[0].equals(key))
    {
      return (Integer) this.blocksize;
    }
    else if (properties[1].equals(key))
    {
      return (Integer) this.blockdistance;
    }
    throw new IllegalArgumentException("No such key "+key);
  }

  protected int blocksize = 10;
  protected int blockdistance = 5;

  @Override
  protected void doDithering(GreyscaleRaster src, BlackWhiteRaster target)
  {
    long lumTotal = 0;
    int pixelcount = 0;
    int width = src.getWidth();
    int height = src.getHeight();

    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        lumTotal += src.getGreyScale(x, y);
      }
      setProgress((100 * pixelcount++) / (2 * height));
    }

    int thresh = (int) (lumTotal / height / width);
    for (int y = 0; y < height; y++)
    {
      for (int x = 0; x < width; x++)
      {
        if (y % (blocksize + blockdistance) <= blocksize
          && x % (blocksize + blockdistance) <= blocksize
          && src.getGreyScale(x, y) < thresh)
        {
          this.setBlack(src, target, x, y, true);
        }
        else
        {
          this.setBlack(src, target, x, y, false);
        }
      }
      setProgress((100 * pixelcount++) / (2 * height));
    }
  }

  @Override
  public DitheringAlgorithm clone() {
    Grid clone = new Grid();
    clone.blockdistance = blockdistance;
    clone.blocksize = blocksize;
    return clone;
  }

  @Override
  public String toString()
  {
    return "Grid";
  }
}
