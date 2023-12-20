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

import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.JobPart;
import de.thomas_oster.liblasercut.LaserCutter;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.ProgressListener;
import de.thomas_oster.liblasercut.RasterizableJobPart;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Util;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.SerialPort;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * This class implements a driver for the LAOS Lasercutter board. Currently it
 * supports the simple code and the G-Code, which may be used in the future.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class Lasersaur extends LaserCutter {

  private static final String SETTING_COMPORT = "COM-Port/Device";
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String SETTING_FLIPX = "X axis goes right to left (yes/no)";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per Raster line (mm)";
  private static final String SETTING_SEEK_RATE = "Max. Seek Rate (mm/min)";
  private static final String SETTING_LASER_RATE = "Max. Laser Rate (mm/min)";

  @Override
  public String getModelName() {
    return "Lasersaur";
  }
  private double addSpacePerRasterLine = 0.5;

  /**
   * Get the value of addSpacePerRasterLine
   *
   * @return the value of addSpacePerRasterLine
   */
  public double getAddSpacePerRasterLine() {
    return addSpacePerRasterLine;
  }

  /**
   * Set the value of addSpacePerRasterLine
   *
   * @param addSpacePerRasterLine new value of addSpacePerRasterLine
   */
  public void setAddSpacePerRasterLine(double addSpacePerRasterLine) {
    this.addSpacePerRasterLine = addSpacePerRasterLine;
  }
  private double seekRate = 2000;

  /**
   * Get the value of seekRate
   *
   * @return the value of seekRate
   */
  public double getSeekRate() {
    return seekRate;
  }

  /**
   * Set the value of seekRate
   *
   * @param seekRate new value of seekRate
   */
  public void setSeekRate(double seekRate) {
    this.seekRate = seekRate;
  }
  private double laserRate = 2000;

  /**
   * Get the value of laserRate
   *
   * @return the value of laserRate
   */
  public double getLaserRate() {
    return laserRate;
  }

  /**
   * Set the value of laserRate
   *
   * @param laserRate new value of laserRate
   */
  public void setLaserRate(double laserRate) {
    this.laserRate = laserRate;
  }
  protected boolean flipXaxis = false;

  /**
   * Get the value of flipXaxis
   *
   * @return the value of flipXaxis
   */
  public boolean isFlipXaxis() {
    return flipXaxis;
  }

  /**
   * Set the value of flipXaxis
   *
   * @param flipXaxis new value of flipXaxis
   */
  public void setFlipXaxis(boolean flipXaxis) {
    this.flipXaxis = flipXaxis;
  }
  protected String comPort = "ttyUSB0";

  /**
   * Get the value of port
   *
   * @return the value of port
   */
  public String getComPort() {
    return comPort;
  }

  /**
   * Set the value of port
   *
   * @param comPort new value of port
   */
  public void setComPort(String comPort) {
    this.comPort = comPort;
  }

  private byte[] generateVectorGCode(VectorPart vp, double resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        case MOVETO:
          double x = cmd.getX();
          double y = cmd.getY();
          move(out, x, y, resolution);
          break;
        case LINETO:
          x = cmd.getX();
          y = cmd.getY();
          line(out, x, y, resolution);
          break;
        case SETPROPERTY:
          LaserProperty p = cmd.getProperty();
          setPower(out, (int) p.getPower());
          setSpeed(out, (int) p.getSpeed());
          break;
      }
    }
    return result.toByteArray();
  }
  private int currentPower = -1;
  private int currentSpeed = -1;

  private void setSpeed(PrintStream out, int speedInPercent) {
    if (speedInPercent != currentSpeed) {
      out.printf(Locale.US, "G1 F%d\n", (int) ((double) speedInPercent * this.getLaserRate() / 100));
      currentSpeed = speedInPercent;
    }

  }

  private void setPower(PrintStream out, int powerInPercent) {
    if (powerInPercent != currentPower) {
      out.printf(Locale.US, "S%d\n", (int) (255d * powerInPercent / 100));
      currentPower = powerInPercent;
    }
  }

  private void move(PrintStream out, double x, double y, double resolution) {
    out.printf(Locale.US, "G0 X%f Y%f\n", Util.px2mm(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), Util.px2mm(y, resolution));
  }

  private void line(PrintStream out, double x, double y, double resolution) {
    out.printf(Locale.US, "G1 X%f Y%f\n", Util.px2mm(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), Util.px2mm(y, resolution));
  }

  /// generate startup code and reset internal states
  private byte[] generateInitializationCode() throws UnsupportedEncodingException {
    // force output of speed and power in the next job part
    currentSpeed = -1;
    currentPower = -1;

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    out.print("G54\n");//use table offset
    out.print("G21\n");//units to mm
    out.print("G90\n");//following coordinates are absolute
    out.print("G0 X0 Y0\n");//move to 0 0
    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    //back to origin and shutdown
    out.print("G0 X0 Y0\n");//move to 0 0
    return result.toByteArray();
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;
    BufferedOutputStream out;
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting");
    CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(this.getComPort());
    CommPort tmp = cpi.open("VisiCut", 10000);
    if (tmp == null)
    {
      throw new Exception("Error: Could not Open COM-Port '"+this.getComPort()+"'");
    }
    if (!(tmp instanceof SerialPort))
    {
      throw new Exception("Port '"+this.getComPort()+"' is not a serial port.");
    }
    SerialPort port = (SerialPort) tmp;
    port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
    port.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
    out = new BufferedOutputStream(port.getOutputStream());
    pl.taskChanged(this, "sending");
    writeJob(out, job, pl, port);
  }

  private void writeJob(BufferedOutputStream out, LaserJob job, ProgressListener pl, SerialPort port) throws IllegalJobException, Exception {
    out.write(this.generateInitializationCode());
    if (pl != null) pl.progressChanged(this, 20);
    int i = 0;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterizableJobPart)
      {
        p = convertRasterizableToVectorPart((RasterizableJobPart) p, job, true, true, true);
      }
      
      out.write(this.generateVectorGCode((VectorPart) p, p.getDPI()));
      i++;
      if (pl!= null) pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
    }
    out.write(this.generateShutdownCode());
    out.close();
    if (port != null) port.close();
    if (pl != null)
    {
      pl.taskChanged(this, "sent.");
      pl.progressChanged(this, 100);
    }
  }
  private List<Double> resolutions;

  @Override
  public List<Double> getResolutions() {
    if (resolutions == null) {
      resolutions = Arrays.asList(500d);
    }
    return resolutions;
  }
  protected double bedWidth = 250;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth() {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth) {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 280;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight() {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight) {
    this.bedHeight = bedHeight;
  }
  private static final String[] settingAttributes = new String[]{
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_FLIPX,
    SETTING_COMPORT,
    SETTING_LASER_RATE,
    SETTING_SEEK_RATE,
    SETTING_RASTER_WHITESPACE,
  };

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      return this.getAddSpacePerRasterLine();
    } else if (SETTING_COMPORT.equals(attribute)) {
      return this.getComPort();
    } else if (SETTING_FLIPX.equals(attribute)) {
      return this.isFlipXaxis();
    } else if (SETTING_LASER_RATE.equals(attribute)) {
      return this.getLaserRate();
    } else if (SETTING_SEEK_RATE.equals(attribute)) {
      return this.getSeekRate();
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.getBedHeight();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      this.setAddSpacePerRasterLine((Double) value);
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComPort((String) value);
    } else if (SETTING_LASER_RATE.equals(attribute)) {
      this.setLaserRate((Double) value);
    } else if (SETTING_SEEK_RATE.equals(attribute)) {
      this.setSeekRate((Double) value);
    } else if (SETTING_FLIPX.equals(attribute)) {
      this.setFlipXaxis((Boolean) value);
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight((Double) value);
    }
  }

  @Override
  public LaserCutter clone() {
    Lasersaur clone = new Lasersaur();
    clone.comPort = comPort;
    clone.laserRate = laserRate;
    clone.seekRate = seekRate;
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    clone.flipXaxis = flipXaxis;
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    return clone;
  }

  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception {
      writeJob(new BufferedOutputStream(fileOutputStream), job, null, null);
  }
}
