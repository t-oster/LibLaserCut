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

import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.platform.Util;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import purejavacomm.*;
import java.util.*;

/**
 * This class implements a driver for the LAOS Lasercutter board. Currently it
 * supports the simple code and the G-Code, which may be used in the future.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class SmoothieBoard extends LaserCutter {

  private static final String SETTING_HOST = "IP/Hostname";
  private static final String SETTING_COMPORT = "COM Port";
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String LINEEND = "\r\n";

  @Override
  public String getModelName() {
    return "SmoothieBoard";
  }
  
  protected String host = "10.10.10.222";

  public String getHost()
  {
    return host;
  }

  public void setHost(String host)
  {
    this.host = host;
  }
  
  protected String comport = "ttyACM0";

  public String getComport()
  {
    return comport;
  }

  public void setComport(String comport)
  {
    this.comport = comport;
  }
  
  @Override
  /**
   * We do not support Frequency atm, so we return power,speed and focus
   */
  public LaserProperty getLaserPropertyForVectorPart() {
    return new PowerSpeedFocusProperty();
  }

  private byte[] generateVectorGCode(VectorPart vp, double resolution) throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        case MOVETO:
          int x = cmd.getX();
          int y = cmd.getY();
          move(out, x, y, resolution);
          break;
        case LINETO:
          x = cmd.getX();
          y = cmd.getY();
          line(out, x, y, resolution);
          break;
        case SETPROPERTY:
          PowerSpeedFocusProperty p = (PowerSpeedFocusProperty) cmd.getProperty();
          setPower(out, p.getPower());
          setSpeed(out, p.getSpeed());
          setFocus(out, p.getFocus(), resolution);
          break;
      }
    }
    return result.toByteArray();
  }
  private double currentPower = -1;
  private int currentSpeed = -1;
  private double nextPower = -1;
  private int nextSpeed = -1;
  private double currentFocus = 0;

  private void setSpeed(PrintStream out, int speedInPercent) {
    nextSpeed = speedInPercent;
  }

  private void setPower(PrintStream out, double powerInPercent) {
    nextPower = powerInPercent;
  }
  
  private void setFocus(PrintStream out, double focus, double resolution) {
    if (currentFocus != focus)
    {
      out.printf(Locale.US, "G0 Z%f"+LINEEND, Util.px2mm(focus, resolution));
      currentFocus = focus;
    }
  }

  private void move(PrintStream out, int x, int y, double resolution) {
    out.printf(Locale.US, "G0 X%f Y%f"+LINEEND, Util.px2mm(x, resolution), Util.px2mm(y, resolution));
  }

  private void line(PrintStream out, int x, int y, double resolution) {
    out.printf(Locale.US, "G1 X%f Y%f", Util.px2mm(x, resolution), Util.px2mm(y, resolution));
    if (nextPower != currentPower)
    {
      out.printf(Locale.US, " S%f", nextPower);
      currentPower = nextPower;
    }
    if (nextSpeed != currentSpeed)
    {
      out.printf(Locale.US, " S%d", nextSpeed);
      currentSpeed = nextSpeed;
    }
    out.printf(Locale.US, LINEEND);
  }

  private byte[] generateInitializationCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    //out.print("G54"+LINEEND);//use table offset
    //out.print("G21"+LINEEND);//units to mm
    //out.print("G90"+LINEEND);//following coordinates are absolute
    //out.print("G0 X0 Y0"+LINEEND);//move to 0 0
    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    //back to origin and shutdown
    //out.print("G0 X0 Y0\n");//move to 0 0
    return result.toByteArray();
  }

  private BufferedInputStream in;
  private BufferedOutputStream out;
  private Socket socket;
  private CommPort port;
  
  protected void connect() throws IOException, PortInUseException, NoSuchPortException
  {
    if (comport != null && !comport.equals(""))
    {
      CommPortIdentifier i = CommPortIdentifier.getPortIdentifier(comport);
      if (i.getPortType()==CommPortIdentifier.PORT_SERIAL)
      {
        System.out.println(i.getName());

        port = i.open("VisiCut", 1000);
        in = new BufferedInputStream(port.getInputStream());
        out = new BufferedOutputStream(port.getOutputStream());
        BufferedReader rd = new BufferedReader(new InputStreamReader(in));
        String line = rd.readLine();
        if (!"Smoothie".equals(line))
        {
          throw new IOException("Does not seem to be a smoothieboard on "+comport);
        }
      }
    }
    else
    {
      socket = new Socket();
      socket.connect(new InetSocketAddress(this.host, 23), 1000);
      in = new BufferedInputStream(socket.getInputStream());
      out = new BufferedOutputStream(socket.getOutputStream());
    }
  }
  
  protected void disconnect() throws IOException
  {
    in.close();
    out.close();
    if (this.socket != null)
    {
      socket.close();
      socket = null;
    }
    else if (this.port != null)
    {
      this.port.close();
      this.port = null;
    }
  }
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;

    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting");
    pl.taskChanged(this, "connecting...");
    connect();
    pl.taskChanged(this, "sending");
    out.write(this.generateInitializationCode());
    pl.progressChanged(this, 20);
    int i = 0;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterPart)
      {
        p = convertRasterToVectorPart((RasterPart) p, p.getDPI(), false);
      }
      if (p instanceof VectorPart)
      {
        out.write(this.generateVectorGCode((VectorPart) p, p.getDPI()));
      }
      i++;
      pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
    }
    out.write(this.generateShutdownCode());
    disconnect();
    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
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
  private static String[] settingAttributes = new String[]{
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_HOST,
    SETTING_COMPORT
  };

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOST.equals(attribute)) {
      return this.getHost();
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.getBedHeight();
    } else if (SETTING_COMPORT.equals(attribute)) {
      return this.getComport();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOST.equals(attribute)) {
      this.setHost((String) value);
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight((Double) value);
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComport((String) value);
    }
  }

  @Override
  public LaserCutter clone() {
    SmoothieBoard clone = new SmoothieBoard();
    clone.host = host;
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    clone.comport = comport;
    return clone;
  }
}
