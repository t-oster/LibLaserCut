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
import de.thomas_oster.liblasercut.FloatPowerSpeedFocusProperty;
import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.JobPart;
import de.thomas_oster.liblasercut.LaserCutter;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import de.thomas_oster.liblasercut.ProgressListener;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.RasterPart;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.PureJavaIllegalStateException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;


/**
 * This driver implements the K3/K5/K6 Laser Protocol
 * found here: https://github.com/RBEGamer/K3_LASER_ENGRAVER_PROTOCOL
 * @author mariolukas
 */
public class K3EngraverDriver extends LaserCutter
{

  /**
   * This is the core method of the driver. It is called, whenever VisiCut wants
   * your driver to send a job to the lasercutter.
   *
   * @param job This is an LaserJob object, containing all information on the
   * job, which is to be sent
   * @param pl Use this object to inform VisiCut about the progress of your
   * sending action.
   * @param warnings If you there are warnings for the user, you can add them to
   * this list, so they can be displayed by VisiCut
   * @throws IllegalJobException Throw this exception, when the job is not
   * suitable for the current machine
   */
  protected static final String SETTING_COMPORT = "COM Port";
  protected static final String SETTING_BAUDRATE = "Baud Rate (Serial)";
  protected static final String SETTING_DESCRETE_MODE = "Discrete Mode";
  protected static final String SETTING_AUTO_HOME = "Auto Home";
  protected static final String SETTING_BED_WIDTH = "Bed Witdh";
  protected static final String SETTING_BED_HEIGHT = "Bed Height";
  protected static final String SETTING_PREVIEW_CYCLES = "Preview Cycles";

  private static final String[] SETTINGS_LIST = new String[]
  {
    SETTING_BAUDRATE,
    SETTING_COMPORT,
    SETTING_DESCRETE_MODE,
    SETTING_AUTO_HOME,
    SETTING_BED_WIDTH,
    SETTING_BED_HEIGHT,
    SETTING_PREVIEW_CYCLES,

  };

  protected static final Locale FORMAT_LOCALE = Locale.US;

  protected boolean autoHome = false;
  protected int baudRate = 115200;
  protected String comport = "auto";
  protected boolean discreteMode = false;
  protected double bedHeight = 75.0;
  protected double bedWidth = 80.0;
  protected int previewCycles = 4;

  //the laser need 10seconds for 1600pixel to move, so we have to wait 10ms for one step
  protected boolean CHECK_OUT_OF_BOUNDING_BOX_MOVE = false;
  protected long TRAVEL_TIME_DELAY = 10;
  protected long TRAVE_TRIME_THRESHOLD = 100;
  protected long BOUNDING_BOX_MAX_X = 1400; //width
  protected long BOUNDING_BOX_MAX_Y = 1420;
  protected int WAIT_FOR_ACK_RETRIES = 100;
  protected int WAIT_FOR_ACK_TIME = 100;

  protected int head_abs_pos_x = 0;
  protected int head_abs_pos_y = 0;


  @Override
  public String getModelName()
  {
    return "K3/K5 Laser Engraver";
  }

  public int getPreviewCycles()
  {
    return previewCycles;
  }

  public void setPreviewCycles(int cycles)
  {
    this.previewCycles = cycles;
  }

  public boolean getAutoHome()
  {
    return autoHome;
  }

  public void setAutoHome(boolean state)
  {
    this.autoHome = state;
  }

  public boolean getDiscreteMode()
  {
    return discreteMode;
  }

  public void setDiscreteMode(boolean state)
  {
    this.discreteMode = state;
  }

  public int getBaudRate()
  {
    return baudRate;
  }

  public void setBaudRate(int baudRate)
  {
    this.baudRate = baudRate;
  }

  public String getComport()
  {
    return comport;
  }

  public void setComport(String comport)
  {
    this.comport = comport;
  }

  protected int serialTimeout = 15000;

  public int getSerialTimeout()
  {
    return serialTimeout;
  }

  public void setSerialTimeout(int serialTimeout)
  {
    this.serialTimeout = serialTimeout;
  }

  protected BufferedReader in;
  protected PrintStream out;
  protected InputStreamReader inStream;
  private CommPort port;
  private CommPortIdentifier portIdentifier;
  private ByteArrayOutputStream outputBuffer;

  protected String connectSerial(CommPortIdentifier i, ProgressListener pl) throws PortInUseException, IOException, UnsupportedCommOperationException
  {
    pl.taskChanged(this, "opening '" + i.getName() + "'");
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
          sp.disableReceiveFraming();
          sp.setSerialPortParams(getBaudRate(), 8, 1, 0);
          sp.setDTR(true);
        }
        out = new PrintStream(port.getOutputStream(), true, StandardCharsets.US_ASCII);
        inStream = new InputStreamReader(port.getInputStream());

