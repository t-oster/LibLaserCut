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
import de.thomas_oster.liblasercut.Raster3dPart;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.RasterizableJobPart;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * This class implements a driver for the LAOS Lasercutter board.
 * Currently it supports the simple code and the G-Code, which may be used in
 * the future.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosCutter extends LaserCutter
{

  private static final String SETTING_HOSTNAME = "Hostname / IP";
  private static final String SETTING_PORT = "Port";
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String SETTING_FLIPX = "X axis goes right to left (yes/no)";
  private static final String SETTING_FLIPY = "Y axis goes bottom to top (yes/no)";
  private static final String SETTING_MMPERSTEP = "mm per Step (for SimpleMode)";
  private static final String SETTING_TFTP = "Use TFTP instead of TCP";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per Raster line";
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
  
  //only kept for backwards compatibility. unused
  private final transient boolean unidir = false;
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

  private double addSpacePerRasterLine = 5;

  /**
   * Get the value of addSpacePerRasterLine
   *
   * @return the value of addSpacePerRasterLine
   */
  public double getAddSpacePerRasterLine()
  {
    return addSpacePerRasterLine;
  }

  /**
   * Set the value of addSpacePerRasterLine
   * This is a space (in mm) for the laserhead to gain
   * speed before the first 'black' pixel in every line
   *
   * @param addSpacePerRasterLine new value of addSpacePerRasterLine
   */
  public void setAddSpacePerRasterLine(double addSpacePerRasterLine)
  {
    this.addSpacePerRasterLine = addSpacePerRasterLine;
  }


  @Override
  public String getModelName()
  {
    return "LAOS";
  }
  protected boolean useTftp = true;

  /**
   * Get the value of useTftp
   *
   * @return the value of useTftp
   */
  public boolean isUseTftp()
  {
    return useTftp;
  }

  /**
   * Set the value of useTftp
   *
   * @param useTftp new value of useTftp
   */
  public void setUseTftp(boolean useTftp)
  {
    this.useTftp = useTftp;
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
  protected int port = 69;

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
  protected double mmPerStep = 0.001;

  /**
   * Get the value of mmPerStep
   *
   * @return the value of mmPerStep
   */
  public double getMmPerStep()
  {
    return mmPerStep;
  }

  /**
   * Set the value of mmPerStep
   *
   * @param mmPerStep new value of mmPerStep
   */
  public void setMmPerStep(double mmPerStep)
  {
    this.mmPerStep = mmPerStep;
  }

  private int px2steps(double px, double dpi)
  {
    return (int) (Util.px2mm(px, dpi) / this.mmPerStep);
  }

  private byte[] generateVectorGCode(VectorPart vp, double resolution) throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    for (VectorCommand cmd : vp.getCommandList())
    {
      switch (cmd.getType())
      {
        case MOVETO:
          move(out, cmd.getX(), cmd.getY(), resolution);
          break;
        case LINETO:
          line(out, cmd.getX(), cmd.getY(), resolution);
          break;
        case SETPROPERTY:
        {
          this.setCurrentProperty(out, cmd.getProperty());
          break;
        }
      }
    }
    return result.toByteArray();
  }

  private void move(PrintStream out, double x, double y, double resolution)
  {
    out.printf("0 %d %d\n", px2steps(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), px2steps(isFlipYaxis() ? Util.mm2px(bedHeight, resolution) - y : y, resolution));
  }

  private void loadBitmapLine(PrintStream out, List<Long> dwords)
  {
    out.printf("9 %s %s ", "1", ""+(dwords.size()*32));
    for(Long d:dwords)
    {
      out.print(" " + d);
    }
    out.print("\n");
  }

  private float currentPower = -1;
  private void setPower(PrintStream out, float power)
  {
    if (currentPower != power)
    {
      out.printf("7 101 %d\n", (int) (power * 100));
      currentPower = power;
    }
  }

  private float currentSpeed = -1;
  private void setSpeed(PrintStream out, float speed)
  {
    if (currentSpeed != speed)
    {
      out.printf("7 100 %d\n", (int) (speed * 100));
      currentSpeed = speed;
    }
  }

  private int currentFrequency = -1;
  private void setFrequency(PrintStream out, int frequency)
  {
    if (currentFrequency != frequency)
    {
      out.printf("7 102 %d\n", frequency);
      currentFrequency = frequency;
    }
  }

  private float currentFocus = 0;
  private void setFocus(PrintStream out, float focus)
  {
    if (currentFocus != focus)
    {
      out.printf(Locale.US, "2 %d\n", (int) (focus/this.mmPerStep));
      currentFocus = focus;
    }
  }

  private Boolean currentVentilation = null;
  private void setVentilation(PrintStream out, boolean ventilation)
  {
    if (currentVentilation == null || !currentVentilation.equals(ventilation))
    {
      out.printf(Locale.US, "7 6 %d\n", ventilation ? 1 : 0);
      currentVentilation = ventilation;
    }
  }

  private Boolean currentPurge = null;
  private void setPurge(PrintStream out, boolean purge)
  {
    if (currentPurge == null || !currentPurge.equals(purge))
    {
      out.printf(Locale.US, "7 7 %d\n", purge ? 1 : 0);
      currentPurge = purge;
    }
  }

  private void setCurrentProperty(PrintStream out, LaserProperty p)
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
      throw new RuntimeException("The Laos driver only accepts LaosCutter properties (was "+p.getClass().toString()+")");
    }
  }

  private void line(PrintStream out, double x, double y, double resolution)
  {
    out.printf("1 %d %d\n", px2steps(isFlipXaxis() ? Util.mm2px(bedWidth, resolution) - x : x, resolution), px2steps(isFlipYaxis() ? Util.mm2px(bedHeight, resolution) - y : y, resolution));
  }

  /**
   * This Method takes a raster-line represented by a list of bytes,
   * where: byte0 ist the left-most byte, in one byte, the MSB is the
   * left-most bit, 0 representing laser off, 1 representing laser on.
   * The Output List of longs, where each value is the unsigned dword
   * of 4 bytes of the input each, where the first dword is the leftmost
   * dword and the LSB is the leftmost bit. If outputLeftToRight is false,
   * the first dword is the rightmost dword and the LSB of each dword is the
   * the Output is padded with zeroes on the right side, if leftToRight is true,
   * on the left-side otherwise
   * rightmost bit
   */
  public List<Long> byteLineToDwords(List<Byte> line, boolean outputLeftToRight)
  {
    List<Long> result = new ArrayList<>();
    int s = line.size();
    for (int i=0;i<s;i++)
    {
      line.set(i, (byte) (Integer.reverse(0xFF&line.get(i))>>>24));
    }
    for(int i=0; i<s; i+=4)
    {
      result.add(
        (((long) (i+3 < s ? 0xFF&line.get(i+3) : 0))<<24)
        + (((long) (i+2 < s ? 0xFF&line.get(i+2) : 0))<<16)
        + (((long) (i+1 < s ? 0xFF&line.get(i+1) : 0))<<8)
        + ((long) (0xFF&line.get(i)))
        );
    }
    if (!outputLeftToRight)
    {
      Collections.reverse(result);
      for(int i=0;i<result.size();i++)
      {
        result.set(i, Long.reverse(result.get(i)) >>> 32);
      }
    }
    return result;
  }

  private byte[] generateLaosRasterCode(RasterPart rp, double resolution) throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    boolean dirRight = true;
    Point rasterStart = rp.getRasterStart();
    LaosEngraveProperty prop = rp.getLaserProperty() instanceof LaosEngraveProperty ? (LaosEngraveProperty) rp.getLaserProperty() : new LaosEngraveProperty(rp.getLaserProperty());
    this.setCurrentProperty(out, prop);
    boolean bu = prop.isEngraveBottomUp();
    ByteArrayList bytes = new ByteArrayList(rp.getRasterWidth());
    for (int line = bu ? rp.getRasterHeight()-1 : 0; bu ? line >= 0 : line < rp.getRasterHeight(); line += bu ? -1 : 1)
    {
      Point lineStart = rasterStart.clone();
      lineStart.y += line;
      rp.getRasterLine(line, bytes);
      //remove heading zeroes
      while (bytes.size() > 0 && bytes.get(0) == 0)
      {
        lineStart.x += 8;
        bytes.remove(0);
      }
      //remove trailing zeroes
      while (bytes.size() > 0 && bytes.get(bytes.size()-1) == 0)
      {
        bytes.remove(bytes.size()-1);
      }
      if (bytes.size() > 0)
      {
        //add space on the left side
        int space = (int) Util.mm2px(this.getAddSpacePerRasterLine(), resolution);
        while (space > 0 && lineStart.x >= 8)
        {
          bytes.add(0, (byte) 0);
          space -= 8;
          lineStart.x -=8;
        }
        //add space on the right side
        space = (int) Util.mm2px(this.getAddSpacePerRasterLine(), resolution);
        int max = (int) Util.mm2px(this.getBedWidth(), resolution);
        while (space > 0 && lineStart.x+(8*bytes.size()) < max-8)
        {
          bytes.add((byte) 0);
          space -= 8;
        }
        if (dirRight)
        {
          //move to the first point of the line
          move(out, lineStart.x, lineStart.y, resolution);
          List<Long> dwords = this.byteLineToDwords(bytes, true);
          loadBitmapLine(out, dwords);
          line(out, lineStart.x + (dwords.size()*32), lineStart.y, resolution);
        }
        else
        {
          //move to the first point of the line
          List<Long> dwords = this.byteLineToDwords(bytes, false);
          move(out, lineStart.x+(dwords.size()*32), lineStart.y, resolution);
          loadBitmapLine(out, dwords);
          line(out, lineStart.x, lineStart.y, resolution);
        }
      }
      if (!prop.isEngraveUnidirectional())
      {
        dirRight = !dirRight;
      }
    }
    return result.toByteArray();
  }
  
  private byte[] generateInitializationCode() throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    return result.toByteArray();
  }

  private byte[] generateShutdownCode() throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    this.setFocus(out, 0f);
    this.setVentilation(out, false);
    this.setPurge(out, false);
    return result.toByteArray();
  }

  protected void writeJobCode(LaserJob job, OutputStream out, ProgressListener pl) throws UnsupportedEncodingException, IOException
  {
    out.write(this.generateInitializationCode());
    if (pl != null)
    {
      pl.progressChanged(this, 20);
    }
    out.write(this.generateBoundingBoxCode(job));
    int i = 0;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof Raster3dPart || p instanceof VectorPart)
      {
        if (p instanceof Raster3dPart)
        {
          p = convertRasterizableToVectorPart((RasterizableJobPart) p, job, true, false, true);
        }
        out.write(this.generateVectorGCode((VectorPart) p, p.getDPI()));
      }
      else if (p instanceof RasterPart)
      {
        out.write(this.generateLaosRasterCode((RasterPart) p, p.getDPI()));
      }
      i++;
      if (pl != null)
      {
        pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
      }
    }
    out.write(this.generateShutdownCode());
  }

  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception
  {
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    checkJob(job);
    job.applyStartPoint();
    this.writeJobCode(job, fileOutputStream, null);
  }
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    currentFrequency = -1;
    currentPower = -1;
    currentSpeed = -1;
    currentFocus = 0;
    currentPurge = false;
    currentVentilation = false;
    pl.progressChanged(this, 0);

    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();

    pl.taskChanged(this, "buffering");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (BufferedOutputStream bufferOutStream = new BufferedOutputStream(buffer))
    {
      this.writeJobCode(job, bufferOutStream, pl);
    }

    if (debugFilename != null && !"".equals(debugFilename))
      {
        pl.taskChanged(this, "writing "+debugFilename);
        try (FileOutputStream o = new FileOutputStream(new File(debugFilename)))
        {
          o.write(buffer.toByteArray());
        }
      }

    pl.taskChanged(this, "connecting");
    if (this.isUseTftp())
    {
      TFTPClient tftp = new TFTPClient();
      try {
        tftp.setDefaultTimeout(5000);
        //open a local UDP socket
        tftp.open();
        pl.taskChanged(this, "sending");
        try (ByteArrayInputStream bain = new ByteArrayInputStream(buffer.toByteArray()))
        {
          tftp.sendFile(job.getName().replace(" ", "") +".lgc", TFTP.BINARY_MODE, bain, this.getHostname(), this.getPort());
        }
      }
      finally
      {
        tftp.close();
      }
    } else {
      try (Socket tcpConnection = new Socket())
      {
        tcpConnection.connect(new InetSocketAddress(hostname, port), 3000);
        pl.taskChanged(this, "sending");
        try (BufferedOutputStream outTcp = new BufferedOutputStream(tcpConnection.getOutputStream()))
        {
          outTcp.write(buffer.toByteArray());
        }
      }
    }

    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
  }
  private List<Double> resolutions;

  @Override
  public List<Double> getResolutions()
  {
    if (resolutions == null)
    {
      //TODO: Calculate possible resolutions
      //according to mm/step
      resolutions = Arrays.asList(100d,
              200d,
              300d,
              500d,
              600d,
              1000d,
              1200d);
    }
    return resolutions;
  }
  protected double bedWidth = 300;

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
  protected double bedHeight = 210;

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
  private static final String[] settingAttributes = new String[]{
    SETTING_HOSTNAME,
    SETTING_PORT,
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    //SETTING_FLIPX,
    //SETTING_FLIPY,
    //SETTING_MMPERSTEP,
    SETTING_SUPPORTS_VENTILATION,
    SETTING_SUPPORTS_PURGE,
    SETTING_SUPPORTS_FOCUS,
    SETTING_SUPPORTS_FREQUENCY,
    SETTING_TFTP,
    SETTING_RASTER_WHITESPACE,
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
    else if (SETTING_RASTER_WHITESPACE.equals(attribute))
    {
      return this.getAddSpacePerRasterLine();
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
    else if (SETTING_FLIPX.equals(attribute))
    {
      return this.isFlipXaxis();
    }
    else if (SETTING_FLIPY.equals(attribute))
    {
      return this.isFlipYaxis();
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
    else if (SETTING_MMPERSTEP.equals(attribute))
    {
      return this.getMmPerStep();
    }
    else if (SETTING_TFTP.equals(attribute))
    {
      return this.isUseTftp();
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
    else if (SETTING_RASTER_WHITESPACE.equals(attribute))
    {
      this.setAddSpacePerRasterLine((Double) value);
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
    else if (SETTING_MMPERSTEP.equals(attribute))
    {
      this.setMmPerStep((Double) value);
    }
    else if (SETTING_TFTP.contains(attribute))
    {
      this.setUseTftp((Boolean) value);
    }
  }

  @Override
  public LaserCutter clone()
  {
    LaosCutter clone = new LaosCutter();
    clone.hostname = hostname;
    clone.port = port;
    clone.debugFilename = debugFilename;
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    clone.flipXaxis = flipXaxis;
    clone.flipYaxis = flipYaxis;
    clone.mmPerStep = mmPerStep;
    clone.useTftp = useTftp;
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    clone.supportsFrequency = supportsFrequency;
    clone.supportsPurge = supportsPurge;
    clone.supportsVentilation = supportsVentilation;
    clone.supportsFocus = supportsFocus;
    return clone;
  }

  /**
   * Calculates the smallest bounding box of all job-parts
   * and generates the laos bounding-box commands
   */
  private byte[] generateBoundingBoxCode(LaserJob job) throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, StandardCharsets.US_ASCII);
    if (job.getParts().size() > 0)
    {
      JobPart p = job.getParts().get(0);
      double xMin = Util.px2mm(p.getMinX(),p.getDPI());
      double xMax = Util.px2mm(p.getMaxX(),p.getDPI());
      double yMin = Util.px2mm(p.getMinY(),p.getDPI());
      double yMax = Util.px2mm(p.getMaxY(),p.getDPI());
      double maxDPI = p.getDPI();
      for (JobPart jp : job.getParts())
      {
        xMin = Math.min(xMin, Util.px2mm(jp.getMinX(),jp.getDPI()));
        xMax = Math.max(xMax, Util.px2mm(jp.getMaxX(),jp.getDPI()));
        yMin = Math.min(yMin, Util.px2mm(jp.getMinY(),jp.getDPI()));
        yMax = Math.max(yMax, Util.px2mm(jp.getMaxY(),jp.getDPI()));
        maxDPI = Math.max(maxDPI, jp.getDPI());
      }
      out.printf("201 %d\n", px2steps(Util.mm2px(isFlipXaxis() ? bedWidth - xMax : xMin,maxDPI), maxDPI));
      out.printf("202 %d\n", px2steps(Util.mm2px(isFlipXaxis() ? bedWidth - xMin : xMax,maxDPI), maxDPI));
      out.printf("203 %d\n", px2steps(Util.mm2px(isFlipYaxis() ? bedWidth - yMax : yMin,maxDPI), maxDPI));
      out.printf("204 %d\n", px2steps(Util.mm2px(isFlipYaxis() ? bedWidth - xMin : yMax,maxDPI), maxDPI));
    }
    return result.toByteArray();
  }

}
