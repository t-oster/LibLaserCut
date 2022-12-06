/**
 * This file is part of LibLaserCut.
 *
 * Copyright (c) 2018 - 2022 Klaus Kämpf <kkaempf@gmail.com>
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

/* *
 * Support for "Ruida" lasers (Ruida controller).
 */

package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.*;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import de.thomas_oster.liblasercut.VectorCommand.CmdType;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Math;
import java.net.BindException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/* for network i/o */
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;

/* for serial/usb i/o */
import java.util.concurrent.TimeUnit;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.PureJavaIllegalStateException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;

public class Ruida extends LaserCutter
{
  private static final int MINFOCUS = -500; //Minimal focus value (not mm)
  private static final int MAXFOCUS = 500; //Maximal focus value (not mm)
  private static final int MAXPOWER = 80;

  protected static final String SETTING_HOST = "IP/Hostname";
  protected static final String SETTING_COMPORT = "USB device";
  protected static final String SETTING_SERIAL_TIMEOUT = "Milliseconds to wait for response";
  protected static final String SETTING_FILE_EXPORT_PATH = "Path to save exported code";
  protected static final String SETTING_UPLOAD_METHOD = "Upload method";
  protected static final String UPLOAD_METHOD_FILE = "File";
  protected static final String UPLOAD_METHOD_IP = "IP";
  protected static final String UPLOAD_METHOD_SERIAL = "USB";

  protected static final String SETTING_MAX_VECTOR_CUT_SPEED = "Max vector cutting speed (mm/s)";
  protected static final String SETTING_MAX_VECTOR_MOVE_SPEED = "Max vector move speed (mm/s)";
  protected static final String SETTING_MIN_POWER = "Min laser power (%)";
  protected static final String SETTING_MAX_POWER = "Max laser power (%)";
  protected static final String SETTING_BED_WIDTH = "Bed width (mm)";
  protected static final String SETTING_BED_HEIGHT = "Bed height (mm)";
  protected static final String SETTING_USE_BIDIRECTIONAL_RASTERING = "Use bidirectional rastering";
  protected static final Locale FORMAT_LOCALE = Locale.US;

  protected static final String[] uploadMethodList = {UPLOAD_METHOD_FILE, UPLOAD_METHOD_IP, UPLOAD_METHOD_SERIAL};

  private transient ByteStream stream;
  private transient InputStreamReader in;
  private transient PrintStream out;
  private transient CommPort port;
  private transient CommPortIdentifier portIdentifier;

  protected int baudRate = 921600;

  public int getBaudRate()
  {
    return baudRate;
  }

  public void setBaudRate(int baudRate)
  {
    this.baudRate = baudRate;
  }

  protected String host = "192.168.1.1";

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

  /* -----------------------------------------------------------------------*/

  public Ruida()
  {
  }

  /**
   * Copies the current instance with all config settings, because
   * it is used for save- and restoring
   * @return clone
   */
  @Override
  public Ruida clone() {
    Ruida clone = new Ruida();
    clone.copyProperties(this);
    return clone;
  }

  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new FloatMinMaxPowerSpeedFrequencyProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
  {
    return new FloatMinMaxPowerSpeedFrequencyProperty();
  }

  @Override
  public LaserProperty getLaserPropertyForRaster3dPart()
  {
    return new FloatMinMaxPowerSpeedFrequencyProperty();
  }

  @Override
  public boolean canEstimateJobDuration()
  {
    return false;
  }

  /**
   * When rastering, whether to always cut from left to right, or to cut in both
   * directions? (i.e. use the return stroke to raster as well)
   */
  protected boolean useBidirectionalRastering = true;

  public boolean getUseBidirectionalRastering()
  {
    return useBidirectionalRastering;
  }

  public void setUseBidirectionalRastering(boolean useBidirectionalRastering)
  {
    this.useBidirectionalRastering = useBidirectionalRastering;
  }

  private void find_and_write_bounding_box(LaserJob job) throws IOException
  {
    double minX = 0.0;
    double minY = 0.0;
    double maxX = 0.0;
    double maxY = 0.0;
    boolean first = true;
    /* compute bounding box */
    for (JobPart p : job.getParts())
    {
      double min_x = Util.px2mm(p.getMinX(), p.getDPI());
      double min_y = Util.px2mm(p.getMinY(), p.getDPI());
      double max_x = Util.px2mm(p.getMaxX(), p.getDPI());
      double max_y = Util.px2mm(p.getMaxY(), p.getDPI());
      if (first) {
        minX = min_x;
        maxX = max_x;
        minY = min_y;
        maxY = max_y;
        first = false;
      }
      else {
        if (min_x < minX) { minX = min_x; }
        if (max_x > maxX) { maxX = max_x; }
        if (min_y < minY) { minY = min_y; }
        if (max_y > maxY) { maxY = max_y; }
      }
    }

    /* bounding box */
    stream.hex("E703").absoluteMM(minX).absoluteMM(minY);
    stream.hex("E707").absoluteMM(maxX).absoluteMM(maxY);
    stream.hex("E750").absoluteMM(minX).absoluteMM(minY);
    stream.hex("E751").absoluteMM(maxX).absoluteMM(maxY);
    stream.hex("E7040001000100000000000000000000");
    stream.hex("E70500");
  }


