/**
 * This file is part of VisiCut.
 * Copyright (C) 2012 Max Gaukler <development@maxgaukler.de>
 * 
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *    VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.t_oster.liblasercut.platform;

/**
 * Integer interval
 */
public class Interval {
  private int min, max;

  public Interval(int a, int b) {
    this.min=Math.min(a,b);
    this.max=Math.max(a,b);
  }
  
  public int getMin() {
    return this.min;
  }
  
  public int getMax() {
    return this.max;
  }

  /**
   * check if this interval is subset of another one (or equal)
   * @param other Interval
   * @return true if this is a subset of other, or equal to it
   */
  public boolean isSubsetOf(Interval other) {
    return ((other.getMin() <= this.getMin()) && (other.getMax() >= this.getMax()));
  }
  
  public boolean isSupersetOf(Interval other) {
    return other.isSubsetOf(this);
  }
  
  /**
   * test if value is inside interval
   * @param x
   * @return true if value is inside [min, max] (borders included)
   */
  public boolean contains(int x) {
    return ((this.getMin() <= x) && (this.getMax() >= x));
  }
  
  /**
   * check if the intervals intersect
   * @param other
   * @return true if the intervals share at least one common value
   */
  public boolean intersects(Interval other) {
    return this.contains(other.getMin()) || this.contains(other.getMax()) || this.isSubsetOf(other);
  }
}