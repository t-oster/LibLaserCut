/*
  This file is part of LibLaserCut.
  Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>

  LibLaserCut is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  LibLaserCut is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.

 */
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.properties.LaserProperty;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class VectorPart extends JobPart
{

  private LaserProperty currentCuttingProperty;
  private double maxX = Double.NEGATIVE_INFINITY;
  private double maxY = Double.NEGATIVE_INFINITY;
  private double minX = Double.POSITIVE_INFINITY;
  private double minY = Double.POSITIVE_INFINITY;
  private final double resolution;
  private final List<VectorCommand> commands;

  public VectorPart(LaserProperty initialProperty, double resolution)
  {
    if (initialProperty == null)
    {
      throw new IllegalArgumentException("Initial Property must not be null");
    }
    this.resolution = resolution;
    commands = new LinkedList<>();
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

  private void checkMin(double x, double y)
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

  private void checkMax(double x, double y)
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
  /**
   * move to (x,y) with laser off
   * @param x coordinate in dots (according to getDPI())
   * @param y coordinate in dots (according to getDPI()) 
   */
  public void moveto(double x, double y)
  {
    commands.add(new VectorCommand(VectorCommand.CmdType.MOVETO, x, y));
    checkMin(x, y);
    checkMax(x, y);
  }

  /**
   * cut a line to (x,y)
   * @param x coordinate in dots (according to getDPI())
   * @param y coordinate in dots (according to getDPI()) 
   */
  public void lineto(double x, double y)
  {
    // ensure that lineto() is only called after moveto(), so that the
    // VectorPart does not depend on the previous state.
    if (!commands.stream().anyMatch(cmd -> cmd.getType() == VectorCommand.CmdType.MOVETO))
    {
      throw new IllegalStateException("lineto() may only be called after moveto().");
    }
    commands.add(new VectorCommand(VectorCommand.CmdType.LINETO, x, y));
    checkMin(x, y);
    checkMax(x, y);
  }
  
  /**
   * cut or move to (x,y)
   * @param x coordinate in dots (according to getDPI())
   * @param y coordinate in dots (according to getDPI()) 
   * @param line true: cut (lineto), false: don't cut (moveto)
   */
  public void linetoOrMoveto(double x, double y, boolean line)
  {
    if (line)
    {
      lineto(x, y);
    } else {
      moveto(x, y);
    }
  }

  @Override
  public double getMinX()
  {
    if (minX == Double.POSITIVE_INFINITY)
    {
      return 0;
    }
    else
    {
      return minX;
    }
  }

  @Override
  public double getMaxX()
  {
    if (maxX == Double.NEGATIVE_INFINITY)
    {
      return 0;
    }
    else
    {
      return maxX;
    }
  }

  @Override
  public double getMinY()
  {
    if (minY == Double.POSITIVE_INFINITY)
    {
      return 0;
    }
    else
    {
      return minY;
    }
  }

  @Override
  public double getMaxY()
  {
    if (maxY == Double.NEGATIVE_INFINITY)
    {
      return 0;
    }
    else
    {
      return maxY;
    }
  }

  @Override
  public boolean isEmpty()
  {
    // VectorPart is empty if it contains no LINETO commands
    return !commands.stream().anyMatch(cmd -> cmd.getType() == VectorCommand.CmdType.LINETO);
  }
}
