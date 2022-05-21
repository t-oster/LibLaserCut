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
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/* for class UpdStream: */
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/* for class Serial; */
/** either gnu.io or purejavacomm implement the SerialPort. Same API. **/
// import gnu.io.*;
import java.util.concurrent.TimeUnit;
import purejavacomm.*;

public class Ruida extends LaserCutter
{
  private static final int MINFOCUS = -500; //Minimal focus value (not mm)
  private static final int MAXFOCUS = 500; //Maximal focus value (not mm)
  private static final int MAXPOWER = 80;
  private static final double FOCUSWIDTH = 0.0252; //How much mm/unit the focus values are
  protected static final String SETTING_USE_FILE = "Write to file";
  protected static final String SETTING_FILE = "File name";
  protected static final String SETTING_USE_NETWORK = "Write to network";
  protected static final String SETTING_NETWORK = "IP address";
  protected static final String SETTING_USE_USB = "Write to USB";
  protected static final String SETTING_USB_DEVICE = "USB device";
  protected static final String SETTING_MAX_VECTOR_CUT_SPEED = "Max vector cutting speed (mm/s)";
  protected static final String SETTING_MAX_VECTOR_MOVE_SPEED = "Max vector move speed (mm/s)";
  protected static final String SETTING_MIN_POWER = "Min laser power (%)";
  protected static final String SETTING_MAX_POWER = "Max laser power (%)";
  protected static final String SETTING_BED_WIDTH = "Bed width (mm)";
  protected static final String SETTING_BED_HEIGHT = "Bed height (mm)";
  protected static final String SETTING_USE_BIDIRECTIONAL_RASTERING = "Use bidirectional rastering";
  // config values
  private static final long[] JogAcceleration = {200000,50000,600000};
  private static final long[] JogMaxVelocity = {16,16,2048};
  private static final long[] EngraveAcceleration = {200000,50000,600000};
  private static final long[] EngraveMaxVelocity = {800,800,2048};
  private static final long[] VectorAcceleration = {100000,25000,20000};
  private static final long[] VectorMaxVelocity = {1000,1000,1000};
  private static final byte FlipLaserPWMPower = 1;
  private static final byte FlipLaserOutput = 0;

  private static final byte HomeDirection = 1;
  private static final byte[] FlipHomeDirection = {1,0,0};
  private static final byte[] LimitContCondition = {0,0,0,0};
  private static final long[] MaxSteps = {250,500,500};
  private static final long[] TableSize = {20000,12000,30000};

  /* output */
  private OutputStream output_stream;
  private ByteStream stream;
  private Serial serial;

  private int mm2focus(float mm)
  {
    return (int) (mm / FOCUSWIDTH);
  }

  private float focus2mm(int focus)
  {
    return (float) (focus * FOCUSWIDTH);
  }

  public Ruida()
  {
  }

