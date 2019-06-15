/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Util;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.ConfigDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * This class should act as a starting-point, when implementing a new
 * Lasercutter driver. It will take a Laserjob and just output the Vecor-Parts
 * as G-Code.
 *
 * The file contains comments prefixed with "#<step>" which should guide you in
 * the process of creating custom drivers. Also read the information in the Wiki
 * on https://github.com/t-oster/VisiCut/wiki/
 *
 * #1: Create a new JavaClass, which extends the
 * com.t_oster.liblasercut.drivers.LaserCutter class #2: Implement all abstract
 * methods. Each of them is explained in this example. #3: In Order to see your
 * driver in VisiCut, add your class to the getSupportedDrivers() method in the
 * com.t_oster.liblasercut.LibInfo class
 * (src/com/t_oster/liblasercut/LibInfo.java)
 *
 * @author Thomas Oster
 */
public class K40NanoDriver extends LaserCutter
{

  private static final String SETTING_BEDWIDTH = "Laserbed Width";
  private static final String SETTING_BEDHEIGHT = "Laserbed Height";
  private static final String SETTING_BOARD = "M2, M1, M, B2, B1, B, A, board selection";
  private static final String SETTING_MOCK = "Use mock usb channel";

  //310mm by 220mm
  double bedWidth = 310;
  double bedHeight = 220;
  String board = "M2";
  boolean mock = false;

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
   * @throws Exception
   */
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    //let's check the job for some errors
    checkJob(job);
    K40Device device = new K40Device();
    device.setBoard(board);

    device.open();

    //Well, first, let's iterate over the different parts of this job.
    for (JobPart p : job.getParts())
    {
      //now we have to check, of which kind this part is. We only accept VectorParts and add a warning for other parts.
      if (!(p instanceof VectorPart))
      {
        warnings.add("Non-vector parts are ignored by this driver.");
      }
      else
      {
        //so, we know it's a VectorPart. We cast it, so we get the real interface
        VectorPart vp = (VectorPart) p;
        //A VectorPart consists of a command List. So let's iterate over this list
        for (VectorCommand cmd : vp.getCommandList())
        {
          //There are three types of commands: MOVETO, LINETO and SETPROPERTY
          switch (cmd.getType())
          {
            case LINETO:
            {
              /**
               * Move the laserhead (laser on) from the current position to the
               * x/y position of this command. All coordinates are in dots
               * respecting to the job resolution
               */
              int x = (int) (Util.mm2inch(Util.px2mm(cmd.getX(), p.getDPI())) * 1000.0);
              int y = (int) (Util.mm2inch(Util.px2mm(cmd.getY(), p.getDPI())) * 1000.0);
              //Native units are mils.
              device.cut_absolute(x, y);
              device.execute();
              break;
            }
            case MOVETO:
            {
              /**
               * Move the laserhead (laser off) from the current position to the
               * x/y position of this command. All coordinates are in mm
               */
              int x = (int) (Util.mm2inch(Util.px2mm(cmd.getX(), p.getDPI())) * 1000.0);
              int y = (int) (Util.mm2inch(Util.px2mm(cmd.getY(), p.getDPI())) * 1000.0);
              //Native units are mils.
              device.move_absolute(x, y);
              device.execute();
              break;
            }
            case SETPROPERTY:
            {
              /**
               * Change properties of current laser-actions (e.g. speed,
               * frequency, power... whatever your driver supports)
               */
              LaserProperty prop = cmd.getProperty();
              for (String key : prop.getPropertyKeys())
              {
                String value = prop.getProperty(key).toString();
                if ("speed".equals(key))
                {
                  device.setSpeed(Double.valueOf(value));
                }
                else if ("power".equals(key))
                {
                  device.setPower(Double.valueOf(value));
                }
                else
                {
                  //warnings.add(Can't use this":  " + key + "=" + value);
                }
                System.out.println(" " + key + "=" + value);
              }
              break;
            }
          }
        }
      }
    }

