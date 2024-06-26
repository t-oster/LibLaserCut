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
package de.thomas_oster.liblasercut.vectoroptimizers;

import de.thomas_oster.liblasercut.properties.LaserProperty;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

  protected static class Element
  {
    LaserProperty prop;
    Point start;
    /**
     * List of moves. CachedEnd must be updated if moves is modified!
     */
    private final ArrayList<Point> moves = new ArrayList<>();
    private Point cachedEnd = null;
    
    // Temporary storage for use in sorting algorithms:
    int index = -1;  /// Data attached to the path. Ignored in equals().
    int startIndex = -1; /// Data attached to the start point. Ignored in equals(). Handled by invert().
    int endIndex = -1; /// Data attached to the end point. Ignored in equals(). Handled by invert() and append().

    @Override
    public boolean equals(Object o)
    {
      if (o == this)
      {
        return true;
      }
      if (!(o instanceof Element))
      {
        return false;
      }
      return equals((Element) o);
    }

    public boolean equals(Element e)
    {
      if (!this.start.equals(e.start) )
      {
        return false;//start point differs
      }
      return this.moves.equals(e.moves);//move lists are different
    }

    /**
     * Reverses the order of points.
     * Also swaps startIndex and endIndex.
     */
    void invert()
    {
      // swap endIndex <-> startIndex
      int tmp = endIndex;
      endIndex = startIndex;
      startIndex = tmp;

      cachedEnd = start;
      if (!moves.isEmpty())
      {
        moves.add(0, start);
        start = moves.remove(moves.size() - 1);
        Collections.reverse(moves);
      }
    }
    
    /**
     * Get the list of points after the start point. Do not modify this list!
     */
    ArrayList<Point> getMoves()
    {
      return moves;
    }

    Point getEnd()
    {
      if (cachedEnd != null)
      {
        return cachedEnd;
      }
      return moves.isEmpty() ? start : moves.get(moves.size() - 1);
    }

    /**
     * Append another path, discarding the other start point.
     * 
     * [a, b, c].append([d, e, f]) == [ a, b, c, e f ] (no "d"!)
     * This operation only makes sense if the current end (c) is near the other start (d).
     * 
     * @param other path to append
     */
    void append(Element other)
    {
      if (!Objects.equals(prop, other.prop)) {
        throw new IllegalArgumentException("Cannot join paths with different properties");
      }
      // the following should be approximately true: (getEnd().equals(other.start));
      moves.addAll(other.moves);
      cachedEnd = other.getEnd();
      endIndex = other.endIndex;
    }
    
    void addPoint(Point p)
    {
      moves.add(p);
      cachedEnd = p;
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

    @Override
    public String toString()
    {
      StringBuilder partial = new StringBuilder("Element {"
              + ((start == null) ? "null" : "(" + start.x + ", " + start.y + ")"));

      for (Point p : moves)
      {
        partial.append(" -> (").append(p.x).append(", ").append(p.y).append(")");
      }

      return partial + "}";
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
    List<Element> result = new ArrayList<>();
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
          cur.addPoint(new Point(cmd.getX(), cmd.getY()));
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
