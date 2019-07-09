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

import com.t_oster.liblasercut.ByteArrayList;
import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.Raster3dPart;
import com.t_oster.liblasercut.RasterPart;
import com.t_oster.liblasercut.RasterizableJobPart;
import com.t_oster.liblasercut.VectorCommand;
import static com.t_oster.liblasercut.VectorCommand.CmdType;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Circle;
import com.t_oster.liblasercut.platform.Point;
import com.t_oster.liblasercut.platform.Util;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class implements a driver for the LTT iLaser 4000
 * http://lttcorp.com/product_iLaser.html
 *
 * This driver should also work for other models from LTT.
 *
 * @author Maximilian Gaukler <development@maxgaukler.de>
 * with some code taken from LaosCutter.java
 */
public class LaserToolsTechnicsCutter extends LaserCutter
{

  private static final String SETTING_HOSTNAME = "Hostname / IP";
  private static final String SETTING_PORT = "Port";
  private static final String SETTING_BEDWIDTH = "Laserbed width (mm)";
  private static final String SETTING_BEDHEIGHT = "Laserbed height (mm)";
  private static final String SETTING_FLIPX = "X axis goes right to left (yes/no)";
  private static final String SETTING_FLIPY = "Y axis goes bottom to top (yes/no)";
  private static final String SETTING_MAXDPI = "machine DPI";
  private static final String SETTING_MAX_ENGRAVE_DPI = "maximum engrave DPI";
  private static final String SETTING_CUTTING_SPEED = "Cutting speed in mm/s at 100% speed";
  private static final String SETTING_TANGENT_ACCEL = "Tangent Curve: maximum acceleration in mm/s^2";
  private static final String SETTING_TANGENT_ENABLE = "Tangent Curve: Enable 'joint tangent curve'";
  private static final String SETTING_ARCCOMP_ENABLE = "'Arc Compensation' is enabled in the machine's configuration menu (Recommended: yes)";
  private static final String SETTING_RASTER_WHITESPACE_MAX = "Engrave: Additional left/right space at 100% speed (mm, see windows driver EngraveExtraSpace divided by 10)";
  private static final String SETTING_RASTER_WHITESPACE_MIN = "Engrave: Additional left/right space at low speed (mm, may be 0, see windows driver EngraveExtraSpace divided by 10)";
  private static final String SETTING_RASTER_SHIFTTABLE = "Engrave shift table (offset in 1/(machine dpi) at 10, 20, ..., 100% speed; whitespace separated list of integers; empty to disable; see windows driver EngraveShiftTbl.)";
  private static final String SETTING_DEBUGFILE = "Debug output file";
  private static final String SETTING_SUPPORTS_PURGE = "Supports purge";
  private static final String SETTING_SUPPORTS_VENTILATION = "Supports ventilation";
  private static final String SETTING_SUPPORTS_FREQUENCY = "Supports frequency";
  private static final String SETTING_SUPPORTS_FOCUS = "Supports focus (Z-axis movement)";

  private boolean supportsFrequency = false;

  public boolean isSupportsFrequency()
  {
    return supportsFrequency;
  }

  public void setSupportsFrequency(boolean supportsFrequency)
  {
    this.supportsFrequency = supportsFrequency;
  }

  private boolean supportsFocus = false;

  public boolean isSupportsFocus()
  {
    return supportsFocus;
  }

  public void setSupportsFocus(boolean supportsFocus)
  {
    this.supportsFocus = supportsFocus;
  }

  private boolean supportsPurge = false;

  public boolean isSupportsPurge()
  {
    return supportsPurge;
  }

  public void setSupportsPurge(boolean supportsPurge)
  {
    this.supportsPurge = supportsPurge;
  }

  private boolean supportsVentilation = false;

  public boolean isSupportsVentilation()
  {
    return supportsVentilation;
  }

  public void setSupportsVentilation(boolean supportsVentilation)
  {
    this.supportsVentilation = supportsVentilation;
  }

  private String debugFilename = "";

  @Override
  public LaosCutterProperty getLaserPropertyForVectorPart()
  {
    return new LaosCutterProperty(!this.supportsPurge, !this.supportsVentilation, !this.supportsFocus, !this.supportsFrequency);
  }

  @Override
  public LaosEngraveProperty getLaserPropertyForRasterPart()
  {
    return new LaosEngraveProperty(!this.supportsPurge, !this.supportsVentilation, !this.supportsFocus, !this.supportsFrequency);
  }

  @Override
  public LaosEngraveProperty getLaserPropertyForRaster3dPart()
  {
    return new LaosEngraveProperty(!this.supportsPurge, !this.supportsVentilation, !this.supportsFocus, !this.supportsFrequency);
  }

  private double addSpacePerRasterLineMaximum = 35;
  private double addSpacePerRasterLineMinimum = 3.5; // 0 would be theoretically okay, but practically it isn't.

  /**
   * Get the number of overscan per raster line
   *
   * @param speedPercent speed in percent of maximum
   */
  public double getAddSpacePerRasterLine(double speedPercent)
  {
    // for constant acceleration a, we have x(t) = v(t)^2 / (2*a).
    // this means, the overscan should be proportional to speedPercent^2
    return Math.max(addSpacePerRasterLineMinimum, addSpacePerRasterLineMaximum * Math.pow(speedPercent / 100., 2));
  }

  @Override
  public String getModelName()
  {
    return "LTT iLaser 4000 (and probably other models)";
  }
  protected boolean useTangentCurves = false;

  /**
   * Get the value of useTangentCurves
   *
   * @return the value of useTangentCurves
   */
  public boolean isUseTangentCurves()
  {
    return useTangentCurves;
  }

  public void setUseTangentCurves(boolean useTangentCurves)
  {
    this.useTangentCurves = useTangentCurves;
  }

  // Is the "Arc compensation" setting in the laser firmware enabled?
  private boolean laserArcCompensationEnabled = true;

  public boolean isLaserArcCompensationEnabled()
  {
    return laserArcCompensationEnabled;
  }

  public void setLaserArcCompensationEnabled(boolean laserArcCompensationEnabled)
  {
    this.laserArcCompensationEnabled = laserArcCompensationEnabled;
  }


  private double tangentCurveMaxAcceleration = 2000; // slightly less than 0.24*g = 0.24* (9.81 * 1000 mm/s^2)

  public double getTangentCurveMaxAcceleration()
  {
    return tangentCurveMaxAcceleration;
  }

  public void setTangentCurveMaxAcceleration(double tangentCurveMaxAcceleration)
  {
    this.tangentCurveMaxAcceleration = tangentCurveMaxAcceleration;
  }

  private double nominalCuttingSpeed = 338.677;

  /**
   * Nominal cutting speed in mm/s at 100% speed
   *
   * @return
   */
  public double getNominalCuttingSpeed()
  {
    return nominalCuttingSpeed;
  }

  public void setNominalCuttingSpeed(double nominalCuttingSpeed)
  {
    this.nominalCuttingSpeed = nominalCuttingSpeed;
  }

  protected boolean flipXaxis = false;

  /**
   * Get the value of flipXaxis
   *
   * @return the value of flipXaxis
   */
  public boolean isFlipXaxis()
  {
    return flipXaxis;
  }

  /**
   * Set the value of flipXaxis
   *
   * @param flipXaxis new value of flipXaxis
   */
  public void setFlipXaxis(boolean flipXaxis)
  {
    this.flipXaxis = flipXaxis;
  }

  protected boolean flipYaxis = true;

  /**
   * Get the value of flipYaxis
   *
   * @return the value of flipYaxis
   */
  public boolean isFlipYaxis()
  {
    return flipYaxis;
  }

  /**
   * Set the value of flipYaxis
   *
   * @param flipYaxis new value of flipYaxis
   */
  public void setFlipYaxis(boolean flipYaxis)
  {
    this.flipYaxis = flipYaxis;
  }

  protected String hostname = "192.168.123.111";

  /**
   * Get the value of hostname
   *
   * @return the value of hostname
   */
  public String getHostname()
  {
    return hostname;
  }

  /**
   * Set the value of hostname
   *
   * @param hostname new value of hostname
   */
  public void setHostname(String hostname)
  {
    this.hostname = hostname;
  }
  protected int port = 9100;

  /**
   * Get the value of port
   *
   * @return the value of port
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Set the value of port
   *
   * @param port new value of port
   */
  public void setPort(int port)
  {
    this.port = port;
  }
  protected double maxDPI = 4000;

  /**
   * Get the value of maxDPI
   *
   * @return the value of maxDPI
   */
  public double getMaxDPI()
  {
    return maxDPI;
  }

  /**
   * Set the value of maxDPI
   *
   * @param maxDPI new value of maxDPI
   */
  public void setMaxDPI(double maxDPI)
  {
    this.maxDPI = maxDPI;
  }

  protected double maxEngraveDPI = 1000;

  public void setMaxEngraveDPI(double maxEngraveDPI)
  {
    this.maxEngraveDPI = maxEngraveDPI;
  }

  public double getMaxEngraveDPI()
  {
    return maxEngraveDPI;
  }

  private int px2steps(double px, double dpi)
  {
    return (int) (Util.px2mm(px, dpi) / this.maxDPI);
  }

