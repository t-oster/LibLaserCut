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
   * @param name
   * @return 
   */
  public abstract Object getMinimumValue(String name);
  
  public abstract Object getMaximumValue(String name);
  
  public abstract Object[] getPossibleValues(String name);
  
  public abstract LaserProperty clone();
  
  // Please override equals so that it works well
  // Otherwise there is trouble in the GUI when it tries to compare laser settings
  @Override
  public abstract boolean equals(Object obj);
}