  private transient double last_x = 0.0;
  private transient double last_y = 0.0;
  private transient int vector_count = 0;
  private transient long travel_distance = 0;

  private void vector(double x, double y, double dpi, boolean as_cut) throws IOException
  {
    double x_mm = Util.px2mm(x, dpi);
    double y_mm = Util.px2mm(y, dpi);
    boolean as_absolute;

    /* compute distance to last known position */
    double dx = x_mm - last_x;
    double dy = y_mm - last_y;

    if ((dx == 0.0) && (dy == 0.0)) {
      return;
    }
    if (vector_count % 10 == 0) {                  /* enforce absolute every 10 vectors */
      as_absolute = true;
    }
    else {
      as_absolute = Math.max(Math.abs(dx), Math.abs(dy)) > 8.191;
    }
    vector_count += 1;

    long distance = (long)Math.sqrt(dx*dx + dy*dy);
    travel_distance += distance;

    // estimate the new real position
    last_x = x_mm;
    last_y = y_mm;

    if (as_absolute) {
      if (as_cut) {
        stream.hex("A8").absoluteMM(x_mm).absoluteMM(y_mm);  // cut to x,y
      }
      else { // as_move
        stream.hex("88").absoluteMM(x_mm).absoluteMM(y_mm);  // move to x,y
      }
    }
    else { // relative
      if (dx == 0.0) {
        if (as_cut) {
          stream.hex("AB").relativeSignedMM(dy);  // cut vertical to y
        }
        else { // as_move
          stream.hex("8B").relativeSignedMM(dy);  // move vertical to y
        }
      }
      else if (dy == 0.0) {
        if (as_cut) {
          stream.hex("AA").relativeSignedMM(dx);  // cut horizontal to y
        }
        else { // as_move
          stream.hex("8A").relativeSignedMM(dx);  // move horizontal to y
        }
      }
      else {
        if (as_cut) {
          stream.hex("A9").relativeSignedMM(dx).relativeSignedMM(dy); // cut relative to x, y
        }
        else { // as_move
          stream.hex("89").relativeSignedMM(dx).relativeSignedMM(dy); // move relative to x, y
        }
      }
    }
  }

  private transient float currentMinPower = 0.0f;
  private transient float currentMaxPower = 0.0f;
  private transient float currentSpeed = 0;

  private float cmd_absoluteMM(String cmd, float old_val, float new_val) throws IOException
  {
    if (old_val != new_val) {
      stream.hex(cmd).absoluteMM((int)new_val);
    }
    return new_val;
  }

  private float cmd_percent(String cmd, float old_val, float new_val) throws IOException
  {
    if (old_val != new_val) {
      stream.hex(cmd).percent((int)new_val);
    }
    return new_val;
  }

  private float cmd_layer_absoluteMM(String cmd, int layer, float old_val, float new_val) throws IOException
  {
    if (old_val != new_val) {
      stream.hex(cmd).byteint(layer).absoluteMM((int)new_val);
    }
    return new_val;
  }

  private float cmd_layer_percent(String cmd, int layer, float old_val, float new_val) throws IOException
  {
    if (old_val != new_val) {
      stream.hex(cmd).byteint(layer).percent((int)new_val);
    }
    return new_val;
  }