  private byte[] toBytes(int[] x)
  {
    byte[] bytes = new byte[x.length];
    for (int i = 0; i < x.length; i++)
    {
      bytes[i] = (byte) x[i];
    }
    return bytes;
  }

  /**
   * decode hex string to byte array:
   * toBytes("42 ff 3c") = new byte[] { (byte) 0x42, (byte) ...}
   *
   * all spaces are ignored, no matter where you place them.
   *
   * @param s
   * @return
   */
  private byte[] toBytes(String s)
  {
    int i = 0;
    s = s.replace(" ", "").toLowerCase();
    byte[] bytes = new byte[s.length() / 2];
    boolean lowerNibble = true;
    int lastCharacterValue = 0;
    for (char c : s.toCharArray())
    {
      int currentCharacterValue;
      if (c >= '0' && c <= '9')
      {
        currentCharacterValue = c - '0';
      }
      else if (c >= 'a' && c <= 'f')
      {
        currentCharacterValue = 10 + c - 'a';
      }
      else
      {
        throw new IllegalArgumentException("argument must be a hex string like 42ff3cfa");
      }
      if (!lowerNibble)
      {
        bytes[i++] = (byte) (lastCharacterValue * 16 + currentCharacterValue);
      }
      lowerNibble = !lowerNibble;
      lastCharacterValue = currentCharacterValue;
    }
    if (!lowerNibble)
    {
      throw new IllegalArgumentException("hex string must have even number of nibbles");
    }
    return bytes;
  }

  private int limit(int value, int min, int max)
  {
    if (value <= min)
    {
      return min;
    }
    else if (value >= max)
    {
      return max;
    }
    else
    {
      return value;
    }
  }

  /**
   * send a 2-byte unsigned value
   *
   * @param out output stream
   * @param value integer value (nonnegative)
   */
  private void writeU16(OutputStream out, int value) throws IOException
  {
    if (value >= 1 << 16 || value < 0)
    {
      throw new IllegalArgumentException();
    }
    out.write((value >> 8) & 0xFF);
    out.write(value & 0xFF);
  }

  /**
   * send as 4-byte unsigned integer
   *
   * @param out output stream
   * @param value integer value (nonnegative)
   */
  private void writeU32(OutputStream out, long value) throws IOException
  {
    if (value >= (1L << 32) || value < 0)
    {
      throw new IllegalArgumentException("invalid value for uint32");
    }
    out.write((int) ((value >> 24) & 0xFF));
    out.write((int) ((value >> 16) & 0xFF));
    out.write((int) ((value >> 8) & 0xFF));
    out.write((int) (value & 0xFF));
  }

  /**
   * send as 4-byte signed integer
   *
   * @param out
   * @param value
   * @throws IOException
   */
  private void writeS32(OutputStream out, long value) throws IOException
  {
    if (Math.abs(value) >= (1L << 31))
    {
      throw new IllegalArgumentException();
    }
    if (Math.abs(value) >= 0x10000000)
    {
      // this would be 0x10000000/4000*inch/km= 1,7 kilometers of movement!!!
      // Must be a conversion errror somewhere.
      // Please invite me if your machine is that large :-D
      throw new IllegalArgumentException("suspiciously high byte value");
    }
    if (value < 0)
    {
      value = value & ((1L << 32) - 1);
    }
    writeU32(out, value);
  }

  private byte[] generateVectorCode(VectorPart vp, double resolution) throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");

    out.write(toBytes("1B 56")); // start vector mode

    out.write(toBytes("1B 45 00 00 00 00 00 00 00")); // disable pulse mode

    setColorCode(out, Color.RED);

    setCurrentDPI(out, resolution, true);

    out.write(toBytes("50 53")); // Vector Pause Flag (probably unused)

    setFrequency(out, 1000); // default frequency (will be overriden later by the profile, except if "enable frequency" is false in the config)
    ArrayList<Double> x = new ArrayList<Double>();
    ArrayList<Double> y = new ArrayList<Double>();
    for (VectorCommand cmd : vp.getCommandList())
    {
      if (cmd.getType() == CmdType.LINETO)
      {
        x.add(cmd.getX());
        y.add(cmd.getY());
      }
      else
      {
        curveOrLine(out, x.toArray(new Double[0]), y.toArray(new Double[0]), resolution);
        x = new ArrayList<Double>();
        y = new ArrayList<Double>();
        switch (cmd.getType())
        {
          case MOVETO:
            move(out, cmd.getX(), cmd.getY(), resolution);
            break;
          case SETPROPERTY:
          {
            this.setCurrentProperty(out, cmd.getProperty());
            break;
          }
        }
      }
    }
    curveOrLine(out, x.toArray(new Double[0]), y.toArray(new Double[0]), resolution);
    setLaserOn(out, false);
    return result.toByteArray();
  }

  private void setCurrentDPI(PrintStream out, double resolution, boolean isVectorActive) throws IOException
  {
    myAssert(resolution == 500); // TODO: resolution is set with the "Prescale" command, not this PPI command. Fix this whole function.

    if (Math.abs(((int) resolution) - resolution) > 1e-10)
    {
      // we do this check here because the library allows non-integer DPI
      throw new IllegalArgumentException("DPI must be integer");
    }

    // check range
    double maximumResolution;
    if (isVectorActive)
    {
      maximumResolution = maxDPI;
    }
    else
    {
      maximumResolution = maxEngraveDPI;
    }
    if (resolution > maximumResolution)
    {
      if (isVectorActive)
      {
        throw new IllegalArgumentException("resolution is larger than supported for vector mode");
      }
      else
      {
        throw new IllegalArgumentException("resolution is larger than supported for engrave mode");
      }
    }
    if (resolution <= 1)
    {
      throw new IllegalArgumentException("DPI must be >1");
    }
  }

  public enum Color
  {

    BLACK(0), RED(1), GREEN(2), YELLOW(3), BLUE(4), MAGENTA(5), CYAN(6), ORANGE(7);
    final public int value;

    Color(int value)
    {
      this.value = value;
    }
  ;

  };

  /**
   * Set color for the preview on the lasercutter's display.
   */
  private void setColorCode(PrintStream out, Color color) throws IOException
  {
    out.write(toBytes("1B 4E"));
    out.write(color.value);
  }

  private transient boolean laserOn = false;

  private void setLaserOn(PrintStream out, boolean on)
  {
    if (on != laserOn)
    {
      if (on)
      {
        out.print("PD"); // 50 44 laser on
      }
      else
      {
        out.print("PU"); // 50 55 laser off
      }
      laserOn = on;
    }
  }

  /**
   * Switch off laser and move to coordinate
   */
  private void move(PrintStream out, double x, double y, double resolution) throws IOException
  {
    setLaserOn(out, false);
    goToCoordinate(out, x, y, resolution, false);
  }

  /**
   * Switch on laser and cut a line to the given coordinate
   */
  private void line(PrintStream out, double x, double y, double resolution) throws IOException
  {
    setLaserOn(out, true);
    goToCoordinate(out, x, y, resolution, true);
  }

