/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2020 Max Gaukler (development@maxgaukler.de)
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
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.GreyRaster;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import org.junit.Test;
import static org.junit.Assert.*;

public class LaserCutterTest extends Dummy
{
  @Test
  public void testEstimateDuration()
  {
    LaserJob job = new LaserJob("", "", "");
    LaserProperty prop = getLaserPropertyForVectorPart();
    int speedPercent = 17;
    prop.setProperty("speed", speedPercent);
    // set resolution such that 42px = 1mm
    double mm2px = 42;
    double dpi = Util.dpmm2dpi(mm2px);
    double expectedTime = 0;
    
    // 1. move mainly in x -> time == dx / move_speed_x
    VectorPart p = new VectorPart(prop, dpi);
    p.moveto(100 * mm2px, 1 * mm2px);
    job.addPart(p);
    expectedTime += 100/10;
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, 0, 0, 0, 0, 0), 1);
    
    // 2. move mainly in y -> time == dy / move_speed_y
    p.moveto(101 * mm2px, 101 * mm2px);
    expectedTime += 100/20;
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, 0, 0, 0, 0, 0), 1);
    
    // 3. cut line
    p.lineto(401 * mm2px, 401 * mm2px);
    double lineSpeed = 30;
    expectedTime += Math.sqrt(2) * 300 / (lineSpeed * speedPercent / 100);
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, lineSpeed, 0, 0, 0, 0), 1);
    
    // 4. engrave
    LaserProperty rasterProp = getLaserPropertyForRasterPart();
    speedPercent = 37;
    rasterProp.setProperty("speed", speedPercent);
    GreyRaster raster = new GreyRaster(123, 456);
    int nonBlankLines = 61;
    for (int x = 0; x < raster.getWidth(); x++)
    {
      for (int y = 0; y < raster.getHeight(); y++)
      {
        if (y <= nonBlankLines)
        {
          raster.setGreyScale(x, y, 0); // black = do engrave
        }
        else
        {
          raster.setGreyScale(x, y, 255); // white = don't engrave
        }
      }
    }
    RasterPart rp = new RasterPart(raster, rasterProp, new Point(501 * mm2px, 401 * mm2px), dpi);
    job.addPart(rp);
    expectedTime += 100 / 10; // move time to startpoint
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, lineSpeed, 0, Double.POSITIVE_INFINITY, 0, 0), 1);
    
    // additionally consider the constant time per engrave line:
    double rasterExtraTimePerLine = 17;
    expectedTime += rasterExtraTimePerLine * raster.getHeight();
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, lineSpeed, rasterExtraTimePerLine, Double.POSITIVE_INFINITY, 0, 0), 1);
    
    // additionally consider the finite engrave speed:
    double rasterLineSpeed = 23;
    expectedTime += nonBlankLines * raster.getWidth() / mm2px / (rasterLineSpeed * speedPercent / 100);
    assertEquals(expectedTime, this.estimateJobDuration(job, 10, 20, lineSpeed, rasterExtraTimePerLine, rasterLineSpeed, 0, 0), 1);
    
    // Raster3dPart is not explicitly tested, it uses almost the same codepath as RasterPart.
  }
}