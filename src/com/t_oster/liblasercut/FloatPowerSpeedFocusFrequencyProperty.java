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

/**
 * The LaserProperty holds all the parameters for parts of the LaserJob.
 * The Frequency value is ignored for Engraving operations
 *
 * @author oster
 */
public class FloatPowerSpeedFocusFrequencyProperty extends AbstractLaserProperty
{
  public FloatPowerSpeedFocusFrequencyProperty()
  {
    super();
    addPropertyRanged("power", 0.0, 0.0, 100.0);
    addPropertyRanged("speed", 100.0, 0.0, 100.0);
    addProperty("focus", 0.0);
    addProperty("frequency", 500);
  }
  public void setPower(float p)
  {
    this.setProperty("power", p);
  }
  
  public void setSpeed(float p) {
    this.setProperty("speed", p);
  }
  
  public void setFocus(float p) {
    this.setProperty("focus", p);
  }
  
  public void setFrequency(int p) {
    this.setProperty("frequency",p);
  }
  
  public float getPower() {
    return this.getFloat("power",0.0f);
  }

  public float getSpeed()
  {
    return this.getFloat("speed");
  }

  public float getFocus()
  {
    return this.getFloat("focus");
  }
  
  public int getFrequency() {
    return this.getInteger("frequency");
  }
}
