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

import de.thomas_oster.liblasercut.ByteArrayList;
import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.JobPart;
import de.thomas_oster.liblasercut.LaserCutter;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.ProgressListener;
import de.thomas_oster.liblasercut.ProgressListenerDummy;
import de.thomas_oster.liblasercut.Raster3dPart;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.RasterizableJobPart;
import de.thomas_oster.liblasercut.VectorCommand;
import static de.thomas_oster.liblasercut.VectorCommand.CmdType;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Circle;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Rectangle;
import de.thomas_oster.liblasercut.platform.Tuple;
import de.thomas_oster.liblasercut.platform.Util;
import static de.thomas_oster.liblasercut.utils.Assertion.assertThat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

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
  private static final String SETTING_ROTARY_AXIS = "Has rotary axis";

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

  private boolean rotaryAxisSupported = false;
  @Override
  public boolean isRotaryAxisSupported() {
    return rotaryAxisSupported;
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
   * Get the raster overscan in mm
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
  protected boolean useTangentCurves = true;

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
   */
  public double getNominalCuttingSpeed()
  {
    return nominalCuttingSpeed;
  }

  public void setNominalCuttingSpeed(double nominalCuttingSpeed)
  {
    this.nominalCuttingSpeed = nominalCuttingSpeed;
  }

  // This variable is ignored, must not be removed to ensure compatibility
  // with imported old settings (before August 2020).
  @Deprecated
  transient protected boolean flipXaxis = false;
  // This variable is only for compatibility with imported old settings:
  @Deprecated
  transient protected boolean flipYaxis = true;

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

  private double generateVectorCode(ByteArrayOutputStream outputstream, VectorPart vp, double resolution) throws UnsupportedEncodingException, IOException, IllegalJobException
  {
    PrintStream out = new PrintStream(outputstream, true, StandardCharsets.US_ASCII);

    out.write(toBytes("1B 56")); // start vector mode

    out.write(toBytes("1B 45 00 00 00 00 00 00 00")); // disable pulse mode

    setColorCode(out, Color.RED);

    setCurrentDPI(out, resolution, true);

    out.write(toBytes("50 53")); // Vector Pause Flag (probably unused)

    setFrequency(out, 1000); // default frequency (will be overriden later by the profile, except if "enable frequency" is false in the config)
    ArrayList<Double> x = new ArrayList<>();
    ArrayList<Double> y = new ArrayList<>();
    double prescalingY = 1;
    if (currentJobIsRotary)
    {
      // If the rotary axis is used, the maximum Y acceleration depends on the engrave diameter and is not the same as the max. X acceleration.
      // However, the algorithm only supports equal maximum acceleration for both axes.
      // Therefore, the Y coordinates are first scaled, then the acceleration is computed, and then they are scaled back to actual size.
      // Required factor is roughly the ratio between machine coordinates for rotary and normal engraving.
      // Use 10 inch length for computing the ratio so that neither roundoff nor axis limits are a problem.
      prescalingY = Math.abs((double) yPxToDeviceCoordinate(10, 1, 1, true, true, true) / yPxToDeviceCoordinate(10, 1, 1, true, true, false));
    }
    double cuttingTime = 0;
    for (VectorCommand cmd : vp.getCommandList())
    {
      if (cmd.getType() == CmdType.LINETO)
      {
        x.add(cmd.getX());
        y.add(cmd.getY() * prescalingY);
      }
      else
      {
        cuttingTime += curveOrLine(out, x.toArray(new Double[0]), y.toArray(new Double[0]), resolution, prescalingY);
        x = new ArrayList<>();
        y = new ArrayList<>();
        switch (cmd.getType())
        {
          case MOVETO:
            cuttingTime += move(out, cmd.getX(), cmd.getY() * prescalingY, resolution, prescalingY);
            break;
          case SETPROPERTY:
          {
            this.setCurrentProperty(out, cmd.getProperty());
            break;
          }
        }
      }
    }
    cuttingTime += curveOrLine(out, x.toArray(new Double[0]), y.toArray(new Double[0]), resolution, prescalingY);
    setLaserOn(out, false);
    return cuttingTime;
  }

  private void setCurrentDPI(PrintStream out, double resolution, boolean isVectorActive) throws IOException
  {
    assertThat(resolution == 500); // TODO: resolution is set with the "Prescale" command, not this PPI command. Fix this whole function.

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

  }

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
   * convert speed in percent to mm/s
   * @param speedPercent speed in %
   * @return speed in mm/s
   */
  private double speedPercentToMmPerSec(double speedPercent) {
      return speedPercent / 100. * nominalCuttingSpeed;
  }
  
  /**
   * cutting time for cutting a line at the current speed
   * @param distancePx length given in device coordinates
   * @param resolution current DPI
   * @param speedPercent speed in percent of maximum
   * @return time in seconds
   */
  private double cuttingTimeForPxDistance(double distancePx, double resolution, double speedPercent) {
    double speedMmPerSec = speedPercentToMmPerSec(speedPercent);
    double distanceMm = Util.px2mm(distancePx, resolution);
    if (distanceMm == 0)
    {
      return 0;
    }
    // for the value of the acceleration, we assume the same as configured for "joint tangential curve".
    if (!isUseTangentCurves())
    {
      // acceleration is not configured, fall back to simple computation without acceleration
      return distanceMm / speedMmPerSec;
    }
    // v = a t, x = 1/2 a t² => t = v² / a², x = 1/2 v² / a
    // distance x for accelerating to full speed:
    double accelDistanceMm = 0.5 * Math.sqrt(speedMmPerSec * speedMmPerSec / tangentCurveMaxAcceleration);
    if (distanceMm > 2 * accelDistanceMm)
    {
      // Accelerating to full speed.
      // during acceleration, the effective speed is half the speed.
      return (distanceMm + 2 * accelDistanceMm) / speedMmPerSec;
    }
    else
    {
      // path is too short for full acceleration.
      // x = 1/2 v² / a => v_max = sqrt(2 a x), where x is half the length
      double peakSpeed = Math.sqrt(tangentCurveMaxAcceleration * distanceMm);
      return 2 * distanceMm / peakSpeed;
    }
  }
  /**
   * cutting time for cutting a line at the current speed
   * @param distancePx length given in device coordinates
   * @param resolution current DPI
   * @return time in seconds
   */
  private double cuttingTimeForPxDistance(double distancePx, double resolution) {
    return cuttingTimeForPxDistance(distancePx, resolution, currentSpeed);
  }

  /**
   * Switch off laser and move to coordinate
   * @return travel time
   */
  private double move(PrintStream out, double x, double y, double resolution, double prescalingY) throws IOException
  {
    setLaserOn(out, false);
    // we assume that the travel speed is the maximum cutting speed.
    double time = cuttingTimeForPxDistance(Math.hypot(x - currentX, y - currentY), resolution, 100);
    goToCoordinate(out, x, y, resolution, prescalingY, false);
    return time;
  }

  /**
   * Switch on laser and cut a line to the given coordinate
   * @return cutting time
   */
  private double line(PrintStream out, double x, double y, double resolution, double prescalingY) throws IOException
  {
    setLaserOn(out, true);
    double time = cuttingTimeForPxDistance(Math.hypot(x - currentX, y - currentY), resolution);
    goToCoordinate(out, x, y, resolution, prescalingY, true);
    return time;
  }
  
  
  static class UnsupportedCircleException extends Exception
  {

        public UnsupportedCircleException(String s) {
            super(s);
        }
  }
  
/**
 * Switch on the laser and cut a full circle, starting from the current point,
 * around the given center point.
 * Note that the radius in mm must not be larger than CIRCLE_MAX_RADIUS_MM,
 * otherwise UnsupportedCircleException will be thrown and nothing will be written
 * to the output.
 * 
 * @param out PrintStream for writing the commands
 * @param center Center point
 * @param resolution DPI for converting pixels to mm
 * @throws UnsupportedCircleException if the circle is too large and it must be sent in a different way
 * @return cutting time
 */
          
  // Note for future development: The same command can also be used for arcs (e.g., quarter circles).
  private double circle(PrintStream out, Point center, double resolution, double prescalingY) throws IOException, UnsupportedCircleException
  {
    if (prescalingY != 1)
    {
      throw new UnsupportedCircleException("Y prescaling is unsupported. This is only useful for cutting circles on rotary engrave, a rare edge case that isn't worth the development time");
    }
    // the firmware works correctly up to this radius:
    final double CIRCLE_MAX_RADIUS_MM = 101.0;
    final double radiusPx = center.hypotTo(new Point(currentX, currentY));
    final double radiusMm = Util.px2mm(radiusPx, resolution);
    if (radiusMm > CIRCLE_MAX_RADIUS_MM) {
        // CAUTION: The circle command is disabled for large radii as it seems broken in hardware: Large circles (radius > 105mm) cause so much acceleration (loud "BANG!" sound) that the mechanics may be damaged.
        throw new UnsupportedCircleException("Circle command radius must not be larger than CIRCLE_MAX_RADIUS_MM due to firmware bugs.");
    }
    if (resolution != 500) {
        // Currently, the driver only supports 500dpi. It is unknown if CIRCLE_MAX_RADIUS_MM is different for different resolutions.
        // If other resolutions are added:
        // Please test all other resolutions with circle radius up to CIRCLE_MAX_RADIUS_MM before removing this exception:
        Logger.getLogger(LaserToolsTechnicsCutter.class.getName()).log(Level.WARNING, "Circle with != 500dpi is not yet supported.");
        throw new UnsupportedCircleException("Circle command with != 500dpi is untested and therefore not yet supported.");
    }
    if (!useTangentCurves) {
        throw new UnsupportedCircleException("joint tangent curves must be enabled to use the circle command");
    }

    // Limit speed to what's possible for the given radius
    // Note: for a circle with constant speed:
    // acceleration = speed^2 / radius
    // -> speed = sqrt(acceleration * radius)
    float maxSpeedPercent = (float) (Math.sqrt(tangentCurveMaxAcceleration * radiusMm) / speedPercentToMmPerSec(1));
    // safety factor: leave some margin for longitudinal acceleration, which adds to the radial acceleration at the start and end
    maxSpeedPercent = maxSpeedPercent * 0.7f;
    // remember previous speed and apply new limit
    float oldSpeedPercent = currentSpeed;
    float oldPower = currentPower;
    if (currentSpeed > maxSpeedPercent)
    {
      setSpeed(out, maxSpeedPercent);
      // speed was reduced; also reduce the power
      setPower(out, oldPower * maxSpeedPercent / oldSpeedPercent);
    }
    
    // the start point is implicitly the current point.
    // enclosing the circle command in PJ...PF (without PE!) helps for smoother acceleration
    // (even if that doesn't make too much sense, but well, the protocol is a bit weird anyway)
    setLaserOn(out, true);
    out.print("PJ"); 
    out.print("PB"); // PB: clockwise, PC: counterclockwise
    // end point relative to start point
    // here: 0,0 because we cut a full circle
    writeS32(out, 0);
    writeS32(out, 0);
    // center point relative to start point
    sendCoordinate(out, (int) center.x - (int) currentX, (int) center.y - (int) currentY, resolution, prescalingY, true);
    out.print("PF");
    // restore previous speed and power setting
    setSpeed(out, oldSpeedPercent);
    setPower(out, oldPower);
    return cuttingTimeForPxDistance(2 * Math.PI * radiusPx, resolution);
  }



  static class PointWithSpeed extends Point
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
   * maximum speed (nominalCuttingSpeed), MUST obey the acceleration limit)
   */
  private double curveWithKnownSpeed(PrintStream out, ArrayList<PointWithSpeed> points, double resolution, double prescalingY) throws IOException
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

      // all the following double variables are in mm, mm/s, s or mm/s^2 respectively.
      double newDx = Util.px2mm(x - currentX, resolution);
      double newDy = Util.px2mm(y - currentY, resolution);
      double newLength = Math.hypot(newDx, newDy);
      // The speed vector at xy[i] has the direction (xy[i] - xy[i-1]).
      double newSpeedX = speedPercentToMmPerSec(speedPercent) * newDx / newLength;
      double newSpeedY = speedPercentToMmPerSec(speedPercent) * newDy / newLength;
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
      //   (see LaserToolsTechnicsCutter_speedInterpolation.svg, section B.1)
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
        //   (see LaserToolsTechnicsCutter_speedInterpolation.svg, section B.3)
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
        assertThat(false);
      } else if (i == points.size() - 1) {
        // end point:
        // zero speed at the end point by definition.
        assertThat(speedPercent == 0);
      } else {
        // normal points (neither start nor end) must have nonzero speed
        assertThat(speedPercent > 0);
      }
      writeU16(out, limit((int) Math.round(sentSpeed * 10), 1, 1000));
      line(out, x, y, resolution, prescalingY); // not actually a line, will be interpreted as smooth curve segment
      // currentX and currentY are set by this call to line()
    }
    avgXYAccelerationSeen = avgXYAccelerationSeen / points.size();
    System.out.println("Maximum acceleration used is " + maxXYAccelerationSeen / tangentCurveMaxAcceleration * 100 + " percent of maximum, average is " + avgXYAccelerationSeen / tangentCurveMaxAcceleration * 100 + "%.");
    System.out.println("Cutting time for this curve is " + totalTime + " s.");
    out.print("PF"); // end join
    return totalTime;
  }

  /**
   * Reinterpolate the list of points by adding linear interpolated intermediate
   * points until all points are less than maxDistance apart
   *
   * @param points Input points, which must not be more than 1e6*maxDistance
   * apart (which should be no problem, as for example 1e6 * 0,1mm = 100 meters)
   */
  ArrayList<PointWithSpeed> reinterpolateWithMaximumDistance(ArrayList<PointWithSpeed> points, double maxDistance)
  {
    if (points.isEmpty())
    {
      return new ArrayList<>(0);
    }
    double totalLength = 0;
    for (PointWithSpeed point : points.subList(1, points.size()))
    {
      totalLength += point.deltaToPrevious.hypot();
    }
    ArrayList<PointWithSpeed> pointsNew = new ArrayList<>(points.size() + (int) (totalLength / maxDistance) + 1);
    pointsNew.add(points.get(0));
    PointWithSpeed previousPoint = points.get(0);
    for (PointWithSpeed point : points.subList(1, points.size()))
    {
      double len = point.deltaToPrevious.hypot();
      assertThat(len < 1e6 * maxDistance); // input points must not be more apart than 1e6*maxDistance
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

  // NOTE: the following constants constants are empirically determined.
  // The values are probably not optimal, but are good enough that nobody complains.
  // There is always a trade-off between mathematically strict adherence to the
  // acceleration limits and a usable result that smoothly rides through curves.

  // maximum position inaccuracy in mm
  private static final double lengthTolerance = 1e-1;
  // maximal angle which may be smoothed, as long as position accuracy is satisfied
  // must be < 90° because otherwise the interpolation code breaks (180° may also be okay, but careful review would be required)
  private static final double angleToleranceShort = Math.toRadians(40);
  // finer restriction on maximal angle for line segments which have a significant length
  // The current value maybe has no effect, except if made really small for certain length???
  private static final double angleToleranceLong = Math.toRadians(10);

  /**
   * Cut a smooth curve with given interpolation points.
   * Only two special cases for the input are permitted:
   * - the list contains exactly two points: Cut a line segment.
   * - all points can be cut in one smooth curve (curvature is small enough).
   * The first point is ignored and must be the current coordinate.
   *
   * @return cutting time
   */
  private double curve(PrintStream out, ArrayList<PointWithSpeed> points, double resolution, double prescalingY) throws IOException
  {
    if (points.size() <= 1)
    {
      return 0;
    }
    if (points.size() == 2)
    {
      var nextPoint = points.get(1);
      // only two points: cut a line segment
      return line(out, nextPoint.x, nextPoint.y, resolution, prescalingY);
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

    // Reinterpolate the points to a small intermediate distance.
    // (The windows driver has a table for maximum change of speed between points.
    //  For very low speeds, it effectively uses roughly 0.3mm maximum distance,
    //  but a lot more for higher speeds. However, it seems that sending so many
    // intermediate points isn't actually required.)
    // In the end, most of the interpolated points are filtered out again.
    points = reinterpolateWithMaximumDistance(points, Util.mm2px(0.9, resolution));

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
        // (see LaserToolsTechnicsCutter_speedInterpolation.svg, section B.2)
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
          // NOTE: The reinterpolation at the beginning of this function should
          // have inserted enough intermediate points so that this case does not
          // happen too often.
        }

        // vectorial: newSpeed = oldSpeed + acceleration * time
        // new speed must point into the direction of p[i].deltaToPrevious.unityVector().
        // we know that angle(newSpeed,oldSpeed) < maxAngle
        // and abs(acceleration) < maxAcceleration.
        // Direction of acceleration is arbitrary.
        // Now, what is the minimum and maximum possible length of newSpeed?
        // see LaserToolsTechnicsCutter_speedInterpolation.svg, section A, case 1 and 1b.
        // Is it possible to reach any speed pointing in the fixed direction of newSpeed?
        // speed * sin(angle) < acceleration * time ?
        double alpha = pBefore.absAngleAtCorner;
        if (pBefore.speed * Math.sin(alpha) > tangentCurveMaxAcceleration * optimismFactor * minTime)
        {
          System.out.println("point " + i + ": Reducing previous velocity (corner too angled)");
          // It is impossible. The previous(!) velocity needs to be lowered.
          // see LaserToolsTechnicsCutter_speedInterpolation.svg, section A, case 2.
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
          }
        }
      }
      if (!somethingChanged)
      {
        System.out.println("Nothing changed. stopping after " + countIterations + " iterations");
      }
    }
    // filter points on *straight* segments with a small change of velocity.
    // Note that because this part of the problem is mathematically convex,
    // points in the middle of a straight line can be omitted without violating the maximum acceleration.
    // This makes transmission faster, but can also be omitted.
    ArrayList<PointWithSpeed> filteredPoints = new ArrayList<>();
    filteredPoints.add(points.get(0)); // keep first point
    double speedNow = points.get(0).speed;
    double distanceSinceLastPointMm = 0;
    for (int i=1; i < points.size() - 1; i++) {
        // required speed change in percent to trigger output of a point:
        // if distance to last point is small: 0.2% + 10% of current speed
        // if distance to last point is 5.0mm: 0.2% (required so that we don't omit all points with 100% speed while we are at 90% speed)
        // inbetween: linearly interpolated.
        final double speedChangeThreshold = 0.2 + Math.max(0, 0.1 * speedNow * (1.0 - distanceSinceLastPointMm/5.0));
        if ((points.get(i).absAngleAtCorner == 0)
                && (Math.abs(points.get(i).speed - speedNow) < speedChangeThreshold)
                )
        {
            // drop point i
            distanceSinceLastPointMm += points.get(i).deltaToPrevious.hypot() * pxToMm;
        } else {
            // keep point i
            speedNow = points.get(i).speed;
            distanceSinceLastPointMm = 0;
            filteredPoints.add(points.get(i));
        }
    }
    filteredPoints.add(points.get(points.size() - 1)); // keep last point

    // convert back to manufacturer units
    for (PointWithSpeed point : filteredPoints)
    {
      point.speed *= relativeSpeedToMmPerSec;
    }

    return curveWithKnownSpeed(out, filteredPoints, resolution, prescalingY);
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
   */
  private double curveOrLine(PrintStream out, Double[] x, Double[] y, double resolution, double prescalingY) throws IOException
  {
    double cuttingTime = 0;
    if (!this.useTangentCurves)
    {
      // smooth curves are disabled, fall back to line segments
      for (int i = 0; i < x.length; i++)
      {
        cuttingTime += line(out, x[i], y[i], resolution, prescalingY);
      }
      return cuttingTime;
    }

    if (x.length == 0)
    {
      return 0;
    }

    // less than 1 pixel length tolerance doesn't make too much sense, because the conversion from spline to polyline in ShapeConverter causes up to 1px error anyway.
    double lengthTolerancePixels = Math.max(1.f, Util.mm2px(lengthTolerance, resolution));

    ArrayList<PointWithSpeed> points = new ArrayList<>();
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
          cuttingTime += curve(out, points, resolution, prescalingY);
          points = new ArrayList<>();
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
      return cuttingTime;
    }
    // we have at least 2 segments remaining (since the last corner).

    // If there were no corners, the path may be a full circle, which we can send more efficiently as a circle command instead of a generic curve.
    Circle detectedCircle = Circle.fromPointList(points, lengthTolerancePixels);
    if (detectedCircle != null) {
      // we found a circle -- use it and send the specialized command
      System.out.println("We found a circle:" + detectedCircle);
      // The circle implicitly starts at the current point,
      try {
        cuttingTime += circle(out, detectedCircle.center, resolution, prescalingY);
      } catch (UnsupportedCircleException exception) {
        System.out.println("unsupported circle, sending as normal curve: " + exception.getMessage());
        detectedCircle = null;
      }
    }
    if (detectedCircle == null)
    {
      // It's a normal curve, or sending as circle was unsupported.
      // -> just send it as normal curve
      cuttingTime += curve(out, points, resolution, prescalingY);
    }
    return cuttingTime;
  }

  // for convenience, we support double coordinates, but the lasercutter only knows integers.
  private transient double currentX = -1;
  private transient double currentY = -1;
  private transient boolean currentJobIsRotary = false;
  private transient double rotaryRadius = Double.NaN;

  /**
   * go to the specified coordinate
   * @param out PrintStream for writing the commands
   * @param x x coordinate in pixels
   * @param y y coordinate in pixels
   * @param resolution DPI for converting pixels to mm
   * @param prescalingY extra factor for Y DPI, normally 1 except for rotary engrave, machine Y will be divided by this factor
   * @param sendAsRelative True: send a relative move command, False: send an absolute move command
   */
  private void goToCoordinate(PrintStream out, double x, double y, double resolution, double prescalingY, boolean sendAsRelative) throws IOException
  {
    if (sendAsRelative)
    {
      out.print("PR"); // relative position
      // note: we convert to int *before* subtracting, so that always: sum(relative increments that we send) == (int) currentX.
      sendCoordinate(out, (int) x - (int) currentX, (int) y - (int) currentY, resolution, prescalingY, true);
    }
    else
    {
      out.print("PA"); // 50 41: absolute position
      sendCoordinate(out, (int) x, (int) y, resolution, prescalingY, false);
    }
    currentX = x;
    currentY = y;
  }

  /**
   * send bounding box, given in mm, WITHOUT ANY PRECEEDING COMMAND.
   * @throws IOException
   */
  private void sendBoundingBoxMm(PrintStream out, Rectangle bbMm) throws IOException
  {
    // Note: device Y axis is flipped compared to our code's Y axis.
    // This flipping is applied in sendCoordinate().
    // minimum X and Y (usually bottom left, except for rotary engrave)
    Rectangle bb = boundingBoxMmToDeviceCoordinates(bbMm);
    sendCoordinate(out, (int) bb.getXMin(), (int) bb.getYMin(), COORDINATE_IS_RAW_VALUE, 1, false);
    // width and height
    sendWidthHeight(out, (int) (bb.getXMax() - bb.getXMin()), (int) (bb.getYMax() - bb.getYMin()), COORDINATE_IS_RAW_VALUE, 1);
  }


  private int yPxToDeviceCoordinate(double y, double resolution, double prescalingY, boolean isRelative, boolean isLength)
  {
    return yPxToDeviceCoordinate(y, resolution, prescalingY, isRelative, isLength, currentJobIsRotary);
  }

  /**
   * convert Y from LibLaserCut coordinates to raw device coordinates (see sendCoordinate)
   * @param y coordinate in pixels
   * @param isLength True: input is a positive length, output must also be positive.
   * @param isRelative True: input is a relative coordinate. False: absolute coordinate. Ignored if isLength==True.
   * @param resolution pixels per inch, or COORDINATE_IS_RAW_VALUE to specify raw machine coordinates
   * @param yIsRotary is Y used as rotary axis? normally False
   */
  private int yPxToDeviceCoordinate(double y, double resolution, double prescalingY, boolean isRelative, boolean isLength, boolean yIsRotaryAxis)
  {
    assertThat(Double.isFinite(prescalingY));
    assertThat(prescalingY > 0);
    if (resolution == COORDINATE_IS_RAW_VALUE)
    {
      assertThat(prescalingY == 1);
      return (int) y;
    }
    if (isLength || !isRelative) {
      assertThat(y >= 0);
    }
    int yRaw;
    int yMax;
    if (yIsRotaryAxis) {
      // rotary engrave: Y-axis is the rotation angle
      final int ROTARY_AXIS_PX_PER_REVOLUTION = 6400; // device pixels for full rotation (approximate guess)
      // there is no real coordinate limit for the rotary axis, but 100 revolutions should be enough for all cases
      yMax = 100 * ROTARY_AXIS_PX_PER_REVOLUTION;
      yRaw = (int) Math.round(Util.px2mm(y / prescalingY, resolution) / (rotaryRadius * 2 * Math.PI) * ROTARY_AXIS_PX_PER_REVOLUTION);
    }
    else
    {
      yMax = (int) Util.mm2px(bedHeight, maxDPI);
      yRaw = (int) Math.round(y / prescalingY * maxDPI / resolution);
    }
    assertThat(yRaw <= yMax);
    if (yIsRotaryAxis)
    {
      return yRaw;
    } else {
      if (isLength) {
        return yRaw;
      } else if (isRelative) {
        return -yRaw;
      } else {
        // absolute position
        return yMax - yRaw;
      }
    }
  }


  /**
   * convert X from LibLaserCut coordinates to raw device coordinates (see sendCoordinate)
   * @param isLength True: input is a positive length, output must also be positive.
   * @param isRelative True: input is a relative coordinate. False: absolute coordinate. Ignored if isLength==True.
   * @param resolution dots per inch, or COORDINATE_IS_RAW_VALUE to specify raw machine coordinates
   */
  private int xPxToDeviceCoordinate(double x, double resolution, boolean isRelative, boolean isLength)
  {
    if (resolution == COORDINATE_IS_RAW_VALUE)
    {
      return (int) x;
    }
    // x-axis is positive, only scaling needed.
    int xRaw = (int) Math.round(x * maxDPI / resolution);
    final int xMax = (int) Util.mm2px(bedWidth, maxDPI);
    if (isLength || !isRelative) {
      assertThat(x >= 0);
    }
    assertThat(xRaw <= xMax);
    return xRaw;
  }

  private Rectangle boundingBoxMmToDeviceCoordinates(Rectangle bb)
  {
    final double MM_TO_DPI = Util.dpmm2dpi(1);
    int rawXMin = xPxToDeviceCoordinate(bb.getXMin(), MM_TO_DPI, false, true);
    int rawXMax = xPxToDeviceCoordinate(bb.getXMax(), MM_TO_DPI, false, true);
    int rawY1 = yPxToDeviceCoordinate(bb.getYMin(), MM_TO_DPI, 1, false, true);
    int rawY2 = yPxToDeviceCoordinate(bb.getYMax(), MM_TO_DPI, 1, false, true);
    int rawYMin = Math.min(rawY1, rawY2);
    int rawYMax = Math.max(rawY1, rawY2);
    return new Rectangle(rawXMin, rawYMin, rawXMax, rawYMax);
  }

  /// Pseudo-value for resolution argument of sendCoordinate() and similar functions.
  /// Used to signal that a coordinate is in raw machine coordinates and must not be transformed.
  private static final double COORDINATE_IS_RAW_VALUE = Double.NEGATIVE_INFINITY;
  /// Workaround to support incorrectly serialized settings (before 2020-12) in which this field is present but useless.
  /// Do not use. Do not remove before 2023.
  @Deprecated private transient double COORDINATE_IS_RAW;
  /**
   * send a coordinate WITHOUT ANY PRECEDING COMMAND.
   * This does not make sense if you don't prepend a command.
   *
   * Coordinate is converted from the given resolution to machine resolution.
   * Input coordinates have (0,0) at top left, +x is right, +y is down. This is
   * the convention in LibLaserCut.
   *
   * Output coordinates are transformed to machine coordinates with:
   * - normally: (0,0) at bottom left, +y up, x unchanged
   * - rotary engrave: (0,0) at top left, +x right, +y down
   *
   * @param x x-coordinate in given resolution
   * @param y x-coordinate in given resolution
   * @param resolution dots per inch, or COORDINATE_IS_RAW_VALUE to specify raw machine coordinates
   * @param isRelative True: relative coordinates, False: absolute coordinates,
   * ignored if isWidthHeight==true
   * @param isWidthHeight False: regular coordinates (relative or absolute),
   * True: dimension values (width and height)
   */
  private void sendCoordinate(PrintStream out, int x, int y, double resolution, double prescalingY, boolean isRelative, boolean isWidthHeight) throws IOException
  {
    // convert to machine resolution (which is maxDPI with some exceptions)
    int yRaw = yPxToDeviceCoordinate(y, resolution, prescalingY, isRelative, isWidthHeight);
    int xRaw = xPxToDeviceCoordinate(x, resolution, isRelative, isWidthHeight);
    if (isWidthHeight || !isRelative)
    {
      // absolute coordinates and dimensions must be positive
      assertThat(xRaw >= 0);
      assertThat(yRaw >= 0);
      writeU32(out, xRaw);
      writeU32(out, yRaw);
    }
    else {
      // relative coordinates may be negative
      writeS32(out, xRaw);
      writeS32(out, yRaw);
    }
  }

  private void sendCoordinate(PrintStream out, int x, int y, double resolution, double prescalingY, boolean isRelative) throws IOException {
    sendCoordinate(out, x, y, resolution, prescalingY, isRelative, false);
  }

  /**
   * send width and height WITHOUT ANY PRECEEDING COMMAND.
   * See sendCoordinate()
   */
  private void sendWidthHeight(PrintStream out, int w, int h, double resolution, double prescalingY) throws IOException {
    sendCoordinate(out, w, h, resolution, prescalingY, false, true);
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

  /**
   * current speed in percent
   */
  private transient float currentSpeed = -1;

  /**
   * set speed
   * @param out stream for writing the commands
   * @param speed speed in percent
   */
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


  /**
   * Send bounding box information.
   *
   * @return Tuple(wLeft, wRight) of the lengths from the center to the overscan borders:
   *     |---overscan left---|---actual job content----|---overscan right-----|
   *     |-------wLeft-------------------|-------------------wRight-----------|
   */
  private Tuple<Double,Double> setBoundingBox(PrintStream out, LaserJob job) throws IOException
  {
    // To simplify the code, we compute the bounding box info directly from the
    // laser job. With this, we don't need to seek back on the output stream,
    // and don't need to consider special cases (circle commands etc.)

    // This is the bounding box of the actual job content without overscan.
    // Note that useless quasi-empty parts (only MOVETO but no cut/engrave)
    // must not be ignored, because the MOVETO also contributes to the bounding
    // box!
    Rectangle bbMm = job.getBoundingBox();

    // Compute maximum engrave overscan
    double maxOverscanMm = 0;
    for (JobPart p: job.getParts())
    {
      if (p instanceof RasterizableJobPart)
      {
        double overscanMm = getAddSpacePerRasterLine(((RasterizableJobPart) p).getPowerSpeedFocusPropertyForColor(0).getSpeed());
        maxOverscanMm = Math.max(overscanMm, maxOverscanMm);
      }
    }
    // Bounding box with engrave overscan
    // If, in the future, we use negative coordinates for engrave overscan, this will need to be considered here.
    double xMinOverscan = Math.max(0, bbMm.getXMin() - maxOverscanMm);
    double xMaxOverscan = Math.min(getBedWidth(), bbMm.getXMax() + maxOverscanMm);
    Rectangle bbOverscanMm = new Rectangle(xMinOverscan, bbMm.getYMin(), xMaxOverscan, bbMm.getYMax());

    setBoundingBox(out, bbMm, bbOverscanMm);

    double center = (bbMm.getXMin() + bbMm.getXMax())/2;
    double centerToLeft = center - bbOverscanMm.getXMin();
    double centerToRight = bbOverscanMm.getXMax() - center;
    return new Tuple<>(centerToLeft, centerToRight);
  }

  /**
   * Send bounding box information. Coordinates are in mm.
   * @param bb bounding box for user content
   * @param bbOverscan bounding box including engrave overscan
   */
  private void setBoundingBox(PrintStream out, Rectangle bb, Rectangle bbOverscan) throws IOException
  {
    out.write(toBytes("1B 6C"));
    sendBoundingBoxMm(out, bb);
    sendBoundingBoxMm(out, bbOverscan);
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

  private double generateRasterCode(ByteArrayOutputStream outputstream, RasterizableJobPart rp, double resolution) throws UnsupportedEncodingException, IOException
  {
    double laserTime = 0;
    PrintStream out = new PrintStream(outputstream, true, StandardCharsets.US_ASCII);
    // TODO: handle the special case if the engraving is near the left or right end of the coordinate system.
    // -> we may use slightly negative or too large coordinates (check original driver output!)
    //    Note that then, setBoundingBox() must be changed accordingly.
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
        if (pixelsPerByte == 1) {
          // 8bit per pixel ("engrave 3D") -- high/low is inverted
          bytes.invertBits();
        }
        
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
        laserTime += engraveBitmapLine(out, bytes, lineStart, dirRight, offsetPixelsDirRight, resolution, pixelsPerByte);
        if (!prop.isEngraveUnidirectional())
        {
          dirRight = !dirRight;
        }
      }
    }
    return laserTime;
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
        assertThat(runlength + COMPRESS_MAGIC_CONSTANT <= 0xFF);
        // "repeat n times"
        compressed.add((byte) (COMPRESS_MAGIC_CONSTANT + runlength));
        compressed.add(currentByte);
      }
      i += runlength;
    }
    assertThat(decompressData(compressed).equals(bytes));
    return compressed;
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
        assertThat(repetitions > 0);
        assertThat(i < data.size());
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
   * engrave a single line of pixels
   *
   * @param out move to the first point of the line and engrave it
   * @param bytes array of bytes, each contains $pixelsPerByte black/white pixels
   * @param lineStart left point of line
   * @param dirLeftToRight left-to-right engrave (true) of right-to-left (false)
   * @param pixelOffset shift the pixels: negative value means that the
   * scanlines are positioned x pixels earlier to compensate the laser tube
   * delay, zero means disabled
   * @param resolution DPI
   * @param pixelsPerByte 1 or 8 pixels per byte (8-bit or 1-bit engrave mode)
   * @return estimated engrave time
   */
  private double engraveBitmapLine(PrintStream out, ByteArrayList bytes, Point lineStart, boolean dirLeftToRight, double pixelOffset, double resolution, int pixelsPerByte) throws IOException
  {
    if (dirLeftToRight)
    {
      out.write(toBytes("1B 30"));
    }
    else
    {
      out.write(toBytes("1B 31"));
      // right-to-left. We need to flip the whole bit and byte order.
      if (pixelsPerByte == 8) {
        bytes.reverseBits();
      } else {
        assertThat(pixelsPerByte == 1);
        Collections.reverse(bytes);
      }
    }
    int bitsPerPixel = 8 / pixelsPerByte;
    bytes.leftShiftBits((int) (-pixelOffset * bitsPerPixel));
    ByteArrayList compressed = compressData(bytes);

    // length
    writeU32(out, compressed.size() + 8);
    // X, Y
    sendCoordinate(out, (int) (lineStart.x + (dirLeftToRight ? 0 : (bytes.size() * pixelsPerByte))), (int) lineStart.y, resolution, 1, false);
    // data (length-8 bytes)
    for (byte b : compressed)
    {
      out.write(b);
    }
    // TODO: this time estimate doesn't include the travel time to the start point
    // TODO make the following parameters configurable
    final double engraveSpeedVersusCutSpeed = 6.4; // Factor between full engrave speed and full cut speed.
    final double engraveExtraSecondsPerLine = 0.1; // extra time per engrave line
    return engraveExtraSecondsPerLine + cuttingTimeForPxDistance(bytes.size() * pixelsPerByte, resolution, currentSpeed  * engraveSpeedVersusCutSpeed);
  }
  

  @Override
    public boolean canEstimateJobDuration() {
        return true;
    }

  @Override
  public int estimateJobDuration(LaserJob job) throws IllegalJobException {
    try {
        checkJob(job);
        return (int) writeJobCode(job, OutputStream.nullOutputStream(), null, null);
    } catch (IOException ex) {
        Logger.getLogger(LaserToolsTechnicsCutter.class.getName()).log(Level.SEVERE, null, ex);
        return -1;
    }
  }

  private byte[] generateInitializationCode(LaserJob job, List<String> warnings) throws UnsupportedEncodingException, IOException, IllegalJobException
  {
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    currentJobMode = -1;
    currentJobIsRotary = job.isRotaryAxisEnabled();
    rotaryRadius = job.isRotaryAxisEnabled() ? job.getRotaryAxisDiameterMm() / 2 : 42;
    assertThat(!job.isRotaryAxisEnabled() || rotaryAxisSupported);
    if (rotaryRadius < 2.5)
    {
      // Check min. rotary diameter. This cannot be moved t setMaterialRadius()
      // to avoid other exceptions that would happen earlier.
      // The Windows driver uses 5mm as minimum diameter.
      throw new IllegalJobException("engrave diameter must be at least 5.0mm");
    }

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);

    out.print("LTT");

    // job.applyStartPoint() is not supported
    assertThat(job.getTransformedOriginX() == 0 && job.getTransformedOriginY() == 0);

    out.write(toBytes("1B 76 01 01 02")); // Format Version 1.1.1

    out.write(toBytes("1B 46")); // File name:
    final int maximumJobNameLength = 15;
    String jobName = job.getName();
    if (jobName.length() > maximumJobNameLength)
    {
      jobName = jobName.substring(0, maximumJobNameLength);
    }
    out.write(jobName.length());
    out.print(jobName);

    // Temporary Reference Point Mode:
    if (job.isRotaryAxisEnabled()) {
      // Rotary engrave must use temporary reference point, at least the documentation says so.
      // We use a reference point at the center.
      out.write(toBytes("1B 61 15")); // temp.ref.point center, stay at ref. point
    } else {
      // currently, temp. ref. is not yet supported except for rotary engraving.
      // The difficulty is that the driver does not support a generic ref. point
      // like LibLaserCut but only nine special positions like "center left".
      out.write(toBytes("1B 61 00")); // temp.ref.point off
    }

    // Job Mode
    setJobMode(out, (job.isRotaryAxisEnabled() ? JOB_MODE_ROTARY_AXIS : JOB_MODE_XY));

    // Box Boundary (used for temp. ref. point function)
    Tuple<Double,Double> widthFromCenter = setBoundingBox(out, job);
    if (job.isRotaryAxisEnabled())
    {
      warnings.add(
        String.format("Rotary engrave active. Please use 'Adjust rotary temp' "
          + "on the machine to set the center position of the engraving. "
          + "Make sure the laser head can move %.0f mm to the left and %.0f mm to the right without crashing! "
          + "This measurement includes some braking distance.",
          widthFromCenter.getA(), widthFromCenter.getB())
      );
    }

    out.write(toBytes("1B 6E 00 00 5D CF 00 00 69 56")); // X-Limits of rotary axis (fixed values for LTT i4000)

    out.write(toBytes("1B 4F 00")); // operation mode (no autorun)

    out.write(toBytes("1B 51 00 00")); // disable "Copy and Quantity"

    // set DPI prescaling ("PITCH")
    int dpiPrescaling = 8;
    // TODO: fixed engrave DPI = 500.
    out.write(toBytes("1B 44"));
    out.write(dpiPrescaling); // engrave (?) DPI = maximum dpi / dpiPrescaling

    setMaterialRadius(out, rotaryRadius);

    // set magic compression constant to C0 (see compressData())
    out.write(toBytes("1B 43"));
    out.write(COMPRESS_MAGIC_CONSTANT);

    // set grayscale palette for 4bit mode ("rubber power")
    // 11..EE are the intermediate values
    // Not used, but we keep it so that our output is similar to the original driver.
    out.write(toBytes("1B 54 00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"));

    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    this.setFocus(out, 0f);
    this.setVentilation(out, false);
    this.setPurge(out, false);
    out.write(toBytes("1B 42 59 45")); // goodbye
    return result.toByteArray();
  }

  protected double writeJobCode(LaserJob job, OutputStream os, ProgressListener pl, List<String> warnings) throws UnsupportedEncodingException, IOException, IllegalJobException
  {
    // fix null arguments
    if (warnings == null) {
      warnings = new LinkedList<>();
    }
    if (pl == null) {
      pl = new ProgressListenerDummy();
    }
    
    // check job
    pl.progressChanged(this, 0);
    pl.taskChanged(this, "checking job");
    checkJob(job);
    
    if (job.getStartX() != 0 || job.getStartY() != 0) {
      throw new UnsupportedOperationException("Manual start point is not yet supported.");
      // We throw this error because manual start points currently dont work.
      // The machine support for start points seems quite limited, as it only
      // allows for nine fixed combinations such as top-left, top-center, ...,
      // bottom-right.
      // To do: - find out how we can hack the commands for general start points
      //          (May be impossible.)
      //        - signal to the laser cutter that the start point is manual
      //        - probably call job.applyStartPoint();
      //        - fix all checks for coordinate limits, take the current offset into account (probably saved in applyStartPoint())
      //        - test if it also works for negative coordinates (starting point at center of job),
      //          especially for engrave (currently causes "negative array size" exception)
    }
    

    if (!isLaserArcCompensationEnabled())
    {
      // "Arc compensation" = off is not properly tested. It also needs fixing:
      // diagonal straight lines (non joint curve, just a single straight line)
      // are not cut with the correct speed. This device configuration has no benefit,
      // so we just tell the user to change it to something more helpful.
      warnings.add("Your configuration states that 'Arc Compensation' is not enabled in the lasercutter's menu. This is not recommended and not well-tested. Please enable it both in the Lasercutter Firmware (Configuration menu) and in VisiCut's laser device settings.");
    }
    
    for (JobPart p: job.getParts())
    {
      Object power = null;
      if (p instanceof RasterizableJobPart) {
        power = ((RasterizableJobPart) p).getLaserProperty().getProperty("power");
      } else if (p instanceof VectorPart) {
        power = ((VectorPart) p).getCurrentCuttingProperty().getProperty("power");
      }
      // Warn if power==0 and job part is not empty
      if (power instanceof Number && ((Number) power).floatValue() == 0 &&
          (p.getMaxX() != p.getMinX() || p.getMaxY() != p.getMinY()))
      {
        String powerZeroWarning = "Power is 0. Please check the laser settings for this material.";
        if (!warnings.contains(powerZeroWarning)) {
          warnings.add(powerZeroWarning);
        }
      }
    }
    

    double duration = 0;
    // reset internal state
    currentX = 0;
    currentY = 0;
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(this.generateInitializationCode(job, warnings));
    pl.taskChanged(this, "processing");
    pl.progressChanged(this, 20);
    int i = 0;
    int max = job.getParts().size();

    // sort job parts so that vector parts are at the end
    // The documentation says that Engrave must be before Vector, not mixed,
    // though it's not entirely clear if that is necessary.
    List<JobPart> parts = job.getParts();
    parts.sort((p1, p2) ->
            Boolean.compare(p1 instanceof VectorPart, p2 instanceof VectorPart));

    for (JobPart p : parts)
    {
      if (p instanceof Raster3dPart || p instanceof RasterPart)
      {
        duration += this.generateRasterCode(out, (RasterizableJobPart) p, p.getDPI());
      }
      else if (p instanceof VectorPart)
      {
        duration += this.generateVectorCode(out, (VectorPart) p, p.getDPI());
      }
      i++;
      pl.progressChanged(this, 20 + (int) (i * (double) 60 / max));
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
    pl.taskChanged(this, "sending");
    os.write(out.toByteArray());
    return duration;
  }

  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, IOException
  {
    this.writeJobCode(job, fileOutputStream, null, null);
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, IOException
  {
    pl.taskChanged(this, "connecting");
    try (Socket connection = new Socket())
    {
      connection.connect(new InetSocketAddress(hostname, port), 3000);
      try (BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream()))
      {
        this.writeJobCode(job, out, pl, warnings);
      }
    }
    pl.progressChanged(this, 100);
  }
  private List<Double> resolutions;

  @Override
  public List<Double> getResolutions()
  {
    if (resolutions == null)
    {
      // TODO: 333 and 166 DPI -> test if they work precisely or are actually 333.33333 and 166.66666
      resolutions = Arrays.asList(
        // TODO: currently forced to 500dpi because of PITCH command 1B 44
//        200d,
//        250d,
        500d
//        1000d
      );
      // iterate over copy of list
      resolutions.removeIf(resolution -> resolution > maxEngraveDPI);
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
   */
  private double getEngraveShiftPixels(double speedPercent, double resolution)
  {
    if (speedPercent > 100 || speedPercent < 0)
    {
      throw new IllegalArgumentException();
    }
    // We assume that the values of engraveShiftList are for 10%, 20%, ..., 100% speed
    // and do linear interpolation inbetween (plus some offset).
    // NOTE: the values are slightly different from the windows driver's interpretation of engraveShiftList,
    // but good enough that nobody notices.
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
    SETTING_ROTARY_AXIS,
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
      return this.addSpacePerRasterLineMinimum;
    }
    else if (SETTING_RASTER_WHITESPACE_MAX.equals(attribute))
    {
      return this.addSpacePerRasterLineMaximum;
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
    else if (SETTING_ROTARY_AXIS.equals(attribute))
    {
      return rotaryAxisSupported;
    }
    else if (SETTING_SUPPORTS_FREQUENCY.equals(attribute))
    {
      return this.supportsFrequency;
    }
    else if (SETTING_SUPPORTS_PURGE.equals(attribute))
    {
      return this.supportsPurge;
    }
    else if (SETTING_SUPPORTS_VENTILATION.equals(attribute))
    {
      return this.supportsVentilation;
    }
    else if (SETTING_SUPPORTS_FOCUS.equals(attribute))
    {
      return this.supportsFocus;
    }
    else if (SETTING_HOSTNAME.equals(attribute))
    {
      return this.getHostname();
    }
    else if (SETTING_PORT.equals(attribute))
    {
      return this.getPort();
    }
    else if (SETTING_BEDWIDTH.equals(attribute))
    {
      return this.getBedWidth();
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      return this.getBedHeight();
    }
    else if (SETTING_MAXDPI.equals(attribute))
    {
      return this.getMaxDPI();
    }
    else if (SETTING_MAX_ENGRAVE_DPI.equals(attribute))
    {
      return this.getMaxEngraveDPI();
    }
    else if (SETTING_ARCCOMP_ENABLE.equals(attribute))
    {
      return this.isLaserArcCompensationEnabled();
    }
    else if (SETTING_TANGENT_ENABLE.equals(attribute))
    {
      return this.isUseTangentCurves();
    }
    else if (SETTING_TANGENT_ACCEL.equals(attribute))
    {
      return this.getTangentCurveMaxAcceleration();
    }
    else if (SETTING_CUTTING_SPEED.equals(attribute))
    {
      return this.getNominalCuttingSpeed();
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
      ArrayList<Integer> l = new ArrayList<>();
      for (String s : ((String) value).trim().split("\\s+"))
      {
        try
        {
          l.add(Integer.parseInt(s));
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
    else if (SETTING_ROTARY_AXIS.equals(attribute))
    {
      rotaryAxisSupported = (Boolean) value;
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
    clone.copyProperties(this);
    return clone;
  }

}
