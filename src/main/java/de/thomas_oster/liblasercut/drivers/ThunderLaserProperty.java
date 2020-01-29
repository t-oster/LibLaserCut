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

import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.FloatPowerSpeedFocusFrequencyProperty;
import java.util.Arrays;
import java.util.LinkedList;

/**
 *
 * @author kkaempf
 *
 * It's all pretty ugly. This should rather extend PowerSpeedFocusFrequencyProperty.
 * But this is incompatible with RasterizableJobPart(), which is expecting a FloatPowerSpeedFocusProperty :-/
 */
public class ThunderLaserProperty extends FloatPowerSpeedFocusFrequencyProperty {

  private int min_power = 10;
  private static final String MIN_POWER = "Min Power(%)";

  public ThunderLaserProperty()
  {
  }

  public ThunderLaserProperty(LaserProperty o)
  {
    for (String k : o.getPropertyKeys())
    {
      try
      {
        this.setProperty(k, o.getProperty(k));
      }
      catch (Exception e)
      {
      }
    }
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

  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.add(MIN_POWER);
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    if (MIN_POWER.equals(name)) {
      return (Integer) this.getMinPower();
    }
    else {
      return super.getProperty(name);
    }
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if (MIN_POWER.equals(name)) {
      this.setMinPower((Integer) value);
    }
    else {
      super.setProperty(name, value);
    }
  }

  @Override
  public ThunderLaserProperty clone()
  {
    ThunderLaserProperty result = new ThunderLaserProperty();
    try {
      for (String s:this.getPropertyKeys())
      {
        result.setProperty(s, this.getProperty(s));
      }
    }
    catch (Exception e)
    {
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
