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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class InnerFirstVectorOptimizer extends VectorOptimizer
{

  // helper classes:
  private abstract class ElementValueComparator implements Comparator<Element>
  {

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
    public int compare(Element a, Element b)
    {
      Integer av = new Integer(getValue(a));
      Integer bv = new Integer(getValue(b));
      return av.compareTo(bv);
    }
  }

  private class XMinComparator extends ElementValueComparator
  {
    // compare by XMin a>b
    @Override
    int getValue(Element e)
    {
      return -e.boundingBox().getXMin();
    }
  }

  private class YMinComparator extends ElementValueComparator
  {
    // compare by YMin a>b
    @Override
    int getValue(Element e)
    {
      return -e.boundingBox().getYMin();
    }
  }

  private class XMaxComparator extends ElementValueComparator
  {
    // compare by XMax a<b
    @Override
    int getValue(Element e)
    {
      return e.boundingBox().getXMax();
    }
  }

  private class YMaxComparator extends ElementValueComparator
  {
    // compare by YMax a<b
    @Override
    int getValue(Element e)
    {
      return e.boundingBox().getYMax();
    }
  }

  @Override
  protected List<Element> sort(List<Element> e)
  {
    List<Element> result = new LinkedList<Element>();
    if (e.isEmpty())
    {
      return result;
    }
    /**
     * cut inside parts first, outside parts later
     * this algorithm is very robust, it works even for unconnected paths that
     * are split into individual lines (e.g. from some DXF imports)
     * it is not completely perfect, as it only considers the bounding-box and
     * not the individual path
     *
     * see below for documentation of the inner workings
     */
    result.addAll(e);
    /**
     * HEURISTIC:
     * this algorithm is based on the following observation:
     * let I and O be rectangles, I inside O
     * for explanations, assume that:
     * - the X-axis goes from left to right
     * - the Y-axis goes from bottom to top
     *
     * ---------------- O: outside rectangle
     * | |
     * | ---- |
     * y axis | |in| I |
     * ^ | ---- |
     * | | |
     * | ----------------
     * |
     * ------> x axis
     *
     * look at each border:
     * right border: I.getXMax() < O.getXMax()
     * left border: I.getXMin() > O.getXMin()
     * top border: I.getYMax() < O.getYMax()
     * bottom border: I.getYMin() > O.getYMin()
     *
     * If we now SORT BY ymax ASCENDING, ymin DESCENDING, xmax ASCENDING, xmin
     * DESCENDING
     * (higher sorting priority listed first)
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
     * 1. O: bottom line
     * 2. I: bottom
     * 3. I: top, left, right (both have same YMax, but top has a higher YMin)
     * 4: O: top, left, right (both have same YMax, but top has a higher YMin)
     *
     * TRADEOFFS AND LIMITATIONS:
     * This algorithm does not work for paths that have the same bounding-box
     * (e.g. a circle inscribed to a square)
     *
     * For concave polygons with the same bounding-box,
     * many simple Polygon-inside-Polygon algorithms also fail
     * (or have a useless definition of "inside" that matches the misbehaviour):
     * Draw a concave polygon, remove one point at a concave edge.
     * The resulting polygon is clearly outside the original, although every
     * edge of it is inside the original!
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
    Collections.sort(result, new XMinComparator());
    Collections.sort(result, new YMinComparator());
    Collections.sort(result, new XMaxComparator());
    Collections.sort(result, new YMaxComparator());
    return result;
  }
}
