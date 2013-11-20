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

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class VectorPart extends JobPart
{

  private LaserProperty currentCuttingProperty;
  private int maxX;
  private int maxY;
  private int minX;
  private int minY;
  private double resolution = 500;
  private List<VectorCommand> commands;

  public VectorPart(LaserProperty initialProperty, double resolution)
  {
    if (initialProperty == null)
    {
      throw new IllegalArgumentException("Initial Property must not be null");
    }
    this.resolution = resolution;
    commands = new LinkedList<VectorCommand>();
    this.currentCuttingProperty = initialProperty;
    commands.add(new VectorCommand(VectorCommand.CmdType.SETPROPERTY, initialProperty));

  }

  @Override
  public double getDPI()
  {
    return resolution;
  }

  public LaserProperty getCurrentCuttingProperty()
  {
    return currentCuttingProperty;
  }

  public void setProperty(LaserProperty cp)
  {
    this.currentCuttingProperty = cp;
    commands.add(new VectorCommand(VectorCommand.CmdType.SETPROPERTY, cp));
  }

  public VectorCommand[] getCommandList()
  {
    return commands.toArray(new VectorCommand[0]);
  }

  private void checkMin(int x, int y)
  {
    if (x < minX)
    {
      minX = x;
    }
    if (y < minY)
    {
      minY = y;
    }
  }

  private void checkMax(int x, int y)
  {
    if (x > maxX)
    {
      maxX = x;
    }
    if (y > maxY)
    {
      maxY = y;
    }
  }

  public void moveto(int x, int y)
  {
    commands.add(new VectorCommand(VectorCommand.CmdType.MOVETO, x, y));
    checkMin(x, y);
    checkMax(x, y);
  }

  public void lineto(int x, int y)
  {
    commands.add(new VectorCommand(VectorCommand.CmdType.LINETO, x, y));
    checkMin(x, y);
    checkMax(x, y);
  }

  @Override
  public int getMinX()
  {
    return minX;
  }

  @Override
  public int getMaxX()
  {
    return maxX;
  }

  @Override
  public int getMinY()
  {
    return minY;
  }

  @Override
  public int getMaxY()
  {
    return maxY;
  }
}
