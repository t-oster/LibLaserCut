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
public interface LaserProperty extends Cloneable, Customizable
{
  
  /**
   * returns the minimum value of this property if it is
   * of type Double, Integer or Float and a minimum value
   * exists. Otherwise it returns null;
   */
  Object getMinimumValue(String name);
  
  Object getMaximumValue(String name);
  
  Object[] getPossibleValues(String name);
  
  LaserProperty clone();
  
  // Please override equals so that it works well
  // Otherwise there is trouble in the GUI when it tries to compare laser settings
  @Override
  boolean equals(Object obj);
  
  
  // The following functions are optional - if your device does not support speed/power, just make them return 100 (or an arbitrary value).
  
  float getPower();
  
  void setPower(float p);
  
  float getSpeed();
}
