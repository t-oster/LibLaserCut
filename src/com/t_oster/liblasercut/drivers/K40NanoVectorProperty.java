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
public class K40NanoVectorProperty implements LaserProperty
{
  
  static final String VAR_MM_PER_SECOND = "mm per second";
  static final String VAR_D_RATIO = "diagonal speed adjustment";

  private float mm_per_second = 30;
  private float d_ratio = 0.2612f;

  public K40NanoVectorProperty()
  {
  }

  @Override
  public K40NanoVectorProperty clone()
  {
    K40NanoVectorProperty p = new K40NanoVectorProperty();
    p.mm_per_second = this.mm_per_second;
    p.d_ratio = this.d_ratio;
    return p;
  }

  private static String[] propertyNames = new String[]
  {
    VAR_MM_PER_SECOND, VAR_D_RATIO
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
    else if (VAR_D_RATIO.equals(name))
    {
      return (Float) this.d_ratio;
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
    else if (VAR_D_RATIO.equals(name))
    {
      this.d_ratio = (Float) value;
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
      return (Float) 0.4f;
    }
    else if (VAR_D_RATIO.equals(name))
    {
      return (Float) 0f;
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
      return (Float) 240f;
    }
    else if (VAR_D_RATIO.equals(name))
    {
      return (Float) 1f;
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
    hash = 47 * hash + Float.floatToIntBits(this.d_ratio);
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
    final K40NanoVectorProperty other = (K40NanoVectorProperty) obj;
    if (Float.floatToIntBits(this.mm_per_second) != Float.floatToIntBits(other.mm_per_second))
    {
      return false;
    }
    if (Float.floatToIntBits(this.d_ratio) != Float.floatToIntBits(other.d_ratio))
    {
      return false;
    }
    return true;
  }

}
