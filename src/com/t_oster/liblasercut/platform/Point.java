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
package com.t_oster.liblasercut.platform;

/**
 * This Class is the replacement of the java.awt.Point and android.graphics.Point
 * because the library wants to run on both platforms without modification
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class Point
{

  public double x;
  public double y;

  public Point(double x, double y)
  {
    this.x = x;
    this.y = y;
  }

  @Override
  public Point clone()
  {
    return new Point(x, y);
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 97 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
    hash = 97 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    final Point other = (Point) obj;
    if (Double.doubleToLongBits(this.x) != Double.doubleToLongBits(other.x))
    {
      return false;
    }
    if (Double.doubleToLongBits(this.y) != Double.doubleToLongBits(other.y))
    {
      return false;
    }
    return true;
  }



  public int compareTo(Point o)
  {
    if (y < o.y) return -1;
    if (y > o.y) return 1;
    if (x < o.x) return -1;
    if (x > o.x) return 1;
    return 0;
  }

    /**
   * compute euclidean distance to another point
   * @param p other point
   * @return
   */
  public double hypothenuseTo(Point p) {
    return this.subtract(p).hypot();
  }

  public Point add(Point p) {
    return new Point(x+p.x, y+p.y);
  }

  public Point subtract(Point p) {
    return new Point(x-p.x, y-p.y);
  }

  /**
   * absolute angle between this point and another point, if both points are interpreted as vector from zero to the point
   *
   * @param p other vector
   * @return Result in the range 0 <= result <= pi
   */
  public double absAngleTo(Point p) {
    // TODO: simplify ( = overapproximate). Before, double-check that all consumers understand this is an approximation!
    double angle = Math.abs(Math.atan2(y, x) - Math.atan2(p.y, p.x));
    // normalize to -pi ... pi
    if (angle > Math.PI) {
      angle = angle - 2*Math.PI;
    }
    return Math.abs(angle);
  }

  /**
   * return a vector with same direction, but length 1
   * @return
   */
  public Point unityVector() {
    double l = hypot();
    return new Point(x/l, y/l);
  }

  /**
   * compute euclidean length
   * @return sqrt(x^2 + y^2)
   */
  public double hypot() {
    return Math.sqrt(x*x + y*y);
  }

  /**
   * multiply with scalar
   * @param factor
   * @return scaled vector
   */
  public Point scale(double factor) {
    return new Point(x*factor, y*factor);
  }
}
