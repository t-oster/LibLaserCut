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

import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.vectoroptimizers.VectorOptimizer.Element;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class OptimizerUtils
{
  /**
   * Given a list of Elements, combines Elements with matching start/end points
   * into single Elements. Elements with different props will never be joined.
   * Joins or forks (if three or more paths meet at start/end, e.g. looking like Y),
   * will never be joined.
   *
   * @param input List of Elements to join.
   * @param tolerance start/end points within this distance (manhattan distance: |dx| + |dy|) are joined
   * @return ArrayList of joined Elements.
   */
  public static ArrayList<Element> joinContiguousLoopElements(
    List<Element> input, double tolerance)
  {
    // Group elements by property, so that
    // propToElements.get(property) == "list of all input[i] with input[i].prop == property"
    Map<LaserProperty, ArrayList<Element>> propToElements = 
      input.stream().collect(
        Collectors.groupingBy(
          el -> el.prop, Collectors.toCollection(ArrayList::new)
        )
      );

    final ArrayList<Element> result = new ArrayList<>();
    // for all elements with the same property:
    for (ArrayList<Element> elements : propToElements.values())
    {
      // Now we can disregard properties, as all elementsWithSameProp reference Elements
      // with identical properties

      // Group paths into two buckets:
      //   closed -> move to result (no further processing required),
      //   non-closed -> keep for the remaining computations
      Map<Boolean, List<Element>> pathsGroupedByClosed = elements.stream()
        .collect(Collectors.groupingBy(
                Element::isClosedPath
        ));
      List<Element> emptyList = new ArrayList<>(0);
      result.addAll(pathsGroupedByClosed.getOrDefault(true, emptyList));
      elements = new ArrayList<>(pathsGroupedByClosed.getOrDefault(false, emptyList));

      // create an ArrayList containing two DirectedElements for each Element
      // with the given prop. The two DirectedElements represent the two possible
      // directions in which the Element could be executed (inverted or not).
      // All points that have been removed are nulled.
      ArrayList<DirectedElement> startAndEndPoints = new ArrayList<>(elements.size() * 2);
      int i = 0;
      for (Element element: elements)
      {
        element.index = i;
        startAndEndPoints.add(new DirectedElement(i, element.start, false));
        startAndEndPoints.add(new DirectedElement(i, element.getEnd(), true));
        i++;
      }
      // sort by x-coordinate to allow for binary search
      Collections.sort(startAndEndPoints);
      // The same list, but without nulling elements.
      // Used for binary search, because binary-searching a "skip-list" is inefficient.
      ArrayList<DirectedElement> originalStartAndEndPoints = new ArrayList<>(startAndEndPoints);
      // add back-reference from elements to startAndEndPoints. This allows for faster deletion.
      i = 0;
      for (DirectedElement el: startAndEndPoints)
      {
        if (el.inverted)
        {
          elements.get(el.index).endIndex = i;
        }
        else
        {
          elements.get(el.index).startIndex = i;
        }
        i++;
      }
      
      // Find the all start/end points near every start/end point, and merge if there is exactly 1 within the tolerance.
      boolean somethingChanged = true;
      while (somethingChanged)
      {
        somethingChanged = false;
        for (Element current: elements)
        {
          if (current == null)
          {
            // element was deleted
            continue;
          }
          boolean hasAnyNeighbors = false;
          // for "invert=1 (check end point)", "invert=0 (check start point)":
          for (int invert = 1; invert >= 0; invert--)
          {
            // "Head" means the point we currently check (start or end).
            Point currentHead = invert == 0 ? current.start : current.getEnd();
            // How many other paths end or start are near the current head?
            // If 0, there's nothing to do.
            // If 1, merge the paths.
            // If 2, we're at a fork, so don't merge.
            //
            // Optimization:
            // We don't need to check if multiple end points meet because we don't join end-to-end, only start-to-end.
            // End-to-end doesn't happen (except at forks) because
            // nearest-first sorting would have inverted one of the paths, resulting in the end-to-start or start-to-end case.
            // Due to symmetry, the end-to-start case is handled by the start-to-end case.
            int pointsNearby = 0;
            Element startNearCurrentHead = null;
            Element endNearCurrentHead = null;

            // Optimization: We can skip all points that are far left of the current head point.
            int skipPoints = numElementsLeftOf(originalStartAndEndPoints, currentHead.x - tolerance);
            for (DirectedElement e: startAndEndPoints.subList(skipPoints, startAndEndPoints.size()))
            {
              // For every candidate point, check if it's nearby.
              // If yes, remember it in startNearCurrentHead or endNearCurrentHead.
              if (e == null || e.index == current.index)
              {
                // skip removed (null) entries,
                // skip current element
                continue;
              }
              // skipPoints guarantees that all points are not too far left of the current head point.
              assert e.start.x >= currentHead.x - tolerance;
              if (e.start.x > currentHead.x + tolerance)
              {
                // Current candidate is too far right.
                // Optimization: All following candidates are even further more right, so we can stop here.
                break;
              }

              if (pointsNearby >= 2)
              {
                break;
              }
              if (e.start.manhattanDistanceTo(currentHead) < tolerance)
              {
                pointsNearby++;
                if (e.inverted)
                {
                  // e.start is actually an end point
                  endNearCurrentHead = elements.get(e.index);
                  assert endNearCurrentHead != null;
                }
                else
                {
                  // e.start is a start point
                  startNearCurrentHead = elements.get(e.index);
                  assert startNearCurrentHead != null;
                }
              }
            }
            if (pointsNearby >= 1) {
              hasAnyNeighbors = true;
            }

//          int pointsNearby = startNearCurrentHead.size() + endNearCurrentHead.size();
            if (pointsNearby == 1) {
              // there is exactly one other start/end point nearby. join the paths.
              Element merged;
              if (startNearCurrentHead != null) {
                // join current head to other.start. Note that head is "start" or "end" depending on invert.
                Element other = startNearCurrentHead;
                if (invert == 1)
                {
                  // because invert==1, "head" means "end".
                  // join current.end ---- other.start
                  startAndEndPoints.set(current.endIndex, null);
                  startAndEndPoints.set(other.startIndex, null);
                  current.append(other);
                  // remove other
                  elements.set(other.index, null);
                  merged = current;
                }
                else
                {
                  // because invert==0, "head" means "start".
                  // join current.start ---- other.start by reversing current.
                  startAndEndPoints.set(current.startIndex, null);
                  startAndEndPoints.set(other.startIndex, null);
                  current.invert();
                  current.append(other);
                  // remove other
                  elements.set(other.index, null);
                  merged = current;
                }
              } else {
                Element other = endNearCurrentHead;
                // join current head to other.end. Note that head is "start" or "end" depending on invert.
                if (invert == 1)
                {
                  // because invert==1, "head" means "end".
                  // join current.end ---- other.end by reversing other.
                  startAndEndPoints.set(current.endIndex, null);
                  startAndEndPoints.set(other.endIndex, null);
                  other.invert();
                  current.append(other);
                  merged = current;
                  // remove other
                  elements.set(other.index, null);
                }
                else
                {
                  // because invert==0, "head" means "start".
                  // join other.end --- current.start
                  startAndEndPoints.set(other.endIndex, null);
                  startAndEndPoints.set(current.startIndex, null);
                  other.append(current);
                  merged = other;
                  // remove current
                  elements.set(current.index, null);
                }
              }
              if (merged.isClosedPath())
              {
                // merged path is closed, move it to the result
                result.add(merged);

                elements.set(merged.index, null);
                startAndEndPoints.set(merged.startIndex, null);
                startAndEndPoints.set(merged.endIndex, null);
              }
              else
              {
                // merged path remains. update the start/end points in the list.
                startAndEndPoints.get(merged.startIndex).inverted = false;
                startAndEndPoints.get(merged.endIndex).inverted = true;
                startAndEndPoints.get(merged.startIndex).index = merged.index;
                startAndEndPoints.get(merged.endIndex).index = merged.index;
              }
              somethingChanged = true;
              break; // The current element has been modified. Go on to the next element.
              // This is optimal if the paths are already pre-sorted.
            }
            else
            {
              // pointsNearby != 1:
              // There is either a fork (pointsNearby >= 2) or an end (pointsNearby == 0) at the current head point.
              // Nothing to do. After the start point (invert==0),
              // the same will be checked for the end point (invert==1)
              // and then for the next point (i++).
            }
          }
          if (!hasAnyNeighbors)
          {
            // neither start nor end have any points nearby.
            // -> information about this path is not relevant for remaining paths
            result.add(current);
            elements.set(current.index, null);
          }
        }
      }
      // result.append( all elements != null )
      elements.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> result));
    }
    
    return result;
  }


  /**
   * Returns the number of elements at the head of the list that
   * are left of the given x-coordinate. Relies on the list already being
   * sorted according to x-coordinate. Elements must not be null.
   * Similar to Collections.binarySearch, but with different return value.
   */
  private static int numElementsLeftOf(ArrayList<DirectedElement> list, Double x)
  {
    if (list.isEmpty())
    {
      return 0;
    }
    int start = -1; // Exclusive.
    int end = list.size() - 1; // Inclusive. Always points to a valid index.

    while (end - start > 1)
    {
      int mid = (start + end) / 2;
      if (list.get(mid).start.x > x)
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
   * inverted/non-inverted flag. Contains the start and end points of the
   * referenced Element, switched if the inverted flag is set. Possesses a
   * pre-order based on the x-coordinate of the start point, allowing for a
   * "weak" but consistent sorting of points. In this order, points near a given
   * x-coordinate can be efficiently found by binary search.
   *
   * Note: this ordering is inconsistent with equals.
   *
   * Note that it is mathematically impossible to extend this idea to both x and
   * y with a single order relation (sort so that nearby points would have nearby
   * indices).
   */
  private static class DirectedElement implements Comparable<DirectedElement>
  {

    int index; // "pointer" to the respective Element
    Point start;
    boolean inverted;
    boolean valid = true;

    DirectedElement(int index, Point start, boolean inverted)
    {
      this.index = index;
      this.start = start;
      this.inverted = inverted;
    }

    @Override
    public int compareTo(DirectedElement o)
    {
      return Double.compare(start.x, o.start.x);
    }
  }
}
