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
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.ProgressListenerDummy;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.properties.FloatMinMaxPowerSpeedFrequencyProperty;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Verifies the E7 03 bounding-box preamble (used by the laser's own "trace outline" feature) stays in
 * sync with a job's start point, and stays correctly ordered (min < max) when an axis is flipped.
 * Decodes the actual RD byte stream (Ruida's 7-bit-packed, XOR-scrambled encoding) instead of just
 * checking driver-internal state, so this also catches encoding mistakes.
 */
public class RuidaTest
{
  /** Inverts ByteStream.write(): XOR with magic, undo the +1, undo the bit0/bit7 swap. */
  private static int unscramble(int raw, int magic)
  {
    int i = ((raw - 1) & 0xff) ^ (magic & 0xff);
    i ^= (i >> 7) & 0xff;
    i ^= (i << 7) & 0xff;
    i ^= (i >> 7) & 0xff;
    return i & 0xff;
  }

  /** Decodes a 5-byte, 7-bit-packed, MSB-first absolute value (as written by ByteStream.absoluteMM) to mm. */
  private static double decodeAbsoluteMM(byte[] raw, int offset, int magic)
  {
    long val = 0;
    for (int i = 0; i < 5; i++)
    {
      val = (val << 7) | unscramble(raw[offset + i] & 0xff, magic);
    }
    return val / 1000.0;
  }

  private LaserJob buildJobWithOnePoint(double xMm, double yMm) throws Exception
  {
    LaserJob job = new LaserJob("test", "test", "test");
    VectorPart vp = new VectorPart(new FloatMinMaxPowerSpeedFrequencyProperty(), 1000);
    double dpmm = 1000 / 25.4;
    vp.moveto((int) Math.round(xMm * dpmm), (int) Math.round(yMm * dpmm));
    vp.lineto((int) Math.round(xMm * dpmm), (int) Math.round(yMm * dpmm));
    job.addPart(vp);
    return job;
  }

  /**
   * Sends the job and decodes the E7 03 (bounding box top-left) command. Calls writeJobCode() directly
   * (like sendJob() eventually does, after its own checkJob() on the original, unshifted job) rather
   * than through saveJob(), which - unlike sendJob() - doesn't call applyStartPoint() itself (a separate,
   * pre-existing gap not under test here).
   */
  private double[] sendAndDecodeBoundingBoxMin(Ruida ruida, LaserJob job) throws Exception
  {
    job.applyStartPoint();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Field outField = Ruida.class.getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(ruida, new PrintStream(out));
    ruida.writeJobCode(job, new ProgressListenerDummy());
    byte[] raw = out.toByteArray();
    for (int i = 0; i + 11 < raw.length; i++)
    {
      if (unscramble(raw[i] & 0xff, 0x88) == 0xE7 && unscramble(raw[i + 1] & 0xff, 0x88) == 0x03)
      {
        double x = decodeAbsoluteMM(raw, i + 2, 0x88);
        double y = decodeAbsoluteMM(raw, i + 7, 0x88);
        return new double[]{x, y};
      }
    }
    fail("E7 03 (bounding box) command not found in output");
    return null;
  }

  @Test
  public void boundingBoxReflectsStartPoint_noFlip() throws Exception
  {
    Ruida ruida = new Ruida();
    ruida.setBedWidth(600.0);
    ruida.setBedHeigth(400.0);
    ruida.setFlipXaxis(false);
    ruida.setFlipYaxis(false);

    // design point at canonical (120, 80); start point anchored at canonical (100, 60)
    // -> after the shift, the point is at local (20, 20).
    LaserJob job = buildJobWithOnePoint(120, 80);
    job.setStartPoint(100, 60);

    double[] xy = sendAndDecodeBoundingBoxMin(ruida, job);
    assertEquals(20, xy[0], 0.05);
    assertEquals(20, xy[1], 0.05);
  }

  @Test
  public void boundingBoxReflectsStartPoint_flippedX() throws Exception
  {
    Ruida ruida = new Ruida();
    ruida.setBedWidth(600.0);
    ruida.setBedHeigth(400.0);
    ruida.setFlipXaxis(true);
    ruida.setFlipYaxis(false);

    // On a flipped axis, machine home is 0, not bedWidth - mirroring a *local* (start-point-relative)
    // offset around the far bed edge would place the start point at completely the wrong end of travel.
    // A valid anchor for a flipped axis must be >= the design's own extent (mirror image of the
    // non-flipped case, where the anchor must be <=), so the design extends validly from home outward.
    LaserJob job = buildJobWithOnePoint(120, 80);
    job.setStartPoint(150, 60);

    double[] xy = sendAndDecodeBoundingBoxMin(ruida, job);
    // local x = 120 - 150 = -30; flipped + start-point-referenced -> mirrored around machine home (0): 0 - (-30) = 30
    assertEquals(30, xy[0], 0.05);
    // y not flipped: local y = 80 - 60 = 20
    assertEquals(20, xy[1], 0.05);
  }

  @Test
  public void boundingBoxUnaffectedWithoutStartPoint() throws Exception
  {
    Ruida ruida = new Ruida();
    ruida.setBedWidth(600.0);
    ruida.setBedHeigth(400.0);
    ruida.setFlipXaxis(true);
    ruida.setFlipYaxis(false);

    LaserJob job = buildJobWithOnePoint(120, 80);
    // no setStartPoint() call at all

    double[] xy = sendAndDecodeBoundingBoxMin(ruida, job);
    assertEquals(600 - 120, xy[0], 0.05);
    assertEquals(80, xy[1], 0.05);
  }
}
