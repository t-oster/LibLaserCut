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
import com.t_oster.liblasercut.Customizable;
import com.t_oster.liblasercut.GreyscaleRaster;
import com.t_oster.liblasercut.TimeIntensiveOperation;
import com.t_oster.liblasercut.platform.Util;
import java.util.Arrays;

/**
 *
* @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public abstract class DitheringAlgorithm extends TimeIntensiveOperation implements Customizable, Cloneable
{

  protected void setBlack(GreyscaleRaster src, BlackWhiteRaster target, int x, int y, boolean black)
  {
    if (target != null)
    {
      target.setBlack(x, y, black);
    }
    else
    {
      src.setGreyScale(x, y, black ? 0 : 255);
    }
  }

  public BlackWhiteRaster dither(GreyscaleRaster input)
  {
    BlackWhiteRaster target = new BlackWhiteRaster(input.getWidth(), input.getHeight());
    doDithering(input, target);
    return target;
  }

  public void ditherDirect(GreyscaleRaster input)
  {
    doDithering(input, null);
  }

  public void ditherDirect(GreyscaleRaster input, BlackWhiteRaster output)
  {
    doDithering(input, output);
  }

  protected abstract void doDithering(GreyscaleRaster src, BlackWhiteRaster target);

  @Override
  public String[] getPropertyKeys() {
    return new String[0];
  }

  @Override
  public void setProperty(String key, Object value) {
  }

  @Override
  public Object getProperty(String key) {
    return null;
  }

  @Override
  public abstract DitheringAlgorithm clone();

  @Override
  public abstract String toString();
  
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass()))
    {
      return false;
    }
    final DitheringAlgorithm other = (DitheringAlgorithm) o;
    String[] own = this.getPropertyKeys();
    String[] ot = other.getPropertyKeys();
    if (!Arrays.deepEquals(own, ot))
    {
      return false;
    }
    for (int i = 0; i < own.length; i++)
    {
      String key = own[i];
      if (!Util.differ(getProperty(key),other.getProperty(key)))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += this.getClass().hashCode();
    for (String key : this.getPropertyKeys())
    {
      hash += key.hashCode();
      hash += this.getProperty(key).hashCode();
    }
    return hash;
  }
}