  protected String connectSerial(CommPortIdentifier i, ProgressListener pl) throws PortInUseException, IOException, UnsupportedCommOperationException
  {
    pl.taskChanged(this, "opening '"+i.getName()+"'");
    if (i.getPortType() == CommPortIdentifier.PORT_SERIAL)
    {
      try
      {
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
          sp.setDTR(true);
        }
        out = new PrintStream(port.getOutputStream());
        in = new InputStreamReader(port.getInputStream(), Charset.defaultCharset());

        portIdentifier = i;
        pl.taskChanged(this, "Connected");
        return null;
      }
      catch (PortInUseException e)
      {
        try { disconnect(); } catch (Exception ex) { System.out.println(ex.getMessage()); }
        return "Port in use: "+i.getName();
      }
      catch (IOException e)
      {
        try { disconnect(); } catch (Exception ex) { System.out.println(ex.getMessage()); }
        return "IO Error from "+i.getName()+": "+e.getMessage();
      }
      catch (PureJavaIllegalStateException e)
      {
        try { disconnect(); } catch (Exception ex) { System.out.println(ex.getMessage()); }
        return "Could not open "+i.getName()+": "+e.getMessage();
      }
    }
    else
    {
      return "Not a serial Port "+comport;
    }
  }

  protected void connect(ProgressListener pl, String jobName) throws IOException, PortInUseException, NoSuchPortException, UnsupportedCommOperationException
  {
    if (UPLOAD_METHOD_IP.equals(uploadMethod))
    {
      if (getHost() == null || getHost().equals(""))
      {
        throw new IOException("IP/Hostname must be set to upload via IP method");
      }
      out = new PrintStream(new UdpStream(getHost()));
      in = null;
    }
    else if (UPLOAD_METHOD_SERIAL.equals(uploadMethod))
    {
      String error = "No serial port found";
      if (portIdentifier == null && !getComport().equals("auto") && !getComport().equals(""))
      {
        try {
          portIdentifier = CommPortIdentifier.getPortIdentifier(getComport());
        }
        catch (NoSuchPortException e) {
          throw new IOException("No such port: "+getComport());
        }
      }

      if (portIdentifier != null)
      {//use port identifier we had last time
        error = connectSerial(portIdentifier, pl);
      }
      else
      {
        Enumeration<CommPortIdentifier> e = CommPortIdentifier.getPortIdentifiers();
        while (e.hasMoreElements())
        {
          CommPortIdentifier i = e.nextElement();
          if (i.getPortType() == CommPortIdentifier.PORT_SERIAL)
          {
            error = connectSerial(i, pl);
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
    else if (UPLOAD_METHOD_FILE.equals(uploadMethod))
    {
      if (getExportPath() == null || getExportPath().equals(""))
      {
        throw new IOException("Export Path must be set to upload via File method.");
      }
      File file = new File(getExportPath(), jobName+".rd"); // LibLaserCut#183 says it must be .rd
      out = new PrintStream(new FileOutputStream(file));
      in = null;
    }
    else
    {
      throw new IOException("Upload Method must be set");
    }
  }

  protected void disconnect() throws IOException, URISyntaxException
  {
    if (in != null)
    {
      in.close();
    }
    out.close();
    if (this.port != null)
    {
      this.port.close();
      this.port = null;
    }
  }

  /**
   * It is called whenever VisiCut wants the driver to send a job to the lasercutter.
   * @param job This is an LaserJob object, containing all information on the job, which is to be sent
   * @param pl Use this object to inform VisiCut about the progress of your sending action.
   * @param warnings If you there are warnings for the user, you can add them to this list, so they can be displayed by VisiCut
   * @throws IllegalJobException Throw this exception, when the job is not suitable for the current machine
   * @throws Exception in all other error cases
   */
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    pl.progressChanged(this, 0);
    this.currentMinPower = -1;
    this.currentMaxPower = -1;
    this.currentSpeed = -1;

    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting...");
    connect(pl, job.getName());
    pl.taskChanged(this, "sending");
    try {
      writeJobCode(job, pl);
    }
    catch (IOException e) {
      pl.taskChanged(this, "disconnecting");
      disconnect();
      throw e;
    }
    disconnect();
    pl.taskChanged(this, "sent.");
    pl.progressChanged(this, 100);
  }

  public void writeJobCode(LaserJob job, ProgressListener pl) throws IOException {
    try {
      stream = new ByteStream(out, (byte)0x88); // 0x11, 0x38
      if (UPLOAD_METHOD_SERIAL.equals(uploadMethod)) {
        char[] inbuf = new char[16];
        stream.hex("DA000004"); // identify
        in.read(inbuf);
      }
    }
    catch (Exception e) {
      pl.taskChanged(this, "Fail: " + e.getMessage());
      throw e;
    }

    /* upload follows */
    stream.hex("D812");
    /* magic 88 */
    stream.hex("F0");
    /* PrepFilename, SetFilename, <filename>, 00 */
    stream.hex("E802").hex("E701").string(job.getTitle()).hex("00");
    /* start */
    stream.hex("F10200");
    /* light red */
    stream.hex("D800");
    /* feeding x, y */
    stream.hex("E706").absoluteMM(job.getStartX()).absoluteMM(job.getStartY());

    find_and_write_bounding_box(job);

    int part_number = 0;

    // layer count
    stream.hex("CA22").byteint(job.getParts().size() - 1);

    boolean first_prop = true;
    boolean first_vector = true;

    for (JobPart p : job.getParts())
    {
      float focus;

      if ((p instanceof RasterPart) || (p instanceof Raster3dPart))
      {
        p = convertRasterizableToVectorPart((RasterizableJobPart)p, job, this.useBidirectionalRastering, true, true);
      }
      /* FALLTHRU */
      if (p instanceof VectorPart)
      {
        double top_left_x = Util.px2mm(p.getMinX(), p.getDPI());
        double top_left_y = Util.px2mm(p.getMinY(), p.getDPI());
        double bottom_right_x = Util.px2mm(p.getMaxX(), p.getDPI());
        double bottom_right_y = Util.px2mm(p.getMaxY(), p.getDPI());
        /* write dimensions */
        stream.hex("E752").byteint(part_number).absoluteMM(top_left_x).absoluteMM(top_left_y);
        stream.hex("E753").byteint(part_number).absoluteMM(bottom_right_x).absoluteMM(bottom_right_y);
        stream.hex("E761").byteint(part_number).absoluteMM(top_left_x).absoluteMM(top_left_y);
        stream.hex("E762").byteint(part_number).absoluteMM(bottom_right_x).absoluteMM(bottom_right_y);  

        VectorPart vp = (VectorPart) p;
          
        //iterate over command list
        for (VectorCommand cmd : vp.getCommandList())
        {
          //There are three types of commands: MOVETO, LINETO and SETPROPERTY
          switch (cmd.getType())
          {
            case LINETO:
            case MOVETO:
            {
              /**
               * Move the laserhead (laser on) from the current position to the x/y position of this command.
               */
              if (first_vector) {
                first_vector = false;

                stream.hex("ca0100");
                stream.hex("ca02").byteint(part_number); // start_layer
                stream.hex("ca0113"); // blow on
                stream.hex("c902").longint((int)currentSpeed);
                // power for laser #1
                stream.hex("c601").percent((int)currentMinPower);
                stream.hex("c602").percent((int)currentMaxPower);

                /* start vector mode */
                stream.hex("ca030f");
                stream.hex("ca1000");
              }
              vector(cmd.getX(), cmd.getY(), p.getDPI(), cmd.getType() == CmdType.LINETO);
              break;
            }
            case SETPROPERTY:
            {
              LaserProperty pr = cmd.getProperty();
              FloatMinMaxPowerSpeedFrequencyProperty prop = (FloatMinMaxPowerSpeedFrequencyProperty) pr;
              if (first_prop) {
                first_prop = false;
                currentMinPower = cmd_layer_percent("c631", part_number, currentMinPower, prop.getMinPower());
                currentMaxPower = cmd_layer_percent("c632", part_number, currentMaxPower, prop.getPower());
                currentSpeed = cmd_layer_absoluteMM("c904", part_number, currentSpeed, prop.getSpeed());
                // focus - n/a
                // frequency
                stream.hex("c660").byteint(part_number).hex("00").longint(prop.getFrequency());
                // color - red for now
                long color = (0 << 16) + (0 << 8) + 100;; //(normalizeColor(this.blue) << 16) + (normalizeColor(this.green) << 8) + normalizeColor(this.red);
                stream.hex("ca06").byteint(part_number).longint(color);
                // CA 41
                stream.hex("ca41").byteint(part_number).byteint(0);
              }
              else {
                currentMinPower = cmd_percent("c601", currentMinPower, prop.getMinPower());
                currentMaxPower = cmd_percent("c602", currentMaxPower, prop.getPower());
                currentSpeed = cmd_absoluteMM("c902", currentSpeed, prop.getSpeed());
              }
              break;
            }
            default:
            {
              System.out.println("*** Ruida unknown vector part(" + cmd.getType() + ")");
            }
          }
        }
      }
    }

    /* work interval */
    stream.hex("DA010620").longint(travel_distance).longint(travel_distance);
    /* finish */
    stream.hex("EB");
    /* stop */
    stream.hex("E700");
    /* eof */
    stream.hex("D7");
//    pl.progressChanged(this, 100);
  } /* sendJob */

  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws IllegalJobException, Exception {
    this.currentMinPower = -1;
    this.currentMaxPower = -1;
    this.currentSpeed = -1;

    checkJob(job);
    try (PrintStream ps = new PrintStream(fileOutputStream))
    {
      this.out = ps;
      writeJobCode(job, new ProgressListenerDummy());
    }
  }

  /**
   * Returns a list of all supported resolutions (in DPI)
   * @return List of Double
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]{100.0,200.0,500.0,1000.0});
  }

  protected Double BedWidth = 900.0;
  /**
   * Returns the width of the laser-bed in mm.
   * @return bed width
   */
  @Override
  public double getBedWidth()
  {
    return BedWidth;
  }

  /**
   * Set the value of BedWidth
   *
   * @param BedWidth new value of BedWidth
   */
  public void setBedWidth(Double BedWidth)
  {
    this.BedWidth = BedWidth;
  }

  protected Double BedHeight = 600.0;
  /**
   * Returns the height of the laser-bed in mm.
   * @return bed height
   */
  @Override
  public double getBedHeight()
  {
    return BedHeight;
  }

  /**
   * Set the value of BedHeigth
   *
   * @param BedHeight new value of BedHeight
   */
  public void setBedHeigth(Double BedHeight)
  {
    this.BedHeight = BedHeight;
  }

  /**
   * Get the name for this driver.
   *
   * @return the name for this driver
   */
  @Override
  public String getModelName()
  {
    return "Ruida";
  }

  protected Integer LaserPowerMin = 0;

  /**
   * Get the value of LaserPowerMin
   *
   * @return the value of LaserPowerMin
   */
  public Integer getLaserPowerMin()
  {
    return LaserPowerMin;
  }

  /**
   * Set the value of LaserPowerMin
   *
   * @param LaserPowerMin new value of LaserPowerMin
   */
  public void setLaserPowerMin(Integer LaserPowerMin)
  {
    this.LaserPowerMin = LaserPowerMin;
  }

  protected Integer LaserPowerMax = MAXPOWER;

  /**
   * Get the value of LaserPowerMax
   *
   * @return the value of LaserPowerMax
   */
  public Integer getLaserPowerMax()
  {
    return LaserPowerMax;
  }

  /**
   * Set the value of LaserPowerMax
   *
   * @param LaserPowerMax new value of LaserPowerMax
   */
  public void setLaserPowerMax(Integer LaserPowerMax)
  {
    if (LaserPowerMax > MAXPOWER) {
      LaserPowerMax = MAXPOWER;
    }
    this.LaserPowerMax = LaserPowerMax;
  }

  protected Integer MaxVectorCutSpeed = 1000;

  /**
   * Get the value of MaxVectorCutSpeed
   *
   * @return the value of Maximum Vector Cut Speed
   */
  public Integer getMaxVectorCutSpeed()
  {
    return MaxVectorCutSpeed;
  }

  /**
   * Set the value of MaxVectorCutSpeed
   *
   * @param MaxVectorCutSpeed new value of MaxVectorCutSpeed
   */
  public void setMaxVectorCutSpeed(Integer MaxVectorCutSpeed)
  {
    this.MaxVectorCutSpeed = MaxVectorCutSpeed;
  }

  protected Integer MaxVectorMoveSpeed = 1000;

  /**
   * Get the value of MaxVectorMoveSpeed
   *
   * @return the value of Vector Moving Speed
   */
  public Integer getMaxVectorMoveSpeed()
  {
    return MaxVectorMoveSpeed;
  }

  /**
   * Set the value of MaxVectorMoveSpeed
   *
   * @param MaxVectorMoveSpeed new value of MaxVectorMoveSpeed
   */
  public void setMaxVectorMoveSpeed(Integer MaxVectorMoveSpeed)
  {
    this.MaxVectorMoveSpeed = MaxVectorMoveSpeed;
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

  private String exportPath = "";

  public void setExportPath(String path)
  {
    this.exportPath = path;
  }

  public String getExportPath()
  {
    return exportPath;
  }

  protected String uploadMethod = "";

  public void setUploadMethod(Object method)
  {
    this.uploadMethod = String.valueOf(method);
  }

  public OptionSelector getUploadMethod()
  {
    if (uploadMethod == null || uploadMethod.length() == 0)
    {
      // Determine using original connect() logic
      if (getHost() != null && getHost().length() > 0)
      {
        uploadMethod = UPLOAD_METHOD_IP;
      }
      else if (getComport() != null && !getComport().equals(""))
      {
        uploadMethod = UPLOAD_METHOD_SERIAL;
      }
      else if (getExportPath() != null && getExportPath().length() > 0)
      {
        uploadMethod = UPLOAD_METHOD_FILE;
      }
    }

    return new OptionSelector(uploadMethodList, uploadMethod);
  }


  /* ---------------------------------------------------------------- */
  /* device properties  */

  private static String[] settingAttributes = new String[]  {
    SETTING_UPLOAD_METHOD,
    SETTING_HOST,
    SETTING_COMPORT,
    SETTING_SERIAL_TIMEOUT,
    SETTING_FILE_EXPORT_PATH,
    SETTING_MAX_VECTOR_CUT_SPEED,
    SETTING_MAX_VECTOR_MOVE_SPEED,
    SETTING_MIN_POWER,
    SETTING_MAX_POWER,
    SETTING_BED_WIDTH,
    SETTING_BED_HEIGHT,
    SETTING_USE_BIDIRECTIONAL_RASTERING
  };

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOST.equals(attribute)) {
      return this.getHost();
    } else if (SETTING_COMPORT.equals(attribute)) {
      return this.getComport();
    } else if (SETTING_SERIAL_TIMEOUT.equals(attribute)) {
      return this.getSerialTimeout();
    } else if (SETTING_FILE_EXPORT_PATH.equals(attribute)) {
      return this.getExportPath();
    } else if (SETTING_UPLOAD_METHOD.equals(attribute)) {
      return this.getUploadMethod();
    } else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)) {
      return this.getMaxVectorCutSpeed();
    } else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)) {
      return this.getMaxVectorMoveSpeed();
    } else if (SETTING_MIN_POWER.equals(attribute)) {
      return this.getLaserPowerMin();
    } else if (SETTING_MAX_POWER.equals(attribute)) {
      return this.getLaserPowerMax();
    } else if (SETTING_BED_WIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BED_HEIGHT.equals(attribute)) {
      return this.getBedHeight();
    } else if (SETTING_USE_BIDIRECTIONAL_RASTERING.equals(attribute)) {
      return this.getUseBidirectionalRastering();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOST.equals(attribute)) {
      this.setHost((String) value);
    } else if (SETTING_COMPORT.equals(attribute)) {
      this.setComport((String) value);
    } else if (SETTING_SERIAL_TIMEOUT.equals(attribute)) {
      this.setSerialTimeout((Integer) value);
    } else if (SETTING_FILE_EXPORT_PATH.equals(attribute)) {
      this.setExportPath((String) value);
    } else if (SETTING_UPLOAD_METHOD.equals(attribute)) {
      this.setUploadMethod(value);
    } else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)) {
      this.setMaxVectorCutSpeed((Integer)value);
    } else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)) {
      this.setMaxVectorMoveSpeed((Integer)value);
    } else if (SETTING_MIN_POWER.equals(attribute)) {
      try {
        this.setLaserPowerMin((Integer)value);
      }
      catch (Exception e) {
        this.setLaserPowerMin(Integer.parseInt((String)value));
      }
    } else if (SETTING_MAX_POWER.equals(attribute)) {
      this.setLaserPowerMax((Integer)value);
    } else if (SETTING_BED_HEIGHT.equals(attribute)) {
      this.setBedHeigth((Double)value);
    } else if (SETTING_BED_WIDTH.equals(attribute)) {
      this.setBedWidth((Double)value);
    } else if (SETTING_USE_BIDIRECTIONAL_RASTERING.equals(attribute)) {
      this.setUseBidirectionalRastering((Boolean) value);
    }
  }

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

}


