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
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.PowerSpeedFocusProperty;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * This class adds custom properties for Epilog engraving.
 *
 * @author Ben Buxton <bbuxton@gmail.com>
 */
public class EpilogEngraveProperty extends PowerSpeedFocusProperty
{
  public EpilogEngraveProperty(boolean hideSoftwareFocus) {
    super(hideSoftwareFocus);
  }
  public EpilogEngraveProperty()
  {};
  
  private static final String BOTTOM_UP = "bottom up";
  private boolean engraveBottomUp = false;
  public boolean isEngraveBottomUp()
  {
    return engraveBottomUp;
  }
  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.add(BOTTOM_UP);
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    if (BOTTOM_UP.equals(name))
    {
      return (Boolean) engraveBottomUp;
    }
    else
    {
      return super.getProperty(name);
    }
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if (BOTTOM_UP.equals(name))
    {
      engraveBottomUp = (Boolean) value;
    }
    else
    {
      super.setProperty(name, value);
    }
  }
  
  @Override
  public EpilogEngraveProperty clone()
  {
    EpilogEngraveProperty result = new EpilogEngraveProperty();
    for (String s:this.getPropertyKeys())
    {
      result.setProperty(s, this.getProperty(s));
    }
    return result;
  }
}
