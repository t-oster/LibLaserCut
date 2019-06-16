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
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.LaserProperty;

/**
 *
 * @author Tat
 */
public class K40NanoRasterProperty implements LaserProperty
{
  
  static final String VAR_MM_PER_SECOND = "mm per second";
  static final String VAR_RASTER_STEP = "raster step";

  private float mm_per_second = 60;
  private int raster_step = 1;

  public K40NanoRasterProperty()
  {
  }

  @Override
  public K40NanoRasterProperty clone()
  {
    K40NanoRasterProperty p = new K40NanoRasterProperty();
    p.mm_per_second = this.mm_per_second;
    p.raster_step = this.raster_step;
    return p;
  }

  private static String[] propertyNames = new String[]
  {
    VAR_MM_PER_SECOND, VAR_RASTER_STEP
  };

  @Override
  public String[] getPropertyKeys()
  {
    return propertyNames;
  }

  @Override
  public Object getProperty(String name)
  {
    if (VAR_MM_PER_SECOND.equals(name))
    {
      return (Float) this.mm_per_second;
    }
    else if (VAR_RASTER_STEP.equals(name))
    {
      return (Integer) this.raster_step;
    }
    return null;
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if (VAR_MM_PER_SECOND.equals(name))
    {
      this.mm_per_second = (Float) value;
    }
    else if (VAR_RASTER_STEP.equals(name))
    {
      this.raster_step = (Integer) value;
    }
    else
    {
    }
  }

  @Override
  public Object getMinimumValue(String name)
  {
    if (VAR_MM_PER_SECOND.equals(name))
    {
      return (Float) 5f;
    }
    else if (VAR_RASTER_STEP.equals(name))
    {
      return (Integer) 1;
    }
    else
    {
      throw new IllegalArgumentException("Unknown setting '" + name + "'");
    }
  }

  @Override
  public Object getMaximumValue(String name)
  {
    if (VAR_MM_PER_SECOND.equals(name))
    {
      return (Float) 500f;
    }
    else if (VAR_RASTER_STEP.equals(name))
    {
      return (Integer) 64;
    }
    else
    {
      throw new IllegalArgumentException("Unknown setting '" + name + "'");
    }
  }

  @Override
  public Object[] getPossibleValues(String name)
  {
    return null;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 47 * hash + Float.floatToIntBits(this.mm_per_second);
    hash = 47 * hash + this.raster_step;
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    final K40NanoRasterProperty other = (K40NanoRasterProperty) obj;
    if (Float.floatToIntBits(this.mm_per_second) != Float.floatToIntBits(other.mm_per_second))
    {
      return false;
    }
    if (this.raster_step != other.raster_step)
    {
      return false;
    }
    return true;
  }

}