/* =================================================================================== */

class ByteStream
{
  private OutputStream out;
  private byte magic;

  public ByteStream(OutputStream out, byte magic) {
    this.out = out;
    this.magic = magic;
  }

  public void write(byte b) throws IOException {
    int i = b & 0xff;
    i ^= (i >> 7) & 0xff;
    i ^= (i << 7) & 0xff;
    i ^= (i >> 7) & 0xff;
    i ^= this.magic;
    i = (i + 1) & 0xff;
 
//      System.out.printf("write b 0x%02X, i %d, v 0x%02X\n", b, i, v);
    out.write(i & 0xff);
  }

  /**
   * append hex string
   *
   * convert hex string to byte values
   * https://stackoverflow.com/questions/11208479/how-do-i-initialize-a-byte-array-in-java
   */
  public ByteStream hex(String s) throws IOException {
    int len = s.length();
    for (int i = 0; i < len; i += 2) {
      byte value = (byte)((Character.digit(s.charAt(i), 16) << 4)
                           + Character.digit(s.charAt(i+1), 16));
      write(value);
    }
    return this;
  }

  /**
   * append single-byte integer value
   */
  public ByteStream byteint(int i) throws IOException {
    write((byte)(i & 0xff));
    return this;
  }

  /**
   * append string value (as series of bytes)
   */
  public ByteStream string(String s) throws IOException {
    for (int i = 0; i < s.length(); i++) {
      this.byteint(s.codePointAt(i));
    }
    return this;
  }

