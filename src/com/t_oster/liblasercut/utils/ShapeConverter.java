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
package com.t_oster.liblasercut.utils;

import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.VectorPart;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

/**
 * This class makes it possible to add java.awt.Shape Objects
 * to a VectorPart. The Shape will be converted to moveto and lineto
 * commands fitting as close as possible
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class ShapeConverter
{

  /**
   * Adds the given Shape to the given VectorPart by converting it to
   * lineto and moveto commands, whose lines differs not more than
   * n pixels from the original shape. n is usually 1, but depends on the
   * LaserCutter used.
   * 
   * @param shape the Shape to be added
   * @param vectorpart the Vectorpart the shape shall be added to
   * @param cutter LaserCutter
   */
  public void addShape(Shape shape, VectorPart vectorpart, LaserCutter cutter)
  {
    AffineTransform scale = AffineTransform.getScaleInstance(1, 1);
    double precision = 1;
    if (cutter != null) // this "if" guard is only needed for compatibility - change to 'if (True)' after the deprecated addShape(Shape,VectorPart) interface has been removed
    {
      precision = cutter.getRequiredCurvePrecision();
    }
    PathIterator iter = shape.getPathIterator(scale, precision);
    double startx = 0;
    double starty = 0;
    int lastx = 0;
    int lasty = 0;
    while (!iter.isDone())
    {
      double[] test = new double[8];
      int result = iter.currentSegment(test);
      if (result == PathIterator.SEG_MOVETO)
      {
        vectorpart.moveto(test[0], test[1]);
        startx = test[0];
        starty = test[1];
        lastx = (int) startx;
        lasty = (int) starty;
      }
      else if (result == PathIterator.SEG_LINETO)
      {
        // skip lines with length 0 https://github.com/t-oster/LibLaserCut/issues/87
        // (or length 0 after converting to integer, so that integer-based drivers have no problems)
        double x = test[0];
        double y = test[1];
        if ((int) x != lastx || (int) y != lasty) {
          vectorpart.lineto(x, y);
          lastx = (int) x;
          lasty = (int) y;
        }
      }
      else if (result == PathIterator.SEG_CLOSE)
      {
        vectorpart.lineto(startx, starty);
      }
      iter.next();
    }
  }

  /**
   * Fallback method for compatibility with old VisiCut code.
   * Remove this as soon as VisiCut uses the new interface of addShape.
   * @param shape
   * @param vectorpart
   * @deprecated
   */
  @Deprecated
  public void addShape(Shape shape, VectorPart vectorpart)
  {
    addShape(shape, vectorpart, null);
  }
}
