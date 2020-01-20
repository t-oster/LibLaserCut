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

package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.platform.Util;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class IModelaProperty implements LaserProperty
{

  private static String DEPTH = "milling depth (mm)";
  private static String FEED_RATE = "feed rate (mm/min)";
  private static String SPINDLE_SPEED = "spindle speed (rpm)";
  private static String TOOL = "tool";
  private static String TOOL_DIAMETER = "tool diameter (mm)";
  
  private double depth = 0;
  private double feedRate = 1;
  private int spindleSpeed = 100;
  private int tool = 1;
  private double toolDiameter = 4;

  public double getToolDiameter()
  {
    return toolDiameter;
  }
  
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
    return new String[]{DEPTH, SPINDLE_SPEED, FEED_RATE, TOOL, TOOL_DIAMETER};
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
    else if (TOOL_DIAMETER.equals(key))
    {
      toolDiameter = (Double) value;
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
    else if (TOOL_DIAMETER.equals(key))
    {
      return toolDiameter;
    }
    return null;
  }

  @Override
  public int hashCode()
  {
    int hash = 13;
    for (String k : this.getPropertyKeys())
    {
      if (this.getProperty(k) != null)
      {
        hash = 97 * hash + k.hashCode() + this.getProperty(k).hashCode();
      }
    }
    hash += super.hashCode();
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    IModelaProperty other = (IModelaProperty) obj;
    for (String k : this.getPropertyKeys())
    {
      if (Util.differ(this.getProperty(k), other.getProperty(k)))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public float getPower()
  {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void setPower(float p)
  {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public float getSpeed()
  {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
  

}