/**
 * Switch on the laser and cut a full circle, starting from the current point,
 * around the given center point.
 * @param out PrintStream for writing the commands
 * @param center Center point
 * @param resolution DPI for converting pixels to mm
 * @throws IOException
 */

  // Note for future development: The same command can also be used for arcs (e.g., quarter circles).
  private void circle(PrintStream out, Point center, double resolution) throws IOException
  {
    // the start point is implicitly the current point.
    setLaserOn(out, true);
    out.print("PB"); // PB: clockwise, PC: counterclockwise
    // end point relative to start point
    // here: 0,0 because we cut a full circle
    writeS32(out, 0);
    writeS32(out, 0);
    // center point relative to start point
    sendCoordinate(out, (int) center.x - (int) currentX, (int) center.y - (int) currentY, resolution, true);
  }



  class PointWithSpeed extends Point
  {

    public double speed = Double.NaN;
    public Point deltaToPrevious = null;
    public double absAngleAtCorner = Double.NaN;

    PointWithSpeed(double x, double y)
    {
      super(x, y);
    }
  }

  /**
   * cut a smooth curve with given interpolation points and interpolation speeds
   *
   * @param out
   * @param x coordinates, starting with currentX
   * @param y coordinates, starting with currentY
   * @param endSpeed end speed at given coordinate (in percent, relative to
   * maximum speed (nominalCuttingSpeed), MUST obey the acceleration limit)
   * @param resolution
   * @throws IOException
   */
  private void curveWithKnownSpeed(PrintStream out, ArrayList<PointWithSpeed> points, double resolution) throws IOException
  {
    setLaserOn(out, true);
    out.print("PJ"); // start join
    double currentSpeedX = 0;
    double currentSpeedY = 0;
    double currentSpeedXY = 0;
    double maxXYAccelerationSeen = 0;
    double avgXYAccelerationSeen = 0;
    double speedPercent = Double.NaN;
    double totalTime = 0;
    for (int i = 0; i < points.size(); i++)
    {
      double x = points.get(i).x;
      double y = points.get(i).y;
      double speedPercentBefore = speedPercent;
      speedPercent = points.get(i).speed;
      if (i == 0)
      {
        if ((currentX != x) || (currentY != y))
        {
          throw new IllegalArgumentException("curve does not start with the current point");
        }
       continue;
      }
      if ((currentX == x) && (currentY == y))
      {
        throw new IllegalArgumentException("curve contains a duplicate point");
      }
      final double speedPercentToMmPerSec = (1 / 100. * nominalCuttingSpeed);

      // all the following double variables are in mm, mm/s, s or mm/s^2 respectively.
      double newDx = Util.px2mm(x - currentX, resolution);
      double newDy = Util.px2mm(y - currentY, resolution);
      double newLength = Math.hypot(newDx, newDy);
      // The speed vector at xy[i] has the direction (xy[i] - xy[i-1]).
      double newSpeedX = speedPercent * speedPercentToMmPerSec * newDx / newLength;
      double newSpeedY = speedPercent * speedPercentToMmPerSec * newDy / newLength;
      double newSpeedXY = Math.hypot(newSpeedX, newSpeedY);
      double newTime = newLength / ((currentSpeedXY + newSpeedXY) / 2);
      totalTime += newTime;
      double newAccelerationX = Math.abs(newSpeedX - currentSpeedX) / newTime;
      double newAccelerationY = Math.abs(newSpeedY - currentSpeedY) / newTime;
      double newAccelerationXY = Math.hypot(newAccelerationX, newAccelerationY);
      System.out.println("point " + i + ": vNew: " + newSpeedXY + " dxy: " + newLength  + " dt: " + newTime + " xy accel: " + newAccelerationXY);
      maxXYAccelerationSeen = Math.max(maxXYAccelerationSeen, newAccelerationXY);
      avgXYAccelerationSeen += newAccelerationXY;
      final double tolerance = 1.00001; // 1 + epsilon,  against numerical errors
      if (Double.isNaN(newAccelerationX) || newAccelerationX > tangentCurveMaxAcceleration * tolerance)
      {
        throw new IllegalArgumentException("X acceleration of joint curve segment " + i + "  is too high.");
      }
      if (Double.isNaN(newAccelerationY) || newAccelerationY > tangentCurveMaxAcceleration * tolerance)
      {
        throw new IllegalArgumentException("Y acceleration of joint curve segment " + i + " is too high.");
      }
      if (newAccelerationXY > tangentCurveMaxAcceleration * tolerance)
      {
        throw new IllegalArgumentException("euclidean sqrt(X^2+Y^2) acceleration of joint curve segment " + i + " is too high.");
      }
      currentSpeedX = newSpeedX;
      currentSpeedY = newSpeedY;
      currentSpeedXY = newSpeedXY;


      out.print("PE"); // 50 45 End Speed
      // The speed value is roughly like the F speed in GCode, but:
      // - the laser may assume that a lookahead buffer of just one point is enough
      //   (that's why we need all these complicated computations).
      // - the speed we send is the target speed at the *middle* of the segment:
      double sentSpeed = (speedPercentBefore + speedPercent)/2;
      double diagonalCorrection;
      if (isLaserArcCompensationEnabled())
      {
        // - if "Arc compensation" is enabled in the laser settings,
        //   the speed we send is the vector magnitude sqrt(v_x^2+v_y^2),
        //   just as a sane person would do it.
        diagonalCorrection = 1;
      } else {
        //   If it is disabled (unusual), this would need to be changed,
        //   because the speed we send is then used as the speed value of the
        //   faster axis, max(abs(v_x), abs(v_y)):
        //   speedPercent points in the (dx,dy) direction, compute the largest component:
        diagonalCorrection = Math.max(Math.abs(newDx), Math.abs(newDy)) / newLength;
      }
      sentSpeed = sentSpeed * diagonalCorrection;

      if (i == 0) {
        // start point:
        // this case does not happen, we don't send the start point,
        // therefore we also don't send its speed (which is implicitly zero)
        myAssert(false);
      } else if (i == points.size() - 1) {
        // end point:
        // zero speed at the end point by definition.
        myAssert(speedPercent == 0);
      } else {
        // normal points (neither start nor end) must have nonzero speed
        myAssert(speedPercent > 0);
      }
      writeU16(out, limit((int) Math.round(sentSpeed * 10), 1, 1000));
      line(out, x, y, resolution); // not actually a line, will be interpreted as smooth curve segment
      // currentX and currentY are set by this call to line()
    }
    avgXYAccelerationSeen = avgXYAccelerationSeen / points.size();
    System.out.println("Maximum acceleration used is " + maxXYAccelerationSeen / tangentCurveMaxAcceleration * 100 + " percent of maximum, average is " + avgXYAccelerationSeen / tangentCurveMaxAcceleration * 100 + "%.");
    System.out.println("Cutting time for this curve is " + totalTime + " s.");
    out.print("PF"); // end join
  }

  /**
   * Reinterpolate the list of points by adding linear interpolated intermediate
   * points until all points are less than maxDistance apart
   *
   * @param points Input points, which must not be more than 1e6*maxDistance
   * apart (which should be no problem, as for example 1e6 * 0,1mm = 100 meters)
   * @param maxDistance
   * @return
   */
  ArrayList<PointWithSpeed> reinterpolateWithMaximumDistance(ArrayList<PointWithSpeed> points, double maxDistance)
  {
    if (points.isEmpty())
    {
      return new ArrayList<PointWithSpeed>(0);
    }
    double totalLength = 0;
    for (PointWithSpeed point : points.subList(1, points.size()))
    {
      totalLength += point.deltaToPrevious.hypot();
    }
    ArrayList<PointWithSpeed> pointsNew = new ArrayList<PointWithSpeed>(points.size() + (int) (totalLength / maxDistance) + 1);
    pointsNew.add(points.get(0));
    PointWithSpeed previousPoint = points.get(0);
    for (PointWithSpeed point : points.subList(1, points.size()))
    {
      double len = point.deltaToPrevious.hypot();
      myAssert(len < 1e6 * maxDistance); // input points must not be more apart than 1e6*maxDistance
      if (len == 0) {
        continue;
      }
      if (len < maxDistance)
      {
        pointsNew.add(point);
      }
      else
      {
        // in the end, we have the previous point, $numExtraPoints intermediate points, and the current point.
        // Number of intermediate points:
        // 0 if len==maxDistance
        // 1 if maxDist < len <= 2*maxDist
        // 2 if 2*maxDist < len <= 3*maxDist
        // ...
        int numExtraPoints = (int) Math.ceil(len / maxDistance) - 1;
        for (int i = 0; i < numExtraPoints; i++)
        {
          Point tmp = point.subtract(point.deltaToPrevious.scale((numExtraPoints - i + 0.d) / (1 + numExtraPoints)));
          PointWithSpeed intermediate = new PointWithSpeed(tmp.x, tmp.y);
          intermediate.absAngleAtCorner = 0;
          intermediate.deltaToPrevious = intermediate.subtract(previousPoint);
          pointsNew.add(intermediate);
          previousPoint = intermediate;
        }
        point.deltaToPrevious = point.subtract(previousPoint);
        pointsNew.add(point);
      }
      previousPoint = point;
    }
    return pointsNew;
  }

  // maximum position inaccuracy in mm
  // TODO!
  private static final double lengthTolerance = 1e-1; // TODO try out different values
  // maximal angle which may be smoothed, as long as position accuracy is satisfied
  // must be < 90° because otherwise the interpolation code breaks (180° may also be okay, but careful review would be required)
  private static final double angleToleranceShort = Math.toRadians(40);
  // finer restriction on maximal angle for line segments which have a significant length
  // TODO has no effect, except if made really small for certain length???
  private static final double angleToleranceLong = Math.toRadians(10);

  /**
   * Cut a smooth curve with given interpolation points.
   * Only two special cases for the input are permitted:
   * - the list contains exactly two points: Cut a line segment.
   * - all points can be cut in one smooth curve (curvature is small enough).
   * The first point is ignored and must be the current coordinate.
   *
   * @param out
   * @param points
   */
  private void curve(PrintStream out, ArrayList<PointWithSpeed> points, double resolution) throws IOException
  {
    if (points.size() <= 1)
    {
      return;
    }
    if (points.size() == 2)
    {
      // only two points: cut a line segment
      boolean firstPoint = true;
      for (Point point : points)
      {
        if (firstPoint)
        {
          // skip first point in list, it is just there to simplify the code
          firstPoint = false;
        }
        else
        {
          line(out, point.x, point.y, resolution);
        }
      }
      return;
    }

    // more than two points: cut a smooth curve. For this, compute the interpolated speed.


    // The final data format sent to the lasercutter is a polyline with speeds for each point.
    //
    // Experiments suggest that the lasercutter does not do spline interpolation
    // on the curve that we send to it, but more or less tries to go along the polyline
    // that we send to it. However, this polyline is effectively low-pass-filtered
    // due to the servo control loop (especially due to belt elasticity).
    // It is also acceleration-limited per individual axis:
    // - The firmware limits acceleration along the "longer" axis
    //   (e.g. X-axis for a move which is larger in delta-X than in delta-Y),
    //   but cannot do lookahead. So we still need to compute velocities which
    //   are possible within the acceleration limit.
    // - The "shorter" axis blindly follows the "longer" axis. Therefore, its
    //   desired velocity (as sent to the servo controller) jumps at angles.
    //   This jump is filtered by the servo control loop (response time of
    //   servo controller) and the mechanics (belt elasticity, laser head inertia).
    // In the end, it is more or less enough to make sure that
    // - the average acceleration (discrete difference quotient delta_v/delta_t between two points)
    //   stays within the limit
    // - the time delta_t in the above computation is not too large, either by
    //   interpolating the points fine enough, or if that fails, just assuming
    //   a fictuous smaller delta_t.


    // Here be dragons:
    //
    // Problem formulation (neglecting unit conversion):
    // given points p[i]
    // vector speed: speed[i] = p[i].speed * p[i].deltaToPrevious.unityVector()
    // vector acceleration: (speed[i] - speed[i-1]) / time[i]
    // time[i] = (p[i] - p[i-1]) / (speed[i]*.5 + speed[i-1]*.5)
    // now, we want to maximize p[i].speed, under the condition that
    // abs(acceleration) < maxAcceleration.
    // This is not easy, because there are forward and backward dependencies!

    // Argh, all these stupid unit conversions.
    // Here we use: mm/s and mm/s^2 for speed (we convert back to manufacturer units in the very end)
    // and pixels (1/dpi, aargh) for the lengths.
    final double maxSpeed = (currentSpeed * nominalCuttingSpeed / 100);

    // conversion factor from mm/s to "% speed".
    final double relativeSpeedToMmPerSec = (100 / nominalCuttingSpeed);
    final double pxToMm = Util.px2mm(1, resolution);

    // numerical safety factor:
    // in theory this may be 1.0, but we keep it slightly smaller to stay away
    // from the exact boundaries which may cause trouble with numerical comparisons.
    final double safetyFactor = 0.99;

    // reinterpolate the points
    // (The windows driver has a table for maximum change of speed between points.
    //  For very low speeds, it effectively uses roughly 0.3mm maximum distance,
    //  but a lot more for higher speeds. However, it seems that sending so many
    // intermediate points isn't actually required.)
    // TODO do this better, or at least do some postprocessing so that we don't send all these points to the cutter.
    points = reinterpolateWithMaximumDistance(points, Util.mm2px(3, resolution));

    // set speed to maximum, and in the following only reduce it.
    for (PointWithSpeed p : points)
    {
      p.speed = maxSpeed;
    }

    // start and end speed is zero
    points.get(0).speed = 0;
    points.get(points.size() - 1).speed = 0;

    // start angle doesn't make sense, can be treated as zero.
    points.get(0).absAngleAtCorner = 0;

    System.out.println("started.");
    // reduce speed wherever the acceleration limit is not hit
    boolean somethingChanged = true; // some speed was reduced in the current iteration
    int countIterations = 0; // for logging only

    /* "Warmup cycles":
     * to prevent too pessimistic results, we use optimistic (unsafe)
     * approximations while warmupCount>0, slowly going back to safe but
     * pessimistic approximations.
     * For warmupCount=0, everything is safely approximated, but we already know
     * that the speed cannot be higher than what was computed before under
     * optimistic approximations.
     * (This is loosely related to "simulated annealing").
     *
     * If you don't understand this, just set WARMUP_ROUNDS=0.
     * Everything will work fine, except that you have slightly lower speeds in
     * some cases.
     */
    final int WARMUP_ROUNDS = 10; // number of overapproximated "warmup" rounds - increases computation time (linearly), but improves speed (by ca 15% for a typical curve, more for extreme cases with small radii)
    final double WARMUP_FACTOR_MAX = 16; // overapproximation for first warmup round - should roughly be at least the typical factor between the maximum speed and a typical low speed on sharp bends of the curve
    int warmupCount = WARMUP_ROUNDS;

    while (somethingChanged || warmupCount > 0)
    {
      for (PointWithSpeed point : points)
      {
        if (Double.isNaN(point.speed)) {
          throw new AssertionError("point speed is NaN");
        }
      }
      // translate warmupCount into an "unsafety" factor (>1 = unsafe but optimistic approximation)
      double optimismFactor = 1; // 1 for a safe pessimistic approximation, larger for an optimistic approximation
      if (warmupCount > 0) {
        warmupCount--;
      }
      if (warmupCount > 0 && WARMUP_ROUNDS > 0) {
        optimismFactor = Math.pow(WARMUP_FACTOR_MAX, warmupCount / (double) WARMUP_ROUNDS);
      }
      System.out.println("iteration: " + countIterations++);
      if (warmupCount > 0) {
        System.out.println("this is a warmup iteration.");
      }
      int count = 0;
      for (PointWithSpeed point : points)
      {
        //System.out.println("i=" + count++ + ",  xy=" + point.x + "," + point.y + " @ " + point.speed + "    , angle=" + point.absAngleAtCorner + " rad");
      }
      somethingChanged = false;
      // ignore the first.
      for (int i = 1; i < points.size(); i++)
      {
        PointWithSpeed pBefore = points.get(i - 1);
        PointWithSpeed p = points.get(i);
        double maxAvgSpeed = (p.speed + pBefore.speed) / 2; // this is an overapproximation of the actual average speed when the computation has finished, because we only decrease and never increase speed.
        double distanceToPrevious = p.deltaToPrevious.hypot() * pxToMm;
        double minTime = distanceToPrevious / maxAvgSpeed; // This is an underapproximation, assuming maximal velocity. We could accelerate more per length if the velocity is smaller! (Therefore, the "warmup" iterations are used.)

        // Special handling for *angled* segments which take longer than the "smoothing time" of the servo controller:
        // Due to the way the machine works (polyline is directly sent to servo controller),
        // the actual acceleration at angled line segments takes place within less than MAX_ACCEL_TIME (ca. 10ms), even if the following segment is very long!
        final double MAX_ACCEL_TIME = 0.005;

        // Note: We compare absAngleAtCorner with zero. This is guaranteed to
        // match points which were added by reinterpolateWithMaximumDistance(),
        // but will not always match collinear points from the original path
        // due to numerical errors.
        if (pBefore.absAngleAtCorner != 0 && minTime > MAX_ACCEL_TIME)
        {
          minTime = MAX_ACCEL_TIME;
          if (warmupCount == 0) {
            System.out.println("point " + i +  " takes longer than MAX_ACCEL_TIME, it could have profited from finer interpolation.");
          }
          // reducing minTime is possible without breaking the assumptions of the following algorithm,
          // because minTime is only used as an underapproximation (guaranteed lower bound).
          // TODO: Instead of just artificially slowing down, we should rather
          //       reinterpolate the path, i.e., insert intermediate points so that
          //       the final output points are less than MAX_ACCEL_TIME apart in time
        }

        // vectorial: newSpeed = oldSpeed + acceleration * time
        // new speed must point into the direction of p[i].deltaToPrevious.unityVector().
        // we know that angle(newSpeed,oldSpeed) < maxAngle
        // and abs(acceleration) < maxAcceleration.
        // Direction of acceleration is arbitrary.
        // Now, what is the minimum and maximum possible length of newSpeed?
        // see LaserToolsTechnicsCutter_speedInterpolation.svg, case 1 and 1b.
        // Is it possible to reach any speed pointing in the fixed direction of newSpeed?
        // speed * sin(angle) < acceleration * time ?
        // TODO: switch from math.sin() to something faster, overapproximative.
        double alpha = pBefore.absAngleAtCorner;
        if (pBefore.speed * Math.sin(alpha) > tangentCurveMaxAcceleration * optimismFactor * minTime)
        {
          System.out.println("point " + i + ": Reducing previous velocity (corner too angled)");
          // It is impossible. The previous(!) velocity needs to be lowered.
          // see LaserToolsTechnicsCutter_speedInterpolation.svg, case 2.
          // Make it small enough so that we can just get around the corner.
          // Otherwise it would be impossible to compute a valid speed for the current point.
          pBefore.speed = safetyFactor * tangentCurveMaxAcceleration * optimismFactor * minTime / alpha;
          somethingChanged = true;
        }

        // Now limit the current speed to its maximum possible range
        double a = pBefore.speed;
        double c = tangentCurveMaxAcceleration * optimismFactor * minTime;
        double cosAlpha = Math.cos(alpha);
        double sinAlpha = Math.sin(alpha);

        double maxSpeedForAccel = a * cosAlpha + Math.sqrt(-a * a * sinAlpha * sinAlpha + c * c);
        double minSpeedForAccel = a * cosAlpha - Math.sqrt(-a * a * sinAlpha * sinAlpha + c * c);
        if (Double.isNaN(minSpeedForAccel) || Double.isNaN(maxSpeedForAccel))
        {
          throw new AssertionError("failed to compute a velocity range");
        }
        if (maxSpeedForAccel > maxSpeed)
        {
          if (minSpeedForAccel < maxSpeed)
          {
            maxSpeedForAccel = maxSpeed;
          }
          else
          {
            throw new AssertionError("computed speed range is completely outside maxSpeed. This must not happen.");
          }
        }
        // not required: minSpeedForAccel = Math.max(minSpeedForAccel, 0);
        if (p.speed > maxSpeedForAccel)
        {
          System.out.println("point " + i + ": Reducing current velocity due to acceleration limit (previous point too slow)");
          // This point's speed is higher than possible with maximum acceleration.
          // Reduce it.
          p.speed = maxSpeedForAccel * safetyFactor + Math.max(0, minSpeedForAccel) * (1 - safetyFactor);
          somethingChanged = true;
        }
        else if (p.speed < minSpeedForAccel)
        {
          System.out.println("point " + i + ": Reducing previous velocity due to decelleration limit. (current point too slow)");
          // This point's speed is lower than what is possible with maximum deceleration.
          // Reduce the previous speed.
          // see LaserToolsTechnicsCutter_speedInterpolation.svg case 4.
          // reduce a appropriately so that
          // a_reduced = b * cos(alpha) + sqrt(b^2*(-1)*sin^2(alpha) + c_max^2)
          // (see case 4, lower drawing).
          double b = p.speed;
          double newAMaximum = b * cosAlpha + Math.sqrt(b * b * (-1) * sinAlpha * sinAlpha + c * c);
          double newAMinimum = b * cosAlpha - Math.sqrt(b * b * (-1) * sinAlpha * sinAlpha + c * c);
          if (newAMinimum < 0) {
            newAMinimum = 0;
          }
          double newA = newAMaximum * safetyFactor + newAMinimum * (1-safetyFactor);
          pBefore.speed = newA;
          somethingChanged = true;
          if (Double.isNaN(newA)) {
            throw new AssertionError("point speed is NaN when reducing due to decelleration limit");
          }
          if (i >= 2 && warmupCount <= 0)
          {
            // restart the loop at the previous point, because it will most likely change as well
            // (usually, one point causes lots of previous points to be changed)
            // this may be left out, but will make the algorithm waaaay slower.

            i = i - 2; // effectively i-1 because there is i++ at the start of the loop.

            // TODO: it would be more efficient to switch the iteration direction: do not go one point backwards now, but later go backwards through the whole list
            continue;
          }
        }
      }
      if (!somethingChanged)
      {
        System.out.println("Nothing changed. stopping after " + countIterations + " iterations");
      }
    }
    // convert back to manufacturer units
    for (PointWithSpeed point : points)
    {
      point.speed *= relativeSpeedToMmPerSec;
    }
    curveWithKnownSpeed(out, points, resolution);
  }

  /**
   * given a polyline, cut it within a certain precision, if possible as smooth
   * curve if the corners are not too angled.
   *
   * The line implicitly starts at the current coordinate, i.e. this function
   * cuts the lines (currentX, currentY) --- (x[0], y[0]) --- (x[1], y[1])  ...
   *
   * Circles are detected and converted to the specific circle command.
   * We currently do not support Bezier splines, although this would allow for
   * huge improvements to this function: Arcs could also be sent with the circle
   * command, even as a part of a continuous "join path" (PJ command). Line
   * interpolation distance could be dynamically adapted to the velocity, so
   * that we interpolate in approximately fixed timesteps, not distance-steps.
   *
   * @param out PrintStream for writing the commands
   * @param x list of x coordinates in pixels
   * @param y list of y coordinates in pixels
   * @param resolution DPI for converting pixels to mm
   * @throws IOException
   */
  private void curveOrLine(PrintStream out, Double[] x, Double[] y, double resolution) throws IOException
  {

    if (!this.useTangentCurves)
    {
      // smooth curves are disabled, fall back to line segments
      for (int i = 0; i < x.length; i++)
      {
        line(out, x[i], y[i], resolution);
      }
      return;
    }

    if (x.length == 0)
    {
      return;
    }

    // less than 1 pixel length tolerance doesn't make too much sense, because the conversion from spline to polyline in ShapeConverter causes up to 1px error anyway.
    double lengthTolerancePixels = Math.max(1.f, Util.mm2px(lengthTolerance, resolution));

    ArrayList<PointWithSpeed> points = new ArrayList<PointWithSpeed>();
    PointWithSpeed lastPoint = new PointWithSpeed(currentX, currentY);
    points.add(lastPoint);
    for (int i = 0; i < x.length; i++)
    {
      PointWithSpeed newPoint = new PointWithSpeed(x[i], y[i]);
      newPoint.deltaToPrevious = newPoint.subtract(lastPoint);
      double newLen = newPoint.deltaToPrevious.hypot();
      if (newLen < lengthTolerancePixels)
      {
        // ignore (almost) duplicate point
        continue;
      }
      if (lastPoint.deltaToPrevious != null)
      {
        double absAngle = lastPoint.deltaToPrevious.absAngleTo(newPoint.deltaToPrevious);
        lastPoint.absAngleAtCorner = absAngle;
        // TODO: hardcoded factor: we actually use 5 times the configured smoothing tolerance!!!
        // TODO: the following if-condition should be simplified or completely rewritten.
        if (absAngle * newLen > lengthTolerancePixels * 5 || absAngle > angleToleranceShort || (newLen > lengthTolerancePixels * 100 && absAngle > angleToleranceLong))
        {
          // The curvature is too large: We are at a corner.
          // Split the list of points at the corner = at lastPoint.
          // Note that newPoint has not yet been added to the list.
          curve(out, points, resolution);
          points = new ArrayList<PointWithSpeed>();
          // copy the last point, but discard angle and deltaToPrevious because that doesn't make sense at the start of the list
          lastPoint = new PointWithSpeed(lastPoint.x, lastPoint.y);
          points.add(lastPoint);
        }
      }
      points.add(newPoint);
      lastPoint = newPoint;
    }
    if (points.size() == 1)
    {
      // after removing duplicates, there is no movement
      return;
    }
    // we have at least 2 segments remaining (since the last corner).

    // If there were no corners, the path may be a full circle, which we can send more efficiently as a circle command instead of a generic curve.
    Circle detectedCircle = Circle.fromPointList(points, lengthTolerancePixels);
    if (detectedCircle != null) {
      // we found a circle -- use it and send the specialized command
      System.out.println("We found a circle:" + detectedCircle);
      // The circle implicitly starts at the current point,
      circle(out, detectedCircle.center, resolution);
    } else {
      // It's a normal curve, just send it.
      curve(out, points, resolution);
    }
  }

  // for convenience, we support double coordinates, but the lasercutter only knows integers.
  private transient double currentX = -1;
  private transient double currentY = -1;

  /**
   * go to the specified coordinate
   * @param out PrintStream for writing the commands
   * @param x x coordinate in pixels
   * @param y y coordinate in pixels
   * @param resolution DPI for converting pixels to mm
   * @param sendAsRelative True: send a relative move command, False: send an absolute move command
   * @throws IOException
   */
  private void goToCoordinate(PrintStream out, double x, double y, double resolution, boolean sendAsRelative) throws IOException
  {
    if (sendAsRelative)
    {
      out.print("PR"); // relative position
      // note: we convert to int *before* subtracting, so that always: sum(relative increments that we send) == (int) currentX.
      sendCoordinate(out, (int) x - (int) currentX, (int) y - (int) currentY, resolution, true);
    }
    else
    {
      out.print("PA"); // 50 41: absolute position
      sendCoordinate(out, (int) x, (int) y, resolution, false);
    }
    currentX = x;
    currentY = y;
  }

  /**
   * send a coordinate WITHOUT ANY PRECEDING COMMAND.
   * coordinate is converted from the given resolution to machine resolution.
   * This does not make sense if you don't prepend a command.
   *
   * @param out
   * @param x
   * @param y
   * @param resolution
   * @throws IOException
   */
  private void sendCoordinate(PrintStream out, int x, int y, double resolution, boolean isRelative) throws IOException
  {
    // convert to maxDPI, which is the actual machine resolution
    x = (int) Math.round(x * maxDPI / resolution);
    y = (int) Math.round(y * maxDPI / resolution);
    int xMax = (int) Util.mm2px(bedWidth, maxDPI);
    int yMax = (int) Util.mm2px(bedHeight, maxDPI);
    myAssert(x < xMax);
    myAssert(y < yMax);
    if (!isRelative)
    {
      myAssert(x >= 0);
      myAssert(y >= 0);
      writeU32(out, (isFlipXaxis() ? xMax - x : x));
      writeU32(out, (isFlipYaxis() ? yMax - y : y));
    }
    else
    {
      // relative
      writeS32(out, (isFlipXaxis() ? -x : x));
      writeS32(out, (isFlipYaxis() ? -y : y));
    }
  }

  private transient float currentPower = -1;

  private void setPower(PrintStream out, float power) throws IOException
  {
    if (currentPower != power)
    {
      out.write(toBytes("1B 4A"));
      writeU16(out, limit((int) (power * 10), 1, 1000));
      currentPower = power;
    }
  }

  private transient float currentSpeed = -1;

  private void setSpeed(PrintStream out, float speed) throws IOException
  {
    if (currentSpeed != speed)
    {
      out.write(toBytes("1B 53"));
      writeU16(out, limit((int) (speed * 10), 1, 1000));
      currentSpeed = speed;
    }
  }

  private transient int currentFrequency = -1;

  private void setFrequency(PrintStream out, int frequency) throws IOException
  {
    if (currentFrequency != frequency)
    {
      //TODO: we just ignore the value and always use 1000.
      // Find out what it exactly does in cut and especially in the engrave mode.
      // Then make it work in both modes.
      out.write(toBytes("1B 50")); // set "PPI" (laser pulses per inch, similar to "frequency" setting of other cutters)
      int x = 4; // probably a divisor: actual PPI = maxDPI / x
      writeU16(out, x);
      currentFrequency = frequency;
    }
  }

  private transient float currentFocus = 0;

  private void setFocus(PrintStream out, float focus)
  {
    if (currentFocus != focus)
    {
      // TODO
      currentFocus = focus;
    }
  }

  // Job mode:
  private static final int JOB_MODE_XY = 0x00;
  private static final int JOB_MODE_ROTARY_AXIS = 0x10;
  private static final int JOB_MODE_1BIT_PER_PIXEL = 0x00; // engrave normal
  private static final int JOB_MODE_4BIT_PER_PIXEL = 0x01; // suggested for stamp engrave3d ("rubber power")
  private static final int JOB_MODE_8BIT_PER_PIXEL = 0x02; // engrave3d
  private static final int JOB_MODE_MASK_BITS_PER_PX = 0x0f; // bitmask for "bits per pixel"

  private transient int currentJobMode = -1;

  private void setJobMode(PrintStream out, int jobMode) throws IOException
  {
    if (jobMode == currentJobMode)
    {
      return;
    }
    currentJobMode = jobMode;
    out.write(toBytes("1B 4D"));
    out.write(jobMode);
  }

  /**
   * set radius for rotary engraving
   */
  private void setMaterialRadius(PrintStream out, double radiusMm) throws IOException
  {
    out.write(toBytes("1B 52"));
    writeU16(out, (int) (radiusMm / 0.01));
  }

  private Boolean currentVentilation = null;

  private void setVentilation(PrintStream out, boolean ventilation)
  {
    if (currentVentilation == null || !currentVentilation.equals(ventilation))
    {
      // TODO
      //out.printf(Locale.US, "7 6 %d\n", ventilation ? 1 : 0);
      currentVentilation = ventilation;
    }
  }

  private Boolean currentPurge = null;

  private void setPurge(PrintStream out, boolean purge)
  {
    if (currentPurge == null || !currentPurge.equals(purge))
    {
      // TODO
      //out.printf(Locale.US, "7 7 %d\n", purge ? 1 : 0);
      currentPurge = purge;
    }
  }

  private void setCurrentProperty(PrintStream out, LaserProperty p) throws IOException
  {
    if (p instanceof LaosCutterProperty)
    {
      LaosCutterProperty prop = (LaosCutterProperty) p;
      if (this.supportsFocus)
      {
        setFocus(out, prop.getFocus());
      }
      if (this.supportsVentilation)
      {
        setVentilation(out, prop.getVentilation());
      }
      if (this.supportsPurge)
      {
        setPurge(out, prop.getPurge());
      }
      setSpeed(out, prop.getSpeed());
      setPower(out, prop.getPower());
      if (this.supportsFrequency)
      {
        setFrequency(out, prop.getFrequency());
      }
    }
    else
    {
      throw new RuntimeException("The driver only accepts LaosCutter properties (was " + p.getClass().toString() + ")");
    }
  }

  private byte[] generateRasterCode(RasterizableJobPart rp, double resolution) throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    // TODO: handle the special case if the engraving is near the left or right end of the coordinate system.
    // -> we may use slightly negative or too large coordinates (check original driver output!)
    // -> and if that's not enough, accept that the first 25mm or so are slower and apply a compensation table which reduces the intensity (or scales the pixels? whatever...) at the start
    // TODO: test if we can really switch the jobmode here or if this needs to be at the start of the file (then we could not do engrave and engrave3d in one file)
    if (rp.getBitsPerRasterPixel() == 1)
    {
      setJobMode(out, (currentJobMode & ~JOB_MODE_MASK_BITS_PER_PX) | JOB_MODE_1BIT_PER_PIXEL);
    }
    else if (rp.getBitsPerRasterPixel() == 8)
    {
      setJobMode(out, (currentJobMode & ~JOB_MODE_MASK_BITS_PER_PX) | JOB_MODE_8BIT_PER_PIXEL);
    }
    else
    {
      throw new IllegalArgumentException();
    }
    setColorCode(out, Color.BLACK);
    final int pixelsPerByte = 8 / rp.getBitsPerRasterPixel(); // 8 ... 1 for 1bit/px (normal) ... 8bit/px (engrave 3d)

    // set speed and power
    LaosEngraveProperty prop = rp.getLaserProperty() instanceof LaosEngraveProperty ? (LaosEngraveProperty) rp.getLaserProperty() : new LaosEngraveProperty(rp.getLaserProperty());
    this.setCurrentProperty(out, prop);

    boolean dirRight = true;
    Point rasterStart = rp.getRasterStart();
    boolean bu = prop.isEngraveBottomUp();
    ByteArrayList bytes = new ByteArrayList(rp.getRasterWidth());
    for (int line = bu ? rp.getRasterHeight() - 1 : 0; bu ? line >= 0 : line < rp.getRasterHeight(); line += bu ? -1 : 1)
    {
      Point lineStart = rasterStart.clone();
      lineStart.y += line;
      rp.getRasterLine(line, bytes);

      //remove heading zeroes
      while (bytes.size() > 0 && bytes.get(0) == 0)
      {
        lineStart.x += pixelsPerByte;
        bytes.remove(0);
      }
      //remove trailing zeroes
      while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0)
      {
        bytes.remove(bytes.size() - 1);
      }

      final double speedPercent = (double) (Float) rp.getLaserProperty().getProperty("speed");
      final int overscan = (int) Util.mm2px(this.getAddSpacePerRasterLine(speedPercent), resolution);

      // TODO the lasercutter permits some overscan outside of the normal cutting coordinate bounds. Make that usable.
      final int maxX = (int) Util.mm2px(this.getBedWidth(), resolution);
      final int minX = 0;

      final double offsetPixelsDirRight = this.getEngraveShiftPixels(speedPercent, resolution);
      if (bytes.size() > 0)
      {
        //add space on the left side
        int space = overscan;
        // but not too much: there must still be space for the pixel offset.
        final double absOffset = Math.ceil(Math.abs(offsetPixelsDirRight));
        while (space > 0 && lineStart.x >= minX + absOffset + pixelsPerByte)
        {
          bytes.add(0, (byte) 0);
          space -= pixelsPerByte;
          lineStart.x -= pixelsPerByte;
        }

        //add space on the right side, similar to the left side
        space = overscan;
        while (space > 0 && lineStart.x + absOffset + pixelsPerByte * bytes.size() < maxX - pixelsPerByte)
        {
          bytes.add((byte) 0);
          space -= pixelsPerByte;
        }

        // In extreme cases, the line covers (almost) the full laser bed width, even before adding space.
        // If a pixel offset is applied, then the line would start outside of the laser bed!
        // -> remove bytes at start or end (probably killing useful pixels) until everything is okay
        while (lineStart.x < minX + absOffset)
        {
          // start point for left-to-right (or end point for right-to-left)
          // would be below X axis limit
          bytes.remove(0);
          lineStart.x += pixelsPerByte;
        }
        while (lineStart.x + pixelsPerByte * bytes.size() > maxX - absOffset)
        {
          // end point for left-to-right (or start point for right-to-left)
          // would be above X axis limit
          bytes.remove(bytes.size() - 1);
        }

        // move to the first point of the line and engrave the pixels:
        engraveBitmapLine(out, bytes, lineStart, dirRight, offsetPixelsDirRight, resolution);
      }
      if (!prop.isEngraveUnidirectional())
      {
        dirRight = !dirRight;
      }
    }
    return result.toByteArray();
  }

  final static int COMPRESS_MAGIC_CONSTANT = 0xC0;

  public static ByteArrayList compressData(ByteArrayList bytes)
  {
    /*
     Compressed data format:

     Like the packbits format, but with COMPRESS_MAGIC_CONSTANT as the "magic constant" instead of 0x80.
     (Therefore, we have a lower number of possible repetitions.)

     Note that COMPRESS_MAGIC_CONSTANT=0xC0 can be changed to something else, it is set by the command 1B 43 C0.

     see decompressData() for a decoding routine, which should be enough for a definition.
     */
    ByteArrayList compressed = new ByteArrayList(bytes.size() / 16);
    int i = 0;
    while (i < bytes.size())
    {
      byte currentByte = bytes.get(i);
      // is the current byte repeated?
      int runlength = 1;
      int maxRunlength = 0xFF - COMPRESS_MAGIC_CONSTANT;
      while (i + runlength < bytes.size() && runlength < maxRunlength)
      {
        if (currentByte == bytes.get(i + runlength))
        {
          runlength++;
        }
        else
        {
          break;
        }
      }
      if (runlength == 1)
      {
        int value = currentByte & 0xFF; // cast to unsigned (java thinks the byte is signed!)
        if (value >= COMPRESS_MAGIC_CONSTANT)
        {
          // escape uncompressed data ("repeat 1 times")
          compressed.add((byte) (COMPRESS_MAGIC_CONSTANT + 1));
        }
        compressed.add(currentByte);
      }
      else
      {
        myAssert(runlength + COMPRESS_MAGIC_CONSTANT <= 0xFF);
        // "repeat n times"
        compressed.add((byte) (COMPRESS_MAGIC_CONSTANT + runlength));
        compressed.add(currentByte);
      }
      i += runlength;
    }
    myAssert(decompressData(compressed).equals(bytes));
    return compressed;
  }

  // somehow, "assert" has no effect, so we use this:
  // TODO do it properly (TM)
  public static void myAssert(boolean mustBeTrue)
  {
    if (!mustBeTrue)
    {
      // somehow, AssertionError is not caught!
      RuntimeException ex = new RuntimeException("assertion failed");
      try
      {
        ex = new RuntimeException("assertion failed: " + ex.getStackTrace()[1].toString());
      }
      catch (Exception ee)
      {
        // failed to set message...
      }
      throw ex;
    }
  }

  public static ByteArrayList decompressData(ByteArrayList data)
  {
    ByteArrayList output = new ByteArrayList(data.size() * 16);
    int i = 0;
    while (i < data.size())
    {
      byte b = data.get(i++);
      if ((b & 0xFF) < COMPRESS_MAGIC_CONSTANT)
      { // "&0xFF" = cast to unsigned
        output.add(b);
      }
      else
      {
        int repetitions = (b & 0xFF) - COMPRESS_MAGIC_CONSTANT;
        myAssert(repetitions > 0);
        myAssert(i < data.size());
        byte b2 = data.get(i++);
        for (int j = 0; j < repetitions; j++)
        {
          output.add(b2);
        }
      }
    }
    return output;
  }

  /**
   * engrave a single line of 1bit pixels
   *
   * @param out move to the first point of the line and engrave it
   * @param bytes array of bytes, each contains 8 black/white pixels
   * @param lineStart left point of line
   * @param dirLeftToRight left-to-right engrave (true) of right-to-left (false)
   * @param pixelOffset shift the pixels: negative value means that the
   * scanlines are positioned x pixels earlier to compensate the laser tube
   * delay, zero means disabled
   * @param resolution DPI
   */
  private void engraveBitmapLine(PrintStream out, ByteArrayList bytes, Point lineStart, boolean dirLeftToRight, double pixelOffset, double resolution) throws IOException
  {
    if (dirLeftToRight)
    {
      out.write(toBytes("1B 30"));
    }
    else
    {
      out.write(toBytes("1B 31"));
      // right-to-left. We need to flip the whole bit and byte order.
      bytes.reverseBits();
    }
    ByteArrayList compressed = compressData(bytes);

    // length
    writeU32(out, compressed.size() + 8);
    // X, Y
    sendCoordinate(out, (int) (lineStart.x + (dirLeftToRight ? pixelOffset : (bytes.size() * 8 - pixelOffset))), (int) lineStart.y, resolution, false);
    // data (length-8 bytes)
    for (byte b : compressed)
    {
      out.write(b);
    }
  }

  private byte[] generateInitializationCode(String jobName) throws UnsupportedEncodingException, IOException
  {
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    currentJobMode = -1;
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");

    out.print("LTT");

    out.write(toBytes("1B 76 01 01 01")); // Format Version 1.1.1

    out.write(toBytes("1B 46")); // File name:
    final int maximumJobNameLength = 15;
    if (jobName.length() > maximumJobNameLength)
    {
      jobName = jobName.substring(0, maximumJobNameLength);
    }
    out.write(jobName.length());
    out.print(jobName);

    out.write(toBytes("1B 4F 00")); // operation mode (TODO)

    out.write(toBytes("1B 51 00 00")); // disable "Copy and Quantity"

    // set DPI prescaling ("PITCH")
    int dpiPrescaling = 8;
    // TODO: fixed engrave DPI = 500.
    out.write(toBytes("1B 44"));
    out.write(dpiPrescaling); // engrave (?) DPI = maximum dpi / dpiPrescaling

    // Y axis location for rotary engraving: unused as per documentation.
    out.write(toBytes("1B 59 00 00 00 00"));

    setJobMode(out, JOB_MODE_XY | JOB_MODE_1BIT_PER_PIXEL);

    setMaterialRadius(out, 42); // TODO what's the default value?

    // set magic compression constant to C0 (see compressData())
    out.write(toBytes("1B 43"));
    out.write(COMPRESS_MAGIC_CONSTANT);

    // set grayscale palette for 4bit mode ("rubber power")
    // 11..EE are the intermediate values
    out.write(toBytes("1B 54 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"));

    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    this.setFocus(out, 0f);
    this.setVentilation(out, false);
    this.setPurge(out, false);
    out.write(toBytes("1B 42 59 45")); // goodbye
    return result.toByteArray();
  }

  protected void writeJobCode(LaserJob job, OutputStream os, ProgressListener pl) throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(this.generateInitializationCode(job.getName()));
    if (pl != null)
    {
      pl.progressChanged(this, 20);
    }
    int i = 0;
    int max = job.getParts().size();

    // sort job parts so that vector parts are at the end
    // the documentation says that Engrave must be before Vector, not mixed
    // TODO unnecessary???
    List<JobPart> parts = job.getParts();
    Collections.sort(parts, new Comparator<JobPart>()
    {
      @Override
      public int compare(JobPart p1, JobPart p2)
      {
        return Boolean.compare(p1 instanceof VectorPart, p2 instanceof VectorPart);
      }
    });

    for (JobPart p : parts)
    {
      if (p instanceof Raster3dPart || p instanceof RasterPart)
      {
        out.write(this.generateRasterCode((RasterizableJobPart) p, p.getDPI()));
      }
      else if (p instanceof VectorPart)
      {
        out.write(this.generateVectorCode((VectorPart) p, p.getDPI()));
      }
      i++;
      if (pl != null)
      {
        pl.progressChanged(this, 20 + (int) (i * (double) 60 / max));
      }
    }
    out.write(this.generateShutdownCode());

    // compute checksum
    out.flush(); // unnecessary?
    byte[] result = out.toByteArray();
    int checksum = 0;
    for (byte b : result)
    {
      checksum += b & 0xFF;
    }
    checksum &= 0xFFFF;
    writeU16(out, checksum);

    // total length
    writeU32(out, result.length + 6);
    os.write(out.toByteArray());
  }

  @Override
  public void saveJob(PrintStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception
  {
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    checkJob(job);
    if (job.getStartX() != 0 || job.getStartY() != 0) {
      throw new UnsupportedOperationException("Manual start point is not yet supported.");
      // FIXME: We throw this error because manual start points currently dont work.
      // To do: - signal to the laser cutter that the start point is manual
      //        - fix all checks for coordinate limits
      //        - test if it also works for negative coordinates (starting point at center of job)
      //          especially for engrave (currently causes "negative array size" exception)
    }
    job.applyStartPoint();
    this.writeJobCode(job, fileOutputStream, null);
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    if (!isLaserArcCompensationEnabled())
    {
      // "Arc compensation" = off is not properly tested. It also needs fixing
      // diagonal straight lines (non joint curve, just a single straight line)
      // are not cut with the correct speed. This device configuration has no benefit,
      // so we just tell the user to change it to something more helpful.
      warnings.add("Your configuration states that 'Arc Compensation' is not enabled in the lasercutter's menu. This is not recommended and not well-tested. Please enable it both in the Lasercutter Firmware (Configuration menu) and in VisiCut's laser device settings.");
    }
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    pl.progressChanged(this, 0);
    BufferedOutputStream out;
    ByteArrayOutputStream buffer = null;
    pl.taskChanged(this, "checking job");
    for (JobPart p: job.getParts())
    {
      Object power = null;
      if (p instanceof RasterizableJobPart) {
        power = ((RasterizableJobPart) p).getLaserProperty().getProperty("power");
      } else if (p instanceof VectorPart) {
        power = ((VectorPart) p).getCurrentCuttingProperty().getProperty("power");
      }
      if (power instanceof Number && ((Number) power).floatValue() == 0) {
        String powerZeroWarning = "Power is 0. Please check the laser settings for this material.";
        if (!warnings.contains(powerZeroWarning)) {
          warnings.add(powerZeroWarning);
        }
      }
    }
    checkJob(job);
    job.applyStartPoint();

    pl.taskChanged(this, "connecting");
    Socket connection = new Socket();
    connection.connect(new InetSocketAddress(hostname, port), 3000);
    out = new BufferedOutputStream(connection.getOutputStream());
    pl.taskChanged(this, "sending");
    this.writeJobCode(job, out, pl);
    out.close();
    connection.close();
    pl.progressChanged(this, 100);
  }
  private List<Double> resolutions;

  @Override
  public List<Double> getResolutions()
  {
    if (resolutions == null)
    {
      // TODO: 333 and 166 DPI -> test if they work precisely or are actually 333.33333 and 166.66666
      resolutions = Arrays.asList(new Double[]
      {
        // TODO: currently forced to 500dpi because of PITCH command 1B 44
//        200d,
//        250d,
        500d
//        1000d
      });
      // iterate over copy of list
      for (Double resolution : resolutions.toArray(new Double[]
      {
      }))
      {
        if (resolution > maxEngraveDPI)
        {
          resolutions.remove(resolution);
        }
      }
    }
    return resolutions;
  }
  protected double bedWidth = 1000;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth()
  {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth)
  {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 600;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight)
  {
    this.bedHeight = bedHeight;
  }

  @Override
  public double getRequiredCurvePrecision() {
    if (this.useTangentCurves) {
      // "Joint tangent curve" is used.
      // we need finely spaced points so that the velocity calculations work correctly
      return 0.2;
    } else {
      // classic polyline - better use lower precision to gain speed.
      return 1;
    }
  }

  private static final Integer[] engraveShiftListDefault = new Integer[]
  {
    -2, -4, -7, -10, -13, -15, -17, -19, -21, -23
  };
  private Integer[] engraveShiftList = engraveShiftListDefault;

  /**
   * Return offset in pixels to account for the lasercutter delay, which shifts
   * the pixels horizontally
   *
   * @param speedPercent engrave speed
   * @param resolution engrave DPI
   * @return
   */
  private double getEngraveShiftPixels(double speedPercent, double resolution)
  {
    if (speedPercent > 100 || speedPercent < 0)
    {
      throw new IllegalArgumentException();
    }
    // We assume that the values of engraveShiftList are for 10%, 20%, ..., 100% speed
    // and do linear interpolation inbetween (plus some offset).
    // TODO change this code so that VisiCut's pixel offset exactly matches the windows driver's interpretation of engraveShiftList.
    // Currently it works correctly for 100% speed (the main use case), but is slightly off for lower speeds.
    double shiftValue;
    if (speedPercent < 10)
    {
      shiftValue = engraveShiftList[0];
    }
    else if (speedPercent == 100)
    {
      shiftValue = engraveShiftList[9];
    }
    else
    {
      // linear interpolation, e.g.
      // 17% -> engraveShiftList[0] * 0.7 + engraveShiftList[1]*0.3
      final double stepsize = 10;
      int idxLow = (int) (speedPercent / stepsize) - 1;
      double speedLow = (idxLow + 1) * stepsize;
      double alpha = (speedPercent - speedLow) / stepsize;
      shiftValue = engraveShiftList[idxLow] * (1 - alpha) + engraveShiftList[idxLow + 1] * alpha;
    }
    // scale end result to the engrave DPI (list is for maxDPI)
    // and add some resolution-dependent offset (determined experimentally to roughly match the windows driver's behaviour, may be incorrect)
    return shiftValue * resolution / maxDPI + 0.5;
  }

  private static final String[] settingAttributes = new String[]
  {
    SETTING_HOSTNAME,
    SETTING_PORT,
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_FLIPX,
    SETTING_FLIPY,
    SETTING_MAXDPI,
    SETTING_MAX_ENGRAVE_DPI,
    SETTING_CUTTING_SPEED,
    SETTING_TANGENT_ENABLE,
    SETTING_TANGENT_ACCEL,
    SETTING_ARCCOMP_ENABLE,
    //    SETTING_SUPPORTS_VENTILATION,
    //    SETTING_SUPPORTS_PURGE,
    //    SETTING_SUPPORTS_FOCUS,
    //    SETTING_SUPPORTS_FREQUENCY,
    SETTING_RASTER_WHITESPACE_MIN,
    SETTING_RASTER_WHITESPACE_MAX,
    SETTING_RASTER_SHIFTTABLE,
    SETTING_DEBUGFILE
  };

  @Override
  public String[] getPropertyKeys()
  {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute)
  {
    if (SETTING_DEBUGFILE.equals(attribute))
    {
      return this.debugFilename;
    }
    else if (SETTING_RASTER_WHITESPACE_MIN.equals(attribute))
    {
      return (Double) this.addSpacePerRasterLineMinimum;
    }
    else if (SETTING_RASTER_WHITESPACE_MAX.equals(attribute))
    {
      return (Double) this.addSpacePerRasterLineMaximum;
    }
    else if (SETTING_RASTER_SHIFTTABLE.equals(attribute))
    {
      // In python, this would just be " ".join(engraveShiftList).trim().
      StringBuilder b = new StringBuilder();
      for (int x : engraveShiftList)
      {
        b.append(x);
        b.append(" ");
      }
      return b.toString().trim();
    }
    else if (SETTING_SUPPORTS_FREQUENCY.equals(attribute))
    {
      return (Boolean) this.supportsFrequency;
    }
    else if (SETTING_SUPPORTS_PURGE.equals(attribute))
    {
      return (Boolean) this.supportsPurge;
    }
    else if (SETTING_SUPPORTS_VENTILATION.equals(attribute))
    {
      return (Boolean) this.supportsVentilation;
    }
    else if (SETTING_SUPPORTS_FOCUS.equals(attribute))
    {
      return (Boolean) this.supportsFocus;
    }
    else if (SETTING_HOSTNAME.equals(attribute))
    {
      return this.getHostname();
    }
    else if (SETTING_FLIPX.equals(attribute))
    {
      return (Boolean) this.isFlipXaxis();
    }
    else if (SETTING_FLIPY.equals(attribute))
    {
      return (Boolean) this.isFlipYaxis();
    }
    else if (SETTING_PORT.equals(attribute))
    {
      return (Integer) this.getPort();
    }
    else if (SETTING_BEDWIDTH.equals(attribute))
    {
      return (Double) this.getBedWidth();
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      return (Double) this.getBedHeight();
    }
    else if (SETTING_MAXDPI.equals(attribute))
    {
      return (Double) this.getMaxDPI();
    }
    else if (SETTING_MAX_ENGRAVE_DPI.equals(attribute))
    {
      return (Double) this.getMaxEngraveDPI();
    }
    else if (SETTING_ARCCOMP_ENABLE.equals(attribute))
    {
      return (Boolean) this.isLaserArcCompensationEnabled();
    }
    else if (SETTING_TANGENT_ENABLE.equals(attribute))
    {
      return (Boolean) this.isUseTangentCurves();
    }
    else if (SETTING_TANGENT_ACCEL.equals(attribute))
    {
      return (Double) this.getTangentCurveMaxAcceleration();
    }
    else if (SETTING_CUTTING_SPEED.equals(attribute))
    {
      return (Double) this.getNominalCuttingSpeed();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value)
  {
    if (SETTING_DEBUGFILE.equals(attribute))
    {
      this.debugFilename = value != null ? (String) value : "";
    }
    else if (SETTING_RASTER_WHITESPACE_MIN.equals(attribute))
    {
      this.addSpacePerRasterLineMinimum = (Double) value;
    }
    else if (SETTING_RASTER_WHITESPACE_MAX.equals(attribute))
    {
      this.addSpacePerRasterLineMaximum = (Double) value;
    }
    else if (SETTING_RASTER_SHIFTTABLE.equals(attribute))
    {
      ArrayList<Integer> l = new ArrayList<Integer>();
      for (String s : ((String) value).trim().split("\\s+"))
      {
        try
        {
          l.add((Integer) Integer.parseInt(s));
        }
        catch (NumberFormatException e)
        {
          Logger.getLogger(LaserToolsTechnicsCutter.class.getName()).warning("failed to parse SETTING_RASTER_SHIFTTABLE - wrong number format");
        }
      }
      if (l.size() != 10)
      {
        // keep old value, refuse updating
        Logger.getLogger(LaserToolsTechnicsCutter.class.getName()).warning("failed to parse SETTING_RASTER_SHIFTTABLE - wrong length");
      }
      else
      {
        this.engraveShiftList = l.toArray(new Integer[0]);
      }
    }
    else if (SETTING_SUPPORTS_FREQUENCY.equals(attribute))
    {
      this.setSupportsFrequency((Boolean) value);
    }
    else if (SETTING_SUPPORTS_PURGE.equals(attribute))
    {
      this.setSupportsPurge((Boolean) value);
    }
    else if (SETTING_SUPPORTS_VENTILATION.equals(attribute))
    {
      this.setSupportsVentilation((Boolean) value);
    }
    else if (SETTING_SUPPORTS_FOCUS.equals(attribute))
    {
      this.setSupportsFocus((Boolean) value);
    }
    else if (SETTING_HOSTNAME.equals(attribute))
    {
      this.setHostname((String) value);
    }
    else if (SETTING_PORT.equals(attribute))
    {
      this.setPort((Integer) value);
    }
    else if (SETTING_FLIPX.equals(attribute))
    {
      this.setFlipXaxis((Boolean) value);
    }
    else if (SETTING_FLIPY.equals(attribute))
    {
      this.setFlipYaxis((Boolean) value);
    }
    else if (SETTING_BEDWIDTH.equals(attribute))
    {
      this.setBedWidth((Double) value);
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      this.setBedHeight((Double) value);
    }
    else if (SETTING_MAXDPI.equals(attribute))
    {
      this.setMaxDPI((Double) value);
    }
    else if (SETTING_MAX_ENGRAVE_DPI.equals(attribute))
    {
      this.setMaxEngraveDPI((Double) value);
    }
    else if (SETTING_ARCCOMP_ENABLE.contains(attribute))
    {
      this.setLaserArcCompensationEnabled((Boolean) value);
    }
    else if (SETTING_TANGENT_ENABLE.contains(attribute))
    {
      this.setUseTangentCurves((Boolean) value);
    }
    else if (SETTING_TANGENT_ACCEL.equals(attribute))
    {
      this.setTangentCurveMaxAcceleration((Double) value);
    }
    else if (SETTING_CUTTING_SPEED.equals(attribute))
    {
      this.setNominalCuttingSpeed((Double) value);
    }
  }

  @Override
  public LaserCutter clone()
  {
    LaserToolsTechnicsCutter clone = new LaserToolsTechnicsCutter();
    // just copy over all the properties, this avoids extra code here.
    // TODO: apply this simplification also to other lasercutters
    for (String setting : settingAttributes)
    {
      clone.setProperty(setting, getProperty(setting));
    }
    return clone;
  }

}
