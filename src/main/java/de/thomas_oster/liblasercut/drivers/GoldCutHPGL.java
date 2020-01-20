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
/*
 * Copyright (C) 2015 Jürgen Weigert <juewei@fabfolk.com>
 */
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.*;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.SerialPort;
import java.net.URI;

/**
 * This class implements a simple HPGL driver, suitable for the GoldCut ABH 721 Cutter.
 *
 * @author Jürgen Weigert <juewei@fabfolk.com>
 */
public class GoldCutHPGL extends LaserCutter {

  private static final String SETTING_COMPORT = "COM-Port/Device (or file:///tmp/out.hpgl)";
  private static final String SETTING_INITSTRING = "HPGL initialization";
  private static final String SETTING_FINISTRING = "HPGL shutdown";
  private static final String SETTING_BEDWIDTH = "Cutter width [mm]";
  private static final String SETTING_HARDWARE_DPI = "Cutter resolution [steps/inch]";
  private static final String SETTING_FLIPX = "X axis goes right to left (yes/no)";
  private static final String SETTING_FLIPY = "Y axis goes front to back (yes/no)";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per raster line (mm)";

  protected int hw_x = 0;
  protected int hw_y = 0;

  @Override
  public String getModelName() {
    return "GoldCutHPGL";
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

  protected boolean flipYaxis = false;
  /**
   * Get the value of flipYaxis
   *
   * @return the value of flipYaxis
   */
  public boolean isFlipYaxis() {
    return flipYaxis;
  }
  /**
   * Set the value of flipYaxis
   *
   * @param flipXaxis new value of flipYaxis
   */
  public void setFlipYaxis(boolean flipYaxis) {
    // System.err.printf("setFlipYaxis %d -> %d\n", this.flipYaxis?1:0, flipYaxis?1:0);
    this.flipYaxis = flipYaxis;
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

  protected String finiString = "!PG;;";
  /**
   * Get the value of finiString
   *
   * @return the value of finiString
   */
  public String getFiniString() {
    return finiString;
  }
  /**
   * Set the value of finiString
   *
   * @param comPort new value of finiString
   */
  public void setFiniString(String finiString) {
    this.finiString = finiString;
  }

  protected String initString = "IN;PA;";
  /**
   * Get the value of initString
   *
   * @return the value of initString
   */
  public String getInitString() {
    return initString;
  }
  /**
   * Set the value of initString
   *
   * @param comPort new value of initString
   */
  public void setInitString(String initString) {
    this.initString = initString;
  }

  protected String comPort = "/dev/ttyUSB0";
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
    PrintStream out = new PrintStream(result, true, "US-ASCII");
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
          PowerSpeedFocusFrequencyProperty p = (PowerSpeedFocusFrequencyProperty) cmd.getProperty();
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
      // out.printf(Locale.US, "G1 F%i\n", (int) ((double) speedInPercent * this.getLaserRate() / 100));
      currentSpeed = speedInPercent;
    }

  }

  private void setPower(PrintStream out, int powerInPercent) {
    if (powerInPercent != currentPower) {
      // out.printf(Locale.US, "S%i\n", (int) (255d * powerInPercent / 100));
      currentPower = powerInPercent;
    }
  }

  private void move(PrintStream out, double x, double y, double resolution) {
    moveOrLine("PU", out, x, y, resolution);
  }

  private void line(PrintStream out, double x, double y, double resolution) {
    moveOrLine("PD", out, x, y, resolution);
  }

  /**
   * send a PU or PD command and move to next coordinate
   * @param command "PU" or "PD"
   * @param out PrintStream for the output
   * @param x coordinate (in pixels)
   * @param y coordinate (in pixels)
   * @param resolution dpi (coordinate pixels per inch)
   */
  private void moveOrLine(String command, PrintStream out, double x, double y, double resolution) {
    double hw_scale = this.getHwDPI()/resolution;
    // Note: standard HPGL coordinates are: (0,0)=top-left, Y=right, X=down.
    hw_x = (int)(hw_scale * (isFlipXaxis() ? Util.mm2px(this.bedWidth, resolution) - y : y));
    hw_y = (int)(hw_scale * (isFlipYaxis() ?  Util.mm2px(getBedHeight(), resolution) - x : x));
    out.printf(Locale.US, command + "%d,%d;", hw_x, hw_y);
  }

