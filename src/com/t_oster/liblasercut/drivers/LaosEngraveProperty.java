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

import com.t_oster.liblasercut.LaserProperty;
import java.util.Arrays;
import java.util.LinkedList;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosEngraveProperty extends LaosCutterProperty
{
  
  public LaosEngraveProperty(boolean hidePurge, boolean hideVentilation, boolean hideFocus, boolean hideFrequency)
  {
    super(hidePurge, hideVentilation, hideFocus, hideFrequency);
  }
  
  public LaosEngraveProperty()
  {};
  
  public LaosEngraveProperty(LaserProperty o)
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
  
  private static final String BOTTOM_UP = "engrave bottom up";
  private boolean engraveBottomUp = false;
  public boolean isEngraveBottomUp()
  {
    return engraveBottomUp;
  }
  private static final String UNIDIRECTIONAL = "engrave unidirectional";
  private boolean engraveUnidirectional = false;
  public boolean isEngraveUnidirectional()
  {
    return engraveUnidirectional;
  }
  
  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.add(BOTTOM_UP);
    result.add(UNIDIRECTIONAL);
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    if (BOTTOM_UP.equals(name))
    {
      return (Boolean) engraveBottomUp;
    }
    else if (UNIDIRECTIONAL.equals(name))
    {
      return (Boolean) engraveUnidirectional;
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
    else if (UNIDIRECTIONAL.equals(name))
    {
      engraveUnidirectional = (Boolean) value;
    }
    else
    {
      super.setProperty(name, value);
    }
  }
  
}
