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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.t_oster.liblasercut;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class VectorCommand
{

  void setX(int d)
  {
    if (this.type == CmdType.MOVETO || this.type == CmdType.LINETO)
    {
      operands[0] = d;
    }
    else
    {
      throw new UnsupportedOperationException("setX not supported for " + type.toString());
    }
  }

  void setY(int d)
  {
    if (this.type == CmdType.MOVETO || this.type == CmdType.LINETO)
    {
      operands[1] = d;
    }
    else
    {
      throw new UnsupportedOperationException("setY not supported for " + type.toString());
    }
  }

  public static enum CmdType
  {

    SETPROPERTY,
    MOVETO,
    LINETO
  }
  private CmdType type;
  private int[] operands;
  private float foperand;
  private LaserProperty property;

  public VectorCommand(CmdType type, int x, int y)
  {
    if (type == CmdType.MOVETO || type == CmdType.LINETO)
    {
      this.type = type;
      this.operands = new int[]
      {
        x, y
      };
    }
    else
    {
      throw new IllegalArgumentException("Wrong number of Parameters for " + type.toString());
    }
  }

  public VectorCommand(CmdType type, LaserProperty p)
  {
    if (type == CmdType.SETPROPERTY)
    {
      this.type = type;
      this.property = p;
    }
    else
    {
      throw new IllegalArgumentException("Only valid for SETPROPERTY");
    }
  }

  public CmdType getType()
  {
    return type;
  }

  public int getX()
  {
    if (this.type == CmdType.MOVETO || this.type == CmdType.LINETO)
    {
      return operands[0];
    }
    throw new UnsupportedOperationException("getX not supported for " + type.toString());
  }

  public int getY()
  {
    if (this.type == CmdType.MOVETO || this.type == CmdType.LINETO)
    {
      return operands[1];
    }
    throw new UnsupportedOperationException("getX not supported for " + type.toString());
  }

  public LaserProperty getProperty()
  {
    if (this.type == CmdType.SETPROPERTY)
    {
      return this.property;
    }
    else
    {
      throw new UnsupportedOperationException("Only valid for PROPERTY");
    }
  }

}
