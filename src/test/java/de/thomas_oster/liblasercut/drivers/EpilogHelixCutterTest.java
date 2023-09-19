/*
  This file is part of LibLaserCut.
  Copyright (C) 2023

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
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.*;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class EpilogHelixCutterTest extends EpilogHelix
{
  // estimate time is truncated to int
  final double DELTA = 1.0;
  @Test
  public void testHostName()
  {
    String hostname = "FooBar";
    EpilogHelix helix = new EpilogHelix(hostname);
    assertEquals(hostname, helix.getHostname());
  }
  @Test
  public void testEstimateDuration()
  {
    // Resolution such that 42px = 1mm
    double mm2px = 42;
    double dpi = Util.dpmm2dpi(mm2px);

    for (int speedPercent : Arrays.asList(5, 17, 100)) {
      LaserProperty prop = getLaserPropertyForVectorPart();
      prop.setProperty("speed", speedPercent);

      double expectedTime = 0;
      LaserJob job = new LaserJob("", "", "");

      System.out.println(speedPercent);

      // 1. Move mainly in x -> time == dx / move_speed_x (always at full speed).
      VectorPart p = new VectorPart(prop, dpi);
      p.moveto(1000 * mm2px, 1 * mm2px);
      job.addPart(p);
      expectedTime += 1000 / this.VECTOR_MOVESPEED_X;
      assertEquals(expectedTime, this.estimateJobDuration(job), DELTA);

      // 2. Move mainly in y -> time == dy / move_speed_y (always at full speed).
      p.moveto(1000 * mm2px, 1001 * mm2px);
      expectedTime += 1000 / this.VECTOR_MOVESPEED_Y;
      assertEquals(expectedTime, this.estimateJobDuration(job), DELTA);

      // 3. Cut line at speedPercent.
      p.lineto(1500 * mm2px, 1701 * mm2px);
      expectedTime += Math.hypot(500, 700) / this.VECTOR_LINESPEED * 100.0 / speedPercent;
      assertEquals(expectedTime, this.estimateJobDuration(job), DELTA);

      // 4. Engrave.
      // Not yet calibrated

      // Raster3dPart is not explicitly tested, it uses almost the same codepath as RasterPart.
    }
  }
}
