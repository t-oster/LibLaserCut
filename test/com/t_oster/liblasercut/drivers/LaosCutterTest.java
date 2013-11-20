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
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.VectorPart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosCutterTest extends LaosCutter{
  ProgressListener pl = new ProgressListener(){

      @Override
      public void progressChanged(Object source, int percent)
      {
      }

      @Override
      public void taskChanged(Object source, String taskName)
      {
      }
    };
  @Test
  public void testVectorCode() throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    LaserJob job = new LaserJob("test", "test", "test");
    LaosCutterProperty prop = new LaosCutterProperty();
    prop.setPower(50.62f);
    prop.setSpeed(100f);
    prop.setFrequency(333);
    prop.setFocus(1f);
    VectorPart vp = new VectorPart(prop, 500);
    vp.moveto(10, 10);
    vp.lineto(0, 0);
    job.addPart(vp);
    this.writeJobCode(job, out, pl);
    List<String> lines = Arrays.asList(out.toString().split("\n"));
    assertTrue(lines.contains("7 101 5062"));
    assertTrue(lines.contains("7 100 10000"));
    assertTrue(lines.contains("7 102 333"));
    assertTrue(lines.contains("7 6 1"));
    assertTrue(lines.contains("7 7 1"));
    assertTrue(lines.contains("2 1000"));
  }
}
