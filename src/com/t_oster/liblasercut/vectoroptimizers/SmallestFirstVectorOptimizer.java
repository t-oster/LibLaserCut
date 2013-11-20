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
public class SmallestFirstVectorOptimizer extends VectorOptimizer
{

  /**
   * cut smaller parts first, bigger parts later
   * Heuristic is explained below...
   */
  class SmallerComparator implements Comparator<Element>
  {
    // compare by XMin a>b
    @Override
    public int compare(Element a, Element b)
    {
      Integer av = new Integer(getValue(a));
      Integer bv = new Integer(getValue(b));
      return av.compareTo(bv);
    }

    int getValue(Element e)
    {
      return (e.boundingBox().getXMax() - e.boundingBox().getXMin()) * (e.boundingBox().getYMax() - e.boundingBox().getYMin());
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

    result.addAll(e);
    /**
     * HEURISTIC:
     * this algorithm is based on the following observation:
     * let S and B be rectangles, S smaller than B
     * for explanations, assume that:
     * - the X-axis goes from left to right
     * - the Y-axis goes from bottom to top
     *
     * ---------------- B: bigger rectangle
     * | |
     * | ---- |
     * y axis | | S| |
     * ^ | ---- |
     * | | |
     * | ----------------
     * |
     * ------> x axis
     *
     * we get the rectangles sorted by size
     * 1. S
     * 2. B
     */
    // do the work:
    Collections.sort(result, new SmallerComparator());

    // the result is now mostly sorted
    return result;
  }
}
