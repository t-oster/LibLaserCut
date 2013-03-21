/**
 * This file is part of VisiCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.LaserProperty;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class IModelaProperty implements LaserProperty
{

  private static String FEED_RATE = "feedRate";
  private static String SPINDLE_SPEED = "spindleSpeed";
  private static String TOOL = "tool";
  
  private double feedRate = 0;
  private double spindleSpeed = 0;
  private int tool = 1;
  
  @Override
  public Object getMinimumValue(String name)
  {
    if (TOOL.equals(name))
    {
      return (Integer) 1;
    }
    else
    {
      return (Double) 0d;
    }
  }

  @Override
  public Object getMaximumValue(String name)
  {
    return null;
  }

  @Override
  public Object[] getPossibleValues(String name)
  {
    return null;
  }

  @Override
  public LaserProperty clone()
  {
    IModelaProperty result = new IModelaProperty();
    result.feedRate = feedRate;
    result.spindleSpeed = spindleSpeed;
    result.tool = tool;
    return result;
  }

  @Override
  public String[] getPropertyKeys()
  {
    return new String[]{SPINDLE_SPEED,FEED_RATE,TOOL};
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (SPINDLE_SPEED.equals(key))
    {
      spindleSpeed = (Double) value;
    }
    else if (FEED_RATE.equals(key))
    {
      feedRate = (Double) value;
    }
    else if (TOOL.equals(key))
    {
      tool = (Integer) value;
    }
  }

  @Override
  public Object getProperty(String key)
  {
    if (SPINDLE_SPEED.equals(key))
    {
      return spindleSpeed;
    }
    else if (FEED_RATE.equals(key))
    {
      return feedRate;
    }
    else if (TOOL.equals(key))
    {
      return tool;
    }
    return null;
  }

}
