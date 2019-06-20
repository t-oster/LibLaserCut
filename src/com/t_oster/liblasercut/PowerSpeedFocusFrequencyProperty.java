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
public class PowerSpeedFocusFrequencyProperty extends AbstractLaserProperty
{

  public PowerSpeedFocusFrequencyProperty()
  {
    this(false);
  }

  /** Make a new PowerSpeedFocusFrequencyProperty that optionally utilizes the focus setting */
  public PowerSpeedFocusFrequencyProperty(boolean hideFocus)
  {
    addPropertyRanged("power", 0.0, 0, 100);
    addPropertyRanged("speed", 100, 0, 100);
    if (!hideFocus) addProperty("focus", 0.0);
    addPropertyRanged("frequency", 5000, 10, 5000);
  }
    
  public void setPower(int p)
  {
    this.setProperty("power", p);
  }
  
  public void setSpeed(int p) {
    this.setProperty("speed", p);
  }
  
  public void setFocus(float p) {
    this.setProperty("focus", p);
  }
  
  public void setFrequencey(int p) {
    this.setProperty("frequency",p);
  }
  
  public int getPower() {
    return this.getInteger("power",0);
  }

  public int getSpeed()
  {
    return this.getInteger("speed");
  }

  public float getFocus()
  {
    return this.getFloat("focus");
  }
  
  public int getFrequency() {
    return this.getInteger("frequency");
  }
}
