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

/**
 * Author: Sven Jung <sven.jung@rwth-aachen.de>
 */

package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import java.util.Arrays;
import java.util.LinkedList;

/**
 *
 * @author Sven
 */
public class MakeBlockXYPlotterProperty extends PowerSpeedFocusFrequencyProperty
{
  private boolean showPowerAndSpeed;
  
  public MakeBlockXYPlotterProperty(boolean showPowerAndSpeed) {
    this.showPowerAndSpeed = showPowerAndSpeed;
  }
  
  public MakeBlockXYPlotterProperty() {
    this(false);
  }
  
  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.remove("focus");
    result.remove("frequency");
    
    if(!showPowerAndSpeed) {
      result.remove("power");
      result.remove("speed");
    }
    
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    return super.getProperty(name);
  }

  @Override
  public void setProperty(String name, Object value)
  {
    super.setProperty(name, value);
  }

  @Override
  public MakeBlockXYPlotterProperty clone()
  {
    MakeBlockXYPlotterProperty result = new MakeBlockXYPlotterProperty();
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
        final MakeBlockXYPlotterProperty other = (MakeBlockXYPlotterProperty) obj;
        if (this.showPowerAndSpeed != other.showPowerAndSpeed) {
            return false;
        }
        return super.equals(other);
    }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 59 * hash + (this.showPowerAndSpeed ? 1 : 0);
    return hash;
  }
}
