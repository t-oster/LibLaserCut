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
 * @author Max Gaukler <development@maxgaukler.de>
 * 
 * (not really compatible) replacement of java.awt.Rectangle,
 * This Rectangle cannot be "empty" - at minimum it needs to have one point.
 *
 * @see Point
 */
public class Rectangle {
  private int x1, x2, y1, y2;

  /**
   * integer interval helper class
   * represents the set { min, min+1, ..., max }
   * 
   * min must be < max
   */
  private class Interval
  {

    private int min,max;

    private Interval(int min, int max)
    {
      this.min = min;
      this.max = max;
      if (min>max) {
        throw new RuntimeException("Interval: min must be < max");
      }
    }

    /**
     * @return true if the other interval is equal or is a subset
     */
    private boolean isSubsetOf(Interval o)
    {
      return o.min <= min && o.max >= max;
    }

    /**
     * @return true if the other interval has at least one element in common with this
     */
    private boolean intersects(Interval o)
    {
      return (o.min >= min && o.min <= max) || (o.max >= min && o.max <= max);
    }

  }

  /**
   * construct a rectangle with the corners (x1,y1) and (x2,y2)
   */
  public Rectangle(int x1, int y1, int x2, int y2)
  {
    this.x1=Math.min(x1,x2);
    this.x2=Math.max(x1,x2);
    this.y1=Math.min(y1,y2);
    this.y2=Math.max(y1,y2);
  }

  /**
   * Construct a rectangle with the corners p1 and p2
   */
  public Rectangle(Point p1, Point p2) {
    this(p1.x,p1.y,p2.x,p2.y);
  }

  /**
   * construct a rectangle with only one point p
   */
  public Rectangle(Point p) {
    this(p,p);
  }


  /**
   * add a point to the boundary of this rectangle
   * use this iteratively to get the boundingBox
   */
  public void add (int x, int y) {
    if (x<x1) {
      this.x1=x;
    } else if (x>x2) {
      this.x2=x;
    }

    if (y<y1) {
      this.y1=y;
    } else if (y>y2) {
      this.y2=y;
    }
  }

  public void add(Point p) {
    if (p != null) {
      add(p.x, p.y);
    }
  }

  /**
   * smallest X coordinate
   * @return int
   */
  public int getXMin() {
    return x1;
  }

  /**
   * greatest X coordinate
   */
  public int getXMax() {
    return x2;
  }

  /**
   * smallest Y coordinate
   */
  public int getYMin() {
    return y1;
  }

  /**
   * greatest Y coordinate
   */
  public int getYMax() {
    return y2;
  }

  /**
   * X interval from left to right
   */
  private Interval getXInterval() {
    return new Interval(x1,x2);
  }

  /**
   * Y interval from top to bottom
   */
  private Interval getYInterval() {
    return new Interval(y1,y2);
  }

  @Override
  public String toString() {
    return "Rectangle(x1="+x1+",y1="+y1+",x2="+x2+",y2="+y2+")";
  }

  @Override
  public Rectangle clone()
  {
    return new Rectangle(x1,y1,x2,y2);
  }

  /**
   * check if this is inside of (or equal) another rectangle
   * @param other
   * @return true if this rectangle is equal to or inside of the other one
   */
  public boolean isInsideOf(Rectangle other) {
    return this.getXInterval().isSubsetOf(other.getXInterval())
                    && this.getYInterval().isSubsetOf(other.getYInterval());
  }

  /**
   * check if the intersection of this rectangle with another one is not empty
   * @param other
   * @return true if rectangles have at least one point in common
   */
  public boolean intersects(Rectangle other) {
    return this.getXInterval().intersects(other.getXInterval())
            && this.getYInterval().intersects(other.getYInterval());
  }
}