  /**
   * append absolute value
   */
  public ByteStream absoluteMM(double d) throws IOException {
    return longint((long)(d * 1000.0));
  }
  public ByteStream longint(long val) throws IOException {
    long mask = 0x7f0000000L; /* 35 (5 * 7) bit total */
    /* output 7 bit wise, msb first */
    for (int i = 0; i <= 4; i++) {
      this.byteint((int)((val & mask) >> ((4-i)*7))); /* shifts: 28, 21, 14, 7, 0 bits */
      mask = mask >> 7;
    }
    return this;
  }

  /**
   * append relative value
   */
  public ByteStream relative(double d, boolean signed) throws IOException {
    int val = (int)Math.floor(d);
//    System.out.println("rel" + ((signed)?"Signed":"Unsigned") + "ValueToByteArray(" + d + " -> " + val + ")");
    if (signed) {
      if (val > 8191) {
        //      System.out.println("relValueToByteArray(" + val + ") > 8191");
        throw new IllegalArgumentException("Relative signed value > 8191");
      }
      else if (val < -8192) {
        //      System.out.println("relValueToByteArray(" + val + ") < 8192");
        throw new IllegalArgumentException("Relative signed value < -8192");
      }
      else if (val < 0) {
        val = val + 16384;
      }
    }
    else {
      if (val > 16383) {
        throw new IllegalArgumentException("Relative unsigned value > 16383");
      }
      else if (val < 0) {
        throw new IllegalArgumentException("Relative unsigned value < 0");
      }
    }
    this.byteint((byte)((val & 0x3f80) >> 7));
    this.byteint((byte)(val & 0x007f));
    return this;
  }

