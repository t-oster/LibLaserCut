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

import de.thomas_oster.liblasercut.platform.Point;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class NearestVectorOptimizer extends VectorOptimizer
{

  @Override
  protected List<Element> sort(List<Element> e)
  {
    List<Element> result = new LinkedList<>();
    if (e.isEmpty())
    {
      return result;
    }

    result.add(e.remove(0));
    while (!e.isEmpty())
    {
      Point end = result.get(result.size() - 1).getEnd();
      //find nearest element
      int next = 0;
      //invert element direction if endpoint is nearer
      boolean invert = false;
      double dst = Double.POSITIVE_INFINITY;
      for (int i = 0; i < e.size(); i++)
      {
        // check distance to next startpoint
        double nd = e.get(i).start.hypothenuseTo(end);
        if (nd < dst)
        {
          next = i;
          dst = nd;
          invert = false;
        }
        if (!e.get(i).start.equals(e.get(i).getEnd()))
        {
          // check distance to next endpoint
          nd = e.get(i).getEnd().hypothenuseTo(end);
          if (nd < dst)
          {
            next = i;
            dst = nd;
            invert = true;
          }
        }
        if (dst == 0)
        {
          break;
        }
      }
      //add next
      if (invert)
      {
        Element m = e.remove(next);
        m.invert();
        result.add(m);
      }
      else
      {
        result.add(e.remove(next));
      }
    }
    return result;
  }
}