  private byte[] generatePseudoRaster3dGCode(Raster3dPart rp, double resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    boolean dirRight = true;
    Point rasterStart = rp.getRasterStart();
    PowerSpeedFocusProperty prop = (PowerSpeedFocusProperty) rp.getLaserProperty();
    setSpeed(out, (int) prop.getSpeed());
    ByteArrayList bytes = new ByteArrayList(rp.getRasterWidth());
    for (int line = 0; line < rp.getRasterHeight(); line++) {
      Point lineStart = rasterStart.clone();
      lineStart.y += line;
      rp.getRasterLine(line, bytes);
      //remove heading zeroes
      while (bytes.size() > 0 && bytes.get(0) == 0) {
        bytes.remove(0);
        lineStart.x += 1;
      }
      //remove trailing zeroes
      while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0) {
        bytes.remove(bytes.size() - 1);
      }
      if (bytes.size() > 0) {
        if (dirRight) {
          //move to the first nonempyt point of the line
          move(out, lineStart.x, lineStart.y, resolution);
          byte old = bytes.get(0);
          for (int pix = 0; pix < bytes.size(); pix++) {
            if (bytes.get(pix) != old) {
              if (old == 0) {
                move(out, lineStart.x + pix, lineStart.y, resolution);
              } else {
                setPower(out, (int) prop.getPower() * (0xFF & old) / 255);
                line(out, lineStart.x + pix - 1, lineStart.y, resolution);
                move(out, lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          setPower(out, (int) prop.getPower() * (0xFF & bytes.get(bytes.size() - 1)) / 255);
          line(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
        } else {
          //move to the last nonempty point of the line
          move(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          byte old = bytes.get(bytes.size() - 1);
          for (int pix = bytes.size() - 1; pix >= 0; pix--) {
            if (bytes.get(pix) != old || pix == 0) {
              if (old == 0) {
                move(out, lineStart.x + pix, lineStart.y, resolution);
              } else {
                setPower(out, (int) prop.getPower() * (0xFF & old) / 255);
                line(out, lineStart.x + pix + 1, lineStart.y, resolution);
                move(out, lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          setPower(out, (int) prop.getPower() * (0xFF & bytes.get(0)) / 255);
          line(out, lineStart.x, lineStart.y, resolution);
        }
      }
      dirRight = !dirRight;
    }
    return result.toByteArray();
  }

  private byte[] generatePseudoRasterGCode(RasterPart rp, double resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    boolean dirRight = true;
    Point rasterStart = rp.getRasterStart();
    PowerSpeedFocusProperty prop = (PowerSpeedFocusProperty) rp.getLaserProperty();
    setSpeed(out, (int) prop.getSpeed());
    setPower(out, (int) prop.getPower());
    for (int line = 0; line < rp.getRasterHeight(); line++) {
      Point lineStart = rasterStart.clone();
      lineStart.y += line;
      List<Byte> bytes = new LinkedList<Byte>();
      boolean lookForStart = true;
      for (int x = 0; x < rp.getRasterWidth(); x++) {
        if (lookForStart) {
          if (rp.isBlack(x, line)) {
            lookForStart = false;
            bytes.add((byte) 255);
          } else {
            lineStart.x += 1;
          }
        } else {
          bytes.add(rp.isBlack(x, line) ? (byte) 255 : (byte) 0);
        }
      }
      //remove trailing zeroes
      while (bytes.size() > 0 && bytes.get(bytes.size() - 1) == 0) {
        bytes.remove(bytes.size() - 1);
      }
      if (bytes.size() > 0) {
        if (dirRight) {
          //add some space to the left
          move(out, Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          //move to the first nonempyt point of the line
          move(out, lineStart.x, lineStart.y, resolution);
          byte old = bytes.get(0);
          for (int pix = 0; pix < bytes.size(); pix++) {
            if (bytes.get(pix) != old) {
              if (old == 0) {
                move(out, lineStart.x + pix, lineStart.y, resolution);
              } else {
                setPower(out, (int) prop.getPower() * (0xFF & old) / 255);
                line(out, lineStart.x + pix - 1, lineStart.y, resolution);
                move(out, lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          setPower(out, (int) prop.getPower() * (0xFF & bytes.get(bytes.size() - 1)) / 255);
          line(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          //add some space to the right
          move(out, Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
        } else {
          //add some space to the right
          move(out, Math.min((int) Util.mm2px(bedWidth, resolution), (int) (lineStart.x + bytes.size() - 1 + Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
          //move to the last nonempty point of the line
          move(out, lineStart.x + bytes.size() - 1, lineStart.y, resolution);
          byte old = bytes.get(bytes.size() - 1);
          for (int pix = bytes.size() - 1; pix >= 0; pix--) {
            if (bytes.get(pix) != old || pix == 0) {
              if (old == 0) {
                move(out, lineStart.x + pix, lineStart.y, resolution);
              } else {
                setPower(out, (int) prop.getPower() * (0xFF & old) / 255);
                line(out, lineStart.x + pix + 1, lineStart.y, resolution);
                move(out, lineStart.x + pix, lineStart.y, resolution);
              }
              old = bytes.get(pix);
            }
          }
          //last point is also not "white"
          setPower(out, (int) prop.getPower() * (0xFF & bytes.get(0)) / 255);
          line(out, lineStart.x, lineStart.y, resolution);
          //add some space to the left
          move(out, Math.max(0, (int) (lineStart.x - Util.mm2px(this.addSpacePerRasterLine, resolution))), lineStart.y, resolution);
        }
      }
      dirRight = !dirRight;
    }
    return result.toByteArray();
  }

  private byte[] generateInitializationCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    out.print(this.initString);
    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    out.printf(Locale.US, "PU%d,%d;", this.hw_y, this.hw_x);
    //back to origin and shutdown
    out.print(this.finiString);
    return result.toByteArray();
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;
    BufferedOutputStream out;
    SerialPort port = null;
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting");
    if (this.getComPort().startsWith("file://"))
    {
	out = new BufferedOutputStream(new FileOutputStream(new File(new URI(this.getComPort()))));
    }
    else
    {
        String ComPortName = this.getComPort();
        if (ComPortName.startsWith("/dev/"))
	{
	  // allow "/dev/ttyUSB0", although we need only "ttyUSB0"
 	  ComPortName = ComPortName.substring(5);
	}
	CommPortIdentifier cpi = null;
	//since the CommPortIdentifier.getPortIdentifier(String name) method
	//is not working as expected, we have to manually find our port.
	Enumeration en = CommPortIdentifier.getPortIdentifiers();
	while (en.hasMoreElements())
	{
	  Object o = en.nextElement();
	  if (o instanceof CommPortIdentifier && ((CommPortIdentifier) o).getName().equals(ComPortName))
	  {
	    cpi = (CommPortIdentifier) o;
	    break;
	  }
	}
	if (cpi == null)
	{
	  throw new Exception("Error: No such COM-Port '"+this.getComPort()+"'");
	}
	CommPort tmp = cpi.open("VisiCut", 10000);
	if (tmp == null)
	{
	  throw new Exception("Error: Could not Open COM-Port '"+this.getComPort()+"'");
	}
	if (!(tmp instanceof SerialPort))
	{
	  throw new Exception("Port '"+this.getComPort()+"' is not a serial port.");
	}
	port = (SerialPort) tmp;
	port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
	port.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
	out = new BufferedOutputStream(port.getOutputStream());
	pl.taskChanged(this, "sending");
    }
    writeJob(out, job, pl, port);
  }

  private void writeJob(BufferedOutputStream out, LaserJob job, ProgressListener pl, SerialPort port) throws IllegalJobException, Exception {
    out.write(this.generateInitializationCode());
    if (pl != null) pl.progressChanged(this, 20);
    int i = 0;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof Raster3dPart)
      {
        out.write(this.generatePseudoRaster3dGCode((Raster3dPart) p, p.getDPI()));
      }
      else if (p instanceof RasterPart)
      {
        out.write(this.generatePseudoRasterGCode((RasterPart) p, p.getDPI()));
      }
      else if (p instanceof VectorPart)
      {
        out.write(this.generateVectorGCode((VectorPart) p, p.getDPI()));
      }
      i++;
      if (pl != null) pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
    }
    out.write(this.generateShutdownCode());
    out.close();
    if (port != null)
    {
      port.close();
    }
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
      resolutions = Arrays.asList(new Double[]{
                500d
              });
    }
    return resolutions;
  }

  protected double bedWidth = 630;
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
   * Set the value of bedWidth [mm]
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth) {
    this.bedWidth = bedWidth;
  }

  // unused dummy code. But needed to survive overloading errors.
  /**
   * Get the value of bedHeight [mm]
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight() {
    return 1000;	// dummy value, used for GUI!
  }

  protected double hwDPI = 1016.; // see Wikipedia: one HPGL "pixel" is 25µm, i.e. 1016 per inch
  /**
   * Get the value of hwDPI
   *
   * @return the value of hwDPI
   */
  public double getHwDPI() {
    return hwDPI;
  }
  /**
   * Set the value of hwDPI
   *
   * @param hwDPI new value of hwDPI
   */
  public void setHwDPI(double hwDPI) {
    this.hwDPI = hwDPI;
  }

  private static String[] settingAttributes = new String[]{
    SETTING_BEDWIDTH,
    SETTING_HARDWARE_DPI,
//    SETTING_FLIPX,
//    SETTING_FLIPY,
    SETTING_COMPORT,
    SETTING_RASTER_WHITESPACE,
    SETTING_INITSTRING,
    SETTING_FINISTRING,
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
//    } else if (SETTING_FLIPX.equals(attribute)) {
//      return this.isFlipXaxis();
//    } else if (SETTING_FLIPY.equals(attribute)) {
//      return this.isFlipYaxis();
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_HARDWARE_DPI.equals(attribute)) {
      return this.getHwDPI();
    } else if (SETTING_INITSTRING.equals(attribute)) {
      return this.getInitString();
    } else if (SETTING_FINISTRING.equals(attribute)) {
      return this.getFiniString();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      this.setAddSpacePerRasterLine((Double) value);
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComPort((String) value);
//    } else if (SETTING_FLIPX.equals(attribute)) {
//      this.setFlipXaxis((Boolean) value);
//    } else if (SETTING_FLIPY.equals(attribute)) {
//      this.setFlipYaxis((Boolean) value);
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_HARDWARE_DPI.equals(attribute)) {
      this.setHwDPI((Double) value);
    } else if (SETTING_INITSTRING.equals(attribute)) {
      this.setInitString((String) value);
    } else if (SETTING_FINISTRING.equals(attribute)) {
      this.setFiniString((String) value);
    }
  }

  @Override
  public LaserCutter clone() {
    GoldCutHPGL clone = new GoldCutHPGL();
    clone.comPort = comPort;
    clone.bedWidth = bedWidth;
    clone.hwDPI = hwDPI;
    clone.flipXaxis = flipXaxis;
    clone.flipYaxis = flipYaxis;
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    return clone;
  }

  @Override
  public void saveJob(PrintStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception {
      writeJob(new BufferedOutputStream(fileOutputStream), job, null, null);
  }
}