  public ByteStream relativeSignedMM(double d) throws IOException {
    return relativeSigned(d * 1000.0);
  }
  private ByteStream relativeSigned(double d) throws IOException {
    return relative(d, true);
  }
  public ByteStream relativeUnsignedMM(double d) throws IOException {
    return relativeUnsigned(d * 1000.0);
  }
  private ByteStream relativeUnsigned(double d) throws IOException {
    return relative(d, false);
  }
  /**
   * append percent value
   */
  public ByteStream percent(int percent) throws IOException {
    double val = (double)percent / 0.006103516; // 100/2^14
//    System.out.println("percentValueToByteArray(" + percent + " -> " + val + ")");
    return relativeUnsigned(val);
  }

}


class UdpStream extends OutputStream
{
  private Integer port = 80;
  private String hostname = "";
  private DatagramSocket socket;
  private InetAddress address;
  public static final int NETWORK_TIMEOUT = 3000;
  public static final int SOURCE_PORT = 40200; // used by rdworks in Windows
  public static final int DEST_PORT = 50200; // fixed UDP port
  public static final int MTU = 998; // max data length per datagram (minus checksum)
  public static final int BUFLEN = 16;

  byte[] receiveData = new byte[MTU+2];
  byte[] buffer = new byte[BUFLEN];
  int bsize = 0;

