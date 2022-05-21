/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 * Copyright (C) 2018 - 2020 Klaus Kämpf <kkaempf@suse.de>
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

/*
 * Add 'min power' to FloatPowerSpeedFocusFrequencyProperty
 */

public class FloatMinMaxPowerSpeedFrequencyProperty extends FloatPowerSpeedFocusFrequencyProperty {

  private float min_power = 10.0f;
  private static final String MIN_POWER = "min power";

  public FloatMinMaxPowerSpeedFrequencyProperty()
  {
    super(true); /* hide focus */
  }

  public FloatMinMaxPowerSpeedFrequencyProperty(LaserProperty o)
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
   * @return float
   */
  public float getMinPower()
  {
    return min_power;
  }

  /**
   * Set the value of min power
   *
   * @param new min power
   */
  public void setMinPower(float power)
  {
    if (power > this.getPower()) { /* minimum must not be larger than maximum */
      power = this.getPower();
    }
    this.min_power = power;
  }

  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.add(MIN_POWER);
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    if (MIN_POWER.equals(name)) {
      return (Float) this.getMinPower();
    }
    else {
      return super.getProperty(name);
    }
  }

  @Override
  public Object getMinimumValue(String name)
  {
    if (MIN_POWER.equals(name)) {
      return 0f;
    }
    else {
      return super.getProperty(name);
    }
  }

  @Override
  public Object getMaximumValue(String name)
  {
    if (MIN_POWER.equals(name)) {
      return 100f;
    }
    else {
      return super.getProperty(name);
    }
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if (MIN_POWER.equals(name)) {
      this.setMinPower((Float) value);
    }
    else {
      super.setProperty(name, value);
    }
  }

  @Override
  public FloatMinMaxPowerSpeedFrequencyProperty clone()
  {
    FloatMinMaxPowerSpeedFrequencyProperty result = new FloatMinMaxPowerSpeedFrequencyProperty();
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
    final FloatMinMaxPowerSpeedFrequencyProperty other = (FloatMinMaxPowerSpeedFrequencyProperty) obj;
    if (this.min_power != other.min_power) {
      return false;
    }
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 97 * hash + (int)min_power;
    hash = 97 * hash + super.hashCode();
    return hash;
  }

  public String toString()
  {
      return "FloatMinMaxPowerSpeedFrequencyProperty(min power="+getMinPower()+", max power="+getPower()+", speed="+getSpeed()+", frequency="+getFrequency()+")";
  }

}
