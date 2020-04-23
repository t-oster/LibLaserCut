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

import de.thomas_oster.liblasercut.PowerSpeedFocusProperty;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Rectangle;
import de.thomas_oster.liblasercut.vectoroptimizers.VectorOptimizer.Element;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class InnerFirstVectorOptimizerTest
{

  final int SCALE = 2; // this must be greater than 2*(tolerance of joinContigousLoops)
  
  @Test
  public void joinsSeparateLineSegments()
  {
    ArrayList<Element> elements = new ArrayList<>();
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

     Note: these coordinates are later multiplied by 10
     */
    elements.add(newElem(50, SCALE, 0, 0, 3, 0));
    elements.add(newElem(50, SCALE, 3, 5, 3, 0));
    elements.add(newElem(50, SCALE, 3, 5, 0, 5));
    elements.add(newElem(50, SCALE, 0, 0, 0, 5));

    elements.add(newElem(50, SCALE, 2, 1, 1, 1));
    elements.add(newElem(50, SCALE, 1, 2, 2, 2));
    elements.add(newElem(50, SCALE, 2, 1, 2, 2));
    elements.add(newElem(50, SCALE, 1, 2, 1, 1));

    elements.add(newElem(50, SCALE, 1, 3, 2, 3));
    elements.add(newElem(50, SCALE, 2, 4, 2, 3));
    elements.add(newElem(50, SCALE, 2, 4, 1, 4));
    elements.add(newElem(50, SCALE, 1, 3, 1, 4));

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    assertEquals(3, sorted.size());
    assertEquals(newElem(50, SCALE, 2, 1, 1, 1, 1, 2, 2, 2, 2, 1), sorted.get(0));
    assertEquals(newElem(50, SCALE, 1, 3, 2, 3, 2, 4, 1, 4, 1, 3), sorted.get(1));
    assertEquals(newElem(50, SCALE, 0, 0, 3, 0, 3, 5, 0, 5, 0, 0), sorted.get(2));
  }

  @Test
  public void doesNotJoinSegmentsWithDifferentProps()
  {
    var elements = new ArrayList<Element>();
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
    elements.add(newElem(50, SCALE, 0, 0, 3, 0));
    elements.add(newElem(50, SCALE, 3, 5, 3, 0));
    elements.add(newElem(50, SCALE, 3, 5, 0, 5));
    elements.add(newElem(50, SCALE, 0, 0, 0, 5));

    elements.add(newElem(50, SCALE, 2, 1, 1, 1));
    elements.add(newElem(50, SCALE, 1, 2, 1, 1));
    elements.add(newElem(100, SCALE, 2, 1, 2, 2));
    elements.add(newElem(50, SCALE, 2, 2, 1, 2));

    elements.add(newElem(50, SCALE, 1, 3, 2, 3));
    elements.add(newElem(50, SCALE, 2, 4, 2, 3));
    elements.add(newElem(50, SCALE, 2, 4, 1, 4));
    elements.add(newElem(50, SCALE, 1, 3, 1, 4));

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    assertEquals(4, sorted.size());
    assertEquals(newElem(100, SCALE, 2, 1, 2, 2), sorted.get(0));
    assertEquals(newElem(50, SCALE, 2, 2, 1, 2, 1, 1, 2, 1), sorted.get(1));
    assertEquals(newElem(50, SCALE, 1, 3, 2, 3, 2, 4, 1, 4, 1, 3), sorted.get(2));
    assertEquals(newElem(50, SCALE, 0, 0, 3, 0, 3, 5, 0, 5, 0, 0), sorted.get(3));
  }

  @Test
  public void doesNotManglePolylinesOrBranchingPaths()
  {
    var elements = new ArrayList<Element>();
    /*
      0 1 2 3
    0 *-----*
      |
    1 | *-*-*
      | | | |
    2 | *-*-*
      | | | |
    3 * *-*-*
      | 
    4 | 
      |
    5 *-----*

     */

    // Both elements starting at 0, 0; without the invert step to check for
    // matches at the start instead of just at the end, this would fail to join.
    elements.add(newElem(50, SCALE, 0, 0, 3, 0));
    elements.add(newElem(50, SCALE, 0, 0, 0, 3, 0, 5, 3, 5));

    for (int i = 1; i <= 2; i++)
    {
      for (int j = 1; j <= 3; j++)
      {
        elements.add(newElem(50, SCALE, i, j, i + 1, j));
        elements.add(newElem(50, SCALE, j, i, j, i + 1));
      }
    }

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    // Grid partially combined.
    assertEquals(9, sorted.size());
    assertEquals(newElem(50, SCALE, 1, 2, 2, 2), sorted.get(0));
    assertEquals(newElem(50, SCALE, 2, 1, 2, 2), sorted.get(1));
    assertEquals(newElem(50, SCALE, 2, 1, 1, 1, 1, 2), sorted.get(2));
    assertEquals(newElem(50, SCALE, 2, 2, 3, 2), sorted.get(3));
    assertEquals(newElem(50, SCALE, 2, 1, 3, 1, 3, 2), sorted.get(4));
    assertEquals(newElem(50, SCALE, 2, 2, 2, 3), sorted.get(5));
    assertEquals(newElem(50, SCALE, 1, 2, 1, 3, 2, 3), sorted.get(6));
    assertEquals(newElem(50, SCALE, 2, 3, 3, 3, 3, 2), sorted.get(7));

    // Polyline combined.
    assertEquals(newElem(50, SCALE, 3, 0, 0, 0, 0, 3, 0, 5, 3, 5), sorted.get(8));
  }

  /**
   * Construct a new path
   * @param power laser power
   * @param scale all coordinates are multiplied by this factor
   */
  private static Element newElem(int power, int scale, int x1, int y1, int... moves)
  {
    Element ret = new Element();

    ret.start = new Point(scale * x1, scale * y1);

    assertEquals(0, moves.length % 2);
    for (int i = 0; i < moves.length; i += 2)
    {
      ret.addPoint(new Point(scale * moves[i], scale * moves[i + 1]));
    }

    PowerSpeedFocusProperty prop = new PowerSpeedFocusProperty();
    prop.setPower(power);
    ret.prop = prop;

    return ret;
  }

  //////////////////////////////////////////////////////////////////////////////
  //
  // Everything below this comment is a bit of fun -- couple of "tests" which
  // output shell scripts which when run, output animated GIFs showing the laser
  // cut path for a couple of tricky laser cutting cases. These are cases where
  // more than two elements meet at a point, where overzealous joining can lead
  // to a cut happening within an already-totalled-enclosed area. Such cuts,
  // which go against the spirit of the INNER_FIRST algorithm, are coloured red
  // in the produced GIFs.
  //
  // Enable the script generation by uncommenting the @Test annotations. The
  // scripts require ImageMagick to be installed.
  // @Test
  public void squareGrid() throws IOException
  {
    ArrayList<Element> elements = new ArrayList();
    for (int i = 0; i <= 3; i++)
    {
      for (int j = 0; j <= 4; j++)
      {
        elements.add(newElem(50, SCALE, 50 * i, 50 * j, 50 * (i + 1), 50 * j));
        elements.add(newElem(50, SCALE, 50 * j, 50 * i, 50 * j, 50 * (i + 1)));
      }
    }

    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);

    drawAnimation("square", sorted);
  }

  // @Test
  public void hexagonGrid() throws IOException
  {
    var elements = new ArrayList<Element>();
    final int SX = 24;
    final int SY = 15;
    final int OX = SX * 3;
    final int OY = 0;
    final int S = 3;
    for (int i = 0; i <= 2000; i++)
    {
      for (int j = 0; j <= 2000; j++)
      {
        if (Math.abs(i - j) < S)
        {
          elements.add(newElem(50, SCALE, OX + SX * (2 * i - j), OY + SY * (3 * j),
            OX + SX * (2 * i - j - 1), OY + SY * (3 * j + 1)));
        }
        if (Math.abs(i - j + 0.5) < S + 0.3 && i < 5)
        {
          elements.add(newElem(50, SCALE, OX + SX * (2 * i - j), OY + SY * (3 * j),
            OX + SX * (2 * i - j + 1), OY + SY * (3 * j + 1)));
        }
        if (Math.abs(i - j - 0.5) < S + 0.3 && j < 5)
        {
          elements.add(newElem(50, SCALE, OX + SX * (2 * i - j - 1), OY + SY * (3 * j + 1),
            OX + SX * (2 * i - j - 1), OY + SY * (3 * j + 3)));
        }
      }
    }

    //drawAnimation("hexagon_unsorted", elements);
    System.out.println("hey");
    System.out.println(elements.size());
    List<Element> sorted = new InnerFirstVectorOptimizer().sort(elements);
    System.out.println("ho");
    //drawAnimation("hexagon", sorted);
  }

  private static final int BORDER = 5;
  private static final int ANTIALIAS = 1;

  private static void drawAnimation(String name, List<Element> sorted)
    throws IOException
  {
    Rectangle bb = new Rectangle(
      sorted.get(0).start.x, sorted.get(0).start.y,
      sorted.get(0).start.x, sorted.get(0).start.y);
    for (Element polyline : sorted)
    {
      bb.add(polyline.start);
      for (Point p : polyline.getMoves())
      {
        bb.add(p);
      }
    }
    String dimensions = ANTIALIAS * ((int) bb.getXMax() + 2 * BORDER + 1) + "x"
      + ANTIALIAS * ((int) bb.getYMax() + 2 * BORDER + 1);
    String finalDimensions = ((int) bb.getXMax() + 2 * BORDER + 1) + "x"
      + ((int) bb.getYMax() + 2 * BORDER + 1);

    PrintWriter out = new PrintWriter(
      new BufferedWriter(new FileWriter(name + "_animation_gen.sh")));

    out.printf("set -e\n");
    out.printf("rm -f frame*.png\n");

    final String CMD = "convert -size " + dimensions + " xc:Yellow +antialias "
      + "-fill none -stroke Magenta -strokewidth " + ANTIALIAS + " -draw \"%s\""
      + " -fill White -draw \"color -0,0 floodfill\" %s frame%05d.png\n";
    final int F = 3;
    int c = 0;
    StringBuilder drawCmd = new StringBuilder();
    String compositeCommand = "";
    for (Element polyline : sorted)
    {
      Point prev = polyline.start;
      for (Point p : polyline.getMoves())
      {
        Point end = null;
        for (int f = 1; f <= F; f++)
        {
          end = new Point(
            (prev.x * (F - f) + p.x * f) / F, (prev.y * (F - f) + p.y * f) / F);
          out.printf(
            CMD, drawCmd + "line" + p2s(prev) + p2s(end), compositeCommand, c);
          compositeCommand = String.format(
            "frame%05d.png -compose Darken -composite", c);
          ++c;
        }
        drawCmd.append("line").append(p2s(prev)).append(p2s(p)).append(" ");
        prev = p;
      }
    }

    --c;

    out.printf("time convert -delay 6 -loop 0 frame*.png -delay 400 "
      + "frame%05d.png -fill Black -opaque Magenta -fill wheat -opaque Yellow "
      + "-resize %s %s.gif\n", c, finalDimensions, name);
    out.printf("rm -f frame*.png\n");
    out.close();
  }

  private static String p2s(Point p)
  {
    return " " + (int) (ANTIALIAS * (p.x + BORDER) + ANTIALIAS / 2)
      + "," + (int) (ANTIALIAS * (p.y + BORDER) + ANTIALIAS / 2);
  }
}