  private int checksum(byte[] data, int start, int length)
  {
    int sum = 0;
    for (int i = start; i < start+length; i++) {
      sum += data[i] & 0xff; // unsigned !
    }
    return sum;
  }

  public UdpStream(String hostname) throws IOException
  {
    this.hostname = hostname;
    this.port = DEST_PORT;
//    System.out.println("UdpStream(" + hostname + ", " + port + ")");
    try {
      socket = new DatagramSocket(SOURCE_PORT);
      socket.setSoTimeout(NETWORK_TIMEOUT);
      address = InetAddress.getByName(hostname);
    }
    catch (BindException e) {
      throw new IOException(e.getMessage());
    }
  }

  public void write(int i) throws IOException
  {
    if (bsize < BUFLEN) {
      buffer[bsize] = (byte)i;
      bsize = bsize + 1;
    }
    if (bsize >= BUFLEN) {
      flushbuf();
    }
  }

  private void flushbuf() throws IOException
  {
    write(buffer);
    bsize = 0;
  }

  public void write(byte[] data) throws IOException
  {
    int start = 0;
    int l = data.length;
    do {
      int chunk = l - start;
      if (chunk > MTU) {
        chunk = MTU;
      }
      int chksum = checksum(data, start, chunk);
      byte[] buf = new byte[2 + chunk];
      buf[0] = (byte)((chksum & 0xff00) >> 8);
      buf[1] = (byte)(chksum & 0xff);
      System.arraycopy(data, start, buf, 2, chunk);
//    System.out.println("UdpStream.write(buf " + buf.length + " bytes)");
      send(buf);
      start += chunk;
    } while (start < l);
  }

