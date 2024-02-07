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
    // nothing to do if input is empty
    List<Element> result = new LinkedList<>();
    if (e.isEmpty())
    {
      return result;
    }

    // Join "broken lines" if the gap is < 1 pixel (according to current DPI).
    // This is needed to avoid that the final result contains many MOVETO
    // commands that do (almost) zero moving but confuse the lasercutter's
    // motion controller.
    // (Possible performance improvement:
    // we could do this joining at the very end, when the paths are already
    // sorted nicely and we just need to compare if one end point is very close
    // to the previous start and all properties (laser power etc.) also match.)
    e = OptimizerUtils.joinContiguousLoopElements(e, 0.9);

    // Sort paths so that the gap between one endpoint and the next startpoint is minimized greedily.
    // Start at the first path.
    result.add(e.remove(0));
    while (!e.isEmpty())
    {
      Point end = result.get(result.size() - 1).getEnd();
      //find the start (or end) point nearest to the end point of the current path
      int next = 0;
      //invert element direction if endpoint is nearer
      boolean invert = false;
      double dst = Double.POSITIVE_INFINITY;
      for (int i = 0; i < e.size(); i++)
      {
        // check distance to next startpoint
        double nd = e.get(i).start.hypotTo(end);
        if (nd < dst)
        {
          next = i;
          dst = nd;
          invert = false;
        }
        if (!e.get(i).start.equals(e.get(i).getEnd()))
        {
          // check distance to next endpoint
          nd = e.get(i).getEnd().hypotTo(end);
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