    device.close();
  }

  /**
   * This method should return an Object of a class extending LaserProperty. A
   * LaserProperty represents all settings for your device like power,speed and
   * frequency which are necessary for a certain job-type (e.g. a VectorPart).
   * See the different classes for examples. We will just use the default,
   * supporting power,speed focus and frequency.
   *
   * @return
   */
  @Override
  public LaserProperty getLaserPropertyForVectorPart()
  {
    return new PowerSpeedFocusFrequencyProperty();
  }

  /**
   * This method should return a list of all supported resolutions (in DPI)
   *
   * @return
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]
    {
      100.0, 200.0, 500.0, 1000.0
    });
  }

  public String getBoard()
  {
    return board;
  }

  public void setBoard(String board)
  {
    this.board = board;
  }

  public boolean isMock()
  {
    return mock;
  }

  public void setMock(boolean mock)
  {
    this.mock = mock;
  }

  public void setBedWidth(double bedWidth)
  {
    this.bedWidth = bedWidth;
  }

  public void setBedHeight(double bedHeight)
  {
    this.bedHeight = bedHeight;
  }

  /**
   * This method should return the width of the laser-bed. You can have a
   * config-setting in order to have different sizes for each instance of your
   * driver. For simplicity we just assume a width of 600mm
   *
   * @return
   */
  @Override
  public double getBedWidth()
  {
    return this.bedWidth;
  }

  /**
   * This method should return the height of the laser-bed. You can have a
   * config-setting in order to have different sizes for each instance of your
   * driver. For simplicity we just assume a height of 300mm
   *
   * @return
   */
  @Override
  public double getBedHeight()
  {
    return this.bedHeight;
  }

  /**
   * This method should return a name for this driver.
   *
   * @return
   */
  @Override
  public String getModelName()
  {
    return "K40 Stock-LIHUIYU M2/M1/M/B2/B1/B/A";
  }

  /**
   * This method must copy the current instance with all config settings,
   * because it is used for save- and restoring
   *
   * @return
   */
  @Override
  public LaserCutter clone()
  {
    K40NanoDriver clone = new K40NanoDriver();
    clone.bedHeight = this.bedHeight;
    clone.bedWidth = this.bedWidth;
    clone.board = this.board;
    clone.mock = this.mock;
    return clone;
  }

  private static final String[] settingAttributes = new String[]
  {
    SETTING_BEDWIDTH, SETTING_BEDHEIGHT, SETTING_BOARD, SETTING_MOCK
  };

  @Override
  public String[] getPropertyKeys()
  {
    return settingAttributes;
  }

  @Override
  public void setProperty(String attribute, Object value)
  {
    if (SETTING_BEDWIDTH.equals(attribute))
    {
      this.setBedWidth((Double) value);
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      this.setBedHeight((Double) value);
    }
    else if (SETTING_BOARD.equals(attribute))
    {
      this.setBoard((String) value);
    }
    else if (SETTING_MOCK.equals(attribute))
    {
      this.setMock((Boolean) value);
    }
  }

  @Override
  public Object getProperty(String attribute)
  {
    if (SETTING_BEDWIDTH.equals(attribute))
    {
      return this.getBedWidth();
    }
    else if (SETTING_BEDHEIGHT.equals(attribute))
    {
      return this.getBedHeight();
    }
    else if (SETTING_BOARD.equals(attribute))
    {
      return this.getBoard();
    }
    else if (SETTING_MOCK.equals(attribute))
    {
      return this.isMock();
    }
    return null;
  }

  public class K40Device
  {

    private static final int UNINIT = 0;
    private static final int DEFAULT = 1;
    private static final int COMPACT = 2;

    static final char LASER_ON = 'D';
    static final char LASER_OFF = 'U';

    static final char RIGHT = 'B';
    static final char LEFT = 'T';
    static final char TOP = 'L';
    static final char BOTTOM = 'R';
    static final char DIAGONAL = 'M';

    K40Queue jobber;
    private StringBuilder builder = new StringBuilder();

    private int mode = UNINIT;

    private boolean is_top = false;
    private boolean is_left = false;
    private boolean is_on = false;

    private double speed = 30;
    private double power = 1;
    private String board = "M2";

    private int x = 0;
    private int y = 0;

    void open()
    {
      jobber = new K40Queue();
      jobber.open();
    }

    void close()
    {
      if (mode == COMPACT)
      {
        exit_compact_mode();
        execute();
      }
      jobber.close();
      jobber = null;
    }

    void setPower(double power)
    {
      this.power = power;
    }

    void setSpeed(double mm_per_second)
    {
      if (mode == COMPACT)
      {
        exit_compact_mode();
      }
      speed = mm_per_second;
    }

    public String getBoard()
    {
      return board;
    }

    public void setBoard(String board)
    {
      this.board = board;
    }

    void send()
    {
      jobber.add(builder.toString());
      builder.delete(0, builder.length());
    }

    void move_absolute(int x, int y)
    {
      int dx = x - this.x;
      int dy = y - this.y;
      move_relative(dx, dy);
    }

    void move_relative(int dx, int dy)
    {
      check_init();
      this.x += dx;
      this.y += dy;
      laser_off();
      if (mode == DEFAULT)
      {
        encode_default_move(dx, dy);
      }
      else
      {
        makeLine(0, 0, dx, dy);
      }
      send();
    }

    void cut_absolute(int x, int y)
    {
      int dx = x - this.x;
      int dy = y - this.y;
      cut_relative(dx, dy);
    }

    void cut_relative(int dx, int dy)
    {
      check_init();
      if (mode != COMPACT)
      {
        start_compact_mode();
      }
      this.x += dx;
      this.y += dy;
      laser_on();
      makeLine(0, 0, dx, dy);
      send();
    }

    void check_init()
    {
      if (mode == UNINIT)
      {
        builder.append('I');
        mode = DEFAULT;
      }
    }

    void exit_compact_mode()
    {
      if (mode == COMPACT)
      {
        builder.append("FNSE-\n");
        send();
        is_on = false;
        mode = UNINIT;
      }
    }

    void start_compact_mode()
    {
      check_init();
      if (mode == COMPACT)
      {
        return;
      }
      if (mode == DEFAULT)
      {
        encode_speed(speed);
        builder.append('N');
        if (is_top)
        {
          builder.append(TOP);
        }
        else
        {
          builder.append(BOTTOM);
        }
        if (is_left)
        {
          builder.append(LEFT);
        }
        else
        {
          builder.append(RIGHT);
        }
        builder.append("S1E");
        is_top = false;
        is_left = false;
      }
      mode = COMPACT;
    }

    void execute()
    {
      jobber.execute();

    }

    void encode_default_move(int dx, int dy)
    {
      move_x(dx);
      move_y(dy);
      builder.append("S1P\n");
      mode = UNINIT;
    }

    void encode_speed(double speed)
    {
      builder.append(getSpeed(speed));
    }

    void move_x(int x)
    {
      if (0 < x)
      {
        builder.append(RIGHT);
        is_left = false;
      }
      else
      {
        builder.append(LEFT);
        is_left = true;
      }
      distance(Math.abs(x));
    }

    void move_y(int y)
    {
      if (0 < y)
      {
        builder.append(BOTTOM);
        is_top = false;
      }
      else
      {
        builder.append(TOP);
        is_top = true;
      }
      distance(Math.abs(y));
    }

    void move_diagonal(int v)
    {
      builder.append(DIAGONAL);
      distance(Math.abs(v));
    }

    void set_top()
    {
      if (!is_top)
      {
        builder.append(TOP);
      }
      is_top = true;
    }

    void set_bottom()
    {
      if (is_top)
      {
        builder.append(BOTTOM);
      }
      is_top = false;
    }

    void set_left()
    {
      if (!is_left)
      {
        builder.append(LEFT);
      }
      is_left = true;
    }

    void set_right()
    {
      if (is_left)
      {
        builder.append(RIGHT);
      }
      is_left = false;
    }

    void laser_on()
    {
      if (!is_on)
      {
        builder.append(LASER_ON);
      }
      is_on = true;
    }

    void laser_off()
    {
      if (is_on)
      {
        builder.append(LASER_OFF);
      }
      is_on = false;
    }

    void makeLine(int x0, int y0, int x1, int y1)
    {
      int dy = y1 - y0; //BRESENHAM LINE DRAW ALGORITHM
      int dx = x1 - x0;

      int stepx, stepy;

      if (dy < 0)
      {
        dy = -dy;
        stepy = -1;
        set_top();
      }
      else
      {
        stepy = 1;
        set_bottom();
      }

      if (dx < 0)
      {
        dx = -dx;
        stepx = -1;
        set_left();
      }
      else
      {
        stepx = 1;
        set_right();
      }
      int straight = 0;
      int diagonal = 0;

      if (dx > dy)
      {
        dy <<= 1;// dy is now 2*dy
        dx <<= 1;
        int fraction = dy - (dx >> 1);// same as 2*dx - dy
        while (x0 != x1)
        {
          if (fraction >= 0)
          {
            y0 += stepy;
            fraction -= dx;// same as fraction -= 2*dx
            if (straight != 0)
            {
              move_x(straight);
              straight = 0;
            }
            diagonal++;
          }
          else
          {
            if (diagonal != 0)
            {
              move_diagonal(diagonal);
              diagonal = 0;
            }
            straight += stepx;
          }
          x0 += stepx;
          fraction += dy;// same as fraction += 2*dy
        }
        if (straight != 0)
        {
          move_x(straight);
        }
        if (diagonal != 0)
        {
          move_diagonal(diagonal);
        }
      }
      else
      {
        dy <<= 1;
        dx <<= 1;
        int fraction = dx - (dy >> 1);
        while (y0 != y1)
        {
          if (fraction >= 0)
          {
            x0 += stepx;
            fraction -= dy;
            if (straight != 0)
            {
              move_y(straight);
              straight = 0;
            }
            diagonal++;
          }
          else
          {
            if (diagonal != 0)
            {
              move_diagonal(diagonal);
              diagonal = 0;
            }
            straight += stepy;
          }
          y0 += stepy;
          fraction += dx;
        }
        if (straight != 0)
        {
          move_y(straight);
        }
        if (diagonal != 0)
        {
          move_diagonal(diagonal);
        }
      }
    }

    public void distance(int v)
    {
      if (v >= 255)
      {
        int z_count = v / 255;
        v %= 255;
        for (int i = 0; i < z_count; i++)
        {
          builder.append("z");
        }
      }
      if (v > 51)
      {
        builder.append(String.format("%03d", v));
        return;
      }
      else if (v > 25)
      {
        builder.append('|');
        v -= 25;
      }
      if (v > 0)
      {
        builder.append((char) ('a' + (v - 1)));
      }
    }

    public int getGear(double mm_per_second)
    {
      if (mm_per_second < 7)
      {
        return 0;
      }
      if (mm_per_second < 25.4)
      {
        return 1;
      }
      if (mm_per_second < 60)
      {
        return 2;
      }
      if (mm_per_second < 127)
      {
        return 3;
      }
      return 4;
    }

    public String getSpeed(double mm_per_second)
    {
      mm_per_second = validateSpeed(mm_per_second);
      int gear = getGear(mm_per_second);
      double b;
      double m = 11148.0;
      if ("M2".equals(board))
      {
        switch (gear)
        {
          case 0:
            b = 8;
            m = 929.0;
            break;
          default:
            b = 5120.0;
            break;
          case 3:
            b = 5632.0;
            break;
          case 4:
            b = 6144.0;
            break;
        }
        return getSpeed(mm_per_second, m, b, gear, true);
      }
      if ("M".equals(board) || "M1".equals(board))
      {
        if (gear == 0)
        {
          gear = 1;
        }
        m = 11148.0;
        switch (gear)
        {
          default:
            b = 5120.0;
            break;
          case 3:
            b = 5632.0;
            break;
          case 4:
            b = 6144.0;
            break;
        }
        return getSpeed(mm_per_second, m, b, gear, "M1".equals(board));
      }
      if ("A".equals(board) || "B".equals(board) || "B1".equals(board))
      {
        if (gear == 0)
        {
          gear = 1;
        }
        m = 11148.0;
        switch (gear)
        {
          default:
            b = 5120.0;
            break;
          case 3:
            b = 5632.0;
            break;
          case 4:
            b = 6144.0;
            break;
        }
        return getSpeed(mm_per_second, m, b, gear, true);
      }
      if ("B2".equals(board))
      {
        m = 22296.0;
        switch (gear)
        {
          case 0:
            b = 784.0;
            m = 1858.0;
            break;
          default:
            b = 784.0;
            break;
          case 3:
            b = 896.0;
            break;
          case 4:
            b = 1024.0;
            break;
        }
        return getSpeed(mm_per_second, m, b, gear, true);
      }
      throw new UnsupportedOperationException("Board is not known.");
    }

    String getSpeed(double mm_per_second, double m, double b, int gear, boolean expanded)
    {
      boolean suffix_c = false;
      if (gear == 0)
      {
        gear = 1;
        suffix_c = true;
      }
      double frequency_kHz = mm_per_second / 25.4;
      double period_in_ms = 1.0 / frequency_kHz;
      double period_value = (m * period_in_ms) + b;
      int speed_value = 65536 - (int) Math.rint(period_value);
      if (speed_value < 0)
      {
        speed_value = 0;
      }
      if (speed_value > 65535)
      {
        speed_value = 65535;
      }
      if (!expanded)
      {
        if (suffix_c)
        {
          return String.format(
            "CV%03d%03d%1dC",
            (speed_value >> 8) & 0xFF, (speed_value & 0xFF),
            gear);
        }
      }
      int step_value = (int) mm_per_second;
      double d_ratio = 0.2612;
      double d_value = d_ratio * (m * period_in_ms) / (double) step_value;
      int diag_add = (int) d_value;
      if (suffix_c)
      {
        return String.format(
          "CV%03d%03d%1d%03d%03d%03dC",
          (speed_value >> 8) & 0xFF, (speed_value & 0xFF),
          gear,
          step_value,
          (diag_add >> 8) & 0xFF, (diag_add & 0xFF));
      }
      return String.format(
        "CV%03d%03d%1d%03d%03d%03d",
        (speed_value >> 8) & 0xFF, (speed_value & 0xFF),
        gear,
        step_value,
        (diag_add >> 8) & 0xFF, (diag_add & 0xFF));
    }

    double validateSpeed(double s)
    {
      if (s < 0.361)
      {
        return 0.361;
      }
      if (s > 240)
      {
        return 240;
      }
      return s;
    }
  }

  public class K40Queue
  {

    final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
    private final StringBuilder buffer = new StringBuilder();
    BaseUsb usb;

    public void open()
    {
      if (mock)
      {
        usb = new MockUsb();
      }
      else
      {
        usb = new K40Usb();
      }
      usb.open();
    }

    public void close()
    {
      usb.close();
      usb = null;
    }

    private void pad_buffer()
    {
      int len = K40Usb.PAYLOAD_LENGTH;
      int pad = (len - (buffer.length() % len)) % len;
      buffer.append("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF".subSequence(0, pad));
    }

    public void add(String element)
    {
      queue.add(element);
    }

    void add_wait()
    {
      add("-\n");
    }

    public void execute()
    {
      while (true)
      {
        boolean wait = false;
        while (!queue.isEmpty())
        {
          String element = queue.poll();
          if (element.endsWith("-\n"))
          {
            buffer.append(element.subSequence(0, element.length() - 2));
            pad_buffer();
            wait = true;
            break;
          }
          else if (element.endsWith("\n"))
          {
            buffer.append(element.subSequence(0, element.length() - 1));
            pad_buffer();
          }
          else
          {
            buffer.append(element);
          }
        } //moved as much of the queue to the buffer as we could.
        while (buffer.length() >= K40Usb.PAYLOAD_LENGTH)
        {
          if (usb != null)
          {
            usb.wait_for_ok();
            usb.send_packet(buffer.subSequence(0, K40Usb.PAYLOAD_LENGTH));
            buffer.delete(0, K40Usb.PAYLOAD_LENGTH);
          }
        } //all sendable packets sent.
        if (wait)
        {
          usb.wait_for_finish();
          wait = false;
        }
        if (queue.isEmpty())
        {
          break; //We finished.
        }
      }
    }

    public int size()
    {
      return queue.size();
    }

  }

  public interface BaseUsb
  {

    void open();

    void close();

    void wait_for_ok();

    void wait_for_finish();

    void send_packet(CharSequence s);
  }

  public class K40Usb implements BaseUsb
  {

    public static final int K40VENDERID = 0x1A86;
    public static final int K40PRODUCTID = 0x5512;

    public static final byte K40_ENDPOINT_WRITE = (byte) 0x02; //0x02  EP 2 OUT
    public static final byte K40_ENDPOINT_READ = (byte) 0x82; //0x82  EP 2 IN
    public static final byte K40_ENDPOINT_READ_I = (byte) 0x81; //0x81  EP 1 IN

    public static final int PAYLOAD_LENGTH = 30;

    private final IntBuffer transfered = IntBuffer.allocate(1);
    private final ByteBuffer request_status = ByteBuffer.allocateDirect(1);
    private final ByteBuffer packet = ByteBuffer.allocateDirect(34);

    private Context context = null;
    private Device device = null;
    private DeviceHandle handle = null;
    private boolean kernel_detached = false;
    private int interface_number = 0;

    public static final int STATUS_OK = 206;
    public static final int STATUS_CRC = 207;

    public static final int STATUS_FINISH = 236;
    public static final int STATUS_BUSY = 238;
    public static final int STATUS_POWER = 239;

    public int byte_0 = 0;
    public int status = 0;
    public int byte_2 = 0;
    public int byte_3 = 0;
    public int byte_4 = 0;
    public int byte_5 = 0;

    /**
     * ******************
     * CRC function via: License: 2-clause "simplified" BSD license Copyright
     * (C) 1992-2017 Arjen Lentz
     * https://lentz.com.au/blog/calculating-crc-with-a-tiny-32-entry-lookup-table
     * *******************
     */
    final int[] CRC_TABLE = new int[]
    {
      0x00, 0x5E, 0xBC, 0xE2, 0x61, 0x3F, 0xDD, 0x83,
      0xC2, 0x9C, 0x7E, 0x20, 0xA3, 0xFD, 0x1F, 0x41,
      0x00, 0x9D, 0x23, 0xBE, 0x46, 0xDB, 0x65, 0xF8,
      0x8C, 0x11, 0xAF, 0x32, 0xCA, 0x57, 0xE9, 0x74
    };

    private byte crc(ByteBuffer line)
    {
      int crc = 0;
      for (int i = 2; i < 32; i++)
      {
        crc = line.get(i) ^ crc;
        crc = CRC_TABLE[crc & 0x0f] ^ CRC_TABLE[16 + ((crc >> 4) & 0x0f)];
      }
      return (byte) crc;
    }
    //*//

    @Override
    public void open() throws LibUsbException
    {
      openContext();
      findK40();
      openHandle();
      checkConfig();
      detatchIfNeeded();
      claimInterface();
      LibUsb.controlTransfer(handle, (byte) 64, (byte) 177, (short) 258, (short) 0, packet, 50);
    }

    @Override
    public void close() throws LibUsbException
    {
      releaseInterface();
      closeHandle();
      if (kernel_detached)
      {
        reattachIfNeeded();
      }
      closeContext();
    }

    @Override
    public void send_packet(CharSequence cs)
    {
      if (cs.length() != PAYLOAD_LENGTH)
      {
        throw new LibUsbException("Packets must be exactly " + PAYLOAD_LENGTH + " bytes.", 0);
      }
      create_packet(cs);
      do
      {
        transmit_packet();
        update_status();
      }
      while (status == STATUS_CRC);
    }

    private void create_packet(CharSequence cs)
    {
      packet.clear();
      packet.put((byte) 166);
      packet.put((byte) 0);
      for (int i = 0; i < cs.length(); i++)
      {
        packet.put((byte) cs.charAt(i));
      }
      packet.put((byte) 166);
      packet.put(crc(packet));

    }

    private void transmit_packet()
    {

      transfered.clear();
      int results = LibUsb.bulkTransfer(handle, K40_ENDPOINT_WRITE, packet, transfered, 2000L);
      if (results == LibUsb.ERROR_TIMEOUT)

      {
        if (results < LibUsb.SUCCESS)
        {
          throw new LibUsbException("Packet Send Failed.", results);
        }
      }

    }

    private void update_status()
    {
      int results;

      transfered.clear();
      request_status.put(0, (byte) 160);
      results = LibUsb.bulkTransfer(handle, K40_ENDPOINT_WRITE, request_status, transfered, 2000L);

      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Status Request Failed.", results);

      }

      ByteBuffer read_buffer = ByteBuffer.allocateDirect(6);
      results = LibUsb.bulkTransfer(handle, K40_ENDPOINT_READ, read_buffer, transfered, 2000L);
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Status Update Failed", results);
      }

      if (transfered.get(0) == 6)
      {
        int next_0 = read_buffer.get(0) & 0xFF;
        int next_1 = read_buffer.get(1) & 0xFF;
        int next_2 = read_buffer.get(2) & 0xFF;
        int next_3 = read_buffer.get(3) & 0xFF;
        int next_4 = read_buffer.get(4) & 0xFF;
        int next_5 = read_buffer.get(5) & 0xFF;

        if ((byte_0 != next_0)
          //|| (status != next_0)
          || (byte_2 != next_2)
          || (byte_3 != next_3)
          || (byte_4 != next_4)
          || (byte_5 != next_5))
        {
          System.out.println(String.format("%d %d %d %d %d %d", next_0, next_1, next_2, next_3, next_4, next_5));
        }

        byte_0 = next_0;
        status = next_1;
        byte_2 = next_2;
        byte_3 = next_3;
        byte_4 = next_4;
        byte_5 = next_5;
      }
    }

    @Override
    public void wait_for_finish()
    {
      wait(STATUS_FINISH);
    }

    @Override
    public void wait_for_ok()
    {
      wait(STATUS_OK);
    }

    public void wait(int state)
    {
      while (true)
      {
        update_status();
        if (status == state)
        {
          break;
        }
        try
        {
          Thread.sleep(100);
        }
        catch (InterruptedException ex)
        {
        }
      }
    }

    //************************
    //USB Functions.
    //************************
    private void findK40() throws LibUsbException
    {
      DeviceList list = new DeviceList();
      try
      {
        int results;
        results = LibUsb.getDeviceList(context, list);
        if (results < LibUsb.SUCCESS)
        {
          throw new LibUsbException("Can't read device list.", results);
        }
        for (Device d : list)
        {
          DeviceDescriptor describe = new DeviceDescriptor();
          results = LibUsb.getDeviceDescriptor(d, describe);
          if (results < LibUsb.SUCCESS)
          {
            throw new LibUsbException("Can't read device descriptor.", results);
          }
          if ((describe.idVendor() == K40VENDERID) && (describe.idProduct() == K40PRODUCTID))
          {
            device = d;
            return;
          }
        }
      }
      finally
      {
        LibUsb.freeDeviceList(list, true);
      }
      throw new LibUsbException("Device was not found.", 0);
    }

    private void openContext() throws LibUsbException
    {
      context = new Context();
      int results = LibUsb.init(context);

      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Could not initialize.", results);
      }
    }

    private void closeContext()
    {
      if (context != null)
      {
        LibUsb.exit(context);
        context = null;
      }
    }

    private void closeHandle()
    {
      if (handle != null)
      {
        LibUsb.close(handle);
        handle = null;
      }
    }

    private void openHandle()
    {
      handle = new DeviceHandle();
      int results = LibUsb.open(device, handle);
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Could not open device handle.", results);
      }
    }

    private void claimInterface()
    {
      int results = LibUsb.claimInterface(handle, interface_number);
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Could not claim the interface.", results);
      }
    }

    private void releaseInterface()
    {
      if (handle != null)
      {
        LibUsb.releaseInterface(handle, interface_number);
      }
    }

    private void detatchIfNeeded()
    {
      if (LibUsb.hasCapability(LibUsb.CAP_SUPPORTS_DETACH_KERNEL_DRIVER)
        && LibUsb.kernelDriverActive(handle, interface_number) != 0)
      {
        int results = LibUsb.detachKernelDriver(handle, interface_number);
        if (results < LibUsb.SUCCESS)
        {
          throw new LibUsbException("Could not remove kernel driver.", results);
        }
        kernel_detached = true;
      }
    }

    private void reattachIfNeeded()
    {
      int results = LibUsb.attachKernelDriver(handle, interface_number);
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Could not reattach kernel driver", results);
      }
      kernel_detached = false;
    }

    private void checkConfig()
    {
      ConfigDescriptor config = new ConfigDescriptor();
      int results = LibUsb.getActiveConfigDescriptor(device, config);
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("configuration was not found.", results);
      }
      Interface iface = config.iface()[0];
      InterfaceDescriptor setting = iface.altsetting()[0];
      interface_number = setting.bInterfaceNumber();
      LibUsb.freeConfigDescriptor(config);
    }

    /*
    
    This is a valid endpoint, but I don't know what it should actually do.
    
    This shouldn't be called.
    
     */
    private void get_interupt()
    {
      transfered.clear();
      ByteBuffer read_buffer = ByteBuffer.allocateDirect(32);
      int results = LibUsb.interruptTransfer(handle, K40_ENDPOINT_READ_I, read_buffer, transfered, 2000L);
      if (results == LibUsb.ERROR_TIMEOUT)
      {
        return;
      }
      if (results < LibUsb.SUCCESS)
      {
        throw new LibUsbException("Data move failed.", results);
      }
    }

  }

  public class MockUsb implements BaseUsb
  {

    private void sleep(int time)
    {
      try
      {
        Thread.sleep(time);
      }
      catch (InterruptedException ex)
      {
      }
    }

    @Override
    public void open()
    {
      sleep(1000);
      System.out.println("Mock Usb Connected.");
    }

    @Override
    public void close()
    {
      sleep(1000);
      System.out.println("Mock Usb Disconnected.");
    }

    @Override
    public void wait_for_ok()
    {
      sleep(20);
      System.out.println("Mock Usb: OKAY!");
    }

    @Override
    public void send_packet(CharSequence subSequence)
    {
      sleep(100);
      System.out.println("Mock Packst Sent:" + subSequence);
    }

    @Override
    public void wait_for_finish()
    {
      sleep(4000);
      System.out.println("Mock Usb: Finished");
    }

  }

}
