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
package com.t_oster.liblasercut;

public class FloatPowerAbsSpeedFocusProperty extends FloatPowerSpeedFocusProperty
{
  private float max_speed = 100;
  
  public FloatPowerAbsSpeedFocusProperty(float max_speed)
  {
    this.max_speed = max_speed;
  }
  
  public void setSpeed(float speed)
  {
    speed = speed < 0 ? 0 : speed;
    speed = speed > max_speed ? max_speed : speed;
    this.speed = speed;
  }

  private static String[] propertyNames = new String[]{"power (%)", "speed (mm/min)", "focus (mm)"};

  @Override
  public String[] getPropertyKeys()
  {
    return propertyNames;
  }

  @Override
  public FloatPowerAbsSpeedFocusProperty clone()
  {
    FloatPowerAbsSpeedFocusProperty p = new FloatPowerAbsSpeedFocusProperty(this.max_speed);
    p.focus = focus;
    p.power = power;
    p.speed = speed;
    return p;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
        return false;
    }
    if (getClass() != obj.getClass()) {
        return false;
    }
    final FloatPowerAbsSpeedFocusProperty other = (FloatPowerAbsSpeedFocusProperty) obj;
    if (Float.floatToIntBits(this.power) != Float.floatToIntBits(other.power)) {
        return false;
    }
    if (Float.floatToIntBits(this.speed) != Float.floatToIntBits(other.speed)) {
        return false;
    }
    if (Float.floatToIntBits(this.max_speed) != Float.floatToIntBits(other.max_speed)) {
        return false;
    }
    if (Float.floatToIntBits(this.focus) != Float.floatToIntBits(other.focus)) {
        return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 67 * hash + Float.floatToIntBits(this.power);
    hash = 67 * hash + Float.floatToIntBits(this.speed);
    hash = 67 * hash + Float.floatToIntBits(this.max_speed);
    hash = 67 * hash + Float.floatToIntBits(this.focus);
    return hash;
  }

}
