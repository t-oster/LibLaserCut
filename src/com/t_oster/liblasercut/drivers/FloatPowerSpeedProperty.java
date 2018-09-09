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
 * Based upon PowerSpeedFocusProperty by Thomas Oster <thomas.oster@rwth-aachen.de>
 * 
 * @author Volkan Vonk <vol.vonk@yandex.com> 
 */
public class FloatPowerSpeedProperty implements LaserProperty
{
  private float power = 0;
  private float speed = 100;

  public FloatPowerSpeedProperty()
  {
  }

  /**
   * Sets the Laserpower. Valid values are from 0 to 100.
   * @param power 
   */
  public void setPower(float power)
  {
    power = power < 0 ? 0 : power;
    power = power > 100 ? 100 : power;
    this.power = power;
  }

  public float getPower()
  {
    return power;
  }

  /**
   * Sets the speed for the Laser. Valid values are from 0 to 100
   * @param speed 
   */
  public void setSpeed(float speed)
  {
    speed = speed < 0 ? 0 : speed;
    speed = speed > 100 ? 100 : speed;
    this.speed = speed;
  }

  public float getSpeed()
  {
    return speed;
  }

  @Override
  public FloatPowerSpeedProperty clone()
  {
    FloatPowerSpeedProperty p = new FloatPowerSpeedProperty();
    p.power = power;
    p.speed = speed;
    return p;
  }

  private static String[] propertyNames = new String[]{"power", "speed"};
  
  @Override
  public String[] getPropertyKeys()
  {
    return propertyNames;
  }

  @Override
  public Object getProperty(String name)
  {
    if ("power".equals(name))
    {
      return (Float) this.getPower();
    }
    else if ("speed".equals(name))
    {
      return (Float) this.getSpeed();
    }
    return null;
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if ("power".equals(name))
    {
      this.setPower((Float) value);
    }
    else if ("speed".equals(name))
    {
      this.setSpeed((Float) value);
    }
    else
    {
      throw new IllegalArgumentException("Unknown setting '"+name+"'");
    }
  }

  @Override
  public Object getMinimumValue(String name)
  {
    if ("power".equals(name))
    {
      return (Float) 0f;
    }
    else if ("speed".equals(name))
    {
      return (Float) 0f;
    }
    else
    {
      throw new IllegalArgumentException("Unknown setting '"+name+"'");
    }  
  }

  @Override
  public Object getMaximumValue(String name)
  {
    if ("power".equals(name))
    {
      return (Float) 100f;
    }
    else if ("speed".equals(name))
    {
      return (Float) 100f;
    }
    else
    {
      throw new IllegalArgumentException("Unknown setting '"+name+"'");
    }
  }  

  @Override
  public Object[] getPossibleValues(String name)
  {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
      if (obj == null) {
          return false;
      }
      if (getClass() != obj.getClass()) {
          return false;
      }
      final FloatPowerSpeedProperty other = (FloatPowerSpeedProperty) obj;
      if (Float.floatToIntBits(this.power) != Float.floatToIntBits(other.power)) {
          return false;
      }
      if (Float.floatToIntBits(this.speed) != Float.floatToIntBits(other.speed)) {
          return false;
      }
      return true;
  }

  @Override
  public int hashCode() {
      int hash = 7;
      hash = 67 * hash + Float.floatToIntBits(this.power);
      hash = 67 * hash + Float.floatToIntBits(this.speed);
      return hash;
  }

}
