/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     LibLaserCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LibLaserCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with LibLaserCut.  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.LaserProperty;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class IModelaProperty implements LaserProperty
{

  private static String DEPTH = "milling depth";
  private static String FEED_RATE = "feed rate";
  private static String SPINDLE_SPEED = "spindle speed";
  private static String TOOL = "tool";
  
  private double depth = 0;
  private double feedRate = 1;
  private int spindleSpeed = 100;
  private int tool = 1;

  public double getDepth()
  {
    return depth;
  }

  public double getFeedRate()
  {
    return feedRate;
  }

  public int getSpindleSpeed()
  {
    return spindleSpeed;
  }

  public int getTool()
  {
    return tool;
  }
  
  @Override
  public Object getMinimumValue(String name)
  {
    if (TOOL.equals(name))
    {
      return (Integer) 1;
    }
    else if (SPINDLE_SPEED.equals(name))
    {
      return (Integer) 100;
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
    for (String k : getPropertyKeys())
    {
      result.setProperty(k, getProperty(k));
    }
    return result;
  }

  @Override
  public String[] getPropertyKeys()
  {
    return new String[]{DEPTH, SPINDLE_SPEED, FEED_RATE, TOOL};
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (DEPTH.equals(key))
    {
      depth = (Double) value;
    }
    else if (SPINDLE_SPEED.equals(key))
    {
      spindleSpeed = (Integer) value;
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
    if (DEPTH.equals(key))
    {
      return depth;
    }
    else if (SPINDLE_SPEED.equals(key))
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