  /**
   * Copies the current instance with all config settings, because
   * it is used for save- and restoring
   * @return
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
    return true;
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

  private void find_and_write_bounding_box(LaserJob job)
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


  private double last_x = 0.0;
  private double last_y = 0.0;
  private int vector_count = 0;
  private long travel_distance = 0;

  private void vector(double x, double y, double dpi, boolean as_cut)
  {
    double x_mm = Util.px2mm(x, dpi);
    double y_mm = Util.px2mm(y, dpi);
    boolean as_absolute;

    /* compute distance to last known position */
    double dx = x_mm - last_x;
    double dy = y_mm - last_y;
//    System.out.println("x " + x + ", y " + y + ",  dx " + dx + ", dy " + dy);
    if ((dx == 0.0) && (dy == 0.0)) {
//      System.out.println("\tno move - skip");
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
//    System.out.println("    distance " + distance + ", absolute? " + as_absolute);
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

  private float min_power = 0.0f;
  private float max_power = 0.0f;
  private float speed = 0;

  private float cmd_absoluteMM(String cmd, float old_val, float new_val)
  {
    if (old_val != new_val) {
      stream.hex(cmd).absoluteMM((int)new_val);
    }
    return new_val;
  }

  private float cmd_percent(String cmd, float old_val, float new_val)
  {
    if (old_val != new_val) {
      stream.hex(cmd).percent((int)new_val);
    }
    return new_val;
  }

  private float cmd_layer_absoluteMM(String cmd, int layer, float old_val, float new_val)
  {
    if (old_val != new_val) {
      stream.hex(cmd).byteint(layer).absoluteMM((int)new_val);
    }
    return new_val;
  }

  private float cmd_layer_percent(String cmd, int layer, float old_val, float new_val)
  {
    if (old_val != new_val) {
      stream.hex(cmd).byteint(layer).percent((int)new_val);
    }
    return new_val;
  }

  /**
   * It is called whenever VisiCut wants the driver to send a job to the lasercutter.
   * @param job This is an LaserJob object, containing all information on the job, which is to be sent
   * @param pl Use this object to inform VisiCut about the progress of your sending action.
   * @param warnings If you there are warnings for the user, you can add them to this list, so they can be displayed by VisiCut
   * @throws IllegalJobException Throw this exception, when the job is not suitable for the current machine
   * @throws Exception
   */
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
//    System.out.println("JOB title >" + job.getTitle() + "< name >" + job.getName() + "< user >"+ job.getUser() + "<");

//    pl.progressChanged(this, 0);
//    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();

    try {
      if (getUseFilename()) {
        output_stream = openFile(getFilename());
      }
      else if (getUseNetwork()) {
        output_stream = openNetwork(getNetwork());
      }
      else if (getUseUsb()) {
        output_stream = openUsb(getUsbDevice());
      }
      else {
        pl.taskChanged(this, "** No output configured");
        return;
      }

      stream = new ByteStream(output_stream);
//      pl.taskChanged(this, "sending");
      if (getUseUsb()) {
        stream.hex("DA000004"); // identify
        serial.read(16);
      }
    }
    catch (Exception e) {
      pl.taskChanged(this, "Fail: " + e.getMessage());
      warnings.add("Fail: " + e.getMessage());
      throw e;
//      throw new IllegalJobException("Fail: " + e.getMessage());
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

//        System.out.println("VectorPart(" + minX + ", " + minY + ", " + maxX + ", " + maxY + " @ " + p.getDPI() + "dpi)");

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
                stream.hex("c902").longint((int)speed);
                // power for laser #1
                stream.hex("c601").percent((int)min_power);
                stream.hex("c602").percent((int)max_power);

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
              if (pr instanceof FloatMinMaxPowerSpeedFrequencyProperty)
              {
                FloatMinMaxPowerSpeedFrequencyProperty prop = (FloatMinMaxPowerSpeedFrequencyProperty) pr;
                if (first_prop) {
                  first_prop = false;
                  min_power = cmd_layer_percent("c631", part_number, min_power, prop.getMinPower());
                  max_power = cmd_layer_percent("c632", part_number, max_power, prop.getPower());
                  speed = cmd_layer_absoluteMM("c904", part_number, speed, prop.getSpeed());
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
                  min_power = cmd_percent("c601", min_power, prop.getMinPower());
                  max_power = cmd_percent("c602", max_power, prop.getPower());
                  speed = cmd_absoluteMM("c902", speed, prop.getSpeed());
                }
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
      else
      {
        warnings.add("Unknown Job part.");
      }
//      ruida.endPart();
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

    close(output_stream);
  } /* sendJob */

  /**
   * Returns a list of all supported resolutions (in DPI)
   * @return
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]{100.0,200.0,500.0,1000.0});
  }

  protected Double BedWidth = 900.0;
  /**
   * Returns the width of the laser-bed in mm.
   * @return
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
   * @return
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

  protected boolean useFilename = false;

  public boolean getUseFilename()
  {
    return useFilename;
  }

  public void setUseFilename(boolean useFilename)
  {
    this.useFilename = useFilename;
  }

  protected String filename = "thunder.rd";

  /**
   * Get the value of output filename
   *
   * @return the value of filename
   */
  public String getFilename()
  {
    return filename;
  }

  /**
   * Set the value of output filename
   *
   * @param filename new value of filename
   */
  public void setFilename(String filename)
  {
    this.filename = filename;
  }

  protected boolean useNetwork = false;

  public boolean getUseNetwork()
  {
    return useNetwork;
  }

  public void setUseNetwork(boolean useNetwork)
  {
    this.useNetwork = useNetwork;
  }

  protected String network_addr = "192.168.1.1";

  /**
   * Get the value of output IP addr
   *
   * @return the value of IP addr
   */
  public String getNetwork()
  {
    return network_addr;
  }

  /**
   * Set the value of output network
   *
   * @param filename new value of network addr
   */
  public void setNetwork(String network_addr)
  {
    this.network_addr = network_addr;
  }


  protected boolean useUsb = false;

  public boolean getUseUsb()
  {
    return useUsb;
  }

  public void setUseUsb(boolean useUsb)
  {
    this.useUsb = useUsb;
  }

  protected String usb_device = "/dev/ttyUSB0";

  /**
   * Get the value of output usb device
   *
   * @return the value of use device
   */
  public String getUsbDevice()
  {
    return usb_device;
  }

  /**
   * Set the value of output usb device
   *
   * @param filename new value of usb device
   */
  public void setUsbDevice(String usb_device)
  {
    this.usb_device = usb_device;
  }

  /**
   * open file output connection
   * @sets out
   */
  public OutputStream openFile(String filename) throws IOException, Exception
  {
    System.out.println("Ruida.open - normal disk file \"" + filename + "\"");
    // a normal disk file
    return new FileOutputStream(new File(filename));
  }
  
  /**
   * open network output connection
   * @sets out
   */
  public static final int DEST_PORT = 50200; // fixed UDP port
  public OutputStream openNetwork(String hostname) throws IOException, Exception
  {
    return new UdpStream(hostname, DEST_PORT);
  }

  /**
   * open USB output connection
   * @sets out
   */
  public OutputStream openUsb(String device) throws IOException, Exception
  {
    if (!(serial instanceof Serial)) { // not open yet
      // the usb device, hopefully
      //
      try {
        System.out.println("Ruida.open - serial " + device);
        serial = new Serial();
        serial.open(device);
      }
      catch (Exception e) {
        System.out.println("Looks like '" + device + "' is not a serial device");
        throw e;
      }
    }
    return serial.outputStream();
  }

  public void close(OutputStream output_stream) throws IOException, Exception
  {
    System.out.println("Ruida.close()");
    try {
      serial.close();
    }
    catch (Exception e) {
    }
    serial = null;
    try {
      output_stream.close();
    }
    catch (Exception e) {
    }
  }

  /* ---------------------------------------------------------------- */
  /* device properties  */

  private static String[] settingAttributes = new String[]  {
    SETTING_USE_FILE,
    SETTING_FILE,
    SETTING_USE_NETWORK,
    SETTING_NETWORK,
    SETTING_USE_USB,
    SETTING_USB_DEVICE,
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
    if (SETTING_USE_FILE.equals(attribute)) {
      return this.getUseFilename();
    }
    else if (SETTING_FILE.equals(attribute)) {
      return this.getFilename();
    }
    else if (SETTING_USE_NETWORK.equals(attribute)) {
      return this.getUseNetwork();
    }
    else if (SETTING_NETWORK.equals(attribute)) {
      return this.getNetwork();
    }
    else if (SETTING_USE_USB.equals(attribute)) {
      return this.getUseUsb();
    }
    else if (SETTING_USB_DEVICE.equals(attribute)) {
      return this.getUsbDevice();
    }
    else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)) {
      return this.getMaxVectorCutSpeed();
    }
    else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)) {
      return this.getMaxVectorMoveSpeed();
    }
    else if (SETTING_MIN_POWER.equals(attribute)) {
      return this.getLaserPowerMin();
    }
    else if (SETTING_MAX_POWER.equals(attribute)) {
      return this.getLaserPowerMax();
    }
    else if (SETTING_BED_WIDTH.equals(attribute)) {
      return this.getBedWidth();
    }
    else if (SETTING_BED_HEIGHT.equals(attribute)) {
      return this.getBedHeight();
    }
    else if (SETTING_USE_BIDIRECTIONAL_RASTERING.equals(attribute)) {
      return this.getUseBidirectionalRastering();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_USE_FILE.equals(attribute)) {
      this.setUseFilename((Boolean) value);
    }
    else if (SETTING_FILE.equals(attribute)) {
      this.setFilename((String) value);
    }
    else if (SETTING_USE_NETWORK.equals(attribute)) {
      this.setUseNetwork((Boolean) value);
    }
    else if (SETTING_NETWORK.equals(attribute)) {
      this.setNetwork((String) value);
    }
    else if (SETTING_USE_USB.equals(attribute)) {
      this.setUseUsb((Boolean) value);
    }
    else if (SETTING_USB_DEVICE.equals(attribute)) {
      this.setUsbDevice((String) value);
    }
    else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)) {
      this.setMaxVectorCutSpeed((Integer)value);
    }
    else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)) {
      this.setMaxVectorMoveSpeed((Integer)value);
    }
    else if (SETTING_MIN_POWER.equals(attribute)) {
      try {
        this.setLaserPowerMin((Integer)value);
      }
      catch (Exception e) {
        this.setLaserPowerMin(Integer.parseInt((String)value));
      }
    }
    else if (SETTING_MAX_POWER.equals(attribute)) {
      this.setLaserPowerMax((Integer)value);
    }
    else if (SETTING_BED_HEIGHT.equals(attribute)) {
      this.setBedHeigth((Double)value);
    }
    else if (SETTING_BED_WIDTH.equals(attribute)) {
      this.setBedWidth((Double)value);
    }
    else if (SETTING_USE_BIDIRECTIONAL_RASTERING.equals(attribute)) {
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
  private static final byte[] encode_table = {
    (byte)0x89, 0x09, (byte)0x8b, 0x0b, (byte)0x8d, 0x0d, (byte)0x8f, 0x0f, (byte)0x81, 0x01, (byte)0x83, 0x03, (byte)0x85, 0x05, (byte)0x87, 0x07,
    (byte)0x99, 0x19, (byte)0x9b, 0x1b, (byte)0x9d, 0x1d, (byte)0x9f, 0x1f, (byte)0x91, 0x11, (byte)0x93, 0x13, (byte)0x95, 0x15, (byte)0x97, 0x17,
    (byte)0xa9, 0x29, (byte)0xab, 0x2b, (byte)0xad, 0x2d, (byte)0xaf, 0x2f, (byte)0xa1, 0x21, (byte)0xa3, 0x23, (byte)0xa5, 0x25, (byte)0xa7, 0x27,
    (byte)0xb9, 0x39, (byte)0xbb, 0x3b, (byte)0xbd, 0x3d, (byte)0xbf, 0x3f, (byte)0xb1, 0x31, (byte)0xb3, 0x33, (byte)0xb5, 0x35, (byte)0xb7, 0x37,
    (byte)0xc9, 0x49, (byte)0xcb, 0x4b, (byte)0xcd, 0x4d, (byte)0xcf, 0x4f, (byte)0xc1, 0x41, (byte)0xc3, 0x43, (byte)0xc5, 0x45, (byte)0xc7, 0x47,
    (byte)0xd9, 0x59, (byte)0xdb, 0x5b, (byte)0xdd, 0x5d, (byte)0xdf, 0x5f, (byte)0xd1, 0x51, (byte)0xd3, 0x53, (byte)0xd5, 0x55, (byte)0xd7, 0x57,
    (byte)0xe9, 0x69, (byte)0xeb, 0x6b, (byte)0xed, 0x6d, (byte)0xef, 0x6f, (byte)0xe1, 0x61, (byte)0xe3, 0x63, (byte)0xe5, 0x65, (byte)0xe7, 0x67,
    (byte)0xf9, 0x79, (byte)0xfb, 0x7b, (byte)0xfd, 0x7d, (byte)0xff, 0x7f, (byte)0xf1, 0x71, (byte)0xf3, 0x73, (byte)0xf5, 0x75, (byte)0xf7, 0x77,
    (byte)0x8a, 0x0a, (byte)0x8c, 0x0c, (byte)0x8e, 0x0e, (byte)0x90, 0x10, (byte)0x82, 0x02, (byte)0x84, 0x04, (byte)0x86, 0x06, (byte)0x88, 0x08,
    (byte)0x9a, 0x1a, (byte)0x9c, 0x1c, (byte)0x9e, 0x1e, (byte)0xa0, 0x20, (byte)0x92, 0x12, (byte)0x94, 0x14, (byte)0x96, 0x16, (byte)0x98, 0x18,
    (byte)0xaa, 0x2a, (byte)0xac, 0x2c, (byte)0xae, 0x2e, (byte)0xb0, 0x30, (byte)0xa2, 0x22, (byte)0xa4, 0x24, (byte)0xa6, 0x26, (byte)0xa8, 0x28,
    (byte)0xba, 0x3a, (byte)0xbc, 0x3c, (byte)0xbe, 0x3e, (byte)0xc0, 0x40, (byte)0xb2, 0x32, (byte)0xb4, 0x34, (byte)0xb6, 0x36, (byte)0xb8, 0x38,
    (byte)0xca, 0x4a, (byte)0xcc, 0x4c, (byte)0xce, 0x4e, (byte)0xd0, 0x50, (byte)0xc2, 0x42, (byte)0xc4, 0x44, (byte)0xc6, 0x46, (byte)0xc8, 0x48,
    (byte)0xda, 0x5a, (byte)0xdc, 0x5c, (byte)0xde, 0x5e, (byte)0xe0, 0x60, (byte)0xd2, 0x52, (byte)0xd4, 0x54, (byte)0xd6, 0x56, (byte)0xd8, 0x58,
    (byte)0xea, 0x6a, (byte)0xec, 0x6c, (byte)0xee, 0x6e, (byte)0xf0, 0x70, (byte)0xe2, 0x62, (byte)0xe4, 0x64, (byte)0xe6, 0x66, (byte)0xe8, 0x68,
    (byte)0xfa, 0x7a, (byte)0xfc, 0x7c, (byte)0xfe, 0x7e, 0x00, (byte)0x80, (byte)0xf2, 0x72, (byte)0xf4, 0x74, (byte)0xf6, 0x76, (byte)0xf8, 0x78
  };

  private OutputStream out;

  public ByteStream(OutputStream out) {
    this.out = out;
  }

  public void write(byte b) {
    int i = b & 0xff;
    if (i < 0) {
      i = 256 + i;
    }
    try {
      byte v = encode_table[i];
//      System.out.printf("write b 0x%02X, i %d, v 0x%02X\n", b, i, v);
      out.write(v);
    }
    catch (IOException e) {
      System.out.println("ByteStream.writeTo() failed");
    };
  }

  /**
   * append hex string
   *
   * convert hex string to byte values
   * https://stackoverflow.com/questions/11208479/how-do-i-initialize-a-byte-array-in-java
   */
  public ByteStream hex(String s) {
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
  public ByteStream byteint(int i) {
    write((byte)(i & 0xff));
    return this;
  }

  /**
   * append string value (as series of bytes)
   */
  public ByteStream string(String s) {
    s.chars().forEach(i -> this.byteint(i));
    return this;
  }

  /**
   * append absolute value
   */
  public ByteStream absoluteMM(double d) {
    return longint((long)(d * 1000.0));
  }
  public ByteStream longint(long val) {
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
  public ByteStream relative(double d, boolean signed) {
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

  public ByteStream relativeSignedMM(double d) {
    return relativeSigned(d * 1000.0);
  }
  private ByteStream relativeSigned(double d) {
    return relative(d, true);
  }
  public ByteStream relativeUnsignedMM(double d) {
    return relativeUnsigned(d * 1000.0);
  }
  private ByteStream relativeUnsigned(double d) {
    return relative(d, false);
  }
  /**
   * append percent value
   */
  public ByteStream percent(int percent) {
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
  public static final int NETWORK_TIMEOUT = 3000;       // TODO
  public static final int SOURCE_PORT = 40200; // used by rdworks in Windows
  public static final int MTU = 1470; // max data length per datagram (minus checksum)
  private ByteArrayOutputStream bos;
  byte[] receiveData = new byte[MTU+2];

  private int checksum(byte[] data, int start, int length)
  {
    int sum = 0;
    for (int i = start; i < start+length; i++) {
      sum += data[i] & 0xff; // unsigned !
    }
    return sum;
  }

  public UdpStream(String hostname, Integer port) throws IOException
  {
    this.hostname = hostname;
    this.port = port;
    System.out.println("UdpStream(" + hostname + ", " + port + ")");
    socket = new DatagramSocket(SOURCE_PORT);
    address = InetAddress.getByName(hostname);
    bos = new ByteArrayOutputStream();
  }

  public void write(int i) throws IOException
  {
    throw new IOException("UdpStream.write(int)");
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
//    byte[] data = bos.toByteArray();
      int chksum = checksum(data, start, chunk);
      byte[] buf = new byte[2 + chunk];
      buf[0] = (byte)((chksum & 0xff00) >> 8);
      buf[1] = (byte)(chksum & 0xff);
      System.arraycopy(data, start, buf, 2, chunk);
//    System.out.println("UdpStream.write(buf " + buf.length + " bytes)");
      send(buf);
      start += chunk;
    } while (start < l);
//    bos.reset();
//    bos.write(data);
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
    System.out.println("UdpStream.close()");
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
