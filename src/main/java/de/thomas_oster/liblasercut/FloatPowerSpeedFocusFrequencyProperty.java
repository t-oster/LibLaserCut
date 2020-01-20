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
package de.thomas_oster.liblasercut;

/**
 * The LaserProperty holds all the parameters for parts of the LaserJob.
 * The Frequency value is ignored for Engraving operations
 *
 * @author oster
 */
public class FloatPowerSpeedFocusFrequencyProperty implements LaserProperty
{

  private float power = 0;
  private float speed = 100;
  private float focus = 0;
  private int frequency = 500;

  public FloatPowerSpeedFocusFrequencyProperty()
  {
  }

  /**
   * Sets the Laserpower. Valid values are from 0 to 100.
   * In 3d-Raster mode, the intensity is scaled to this power setting
   * @param power 
   */
  @Override
  public void setPower(float power)
  {
    power = power < 0 ? 0 : power;
    power = power > 100 ? 100 : power;
    this.power = power;
  }

  @Override
  public float getPower()
  {
    return power;
  }

  /**
   * Sets the speed for the Laser. Valid values is from 0 to 100
   * @param speed 
   */
  public void setSpeed(float speed)
  {
    speed = speed < 0 ? 0 : speed;
    speed = speed > 100 ? 100 : speed;
    this.speed = speed;
  }

  @Override
  public float getSpeed()
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
  
  public void setFrequency(int f)
  {
    this.frequency = f;
  }
  
  public int getFrequency()
  {
    return this.frequency;
  }

  @Override
  public FloatPowerSpeedFocusFrequencyProperty clone()
  {
    FloatPowerSpeedFocusFrequencyProperty p = new FloatPowerSpeedFocusFrequencyProperty();
    p.focus = focus;
    p.frequency = frequency;
    p.power = power;
    p.speed = speed;
    return p;
  }

  private static String[] propertyNames = new String[]{"power", "speed", "focus", "frequency"};
  
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
    else if ("focus".equals(name))
    {
      return (Float) this.getFocus();
    }
    else if ("frequency".equals(name))
    {
      return (Integer) this.getFrequency();
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
    else if ("focus".equals(name))
    {
      this.setFocus((Float) value);
    }
    else if ("frequency".equals(name))
    {
      this.setFrequency((Integer) value);
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
    else if ("focus".equals(name))
    {
      return null;
    }
    else if ("frequency".equals(name))
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
      return (Float) 100f;
    }
    else if ("speed".equals(name))
    {
      return (Float) 100f;
    }
    else if ("focus".equals(name))
    {
      return null;
    }
    else if ("frequency".equals(name))
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
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FloatPowerSpeedFocusFrequencyProperty other = (FloatPowerSpeedFocusFrequencyProperty) obj;
        if (Float.floatToIntBits(this.power) != Float.floatToIntBits(other.power)) {
            return false;
        }
        if (Float.floatToIntBits(this.speed) != Float.floatToIntBits(other.speed)) {
            return false;
        }
        if (Float.floatToIntBits(this.focus) != Float.floatToIntBits(other.focus)) {
            return false;
        }
        if (this.frequency != other.frequency) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Float.floatToIntBits(this.power);
        hash = 67 * hash + Float.floatToIntBits(this.speed);
        hash = 67 * hash + Float.floatToIntBits(this.focus);
        hash = 67 * hash + this.frequency;
        return hash;
    }
  

}
