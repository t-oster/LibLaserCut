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
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public interface Customizable {
    public String[] getPropertyKeys();
    /**
     * Sets the property with the given key
     * a property may only be of the classes
     * Integer, Boolean, Double, Float and String
     * and never set to null
     * @param key
     * @param value 
     */
    public void setProperty(String key, Object value);
    /**
     * Returns the value of the property or null, if the key
     * does not name a valid property
     * A property may never return null!
     * @param key
     * @return 
     */
    public Object getProperty(String key);
}
