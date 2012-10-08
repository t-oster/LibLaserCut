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
public interface LaserProperty extends Cloneable
{

  /**
   * Returns the names of possible propertys,
   * e.g. for epilog-cutter this is power, speed and frequency
   * @return 
   */
  public abstract String[] getPropertyNames();
  
  /**
   * returns the value for this property
   * May be of String, Boolean, Integer, Double or Float
   * @param name
   * @return 
   */
  public abstract Object getProperty(String name);
  
  public abstract void setProperty(String name, Object value);
  
  /**
   * If name is a name of a vaild property, return the class
   * of this property's objects. else return null
   * @param name
   * @return 
   */
  public abstract Class getPropertyClass(String name);
  
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
}
