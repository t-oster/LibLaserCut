/**
 * This file is part of VisiCut.
 * Copyright (C) 2012 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 * 
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *    VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.t_oster.liblasercut;

import java.util.Arrays;
import java.util.Collection;

/**
 * The LaserProperty holds all the parameters for parts of the LaserJob.
 * The Frequency value is ignored for Engraving operations
 * 
 * @author oster
 */
public class PowerSpeedFocusProperty implements LaserProperty
{

  private int power = 20;
  private int speed = 100;
  private float focus = 0;

  public PowerSpeedFocusProperty()
  {
  }

  /**
   * Sets the Laserpower. Valid values are from 0 to 100.
   * In 3d-Raster mode, the intensity is scaled to this power setting
   * @param power 
   */
  public void setPower(int power)
  {
    power = power < 0 ? 0 : power;
    power = power > 100 ? 100 : power;
    this.power = power;
  }

  public int getPower()
  {
    return power;
  }

  /**
   * Sets the speed for the Laser. Valid values is from 0 to 100
   * @param speed 
   */
  public void setSpeed(int speed)
  {
    speed = speed < 0 ? 0 : speed;
    speed = speed > 100 ? 100 : speed;
    this.speed = speed;
  }

  public int getSpeed()
  {
    return speed;
  }

  /**
   * Sets the Focus aka moves the Z axis. Values are given in mm.
   * Positive values move the Z axis down aka makes the distance between
   * laser and object bigger.
   * The possible range depends on the LaserCutter, so wrong setting
   * may result in IllegalJobExceptions
   * @param focus the relative Distance from object to Laser in mm
   */
  public void setFocus(float focus)
  {
    this.focus = focus;
  }

  /**
   * Returns the relative (to the distance at starting the job) distance
   * between laser and object in mm/10s
   */
  public float getFocus()
  {
    return this.focus;
  }

  @Override
  public PowerSpeedFocusProperty clone()
  {
    PowerSpeedFocusProperty p = new PowerSpeedFocusProperty();
    p.power = power;
    p.speed = speed;
    p.focus = focus;
    return p;
  }

  private static String[] propertyNames = new String[]{"power", "speed", "focus"};
  
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
      return (Integer) this.getPower();
    }
    else if ("speed".equals(name))
    {
      return (Integer) this.getSpeed();
    }
    else if ("focus".equals(name))
    {
      return (Float) this.getFocus();
    }
    return null;
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if ("power".equals(name))
    {
      this.setPower((Integer) value);
    }
    else if ("speed".equals(name))
    {
      this.setSpeed((Integer) value);
    }
    else if ("focus".equals(name))
    {
      this.setFocus((Float) value);
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
      return (Integer) 0;
    }
    else if ("speed".equals(name))
    {
      return (Integer) 0;
    }
    else if ("focus".equals(name))
    {
      return null;
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
      return (Integer) 100;
    }
    else if ("speed".equals(name))
    {
      return (Integer) 100;
    }
    else if ("focus".equals(name))
    {
      return null;
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
  public String toString()
  {
      return "PowerSpeedFocusFrequencyProperty(power="+getPower()+",speed="+getSpeed()+",focus="+getFocus()+")";
  }
  
  

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + this.power;
        hash = 71 * hash + this.speed;
        hash = 71 * hash + Float.floatToIntBits(this.focus);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PowerSpeedFocusProperty other = (PowerSpeedFocusProperty) obj;
        if (this.power != other.power) {
            return false;
        }
        if (this.speed != other.speed) {
            return false;
        }
        if (Float.floatToIntBits(this.focus) != Float.floatToIntBits(other.focus)) {
            return false;
        }
        return true;
    }
}
