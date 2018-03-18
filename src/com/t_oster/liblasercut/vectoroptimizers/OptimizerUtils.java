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
import com.t_oster.liblasercut.platform.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class OptimizerUtils
{
  /**
   * Given a list of Elements, combines Elements with matching start/end points
   * into single Elements. Elements with different props will never be joined.
   *
   * Joining will be perfect for any sequence of Elements which forms a perfect,
   * straightforward loop within which every point matches precisely two
   * Elements' start or end points. In more general cases (e.g., more than two
   * Elements meeting at a single point, or non-closed loops), joining of
   * contiguous Elements will not necessarily be as complete as possible, but
   * the output Elements will always include all of the moves in the input
   * (possibly inverted.)
   *
   * @param input List of Elements to join.
   * @return ArrayList of joined Elements.
   */
  public static ArrayList<VectorOptimizer.Element> joinContiguousLoopElements(
    List<VectorOptimizer.Element> input)
  {
    // For each prop encountered in the input, create an ArrayList containing
    // two DirectedElements for each Element with the given prop. The two
    // DirectedElements represent the two possible directions in which the
    // Element could be executed (inverted or not).
    HashMap<LaserProperty, ArrayList<DirectedElement>> propToElements = new HashMap();
    for (int i = 0; i < input.size(); i++)
    {
      VectorOptimizer.Element element = input.get(i);
      ArrayList<DirectedElement> list = propToElements.get(element.prop);
      if (list == null)
      {
        list = new ArrayList();
        propToElements.put(element.prop, list);
      }
      list.add(new DirectedElement(i, element.start, false));
      list.add(new DirectedElement(i, element.getEnd(), true));
    }

    ArrayList<VectorOptimizer.Element> result = new ArrayList();
    for (ArrayList<DirectedElement> directedElements : propToElements.values())
    {
      // Now we can disregard prop, as all directedElements reference Elements
      // with identical prop.

      // Sort directedElements according to their associated points, in order
      // to allow efficient search for directedElements starting from a
      // particular point later.
      Collections.sort(directedElements);

      while (!directedElements.isEmpty())
      {
        // Grab an arbitrary element, setting it to null in the process to
        // prevent it being seen again.
        DirectedElement de = directedElements.remove(directedElements.size() - 1);
        VectorOptimizer.Element current = input.get(de.index);
        if (current == null)
        {
          continue;
        }
        input.set(de.index, null);

        // Search for any other elements that can be joined on to the end of
        // this element.
        boolean keepGoing = true;
        while (keepGoing)
        {
          Point endPoint = current.getEnd();
          int i = binarySearch(directedElements, endPoint);
          keepGoing = false;
          while (i < directedElements.size()
            && directedElements.get(i).point.equals(endPoint))
          {
            int nextIndex = directedElements.get(i).index;
            VectorOptimizer.Element next = input.get(nextIndex);
            if (next == null)
            {
              i++;
              continue;
            }

            // We've found an element (next) that can be joined onto the end of
            // current! Do so, delete the element to prevent it from being seen
            // again, and then go through the outer loop again to see if we can
            // find yet another element to join on to the newly combined one.
            input.set(nextIndex, null);
            if (directedElements.get(i).inverted)
            {
              next.invert();
            }
            current.append(next);
            keepGoing = true;
            break;
          }
        }

        // We couldn't find any more elements to join on. Either the loop is
        // complete, or we've reached one end of an open polyline.
        //
        // In order to do a better joining job with open polylines, We could
        // perhaps invert the combined element and go through the while loop
        // again -- but this would add more complexity (both conceptual and in
        // CPU time) and doesn't seem worthwhile.
        result.add(current);
      }
    }
    return result;
  }

  /**
   * Returns the index of the first DirectedElement that is greater than or
   * equal to the provided point. Relies on the list already being sorted
   * according to the natural ordering of DirectedElement.point.
   * Collections.binarySearch cannot be used because it has undefined behaviour
   * when several equal elements exist.
   */
  private static int binarySearch(ArrayList<DirectedElement> list, Point p)
  {
    int start = -1; // Exclusive.
    int end = list.size() - 1; // Inclusive.

    while (end - start > 1)
    {
      int mid = (start + end) / 2;
      if (list.get(mid).point.compareTo(p) >= 0)
      {
        end = mid;
      }
      else
      {
        start = mid;
      }
    }
    return end;
  }

  /**
   * Represents an index within an ArrayList of Elements, combined with a
   * inverted/non-inverted flag. Contains the start or end point of the
   * referenced Element (depending on whether the inverted flag is set.)
   * Possesses a natural ordering based on this start/end point, allowing
   * a sorted ArrayList of DirectedElements to be efficiently binary searched
   * to find Elements starting or ending at a given point.
   */
  private static class DirectedElement implements Comparable
  {
    int index;
    Point point;
    boolean inverted;

    DirectedElement(int index, Point point, boolean inverted)
    {
      this.index = index;
      this.point = point;
      this.inverted = inverted;
    }

    @Override
    public int compareTo(Object o)
    {
      return compareTo((DirectedElement) o);
    }

    public int compareTo(DirectedElement o)
    {
      return point.compareTo(o.point);
    }
  }
}
