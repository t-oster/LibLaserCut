/*
  This file is part of LibLaserCut.
  Copyright (C) 2020 Max Gaukler (development@maxgaukler.de)

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

import de.thomas_oster.liblasercut.GreyRaster;
import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.LaserCutter;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.LibInfo;
import de.thomas_oster.liblasercut.Raster3dPart;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.RasterizableJobPartTest;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Point;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Max Gaukler
 */
public class AllDriversTest {

  public static void setPropertyToExampleValues(LaserProperty prop)
  {
    // TODO switch to new interface for setPower(), getPower(), getSpeed() ?
    for (String key: prop.getPropertyKeys())
    {
      if (prop.getProperty(key) instanceof Boolean || prop.getProperty(key) instanceof String)
      {
        // FIXME this should not be necessary per the documentation of getMaximumValue
        continue;
      }
      if (prop.getMaximumValue(key) != null && prop.getMinimumValue(key) != null)
      {
        prop.setProperty(key, prop.getMaximumValue(key));
        // TODO set to something inbetween minimum and maximum
      }
    }
  }
  
  /**
   * filename for storing the test output
   * @param c Class of lasercutter driver
   * @param isNew temporary file for storing the new results / False: "old" file with known-good result
  */
  private String getOutputFilename(Class<? extends LaserCutter> c, boolean isNew)
  {
    return "./test-output/" + c.getName() + ".out" + (isNew ? ".new": "");
  }
  
  /**
   * Generate a laser job that uses most features.
   */
  public LaserJob generateDummyJob(LaserCutter lc)
  {
    LaserProperty prop = lc.getLaserPropertyForVectorPart();
    setPropertyToExampleValues(prop);
    LaserJob job = new LaserJob("test", "aaaa", "bbb");
    // vector cut
    double dpi = lc.getResolutions().get(lc.getResolutions().size()/2);
    VectorPart vp = new VectorPart(prop, dpi);
    // draw something looking roughly like "VC"
    vp.moveto(10, 10);
    vp.lineto(500, 1000);
    vp.lineto(1000, 0);
    vp.moveto(2000, 0);
    vp.lineto(1500, 100);
    // "smooth" segment to test drivers with own speed/acceleration computation
    vp.lineto(1499, 400);
    vp.lineto(1499.42, 450.1337); // non-integer coordinates are also permitted
    vp.lineto(1498, 500);
    vp.lineto(1499, 600);
    vp.lineto(1500, 900);
    vp.lineto(2000, 1000);
    job.addPart(vp);
    // raster engrave
    prop = lc.getLaserPropertyForRasterPart();
    setPropertyToExampleValues(prop);
    RasterPart rp = new RasterPart(new GreyRaster(RasterizableJobPartTest.getTest1bitRasterElement()), prop, new Point(13,37), dpi);
    job.addPart(rp);
    Raster3dPart r3p = new Raster3dPart(new GreyRaster(RasterizableJobPartTest.getTest8bitRasterElement()), prop, new Point(42,77), dpi);
    job.addPart(r3p);
    return job;
  }
  
  /**
   * For every driver that supports saveToFile(), check that the output for a
   * dummy job matches a known-good output (stored in test-output/).
   * This catches bugs that accidentally break drivers.
   */
  @Test
  public void compareWithKnownOutput() throws FileNotFoundException, IllegalJobException, Exception
  {
    for (Class<? extends LaserCutter> c: LibInfo.getSupportedDrivers())
    {
      System.out.println("Generating test output for driver " + c.getSimpleName());
      LaserCutter lc = c.getDeclaredConstructor().newInstance();
      LaserJob job = generateDummyJob(lc);
      
      File newResult = new File(getOutputFilename(c, true));
      try (PrintStream fs = new PrintStream(newResult)) {
        lc.saveJob(fs, job);
      } catch (UnsupportedOperationException e) {
        if ("Your driver does not implement saveJob(LaserJob job)".equals(e.getMessage())) {
          System.err.println("Warning: Cannot test driver " + c.getName() + " because it does not support saveJob()");
        } else {
          throw e;
        }
      }
    }
    // compare the results
    List<String> comparisonResults = new ArrayList<>();
    for (Class<? extends LaserCutter> c: LibInfo.getSupportedDrivers())
    {
      File newResult = new File(getOutputFilename(c, true));
      File previousResult = new File(getOutputFilename(c, false));
      if (previousResult.exists())
      {
        if (!Arrays.equals(new FileInputStream(previousResult).readAllBytes(), new FileInputStream(newResult).readAllBytes()))
        {
          comparisonResults.add("Output for " + c.getName() + " changed. For details, compare the files " + previousResult + " and " + newResult + " manually. If this change is okay, delete the old file " + previousResult +  " , rerun the tests twice and then don't forget to commit the changed file.");
        }
      } else {
        newResult.renameTo(previousResult);
        comparisonResults.add("No previous known output found for " + c.getName() + ". Saving current output. Please re-run the tests and don't forget to commit the test output file to the repository.");
      }
    }
    if (!comparisonResults.isEmpty())
    {
      StringBuilder msg = new StringBuilder("Driver output changed:\n");
      for (String s: comparisonResults)
      {
        msg.append(s);
        msg.append("\n");
      }
      throw new Exception(msg.toString());
    }
  }
}
