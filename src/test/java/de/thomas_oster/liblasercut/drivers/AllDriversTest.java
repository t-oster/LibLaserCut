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
import de.thomas_oster.liblasercut.properties.LaserProperty;
import de.thomas_oster.liblasercut.LibInfo;
import de.thomas_oster.liblasercut.Raster3dPart;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.RasterizableJobPartTest;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
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
   * @param isNew True: temporary file for storing the new results / False: "old" file with known-good result
   * @param isNewRepeated: True (only for isNew==True) : file was created by printing to file a second time / False: everything else
  */
  private String getOutputFilename(Class<? extends LaserCutter> c, boolean isNew, boolean isNewRepeated)
  {
    return "./test-output/" + c.getName() + ".out" + (isNew ? ".new": "") + (isNewRepeated ? "2": "");
  }
  
  /**
   * Generate a laser job that uses most features.
   *
   * @param lc Lasercutter to send the job.
   * @param tooLarge if true, the job is deliberately made larger than the laser bed size.
   */
  public LaserJob generateDummyJob(LaserCutter lc, boolean tooLarge)
  {
    LaserProperty prop = lc.getLaserPropertyForVectorPart();
    setPropertyToExampleValues(prop);
    LaserJob job = new LaserJob("test", "aaaa", "bbb");
    // vector cut
    double dpi = lc.getResolutions().get(lc.getResolutions().size()/2);
    // The following cutting test data assumes that the laser bed size is W > 2000 and H > 1000 pixels.
    // If the bed is smaller, scale down the test data accordingly so that the job fits on the laser bed.
    double scaling = 0.99 * Math.min(Util.mm2px(lc.getBedWidth(), dpi) / 2000, Util.mm2px(lc.getBedHeight(), dpi) / 1000);
    if (tooLarge) {
      // special case: caller requested to explicitly generate a job that exceeds the bed size
      scaling = scaling * 2;
    } else {
      // generate a job that will be smaller than the bed size.
      // Leave scaling at 100% if possible.
      if (scaling > 1)
      {
        scaling = 1;
      }
    }
    VectorPart vp = new VectorPart(prop, dpi);
    // draw something looking roughly like "VC"
    vp.moveto(10 * scaling, 10 * scaling);
    vp.lineto(500 * scaling, 1000 * scaling);
    vp.lineto(1000 * scaling, 0 * scaling);
    vp.moveto(2000 * scaling, 0 * scaling);
    vp.lineto(1500 * scaling, 100 * scaling);
    // "smooth" segment to test drivers with own speed/acceleration computation
    vp.lineto(1499 * scaling, 400 * scaling);
    vp.lineto(1499.42 * scaling, 450.1337 * scaling); // non-integer coordinates are also permitted
    vp.lineto(1498 * scaling, 500 * scaling);
    vp.lineto(1499 * scaling, 600 * scaling);
    vp.lineto(1500 * scaling, 900 * scaling);
    vp.lineto(2000 * scaling, 1000 * scaling);
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

      // Send laser job to file twice.
      // repeated=false: first run.
      // repeated=true: second run.
      // Should give the same output as in the first run.
      // Otherwise the laser driver is probably missing some re-initialization code.
      for (boolean repeated : new boolean[] {false, true}) {
        LaserJob job = generateDummyJob(lc, false);

        File newResult = new File(getOutputFilename(c, true, repeated));
        try (PrintStream fs = new PrintStream(newResult)) {
          lc.saveJob(fs, job);
        } catch (UnsupportedOperationException e) {
          if ("Your driver does not implement saveJob(LaserJob job)".equals(e.getMessage())) {
            if (!repeated)
            {
              System.err.println("Warning: Cannot test driver " + c.getName() + " because it does not support saveJob()");
            }
          } else {
            throw e;
          }
        }
      }
    }
    
    // compare the results
    List<String> comparisonResults = new ArrayList<>();
    for (Class<? extends LaserCutter> c: LibInfo.getSupportedDrivers())
    {
      File expectedResult = new File(getOutputFilename(c, false, false));
      File newResult = new File(getOutputFilename(c, true, false));
      File newResultRepeated = new File(getOutputFilename(c, true, true));
      if (expectedResult.exists())
      {
        if (newResult.exists() && newResultRepeated.exists())
        {
          // Check: new output (from driver) == expected output (from git)
          if (!Arrays.equals(new FileInputStream(expectedResult).readAllBytes(), new FileInputStream(newResult).readAllBytes()))
          {
            comparisonResults.add("Output for " + c.getName() + " changed. For details, compare the files " + expectedResult + " and " + newResult + " manually. If this change is okay, delete the old file " + expectedResult +  " , rerun the tests twice and then don't forget to commit the changed file.");
          }
          // Check: new output == new output when run again
          else if (!Arrays.equals(new FileInputStream(newResult).readAllBytes(), new FileInputStream(newResultRepeated).readAllBytes())) {
            comparisonResults.add("Output for " + c.getName() + " is different when sending the same laser job a second time. Probably the driver code does not correctly internal states. For details, compare the files " + newResult + " and " + newResultRepeated );
          }
        }
        else
        {
          // new output file missing.
          // This can only occur 
        }
      } else {
        // Expected output file missing
        if (! newResult.renameTo(expectedResult)) {
          throw new RuntimeException("Renaming of file failed");
        }
        comparisonResults.add("No expected known output found for " + c.getName() + ". Saving current output. Please re-run the tests and don't forget to commit the test output file to the repository.");
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

  @Test
  public void checkErrorOnTooLargeJobs() throws Exception
  {
    for (var c: LibInfo.getSupportedDrivers())
    {
      LaserCutter lc = c.getDeclaredConstructor().newInstance();
      LaserJob job = generateDummyJob(lc, true);

      try (PrintStream fs = new PrintStream(OutputStream.nullOutputStream());) {
        lc.saveJob(fs, job);
      } catch (UnsupportedOperationException e) {
        if ("Your driver does not implement saveJob(LaserJob job)".equals(e.getMessage())) {
          System.err.println("Warning: Cannot test driver " + c.getName() + " because it does not support saveJob()");
          continue;
        } else {
          throw e;
        }
      } catch (IllegalJobException e) {
        // expected exception --> good
        continue;
      } catch (Exception e) {
        var ex = new Exception("Driver " + c.getName() + " threw the wrong exception type when given a job larger than the laser bed.");
        ex.addSuppressed(e);
      }
      throw new Exception("Driver " + c.getName() + " should have thrown an IllegalJobException for a job larger than the laser bed, but it did not throw any exception. Forgot to call checkJob()?");
    }
  }
}
