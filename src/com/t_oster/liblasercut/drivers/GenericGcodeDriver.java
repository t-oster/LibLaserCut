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
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import purejavacomm.*;
import java.util.*;
import net.sf.corn.httpclient.HttpClient;
import net.sf.corn.httpclient.HttpResponse;

/**
 * This class implements a driver for a generic GRBL GCode Lasercutter.
 * It should contain all possible options and is inteded to be the superclass
 * for e.g. the SmoothieBoard and the Lasersaur driver.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class GenericGcodeDriver extends LaserCutter {

  protected static final String SETTING_HOST = "IP/Hostname";
  protected static final String SETTING_COMPORT = "COM Port";
  protected static final String SETTING_BAUDRATE = "Baud Rate (Serial)";
  protected static final String SETTING_BEDWIDTH = "Laserbed width";
  protected static final String SETTING_BEDHEIGHT = "Laserbed height";
  protected static final String SETTING_FLIP_X = "Flip X Axis";
  protected static final String SETTING_FLIP_Y = "Flip Y Axis";
  protected static final String SETTING_HTTP_UPLOAD_URL = "HTTP Upload URL";
  protected static final String SETTING_LINEEND = "Lineend (CR,LF,CRLF)";
  protected static final String SETTING_MAX_SPEED = "Max speed (in mm/min)";
  protected static final String SETTING_TRAVEL_SPEED = "Travel (non laser moves) speed (in mm/min)";
  protected static final String SETTING_PRE_JOB_GCODE = "Pre-Job GCode (comma separated)";
  protected static final String SETTING_POST_JOB_GCODE = "Post-Job GCode (comma separated)";
  protected static final String SETTING_RESOLUTIONS = "Supported DPI (comma separated)";
  protected static final String SETTING_IDENTIFICATION_STRING = "Board Identification String (startsWith)";
  protected static final String SETTING_WAIT_FOR_OK = "Wait for OK after each line (interactive mode)";
  protected static final String SETTING_INIT_DELAY = "Seconds to wait for board reset (Serial)";
  protected static final String SETTING_SERIAL_TIMEOUT = "Milliseconds to wait for response";
  
  private String lineend = "LF";

  public String getLineend()
  {
    return lineend;
  }

  public void setLineend(String lineend)
  {
    this.lineend = lineend;
  }
  
  protected String LINEEND()
  {
    return getLineend()
      .replace("LF", "\n")
      .replace("CR", "\r")
      .replace("\\r", "\r")
      .replace("\\n", "\n");
  }

  protected int baudRate = 115200;

  public int getBaudRate()
  {
    return baudRate;
  }

  public void setBaudRate(int baudRate)
  {
    this.baudRate = baudRate;
  }

  protected boolean flipXaxis = false;

  public boolean isFlipXaxis()
  {
    return flipXaxis;
  }

  public void setFlipXaxis(boolean flipXaxis)
  {
    this.flipXaxis = flipXaxis;
  }
  
  protected boolean flipYaxis = false;

  public boolean isFlipYaxis()
  {
    return flipYaxis;
  }

  public void setFlipYaxis(boolean flipYaxis)
  {
    this.flipYaxis = flipYaxis;
  }
  
  protected String httpUploadUrl = "http://10.10.10.100/upload";

  public String getHttpUploadUrl()
  {
    return httpUploadUrl;
  }

  public void setHttpUploadUrl(String httpUploadUrl)
  {
    this.httpUploadUrl = httpUploadUrl;
  }
  
  protected String supportedResolutions = "100,500,1000";

  public String getSupportedResolutions()
  {
    return supportedResolutions;
  }

  public void setSupportedResolutions(String supportedResolutions)
  {
    this.resolutions = null;
    this.supportedResolutions = supportedResolutions;
  }
  
  protected boolean waitForOKafterEachLine = true;

  public boolean isWaitForOKafterEachLine()
  {
    return waitForOKafterEachLine;
  }

  public void setWaitForOKafterEachLine(boolean waitForOKafterEachLine)
  {
    this.waitForOKafterEachLine = waitForOKafterEachLine;
  }

  public String getIdentificationLine()
  {
    return identificationLine;
  }

  public void setIdentificationLine(String identificationLine)
  {
    this.identificationLine = identificationLine;
  }
  
  protected String preJobGcode = "G21,G90";

  public String getPreJobGcode()
  {
    return preJobGcode;
  }

  public void setPreJobGcode(String preJobGcode)
  {
    this.preJobGcode = preJobGcode;
  }
  
  protected String postJobGcode = "G0 X0 Y0";

  public String getPostJobGcode()
  {
    return postJobGcode;
  }

  public void setPostJobGcode(String postJobGcode)
  {
    this.postJobGcode = postJobGcode;
  }
  
  protected int serialTimeout= 15000;

  public int getSerialTimeout()
  {
    return serialTimeout;
  }

  public void setSerialTimeout(int serialTimeout)
  {
    this.serialTimeout = serialTimeout;
  }
  
  /**
   * What is expected to be received after serial/telnet connection
   * Used e.g. for auto-detecting the serial port.
   */
  protected String identificationLine = "Grbl";
  
  @Override
  public String getModelName() {
    return "Generic GRBL GCode Driver";
  }
  
  /**
   * Time to wait before firsts reads of serial port.
   * See autoreset feature on arduinos.
   */
  protected int initDelay = 5; 

  public int getInitDelay()
  {
    return initDelay;
  }

  public void setInitDelay(int initDelay)
  {
    this.initDelay = initDelay;
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
  
  protected String comport = "auto";

  public String getComport()
  {
    return comport;
  }

  public void setComport(String comport)
  {
    this.comport = comport;
  }
  
  protected double max_speed = 20*60;

  public double getMax_speed()
  {
    return max_speed;
  }

  public void setMax_speed(double max_speed)
  {
    this.max_speed = max_speed;
  }
  
  protected double travel_speed = 60*60;

  public double getTravel_speed()
  {
    return travel_speed;
  }

  public void setTravel_speed(double travel_speed)
  {
    this.travel_speed = travel_speed;
  }
  
  @Override
  /**
   * We do not support Frequency atm, so we return power,speed and focus
   */
  public LaserProperty getLaserPropertyForVectorPart() {
    return new FloatPowerSpeedFocusProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRaster3dPart()
  {
    return new FloatPowerSpeedFocusProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
  {
    return new FloatPowerSpeedFocusProperty();
  }

  protected void writeVectorGCode(VectorPart vp, double resolution) throws UnsupportedEncodingException, IOException {
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
          FloatPowerSpeedFocusProperty p = (FloatPowerSpeedFocusProperty) cmd.getProperty();
          setPower(p.getPower());
          setSpeed(p.getSpeed());
          setFocus(out, p.getFocus(), resolution);
          break;
      }
    }
  }
  private double currentPower = -1;
  private double currentSpeed = -1;
  private double nextPower = -1;
  private double nextSpeed = -1;
  private double currentFocus = 0;

  protected void setSpeed(double speedInPercent) {
    nextSpeed = speedInPercent;
  }

  protected void setPower(double powerInPercent) {
    nextPower = powerInPercent;
  }
  
  protected void setFocus(PrintStream out, double focus, double resolution) throws IOException {
    if (currentFocus != focus)
    {
      sendLine("G0 Z%f", Util.px2mm(focus, resolution));
      currentFocus = focus;
    }
  }

  protected void move(PrintStream out, double x, double y, double resolution) throws IOException {
    x = isFlipXaxis() ? getBedWidth() - Util.px2mm(x, resolution) : Util.px2mm(x, resolution);
    y = isFlipYaxis() ? getBedHeight() - Util.px2mm(y, resolution) : Util.px2mm(y, resolution);
    currentSpeed = getTravel_speed();
    sendLine("G0 X%f Y%f F%d", x, y, (int) (travel_speed));
  }

  protected void line(PrintStream out, double x, double y, double resolution) throws IOException {
    x = isFlipXaxis() ? getBedWidth() - Util.px2mm(x, resolution) : Util.px2mm(x, resolution);
    y = isFlipYaxis() ? getBedHeight() - Util.px2mm(y, resolution) : Util.px2mm(y, resolution);
    String append = "";
    if (nextPower != currentPower)
    {
      append += String.format(Locale.US, " S%f", nextPower/100.0);
      currentPower = nextPower;
    }
    if (nextSpeed != currentSpeed)
    {
      append += String.format(Locale.US, " F%d", (int) (max_speed*nextSpeed/100.0));
      currentSpeed = nextSpeed;
    }
    sendLine("G1 X%f Y%f"+append, x, y);
  }

  private void writeInitializationCode() throws IOException {
    if (preJobGcode != null)
    {
      for (String line : preJobGcode.split(","))
      {
        sendLine(line);
      }
    }
  }

  private void writeShutdownCode() throws IOException {
    if (postJobGcode != null)
    {
      for (String line : postJobGcode.split(","))
      {
        sendLine(line);
      }
    }
  }

  private BufferedReader in;
  private PrintStream out;
  private Socket socket;
  private CommPort port;
  private CommPortIdentifier portIdentifier;
  
  protected void sendLine(String text, Object... parameters) throws IOException
  {
    out.format(text+LINEEND(), parameters);
    //TODO: Remove
    System.out.format("> "+text+LINEEND(), parameters);
    out.flush();
    if (isWaitForOKafterEachLine())
    {
      String line = waitForLine();
      if (!"ok".equals(line))
      {
        throw new IOException("Lasercutter did not respond 'ok', but '"+line+"'instead.");
      }
    }
  }
  
  protected void http_upload(URI url, String data, String filename) throws IOException
  {
    HttpClient client = new HttpClient(url);
    client.putAdditionalRequestProperty("X-Filename", filename);
    HttpResponse response = client.sendData(HttpClient.HTTP_METHOD.POST, data);
    if (response == null || response.hasError())
    {
      throw new IOException("Error during POST Request");
    }
    System.out.println("Response: "+response.toString());//TODO: Remove
  }
  
  protected String waitForLine() throws IOException
  {
    String line = "";
    while ("".equals(line))
    {//skip empty lines
      line = in.readLine();
    }
    System.out.println("< "+line);//TODO: remove
    return line;
  }
  
  /**
   * Waits for the Identification line and returns null if it's allright
   * Otherwise it returns the wrong line
   * @return
   * @throws IOException 
   */
  protected String waitForIdentificationLine() throws IOException
  {
    if (getIdentificationLine() != null && getIdentificationLine().length() > 0)
    {
      String line = "";
      for (int trials = 3; trials > 0; trials--)
      {
        line = waitForLine();
        if (line.startsWith(getIdentificationLine()))
        {
          return null;
        }
      }
      return line;
    }
    return null;
  }
  
  @SuppressWarnings("empty-statement")
  protected String connect_serial(CommPortIdentifier i, ProgressListener pl) throws PortInUseException, IOException, UnsupportedCommOperationException
  {
    pl.taskChanged(this, "opening '"+i.getName()+"'");
    if (i.getPortType() == CommPortIdentifier.PORT_SERIAL)
    {
      try
      {
        try
        {
          in.close();
          out.close();
          port.close();
        }
        catch (Exception e)
        { } //do nothing
       
       
        port = i.open("VisiCut", 1000);
        try
        {
          port.enableReceiveTimeout(getSerialTimeout());
        }
        catch (UnsupportedCommOperationException e)
        {
          System.err.println("Serial timeout not supported. Driver may hang if device does not respond properly.");
        }
        if (this.getBaudRate() > 0 && port instanceof SerialPort)
        {
          SerialPort sp = (SerialPort) port;
          sp.setSerialPortParams(getBaudRate(), 8, 1, 0);
        }
        out = new PrintStream(port.getOutputStream(), true, "US-ASCII");
        in = new BufferedReader(new InputStreamReader(port.getInputStream()));
        // Wait 5 seconds since GRBL is long to wake up..
        for (int rest = getInitDelay(); rest > 0; rest--) {
          pl.taskChanged(this, String.format("Waiting %ds", rest));
          try
          {
            Thread.sleep(1000);
          }
          catch(InterruptedException ex)
          {
            Thread.currentThread().interrupt();
          }
        }
        if (waitForIdentificationLine() != null)
        {
          in.close();
          out.close();
          port.close();
          return "Does not seem to be a "+getModelName()+" on "+i.getName();
        }
        portIdentifier = i;
        return null;
      }
      catch (PortInUseException e)
      {
        return "Port in use "+i.getName();
      }
      catch (IOException e)
      {
        return "IO Error "+i.getName();
      }
      catch (PureJavaIllegalStateException e)
      {
        return "Could not open "+i.getName();
      }
    }
    else
    {
      return "Not a serial Port "+comport;
    }
  }
  /**
   * Used to buffer the file before uploading via http
   */
  private ByteArrayOutputStream outputBuffer;
  protected void connect(ProgressListener pl) throws IOException, PortInUseException, NoSuchPortException, UnsupportedCommOperationException
  {
    outputBuffer = null;
    if (getHost() != null && getHost().length() > 0)
    {
      socket = new Socket();
      socket.connect(new InetSocketAddress(getHost(), 23), 1000);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintStream(socket.getOutputStream(), true, "US-ASCII");
      String line = waitForIdentificationLine();
      if (line != null)
      {
        in.close();
        out.close();
        throw new IOException("Wrong identification Line: "+line+"\n instead of "+getIdentificationLine());
      }
    }
    else if (getComport() != null && !getComport().equals(""))
    {
      String error = "No serial port found";
      if (portIdentifier == null && !getComport().equals("auto"))
      {
        portIdentifier = CommPortIdentifier.getPortIdentifier(getComport());
      }
      
      if (portIdentifier != null)
      {//use port identifier we had last time
        error = connect_serial(portIdentifier, pl);
      }
      else
      {
        Enumeration<CommPortIdentifier> e = CommPortIdentifier.getPortIdentifiers();
        while (e.hasMoreElements())
        {
          CommPortIdentifier i = e.nextElement();
          if (i.getPortType() == CommPortIdentifier.PORT_SERIAL)
          {
            error = connect_serial(i, pl);
            if (error == null)
            {
              break;
            }
          }
        }
      }
      if (error != null)
      {
        throw new IOException(error);
      }
    }
    else if (getHttpUploadUrl() != null && getHttpUploadUrl().length() > 0)
    {
      outputBuffer = new ByteArrayOutputStream();
      out = new PrintStream(outputBuffer);
      setWaitForOKafterEachLine(false);
      in = null;
    }
    else
    {
      throw new IOException("Either COM Port or IP/Host has to be set");
    }
  }
  
  protected void disconnect(String jobname) throws IOException, URISyntaxException
  {
    if (outputBuffer != null)
    {
      out.close();
      http_upload(new URI(getHttpUploadUrl()), outputBuffer.toString("UTF-8"), jobname);
    }
    else
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

  }
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    this.currentPower = -1;
    this.currentSpeed = -1;

    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting...");
    connect(pl);
    pl.taskChanged(this, "sending");
    writeInitializationCode();
    pl.progressChanged(this, 20);
    int i = 0;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterPart)
      {
        RasterPart rp = (RasterPart) p;
        LaserProperty black = rp.getLaserProperty();
        LaserProperty white = black.clone();
        white.setProperty("power", 0.0f);
        p = convertRasterToVectorPart((RasterPart) p, black, white,  p.getDPI(), false);
      }
      if (p instanceof VectorPart)
      {
        //TODO: in direct mode use progress listener to indicate progress 
        //of individual job
        writeVectorGCode((VectorPart) p, p.getDPI());
      }
      i++;
      pl.progressChanged(this, 20 + (int) (i*(double) 60/max));
    }
    writeShutdownCode();
    disconnect(job.getName()+".gcode");
    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
  }
  private List<Double> resolutions;

  @Override
  public List<Double> getResolutions() {
    if (resolutions == null) {
      resolutions = new LinkedList<Double>();
      for (String s : getSupportedResolutions().split(","))
      {
        resolutions.add(Double.parseDouble(s));
      }
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
    SETTING_BAUDRATE,
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_COMPORT,
    SETTING_FLIP_X,
    SETTING_FLIP_Y,
    SETTING_HOST,
    SETTING_HTTP_UPLOAD_URL,
    SETTING_IDENTIFICATION_STRING,
    SETTING_INIT_DELAY,
    SETTING_LINEEND,
    SETTING_MAX_SPEED,
    SETTING_TRAVEL_SPEED,
    SETTING_PRE_JOB_GCODE,
    SETTING_POST_JOB_GCODE,
    SETTING_RESOLUTIONS,
    SETTING_WAIT_FOR_OK,
    SETTING_SERIAL_TIMEOUT
  };

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOST.equals(attribute)) {
      return this.getHost();
    } else if (SETTING_BAUDRATE.equals(attribute)) {
      return this.getBaudRate();
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.getBedHeight();
    } else if (SETTING_COMPORT.equals(attribute)) {
      return this.getComport();
    } else if (SETTING_FLIP_X.equals(attribute)) {
      return this.isFlipXaxis();
    } else if (SETTING_FLIP_Y.equals(attribute)) {
      return this.isFlipYaxis();
    } else if (SETTING_HOST.equals(attribute)) {
      return this.getHost();
    } else if (SETTING_HTTP_UPLOAD_URL.equals(attribute)) {
      return this.getHttpUploadUrl();
    } else if (SETTING_IDENTIFICATION_STRING.equals(attribute)) {
      return this.getIdentificationLine();
    } else if (SETTING_INIT_DELAY.equals(attribute)) {
      return this.getInitDelay();
    } else if (SETTING_LINEEND.equals(attribute)) {
      return this.getLineend();
    } else if (SETTING_MAX_SPEED.equals(attribute)) {
      return this.getMax_speed();
    } else if (SETTING_TRAVEL_SPEED.equals(attribute)) {
      return this.getTravel_speed();
    } else if (SETTING_PRE_JOB_GCODE.equals(attribute)) {
      return this.getPreJobGcode();
    } else if (SETTING_POST_JOB_GCODE.equals(attribute)) {
      return this.getPostJobGcode();
    } else if (SETTING_RESOLUTIONS.equals(attribute)) {
      return this.getSupportedResolutions();
    } else if (SETTING_WAIT_FOR_OK.equals(attribute)) {
      return this.isWaitForOKafterEachLine();
    } else if (SETTING_SERIAL_TIMEOUT.equals(attribute)) {
      return this.getSerialTimeout();
    }
    
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOST.equals(attribute)) {
      this.setHost((String) value);
    } else if (SETTING_BAUDRATE.equals(attribute)) {
      this.setBaudRate((Integer) value);
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight((Double) value);
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComport((String) value);
    } else if (SETTING_FLIP_X.equals(attribute)) {
      this.setFlipXaxis((Boolean) value);
    } else if (SETTING_FLIP_Y.equals(attribute)) {
      this.setFlipYaxis((Boolean) value);
    } else if (SETTING_HOST.equals(attribute)) {
      this.setHost((String) value);
    } else if (SETTING_HTTP_UPLOAD_URL.equals(attribute)) {
      this.setHttpUploadUrl((String) value);
    } else if (SETTING_IDENTIFICATION_STRING.equals(attribute)) {
      this.setIdentificationLine((String) value);
    } else if (SETTING_INIT_DELAY.equals(attribute)) {
      this.setInitDelay((Integer) value);
    } else if (SETTING_LINEEND.equals(attribute)) {
      this.setLineend((String) value);
    } else if (SETTING_MAX_SPEED.equals(attribute)) {
      this.setMax_speed((Double) value);
    } else if (SETTING_TRAVEL_SPEED.equals(attribute)) {
      this.setTravel_speed((Double) value);
    } else if (SETTING_PRE_JOB_GCODE.equals(attribute)) {
      this.setPreJobGcode((String) value);
    } else if (SETTING_POST_JOB_GCODE.equals(attribute)) {
      this.setPostJobGcode((String) value);
    } else if (SETTING_RESOLUTIONS.equals(attribute)) {
      this.setSupportedResolutions((String) value);
    } else if (SETTING_WAIT_FOR_OK.equals(attribute)) {
      this.setWaitForOKafterEachLine((Boolean) value);
    } else if (SETTING_SERIAL_TIMEOUT.equals(attribute)) {
      this.setSerialTimeout((Integer) value);
    }
  }

  @Override
  public GenericGcodeDriver clone() {
    GenericGcodeDriver clone = new GenericGcodeDriver();
    clone.copyProperties(this);
    return clone;
  }
}