  private void send(byte[] ary) throws IOException
  {
//    System.out.println("UdpStream.send(ary " + ary.length + " bytes)");
    DatagramPacket packet = new DatagramPacket(ary, ary.length, address, port);
    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
    socket.send(packet);
    socket.setSoTimeout(NETWORK_TIMEOUT);
    try {
      socket.receive(receivePacket);
    }
    catch (SocketTimeoutException e) {
      throw new IOException("Response timeout in UdpStream");
    }
    int l = receivePacket.getLength();
    if (l == 0) {
      System.out.println("received nothing");
    }
    else if (l > 1) {
      System.out.println(String.format("received %d bytes\n", l));
    }
    else {
      // l == 1
      byte[] data = receivePacket.getData();
      if (data[0] == (byte)0x46) {
        throw new IOException("checksum error");
      }
      else if (data[0] == (byte)0xc6) {
        // ACK
      }
      else {
        System.out.println(String.format("unknown response %02x\n", data[0]));
      }
    }
  }

  public void close() throws IOException
  {
    if (bsize > 0) {
      flushbuf();
    }
    socket.close();
  }
}


class Serial {

  private SerialPort serialPort;
  private InputStream in;
  private OutputStream out;
  private boolean is_open;

  Serial()
  {
    is_open = false;
  }

  public SerialPort open ( String portName ) throws Exception
  {
    System.out.println("Serial.open(" + portName + ")");
    CommPortIdentifier portIdentifier;
    try {
      portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
    }
    catch (purejavacomm.NoSuchPortException e) {
      throw new Exception("No such device: " + portName);
    }
    if ( portIdentifier.isCurrentlyOwned() )
    {
      throw new Exception("Error: Port is currently in use");
    }
    else
    {
      CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);

      if ( commPort instanceof SerialPort )
      {
        System.out.println("Serial.open has a commPort");
        serialPort = (SerialPort) commPort;
        serialPort.setSerialPortParams(921600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        serialPort.setRTS(false);
        TimeUnit.MILLISECONDS.sleep(5);
        serialPort.setDTR(false);
        TimeUnit.MILLISECONDS.sleep(100);
        in = serialPort.getInputStream();
        out = serialPort.getOutputStream();
        is_open = true;
        return serialPort;
      }
      else
      {
        System.out.println("Error: Only serial ports are handled by this example.");
      }
    }
    return null;
  }

  public void close() throws Exception
  {
    System.out.println("Serial.close()");
    if (!is_open) {
      return;
    }
    try {
      in.close();
      out.close();
      serialPort.close();
    }
    catch (Exception e) {
      throw e;
    }
    return;
  }

  public void write(byte[] data) throws IOException
  {
    out.write(data);
    return;
  }

  private static byte[] buf = new byte[1024];
  public byte[] read(int max) throws IOException, UnsupportedCommOperationException
  {
    int idx = 0;

    if (max > 1024) {
      System.out.println(String.format("Serial.read max %d > 1024", max));
      return null;
    }
    serialPort.enableReceiveTimeout(500); // start with 500msec for first byte
    byte first = (byte)in.read();
    if (first == 0) {
      System.out.println("Serial.read timeout");
      // timeout
      return buf;
    }
    buf[0] = first;
    serialPort.enableReceiveTimeout(1); // 1msec
//    System.out.println(String.format("%d: 0x%02x", idx, buf[idx]));
    while((this.in.available() != 0) && (idx < max)) {
      idx++;
      buf[idx] = (byte)in.read();
//      System.out.println(String.format("%d: 0x%02x", idx, buf[idx]));
    }
//    System.out.println(String.format("Serial.read got %d bytes", idx+1));
    return Arrays.copyOfRange(buf, 0, idx+1);
  }

  public OutputStream outputStream()
  {
    return out;
  }

  public void listPorts()
  {
    java.util.Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
    while ( portEnum.hasMoreElements() )
    {
      CommPortIdentifier portIdentifier = portEnum.nextElement();
      System.out.println(portIdentifier.getName()  +  " - " +  getPortTypeName(portIdentifier.getPortType()) );
    }
  }

  /**
   * purejavacomm.* supports PORT_SERIAL, PORT_PARALLEL
   * gnu.io.*       supports PORT_SERIAL, PORT_PARALLEL, PORT_i2C, PORT_RAW, PORT_RS485
   **/
  private String getPortTypeName ( int portType )
  {
    switch ( portType )
    {
//    case CommPortIdentifier.PORT_I2C:
//      return "I2C";
    case CommPortIdentifier.PORT_PARALLEL:
      return "Parallel";
//    case CommPortIdentifier.PORT_RAW:
//      return "Raw";
//    case CommPortIdentifier.PORT_RS485:
//      return "RS485";
    case CommPortIdentifier.PORT_SERIAL:
      return "Serial";
    default:
      return "unknown type";
    }
  }
}
