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
package com.t_oster.liblasercut.utils;

import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Rectangle;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class VectorOptimizer
{
  public enum OrderStrategy
  {
    FILE,
    NEAREST,
    INNER_FIRST,
    SMALLEST_FIRST
  }

  class Element
  {
    LaserProperty prop;
    Point start;
    List<Point> moves = new LinkedList<Point>();
    void invert()
    {
      if (!moves.isEmpty())
      {
        moves.add(0, start);
        start = moves.remove(moves.size()-1);
        List<Point> inv = new LinkedList<Point>();
        while (!moves.isEmpty())
        {
          inv.add(moves.remove(moves.size()-1));
        }
        moves = inv;
      }
    }
    
   
    
    Point getEnd()
    {
      return moves.isEmpty() ? start : moves.get(moves.size()-1);
    }
    
    /** 
     * compute bounding box of moves, including start point
     * @return Rectangle 
     */
    Rectangle boundingBox() {
      if (start == null) { // TODO may this happen?
        return null;
      }
      Rectangle bb=new Rectangle(start.x,start.y,start.x,start.y);
      for (Point p: moves) {
        bb.add(p);
      }
      return bb;
    }
    
    /**
     * test if this Element represents a closed path (polygon)
     * @return true if start equals end, false otherwise
     */
    boolean isClosedPath() {
      if ((start == null) || moves.isEmpty()) {
        return false;
      }
      return getEnd().equals(start);
    }
  }
  


  private OrderStrategy strategy = OrderStrategy.FILE;

  public VectorOptimizer(OrderStrategy s)
  {
    this.strategy = s;
  }

  private List<Element> divide(VectorPart vp)
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

  private double dist(Point a, Point b)
  {
    return Math.sqrt((a.y-b.y)*(a.y-b.y)+(a.x-b.x)*(a.x-b.x));
  }

    
    
    
    // helper classes:
    private abstract class ElementValueComparator implements Comparator<Element> {
        /**
         * get one integer from the element
         * order ascending by this integer
         * inside objects should have the lowest values
         */
        abstract int getValue(Element e);
        
        /**
         * compare by getValue()
         */
        @Override
        public int compare(Element a, Element b) {
            Integer av = new Integer(getValue(a));
            Integer bv = new Integer(getValue(b));
            return av.compareTo(bv);
        }
        
    }
    

    
    
  private List<Element> sort(List<Element> e)
  {
    List<Element> result = new LinkedList<Element>();
    if (e.isEmpty())
    {
      return result;
    }
    switch (strategy)
    {
      case FILE:
      {
        result.addAll(e);
        break;
      }
      case NEAREST:
      {
        result.add(e.remove(0));
        while (!e.isEmpty())
        {
          Point end = result.get(result.size()-1).getEnd();
          //find nearest element
          int next = 0;
          //invert element direction if endpoint is nearer
          boolean invert = false;
          double dst = -1;
          for (int i = 1; i < e.size(); i++)
          {
            //check distance to startpoint
            double nd = dist(e.get(i).start, end);
            if (nd < dst || dst == -1)
            {
              next = i;
              dst = nd;
              invert = false;
            }
            if (!e.get(i).start.equals(e.get(i).getEnd()))
            {
              //check distance to endpoint
              nd = dist(e.get(i).getEnd(), end);
              if (nd < dst || dst == -1)
              {
                next = i;
                dst = nd;
                invert = true;
              }
            }
          }
          //add next
          if (invert)
          {
            Element m = e.remove(next);
            m.invert();
            result.add(m);
          }
          else
          {
            result.add(e.remove(next));
          }
        }
        break;
      }
      case INNER_FIRST: {
        /** cut inside parts first, outside parts later
         * this algorithm is very robust, it works even for unconnected paths that are split into individual lines (e.g. from some DXF imports)
         * it is not completely perfect, as it only considers the bounding-box and not the individual path
         * 
         * see below for documentation of the inner workings
         */
        
        
        class XMinComparator extends ElementValueComparator {
          // compare by XMin a>b
          @Override
          int getValue(Element e) {
            return -e.boundingBox().getXMin();
          }
        }
        
        class YMinComparator extends ElementValueComparator {
          // compare by YMin a>b
          @Override
          int getValue(Element e) {
            return -e.boundingBox().getYMin();
          }
        }
        
        class XMaxComparator extends ElementValueComparator {
          // compare by XMax a<b
          @Override
          int getValue(Element e) {
            return e.boundingBox().getXMax();
          }
        }
        
        class YMaxComparator extends ElementValueComparator {
          // compare by YMax a<b
          @Override
          int getValue(Element e) {
            return e.boundingBox().getYMax();
          }
        }
        result.addAll(e);
        /**
         * HEURISTIC:
         * this algorithm is based on the following observation:
         * let I and O be rectangles, I inside O
         * for explanations, assume that:
         * - the X-axis goes from left to right
         * - the Y-axis goes from bottom to top
         * 
         *         ---------------- O: outside rectangle
         *         |              |
         *         |    ----      |
         * y axis  |    |in| I    |
         * ^       |    ----      |
         * |       |              |
         * |       ----------------
         * |
         *  ------> x axis
         * 
         * look at each border:
         *  right border: I.getXMax() < O.getXMax()
         *   left border: I.getXMin() > O.getXMin()
         *    top border: I.getYMax() < O.getYMax()
         * bottom border: I.getYMin() > O.getYMin()
         * 
         * If we now SORT BY ymax ASCENDING, ymin DESCENDING, xmax ASCENDING, xmin DESCENDING
         *           (higher sorting priority listed first)
         * we get the rectangles sorted inside-out:
         * 1. I
         * 2. O
         * 
         * Because we sort by four values, this still works if 
         * the two rectangles start at the same corner and have the same width,
         * but only differ in height.
         *  
         * If each rectangle is split into four separate lines
         * (e.g. because of a bad DXF import),
         * this still mostly works:
         *  1. O: bottom line
         *  2. I: bottom
         *  3. I: top, left, right (both have same YMax, but top has a higher YMin)
         *  4: O: top, left, right (both have same YMax, but top has a higher YMin)
         * 
         * TRADEOFFS AND LIMITATIONS:
         * This algorithm does not work for paths that have the same bounding-box
         * (e.g. a circle inscribed to a square)
         * 
         * For concave polygons with the same bounding-box,
         * many simple Polygon-inside-Polygon algorithms also fail 
         * (or have a useless definition of "inside" that matches the misbehaviour):
         * Draw a concave polygon, remove one point at a concave edge.
         * The resulting polygon is clearly outside the original, although every edge of it is inside the original!
         * 
         * FUTURE WORK:
         * It would also be nice to sort intersecting polygons, where one polygon
         * is "90% inside" and "10% outside" the other.
         * Real-world example:_A circular hole at the border of a rectangle.
         * Due to rounding errors, it may appear slightly outside the rectangle.
         * Mathematically, it is neither fully inside nor fully outside, but the
         * user clearly wants it to be counted as "inside".
         * 
         * POSSIBLE LIBRARIES:
         * http://sourceforge.net/projects/geom-java/
         * http://sourceforge.net/projects/jts-topo-suite
         * 
         * USEFUL METHODS:
         * Element.isClosedPath()
         */
        
        // do the work:
        Collections.sort(result,new XMinComparator());
        Collections.sort(result,new YMinComparator());
        Collections.sort(result,new XMaxComparator());
        Collections.sort(result,new YMaxComparator());
        
        // the result is now mostly sorted
        // TODO somehow sort by intersecting area
        break;
      }
            
    case SMALLEST_FIRST: {
        /** cut smaller parts first, bigger parts later
         Heuristic is explained below...
         */
        
        
        class SmallerComparator extends ElementValueComparator {
            // compare by XMin a>b
            @Override
            int getValue(Element e) {
                return (e.boundingBox().getXMax()-e.boundingBox().getXMin()) * (e.boundingBox().getYMax()-e.boundingBox().getYMin());
            }
        }
        
        
        result.addAll(e);
        /**
         * HEURISTIC:
         * this algorithm is based on the following observation:
         * let S and B be rectangles, S smaller than B
         * for explanations, assume that:
         * - the X-axis goes from left to right
         * - the Y-axis goes from bottom to top
         *
         *         ---------------- B: bigger rectangle
         *         |              |
         *         |    ----      |
         * y axis  |    | S|      |
         * ^       |    ----      |
         * |       |              |
         * |       ----------------
         * |
         *  ------> x axis
         *
         * we get the rectangles sorted by size
         * 1. S
         * 2. B
         */
        
        // do the work:
        Collections.sort(result,new SmallerComparator());
        
        // the result is now mostly sorted
        break;
      }
    }
    return result;
  }

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
