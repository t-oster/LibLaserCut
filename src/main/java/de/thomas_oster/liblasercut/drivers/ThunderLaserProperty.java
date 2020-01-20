/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 * Copyright (c) 2018 - 2020 Klaus Kämpf <kkaempf@suse.de>
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
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.FloatPowerSpeedFocusProperty;
import java.util.Arrays;

/**
 *
 * @author kkaempf
 *
 * It's all pretty ugly. This should rather extend PowerSpeedFocusFrequencyProperty.
 * But this is incompatible with RasterizableJobPart(), which is expecting a FloatPowerSpeedFocusProperty :-/
 */
public class ThunderLaserProperty extends FloatPowerSpeedFocusProperty {

  private int min_power = 10;

  public ThunderLaserProperty()
  {
  }

  /**
   * Get the value of min power
   *
   * @return int
   */
  public int getMinPower()
  {
    return min_power;
  }

  /**
   * Set the value of min power
   *
   * @param new min power
   */
  public void setMinPower(int power)
  {
    if (power > this.getPower()) { /* minimum must not be larger than maximum */
      power = (int)this.getPower();
    }
    this.min_power = power;
  }

  private int speed = 100;
  private static final int MAXSPEED = 10000; // mm/s
  /**
   * Sets the speed for the Laser. Valid values is from 0 to MAXSPEED
   * @param speed
   */
  @Override
  public void setSpeed(float speed)
  {
    speed = speed < 0 ? 0 : speed;
    speed = speed > MAXSPEED ? MAXSPEED : speed;
    this.speed = (int)speed;
  }

  @Override
  public float getSpeed()
  {
    return (float)speed;
  }

  private int frequency = 100;

  /**
   * Sets the frequency for the Laser.
   * @param speed
   */
  public void setFrequency(float frequency)
  {
    frequency = frequency < 0 ? 0 : frequency;
    this.frequency = (int)frequency;
  }

  public float getFrequency()
  {
    return (float)frequency;
  }
                                                      // 0              1               2              3            4
  private static String[] propertyNames = new String[]{"Min Power(%)", "Max Power(%)", "Speed(mm/s)", "Focus(mm)", "Frequency(Hz)"};
  private static String[] superPropertyNames = new String[]{null,      "power",        "speed",       "focus",     "frequency"};
  @Override
  public String[] getPropertyKeys()
  {
    return propertyNames;
  }

  @Override
  public Object getProperty(String name)
  {
    if (propertyNames[0].equals(name)) {
      return (Integer) this.getMinPower();
    }
    else if (propertyNames[2].equals(name)) {
      return this.getSpeed();
    }
    else if (propertyNames[4].equals(name)) {
      return this.getFrequency();
    }
    else {
      int l = superPropertyNames.length;
      for (int i = 1; i < l; i++) {
        if (propertyNames[i].equals(name)) {
          return super.getProperty(superPropertyNames[i]);
        }
      }
    }
    return null;
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if (propertyNames[0].equals(name)) {
      this.setMinPower((Integer) value);
    }
    else if (propertyNames[1].equals(name)) {
      float power = (float)(Float)value;
      if (power < this.min_power) { /* (max) power must not be smaller than minimum power */
        power = (float)this.min_power;
      }
      this.setSpeed(power);
    }
    else if (propertyNames[2].equals(name)) {
      this.setSpeed((float)(Float)value);
    }
    else if (propertyNames[4].equals(name)) {
      this.setFrequency((float)(Float)value);
    }
    else {
      int l = superPropertyNames.length;
      for (int i = 1; i < l; i++) {
        if (propertyNames[i].equals(name)) {
          super.setProperty(superPropertyNames[i], value);
        }
      }
    }
  }

  @Override
  public ThunderLaserProperty clone()
  {
    ThunderLaserProperty result = new ThunderLaserProperty();
    for (String s:this.getPropertyKeys())
    {
      result.setProperty(s, this.getProperty(s));
    }
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ThunderLaserProperty other = (ThunderLaserProperty) obj;
    if (this.min_power != other.min_power) {
      return false;
    }
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 97 * hash + min_power;
    hash = 97 * hash + super.hashCode();
    return hash;
  }

  public String toString()
  {
      return "ThunderLaserProperty(min power="+getMinPower()+", power="+getPower()+", speed="+getSpeed()+", focus="+getFocus()+", frequency="+getFrequency()+")";
  }

}
