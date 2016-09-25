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
package com.t_oster.liblasercut.vectoroptimizers;

import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Rectangle;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public abstract class VectorOptimizer
{

  public enum OrderStrategy
  {
    FILE,
    NEAREST,
    INNER_FIRST,
    SMALLEST_FIRST,
    DELETE_DUPLICATE_PATHS
  }

  protected class Element
  {

    LaserProperty prop;
    Point start;
    List<Point> moves = new LinkedList<Point>();

  public boolean equals(Element e)
    {
      if(this.moves.size()==e.moves.size())
      {
        if (!this.start.equals(e.start) )
        {
          return false;//start point differs
        }
        
        for (int j = 0; j < this.moves.size(); j++)
        {
          if (!this.moves.get(j).equals(e.moves.get(j)))
          {
            return false;//one move point differs
          }
        }
      }
      else
      {
        return false;
      }
      return true;
    }

    void invert()
    {
      if (!moves.isEmpty())
      {
        moves.add(0, start);
        start = moves.remove(moves.size() - 1);
        List<Point> inv = new LinkedList<Point>();
        while (!moves.isEmpty())
        {
          inv.add(moves.remove(moves.size() - 1));
        }
        moves = inv;
      }
    }

    Point getEnd()
    {
      return moves.isEmpty() ? start : moves.get(moves.size() - 1);
    }

    /**
     * compute bounding box of moves, including start point
     *
     * @return Rectangle
     */
    Rectangle boundingBox()
    {
      if (start == null)
      { // TODO may this happen?
        return null;
      }
      Rectangle bb = new Rectangle(start.x, start.y, start.x, start.y);
      for (Point p : moves)
      {
        bb.add(p);
      }
      return bb;
    }

    /**
     * test if this Element represents a closed path (polygon)
     *
     * @return true if start equals end, false otherwise
     */
    boolean isClosedPath()
    {
      if ((start == null) || moves.isEmpty())
      {
        return false;
      }
      return getEnd().equals(start);
    }
  }

  public static VectorOptimizer create(OrderStrategy s)
  {
    switch (s)
    {
      case FILE:
        return new FileVectorOptimizer();
      case NEAREST:
        return new NearestVectorOptimizer();
      case INNER_FIRST:
        return new InnerFirstVectorOptimizer();
      case SMALLEST_FIRST:
        return new SmallestFirstVectorOptimizer();
      case DELETE_DUPLICATE_PATHS:
        return new DeleteDuplicatePathsOptimizer();
    }
    throw new IllegalArgumentException("Unknown Order Strategy: " + s);
  }

  protected List<Element> divide(VectorPart vp)
  {
    List<Element> result = new LinkedList<Element>();
    Element cur = null;
    Point lastMove = null;
    LaserProperty lastProp = null;
    boolean stop = false;
    for (VectorCommand cmd : vp.getCommandList())
    {
      switch (cmd.getType())
      {
        case MOVETO:
        {
          lastMove = new Point(cmd.getX(), cmd.getY());
          stop = true;
          break;
        }
        case LINETO:
        {
          if (stop)
          {
            stop = false;
            if (cur != null)
            {
              result.add(cur);
            }
            cur = new Element();
            cur.start = lastMove;
            cur.prop = lastProp;
          }
          cur.moves.add(new Point(cmd.getX(), cmd.getY()));
          break;
        }
        case SETPROPERTY:
        {
          lastProp = cmd.getProperty();
          stop = true;
          break;
        }
      }
    }
    if (cur != null)
    {
      result.add(cur);
    }
    return result;
  }

  protected double dist(Point a, Point b)
  {
    return Math.sqrt((a.y - b.y) * (a.y - b.y) + (a.x - b.x) * (a.x - b.x));
  }

  protected abstract List<Element> sort(List<Element> e);

  public VectorPart optimize(VectorPart vp)
  {
    List<Element> opt = this.sort(this.divide(vp));
    LaserProperty cp = opt.isEmpty() ? vp.getCurrentCuttingProperty() : opt.get(0).prop;
    VectorPart result = new VectorPart(cp, vp.getDPI());
    for (Element e : opt)
    {
      if (!e.prop.equals(cp))
      {
        result.setProperty(e.prop);
        cp = e.prop;
      }
      result.moveto(e.start.x, e.start.y);
      for (Point p : e.moves)
      {
        result.lineto(p.x, p.y);
      }
    }
    return result;
  }
}
