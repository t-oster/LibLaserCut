/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2019 Max Gaukler <development@maxgaukler.de>
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
package com.t_oster.liblasercut.platform;

import java.util.List;

public class Circle
{
  public double radius = Double.NaN;
  public Point center;

  @Override
  public String toString() {
    return "Circle(radius=" + radius + ", center=" + center + ")";
  }

  /**
   * Test if a point is on this circle (on the edge)
   *
   * @param p Point to test
   * @param tolerance absolute tolerance for distance to circle
   * @return
   */
  public boolean containsPoint(Point p, double tolerance)
  {
    return Math.abs(p.hypothenuseTo(center) - radius) < tolerance;
  }

  /**
   * Detect a circle from a list of points with (roughly) equal distance
   * This can be used to "undo" a circle-to-polyline conversion.
   *
   * Note that the current implementation is rather simple
   *
   * @param points List of points
   * @param tolerance Maximum absolute deviation between points and the detected circle.
   * @return null if no circle was found, Circle object otherwise.
   */
  public static Circle fromPointList(List<? extends Point> points, double tolerance)
  {
    Circle circle = new Circle();
    if (points.size() < 8) {
      // too few points
      return null;
    }
    Point end = points.get(points.size() - 1);
    Point start = points.get(0);
    if (!start.equals(end)) {
      // circle is not closed
      return null;
    }

    // Compute the center:
    // If the points are equally distributed, the center is the average of all points.
    // To make that slightly more precise, we compute the weighted average of the segment midpoints, weighted by segment length.
    // (The result is equal to reinterpolating the path with a constant and very small distance, and then computing the average).
    circle.center = new Point(0, 0);
    double length = 0;
    double maxSegmentLength = 0;
    Point pBefore = points.get(0);
    for (Point p: points) {
      double segmentLength = p.hypothenuseTo(pBefore);
      circle.center.x += (p.x + pBefore.x) / 2 * segmentLength;
      circle.center.y += (p.y + pBefore.y) / 2 * segmentLength;
      if (segmentLength > maxSegmentLength) {
        maxSegmentLength = segmentLength;
      }
      length += segmentLength;
      pBefore = p;
    }
    circle.center = circle.center.scale(1 / length);

    // Compute the radius and check that all points have the same radius (+/- tolerance).
    double minRadiusSquare = Double.POSITIVE_INFINITY;
    double avgRadiusSquare = 0;
    double maxRadiusSquare = Double.NEGATIVE_INFINITY;
    for (Point p: points) {
      double radiusSquare = (circle.center.x - p.x) * (circle.center.x - p.x) + (circle.center.y - p.y) * (circle.center.y - p.y);
      avgRadiusSquare += radiusSquare;
      boolean radiusChanged = false;
      if (radiusSquare < minRadiusSquare) {
        minRadiusSquare = radiusSquare;
        radiusChanged = true;
      }
      if (radiusSquare > maxRadiusSquare) {
        maxRadiusSquare = radiusSquare;
        radiusChanged = true;
      }

      // Note for the tolerance check:
      //      maxRadius**2 - minRadius**2
      //    = (minRadius + (maxRadius-minRadius))**2 - minRadius**2
      //    = minRadius**2 + 2*minRadius*(maxRadius - minRadius) + (maxRadius - minRadius)**2 - minRadius**2
      //    =  2*minRadius*(maxRadius - minRadius) + (maxRadius - minRadius)**2
      // Because (maxRadius - minRadius) must be <= tolerance, this must be
      //   <=  2*minRadius*(     tolerance       ) + (       tolerance     )**2

      // Rewrite to get (maxRadius**2 - minRadius**2)**2 <= 4 * minRadius**2 * tolerance ** 2 + ...,
      // where the rest .. can be neglected as minRadius is much larger than the tolerance.

      if (radiusChanged)
      {
        // we check this as often as possible to abort early for non-circles
        if ((maxRadiusSquare - minRadiusSquare) * (maxRadiusSquare - minRadiusSquare) > 4 * tolerance * tolerance * minRadiusSquare)
        {
          // shape is not circle-like within the tolerance (or our guess for the centerpoint was bad)
          return null;
        }
        if (minRadiusSquare <= 45 * tolerance) {
          // the circle is not much larger than the tolerance -- this does not make sense.
          return null;
        }
      }
    }
    circle.radius = Math.sqrt(avgRadiusSquare / points.size());

    // We now know that the radius is correct, i.e., all points are on the circle.
    // However, if a segment is extremely long, the shape is still not a circle.
    //
    // Geometry (Pythagoras) leads to:
    // radius ** 2 == maxSegmentLength ** 2 / 4 + (radius - deviationFromCircle)**2
    // Therefore,
    // maxSegmentLength ** 2 == 4 * (radius ** 2  - (radius - deviationFromCircle)**2)
    //                       == 4 * (2*radius*deviationFromCircle + deviatonFromCircle**2)
    //  where the last term can be neglected as the radius is much larger then the allowed deviation.
    if (maxSegmentLength * maxSegmentLength > 8 * circle.radius * tolerance) {
      // The current shape has constant "radius" but isn't circle-ish enough, e.g. a hexagon.
      return null;
    }

    if (Math.abs(2*Math.PI*circle.radius - length) > 0.05 * length)
    {
      // The circumference doesn't match the radius  within 5% tolerance.
      // This shape may be something odd like a circle that makes two revolutions.
      return null;
    }

    // Everything is okay. Return the circle
    return circle;
  }
}