        portIdentifier = i;
        pl.taskChanged(this, "Connected");
        return null;
      }
      catch (PortInUseException e)
      {
        try
        {
          disconnect("");
        }
        catch (Exception ex)
        {
          System.out.println(ex.getMessage());
        }
        return "Port in use: " + i.getName();
      }
      catch (IOException e)
      {
        try
        {
          disconnect("");
        }
        catch (Exception ex)
        {
          System.out.println(ex.getMessage());
        }
        return "IO Error from " + i.getName() + ": " + e.getMessage();
      }
      catch (PureJavaIllegalStateException e)
      {
        try
        {
          disconnect("");
        }
        catch (Exception ex)
        {
          System.out.println(ex.getMessage());
        }
        return "Could not open " + i.getName() + ": " + e.getMessage();
      }
    }
    else
    {
      return "Not a serial Port " + comport;
    }
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

  protected int waitForACK() throws IOException, Exception
  {
    int rec = 0;
    char[] inBuf = new char[128];
    int trys = 0;

    while (trys < WAIT_FOR_ACK_RETRIES)
    {
      Thread.sleep(WAIT_FOR_ACK_TIME);
      trys++;

      rec = inStream.read(inBuf);
      if (inBuf[0] == (byte) 0x09)
      {
        return rec;
      }
    }

    System.out.println("ACK Timeout");
    return rec;
  }

  protected int sendLine(byte[] command, int bufStart, int bufEnd, Object... parameters) throws IOException, Exception
  {
    out.write(command, bufStart, bufEnd);
    out.flush();

    return waitForACK();
  }

  protected int send4ByteCommand(byte command) throws IOException, Exception
  {
    byte[] buffer =
    {
      command, (byte) 0x00, (byte) 0x04, (byte) 0x00
    };
    return sendLine(buffer, 0, 4);
  }

  protected int send5ByteCommand(byte command, int val) throws IOException, Exception
  {
    byte[] buffer =
    {
      command, (byte) 0x00, (byte) 0x05, (byte) (val >> 8), (byte) val
    };
    return sendLine(buffer, 0, 5);
  }

  protected int send1ByteCommand(byte command) throws IOException, Exception
  {
    byte[] buffer =
    {
      command
    };
    return sendLine(buffer, 0, 1);
  }

  protected int sendHomeCommand() throws IOException, Exception
  {
    int ret = send4ByteCommand((byte) 0x17);
    if (ret == 1)
    {
      head_abs_pos_x = 0;
      head_abs_pos_y = 0;
    }
    Thread.sleep(TRAVEL_TIME_DELAY * (long) Math.sqrt(BOUNDING_BOX_MAX_X * BOUNDING_BOX_MAX_Y));
    return ret;
  }

  protected int sendHomeAndCenter() throws IOException, Exception
  {
    int ret = send4ByteCommand((byte) 0x1A);
    if (ret == 1)
    {
      head_abs_pos_x = 0;
      head_abs_pos_y = 0;
    }
    Thread.sleep(TRAVEL_TIME_DELAY * (long) Math.sqrt(BOUNDING_BOX_MAX_X * BOUNDING_BOX_MAX_Y));
    return ret;
  }

  protected int moveHeadRelative(long x, long y) throws IOException, Exception
  {

    byte sendBuffer[] =
    {
      1, 0, 7, (byte) (x >> 8), (byte) (x), (byte) (y >> 8), (byte) (y)
    };
    head_abs_pos_x += x;
    head_abs_pos_y += y;
    int ret = sendLine(sendBuffer, 0, 7);
    long tt = (long) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2)) * TRAVEL_TIME_DELAY; //calc travel time

    if (tt > TRAVE_TRIME_THRESHOLD)
    {
      Thread.sleep(tt);
    }
    return ret;
  }

  protected int enableDiscretMode(boolean enable) throws IOException, Exception
  {
    int ret = 0;
    if (enable)
    {
      ret = send4ByteCommand((byte) 0x1B);
    }
    else
    {
      ret = send4ByteCommand((byte) 0x1C);
    }
    return ret;
  }

  protected int reset() throws IOException, Exception
  {
    int ret = 0;
    ret = send4ByteCommand((byte) 0x06);
    return ret;
  }

  protected int sendConnectSequence() throws IOException, Exception
  {
    int ret = 0;
    ret = send4ByteCommand((byte) 0x0A);
    return ret;
  }

  protected int switchFanOn(boolean on) throws IOException, Exception
  {
    int ret = 0;
    byte state = (byte) 0x04;

    if (!on)
    {
      state = (byte) 0x05;
    }

    ret = send4ByteCommand(state);
    return ret;
  }

  protected int moveX(int x) throws IOException, Exception
  {
    int ret = 0;
    ret = send5ByteCommand((byte) 0x0B, x);
    return ret;
  }

  protected int moveY(int y) throws IOException, Exception
  {
    int ret = 0;
    ret = send5ByteCommand((byte) 0x0C, y);
    return ret;
  }

  protected int moveRelative(int x, int y) throws IOException, Exception
  {
    int ret = 0;
    byte[] sendBuffer =
    {
      (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) (x >> 8), (byte) (x), (byte) (y >> 8), (byte) (y)
    };
    ret = sendLine(sendBuffer, 0, 7);
    return ret;
  }

  protected int startEngrave(int x, int y) throws IOException, Exception
  {

    int ret = 0;

    byte[] buffer =
    {
      (byte) 0x14, (byte) 0x00, (byte) 0x07, (byte) 0x05, (byte) 74, (byte) 0x05, (byte) 0x7a
    };
    head_abs_pos_x += x;
    head_abs_pos_y += y;
    ret = sendLine(buffer, 0, 7);

    return ret;

  }

  protected void connect(ProgressListener pl) throws IOException, PortInUseException, NoSuchPortException, UnsupportedCommOperationException
  {
    outputBuffer = null;

    String error = "No serial port found";
    if (portIdentifier == null && !getComport().equals("auto") && !getComport().equals(""))
    {
      try
      {
        portIdentifier = CommPortIdentifier.getPortIdentifier(getComport());
      }
      catch (NoSuchPortException e)
      {
        throw new IOException("No such port: " + getComport());
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

  protected void disconnect(String jobname) throws IOException, URISyntaxException
  {
    if (outputBuffer != null)
    {
      out.close();
    }
    else
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

  }

  public byte[] toByteArray(long value)
  {
    return new byte[]
    {
      (byte) (value >> 8), (byte) value
    };
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    //let's check the job for some errors
    pl.taskChanged(this, "checking job");
    checkJob(job);

    pl.taskChanged(this, "connecting...");
    connect(pl);
    sendConnectSequence();

    if (this.getAutoHome())
    {
      sendHomeCommand();
    }

    Thread.sleep(500);
    pl.taskChanged(this, "waiting ...");

    byte[] imageBuffer = new byte[10000];
    //Well, first, let's iterate over the different parts of this job.
    for (JobPart p : job.getParts())
    {
      //now we have to check, of which kind this part is. We only accept VectorParts and add a warning for other parts.
      if ((p instanceof VectorPart))
      {
        VectorPart vp = ((VectorPart) p);

        for (VectorCommand cmd : vp.getCommandList())
        {

          if (cmd.getType() == VectorCommand.CmdType.MOVETO)
          {

            moveRelative((int) cmd.getX() / 2, (int) cmd.getY() / 2);

            break;
          }
          else
          {
            warnings.add("This driver is not able to handle cutting and marking.");
          }

        }

      }
      else
      {
        RasterPart rp = ((RasterPart) p);

        if (rp.getLaserProperty().getPower() == 0 && rp.getLaserProperty().getSpeed() == 0)
        {
          moveRelative((int) rp.getRasterStart().x, (int) rp.getRasterStart().y);

          for (int circles = 0; circles < this.getPreviewCycles(); circles++)
          {
            moveX(rp.getRasterWidth());
            moveY(rp.getRasterHeight());
            moveX(-rp.getRasterWidth());
            moveY(-rp.getRasterHeight());
          }

        }
        else
        {

          moveRelative((int) rp.getRasterStart().x, (int) rp.getRasterStart().y);
          enableDiscretMode(this.getDiscreteMode());
          reset();

          switchFanOn(true);

          int posX = (int) rp.getRasterStart().x;
          int posY = (int) rp.getRasterStart().y;

          startEngrave(posX + 1, posY + 1);

          ByteArrayList line = new ByteArrayList(rp.getRasterWidth());

          for (int y = 0; y < rp.getRasterHeight(); y++)
          {

            rp.getRasterLine(y, line);
            int bufferHeaderSize = 9;
            int buffersize = line.size() + bufferHeaderSize;

            imageBuffer[0] = (byte) 0x09;
            imageBuffer[1] = (byte) (buffersize >> 8);
            imageBuffer[2] = (byte) (buffersize);

            int engravingDepth = (int) rp.getLaserProperty().getSpeed();
            imageBuffer[3] = (byte) (engravingDepth >> 8); // engraving depth 
            imageBuffer[4] = (byte) (engravingDepth);

            int laserPower = (int) rp.getLaserProperty().getPower() * 10;
            imageBuffer[5] = (byte) (laserPower >> 8); // laser intensity
            imageBuffer[6] = (byte) (laserPower);

            imageBuffer[7] = (byte) (y >> 8);
            imageBuffer[8] = (byte) (y);

            for (int i = 0; i <= line.size(); i++)
            {
              imageBuffer[i + bufferHeaderSize] = (byte) line.get(i);
            }

            /*
               check for blank line
             */
            boolean blankLine = true;
            for (int index = 9; index <= line.size(); ++index)
            {
              if (imageBuffer[index] != (byte) 0)
              {
                blankLine = false;
                break;
              }
            }

            //IF SOMETHIN TO LASER IS IN THIS LINE
            if (!blankLine)
            {
              int progress = (y * 100 / rp.getRasterHeight());
              System.out.println("Progress:" + progress + "% Writing Line: " + y + " Buffer Size:" + buffersize);
              pl.taskChanged(this, progress + "%");
              sendLine(imageBuffer, 0, buffersize);

              pl.progressChanged(this, progress);

            }

          }

        }
      }
    }
    sendHomeCommand();
    disconnect(job.getName());
  }

  /**
   * This method should return an Object of a class extending LaserProperty. A
   * LaserProperty represents all settings for your device like power,speed and
   * frequency which are necessary for a certain job-type (e.g. a VectorPart).
   * See the different classes for examples. We will just use the default,
   * supporting power,speed focus and frequency.
   */
  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new PowerSpeedFocusFrequencyProperty();
  }

  /**
   * This method should return a list of all supported resolutions (in DPI)
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(100.0, 200.0, 500.0, 1000.0);
  }

  /**
   * This method should return the width of the laser-bed. You can have a
   * config-setting in order to have different sizes for each instance of your
   * driver. For simplicity we just assume a width of 600mm
   */
  @Override
  public double getBedWidth()
  {
    return this.bedWidth;
  }

  public void setBedWidth(double width)
  {
    this.bedWidth = width;
  }

  /**
   * This method should return the height of the laser-bed. You can have a
   * config-setting in order to have different sizes for each instance of your
   * driver. For simplicity we just assume a height of 300mm
   */
  @Override
  public double getBedHeight()
  {
    return this.bedHeight;
  }

  public void setBedHeight(double height)
  {
    this.bedHeight = height;
  }

  @Override
  public Object getProperty(String attribute)
  {
    if (SETTING_BAUDRATE.equals(attribute))
    {
      return this.getBaudRate();
    }
    if (SETTING_COMPORT.equals(attribute))
    {
      return this.getComport();
    }
    if (SETTING_DESCRETE_MODE.equals(attribute))
    {
      return this.getDiscreteMode();
    }
    if (SETTING_AUTO_HOME.equals(attribute))
    {
      return this.getAutoHome();
    }
    if (SETTING_BED_WIDTH.equals(attribute))
    {
      return this.getBedWidth();
    }
    if (SETTING_BED_HEIGHT.equals(attribute))
    {
      return this.getBedHeight();
    }
    if (SETTING_PREVIEW_CYCLES.equals(attribute))
    {
      return this.getPreviewCycles();
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value)
  {
    if (SETTING_BAUDRATE.equals(attribute))
    {
      this.setBaudRate((Integer) value);
    }
    else if (SETTING_COMPORT.equals(attribute))
    {
      this.setComport((String) value);
    }
    if (SETTING_DESCRETE_MODE.equals(attribute))
    {
      this.setDiscreteMode((boolean) value);
    }
    if (SETTING_AUTO_HOME.equals(attribute))
    {
      this.setAutoHome((boolean) value);
    }
    if (SETTING_BED_HEIGHT.equals(attribute))
    {
      this.setBedHeight((double) value);
    }
    if (SETTING_BED_WIDTH.equals(attribute))
    {
      this.setBedWidth((double) value);
    }
    if (SETTING_PREVIEW_CYCLES.equals(attribute))
    {
      this.setPreviewCycles((int) value);
    }

  }

  @Override
  public String[] getPropertyKeys()
  {
    return SETTINGS_LIST;
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
  {
    return new FloatPowerSpeedFocusProperty();
  }

  /**
   * This method must copy the current instance with all config settings,
   * because it is used for save- and restoring
   */
  @Override
  public LaserCutter clone()
  {
    K3EngraverDriver clone = new K3EngraverDriver();
    clone.copyProperties(this);
    return clone;
  }

}
