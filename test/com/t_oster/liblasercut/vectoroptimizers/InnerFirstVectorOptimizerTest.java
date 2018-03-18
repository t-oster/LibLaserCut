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

import com.t_oster.liblasercut.PowerSpeedFocusProperty;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.vectoroptimizers.VectorOptimizer.Element;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

public class InnerFirstVectorOptimizerTest
{

  @Test
  public void joinsSeparateLineSegments()
  {
    ArrayList<Element> elements = new ArrayList();
    /*
      0 1 2 3
    0 *-----*
      |     |
    1 | *-* |
      | | | |
    2 | *-* |
      |     |
    3 | *-* |
      | | | |
    4 | *-* |
      |     |
    5 *-----*

     */
    elements.add(newElem(50, 0, 0, 3, 0));
    elements.add(newElem(50, 3, 5, 3, 0));
    elements.add(newElem(50, 3, 5, 0, 5));
    elements.add(newElem(50, 0, 0, 0, 5));

    elements.add(newElem(50, 2, 1, 1, 1));
    elements.add(newElem(50, 1, 2, 2, 2));
    elements.add(newElem(50, 2, 1, 2, 2));
    elements.add(newElem(50, 1, 2, 1, 1));

    elements.add(newElem(50, 1, 3, 2, 3));
    elements.add(newElem(50, 2, 4, 2, 3));
    elements.add(newElem(50, 2, 4, 1, 4));
    elements.add(newElem(50, 1, 3, 1, 4));

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    assertEquals(3, sorted.size());
    assertEquals(newElem(50, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1), sorted.get(0));
    assertEquals(newElem(50, 2, 4, 1, 4, 1, 3, 2, 3, 2, 4), sorted.get(1));
    assertEquals(newElem(50, 3, 5, 0, 5, 0, 0, 3, 0, 3, 5), sorted.get(2));
  }

  @Test
  public void doesNotJoinSegmentsWithDifferentProps()
  {
    ArrayList<Element> elements = new ArrayList();
    /*
      0 1 2 3
    0 *-----*
      |     |
    1 | *-* |
      | | | |
    2 | *-* |
      |     |
    3 | *-* |
      | | | |
    4 | *-* |
      |     |
    5 *-----*

     */
    elements.add(newElem(50, 0, 0, 3, 0));
    elements.add(newElem(50, 3, 5, 3, 0));
    elements.add(newElem(50, 3, 5, 0, 5));
    elements.add(newElem(50, 0, 0, 0, 5));

    elements.add(newElem(50, 2, 1, 1, 1));
    elements.add(newElem(100, 1, 2, 2, 2));
    elements.add(newElem(100, 2, 1, 2, 2));
    elements.add(newElem(50, 1, 2, 1, 1));

    elements.add(newElem(50, 1, 3, 2, 3));
    elements.add(newElem(50, 2, 4, 2, 3));
    elements.add(newElem(50, 2, 4, 1, 4));
    elements.add(newElem(50, 1, 3, 1, 4));

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    assertEquals(4, sorted.size());
    assertEquals(newElem(50, 2, 1, 1, 1, 1, 2), sorted.get(0));
    assertEquals(newElem(50, 2, 1, 2, 2, 1, 2), sorted.get(1));
    assertEquals(newElem(50, 2, 4, 1, 4, 1, 3, 2, 3, 2, 4), sorted.get(2));
    assertEquals(newElem(50, 3, 5, 0, 5, 0, 0, 3, 0, 3, 5), sorted.get(3));
  }

  @Test
  public void doesNotManglePolylinesOrBranchingPaths()
  {
    ArrayList<Element> elements = new ArrayList();
    /*
      0 1 2 3
    0 *-----*
      |
    1 | *-*-*
      | | | |
    2 | *-*-*
      | | | |
    3 * *-*-*
      | | | |
    4 | *-*-*
      |
    5 *-----*

     */
    elements.add(newElem(50, 0, 0, 3, 0));
    elements.add(newElem(50, 3, 5, 0, 5, 0, 3, 0, 0));

    for (int i = 1; i <= 3; i++)
    {
      for (int j = 1; j<= 4; j++)
      {
        elements.add(newElem(50, i, j, i + 1, j));
        elements.add(newElem(50, j, i, j, i + 1));
      }
    }

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    // Grid partially combined.
    assertEquals(7, sorted.size());
    assertEquals(newElem(50, 3, 2, 4, 2), sorted.get(0));
    assertEquals(newElem(50, 1, 2, 1, 3), sorted.get(1));
    assertEquals(newElem(50, 3, 3, 4, 3, 4, 2, 4, 1, 3, 1), sorted.get(2));
    assertEquals(newElem(50, 3, 3, 3, 4), sorted.get(3));
    assertEquals(newElem(50, 3, 2, 3, 3, 2, 3, 2, 4), sorted.get(4));
    assertEquals(newElem(50, 4, 3, 4, 4, 3, 4, 2, 4, 1, 4, 1, 3, 2, 3, 2, 2, 1, 2, 1, 1, 2, 1, 2, 2, 3, 2, 3, 1, 2, 1), sorted.get(5));

    // Polyline combined.
    assertEquals(newElem(50, 3, 5, 0, 5, 0, 3, 0, 0, 3, 0), sorted.get(6));
  }

  private static Element newElem(int power, int x1, int y1, int... moves)
  {
    Element ret = new Element();

    ret.start = new Point(x1, y1);

    ret.moves = new ArrayList();
    assertEquals(0, moves.length % 2);
    for (int i = 0; i < moves.length; i += 2)
    {
      ret.moves.add(new Point(moves[i], moves[i + 1]));
    }

    PowerSpeedFocusProperty prop = new PowerSpeedFocusProperty();
    prop.setPower(power);
    ret.prop = prop;

    return ret;
  }
}
