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

package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.JobPart;
import de.thomas_oster.liblasercut.LaserCutter;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.LaserProperty;
import de.thomas_oster.liblasercut.ProgressListener;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;


/**
 * Support for full spectrum lasers, just vector cuts.
 * 
 * TODO:
 * - Add run perimeter action, show button on gui
 * - Add a acc/dec stage, there is jerking at high velocities
 * - Add a function to set the header/config parameters
 * - Calculate the width and height of the cut(to place into the header)
 * - Use threads to get status from port 12347
 * 
 * @author Volkan Vonk <vol.vonk@yandex.com> 
 */

public class FullSpectrumCutter extends LaserCutter
{
  
  protected static final String SETTING_HOST = "IP/Hostname";
  protected static final String SETTING_MAX_VECTOR_CUT_SPEED = "Max vector cutting speed";
  protected static final String SETTING_MAX_VECTOR_MOVE_SPEED = "Max vector move speed";
  protected static final String SETTING_MAX_POWER = "Max laser power";
  protected static final String SETTING_BED_WIDTH = "Bed width (mm)";
  protected static final String SETTING_BED_HEIGHT = "Bed height (mm)";
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

  // TODO: use valid values, must be in steps ( 1000 steps = 1 inch)
  private static final int width = 0;
  private static final int height = 0;
  
  
  public FullSpectrumCutter()
  {
    
  }
  /**
   * It is called, whenever VisiCut wants the driver to send a job to the lasercutter.
   * @param job This is an LaserJob object, containing all information on the job, which is to be sent
   * @param pl Use this object to inform VisiCut about the progress of your sending action. 
   * @param warnings If you there are warnings for the user, you can add them to this list, so they can be displayed by VisiCut
   * @throws IllegalJobException Throw this exception, when the job is not suitable for the current machine
   */
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    float power = 0;
    float speed = 100;
    float moving_speed = getMaxVectorMoveSpeed();
    float xsim = 0;
    float ysim = 0;
    
    ByteArrayOutputStream bosRawCmds = new ByteArrayOutputStream();
    ByteArrayOutputStream bosFullPacket = new ByteArrayOutputStream();

    pl.progressChanged(this, 0);
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    
    for (JobPart p : job.getParts())
    {
      //only accept VectorParts and add a warning for other parts.
      if (!(p instanceof VectorPart))
      {
        warnings.add("Non-vector parts are ignored by this driver.");
      }
      else
      {
        //get the real interface
        VectorPart vp = (VectorPart) p;
        //iterate over command list
        for (VectorCommand cmd : vp.getCommandList())
        {
          //There are three types of commands: MOVETO, LINETO and SETPROPERTY
          switch (cmd.getType())
          {
            case LINETO:
            {
              /**
               * Move the laserhead (laser on) from the current position to the x/y position of this command. 
               */
              // x/y in inches
              double x = Util.px2mm(cmd.getX(), p.getDPI())*0.0393701;
              double y = Util.px2mm(cmd.getY(), p.getDPI())*0.0393701;
              bosRawCmds.write(line(xsim,x,ysim,y,power,speed));
              
              // estimate the new real position
              xsim += Math.round((x-xsim)*1000)/1000f;
              ysim += Math.round((y-ysim)*1000)/1000f;
              break;
            }
            case MOVETO:
            {
              /**
               * Move the laserhead (laser off) from the current position to the x/y position of this command.
               */
              // x/y in inches
              double x = Util.px2mm(cmd.getX(), p.getDPI())*0.0393701;
              double y = Util.px2mm(cmd.getY(), p.getDPI())*0.0393701;
              bosRawCmds.write(line(xsim,x,ysim,y,0,moving_speed));
              
              // estimate the new real position
              xsim += Math.round((x-xsim)*1000)/1000f;
              ysim += Math.round((y-ysim)*1000)/1000f;
              break;
            }
            case SETPROPERTY:
            {
              /**
               * Change speed or power.
               */
              LaserProperty prop = cmd.getProperty();
              System.out.println("Changing Device Parameters:");
              for (String key : prop.getPropertyKeys())
              {
                String value = prop.getProperty(key).toString();
                System.out.println("  "+key+"="+value);
                if(key.equals("power"))
                  power=Float.parseFloat(value);
                if(key.equals("speed"))
                {
                  speed=Float.parseFloat(value);
                  speed=getMaxVectorCutSpeed()*speed/100f; // to steps per sec
                }
              }
              break;
            }
          }
        }
      }
    }
    
    // feeds the commands into packet generator
    bosFullPacket.write(generatePacket(bosRawCmds.toByteArray()));
    
    BufferedOutputStream italkout;
    BufferedOutputStream jobout;
    BufferedInputStream italkin;
    
    // connect to italk
    pl.taskChanged(this, "connecting");
    System.out.println("begin connection");
    
    Socket connection=new Socket();
    connection.connect(new InetSocketAddress(hostname, 12345), 3000);
    italkout = new BufferedOutputStream(connection.getOutputStream());
    italkin = new BufferedInputStream(connection.getInputStream()); 
    receiveResponse(italkin);
    pl.taskChanged(this, "sending");
    
    // sending protocol
    sendTextCmd("xjob\n",italkout);
    receiveResponse(italkin);
    
    // send: "immediate <size packet>\n"
    StringBuilder sb = new StringBuilder();
    sb.append("immediate ");
    sb.append(bosFullPacket.toByteArray().length);
    sb.append("\n");
    String msgSize = sb.toString();
    sendTextCmd(msgSize,italkout);
    receiveResponse(italkin);
    
    sendTextCmd("data\n",italkout);
    receiveResponse(italkin);
    
    // connect and send packet to port 12346
    Socket jobconn = new Socket();
    jobconn.connect(new InetSocketAddress(hostname, 12346), 3000);
    
    sendTextCmd("sending\n",italkout);
    receiveResponse(italkin);
    
    jobout = new BufferedOutputStream(jobconn.getOutputStream());
    jobout.write(bosFullPacket.toByteArray());
    jobout.flush();
    jobout.close();
    jobconn.close();
    
    receiveResponse(italkin);
    
    // begin job execution
    sendTextCmd("run\n",italkout);
    receiveResponse(italkin);
    
    waitjobend();
    
    System.out.println("End job");
    
    sendTextCmd("bye\n",italkout);
    receiveResponse(italkin);
    
    italkout.close();
    italkin.close();
    connection.close();
    
    pl.progressChanged(this, 100);
  }
  
  /**
   * Loops until the machine finish cutting
   */
  private void waitjobend() throws IOException
  {
    BufferedInputStream status_in;

    // conect to status port
    Socket status=new Socket();
    status.connect(new InetSocketAddress(hostname, 12347), 3000);
    status_in = new BufferedInputStream(status.getInputStream()); 
    
    byte countAction=0;
    byte[] statusPacket=new byte[68];
    
    // there must be 4 consecutive status packets with the same info to make sure the machine finish cutting
    while(countAction<4)
    {
      status_in.read(statusPacket, 0, 68);
      if (statusPacket[4]!=1) // 1 means cutting
      {
        countAction++;
      }
      else
      {
        countAction=0;
      }
      //clear queue
      while(status_in.available()!=0)
      {
        status_in.read();
      }
    }    
  }
  
  
  /**
   * Generates the full packet to send, given a set of raw machine commands.
   * @return the packet as a byte array
   */
  private byte[] generatePacket(byte[] rawCmds)throws IOException
  {
    byte[] header = new byte[1024];
    ByteArrayOutputStream packet = new ByteArrayOutputStream();

    // header
    header = generateHeader();
    packet.write(header);
    
    // job compressed contents
    packet.write(jobContents(rawCmds));
    
    return packet.toByteArray();
  }
  
  
  /**
   * Generates the header of the packet.
   */
  private byte[] generateHeader()
  {
    byte[] header = new byte[1024];
    
    for(int ii=0; ii<1024; ii++)
    {
      header[ii] = 0;
    }
    
    header[0] = 2; // vector cut
    header[512] = 5;
    header[668] = 23;
    
    ByteBuffer bb = ByteBuffer.allocate(132);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    
    bb.putInt((int)f1(EngraveAcceleration[0]));
    bb.putInt((int)f1(EngraveAcceleration[1]));
    bb.putInt((int)f1(EngraveAcceleration[2]));
    
    bb.putInt((int)EngraveMaxVelocity[0]);
    bb.putInt((int)EngraveMaxVelocity[1]);
    bb.putInt((int)EngraveMaxVelocity[2]);
    
    bb.putInt((int)f1(VectorAcceleration[0]));
    bb.putInt((int)f1(VectorAcceleration[1]));
    bb.putInt((int)f1(VectorAcceleration[2]));
    
    bb.putInt((int)VectorMaxVelocity[0]);
    bb.putInt((int)VectorMaxVelocity[1]);
    bb.putInt((int)VectorMaxVelocity[2]);
    
    bb.putInt((int)f1(JogAcceleration[0]));
    bb.putInt((int)f1(JogAcceleration[1]));
    bb.putInt((int)f1(JogAcceleration[2]));
    
    bb.putInt((int)JogMaxVelocity[0]);
    bb.putInt((int)JogMaxVelocity[1]);
    bb.putInt((int)JogMaxVelocity[2]);
    
    bb.putInt(0);
    bb.putInt(width);
    bb.putInt(height);
    
    bb.putInt(2);
    bb.putInt(2);
    bb.putInt(1);
    
    bb.put((byte)0);
    bb.put(FlipLaserOutput);
    bb.put(FlipLaserPWMPower);
    bb.put(LaserPowerMax.byteValue());
    bb.put(HomeDirection);
    
    bb.put(FlipHomeDirection);
    
    bb.put(LimitContCondition);
    
    bb.putInt((int)MaxSteps[0]);
    bb.putInt((int)MaxSteps[1]);
    bb.putInt((int)MaxSteps[2]);
    
    bb.putInt((int)TableSize[0]);
    bb.putInt((int)TableSize[1]);
    bb.putInt((int)TableSize[2]);
    
    System.arraycopy(bb.array(),0,header,536,132);
    return header;
  }
  
  
  /**
   * Implements the function f(x) = sqrt(2/x) 10^8
   */
  public int f1(long x)
  {
  return (int)(Math.sqrt(2 / (double)x) * Math.pow(10,8));
  }
  
  
  /**
   * Generates the load of the packet(packet=header+load) given a set of raw machine commands.
   */
  private byte[] jobContents(byte[] rawCmds)throws IOException
  {
    byte numberSubpackets = 0; // number of additional subpackets
    int remainder = 0; // remainder of commands
    
    ByteArrayOutputStream jobrawload = new ByteArrayOutputStream();
    ByteArrayOutputStream jobload = new ByteArrayOutputStream();

    numberSubpackets=(byte)((rawCmds.length+8)/0x40000);
    remainder=(((int)rawCmds.length%0x40000)/4);

    /* 
    The first step is to add a little header(raw_header) to the raw machine commands
    When the (raw_header+raw job)  exceeds 0x40000 bytes, it is divided in chunks of 0x40000
    bytes (the quantity of chunks of 0x40000 = numberSubpackets) and the remaining bytes are grouped
    in one additional subpacket, which would have a size of remainder+2 (or remainder*4 + 8  bytes)
    this additional 8 bytes are because of the added header
    The raw_header stores this info:
    02 00 00 00 <short remainder> <byte numberSubpackets> 00  
    At the end the relation
    number of commands = numberSubpackets*0x10000 + remainder
    must be true
    Where each command consists of 4 bytes
    */
    // add raw_header
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.put(new byte[] {2,0,0,0});
    bb.putShort((short)(remainder));
    bb.put(numberSubpackets);
    bb.put((byte)0);
    jobrawload.write(bb.array());
    
    // add raw commands
    jobrawload.write(rawCmds);
    
    byte[] raw_job = jobrawload.toByteArray(); // uncompressed raw_header and commands
    
    /* the final packet has the form 
    header + (number of subpackets = numberSubpackets+1) + 00 00
    zlib_string1.length + 00 00 + zlib_string1 +
    zlib_string2.length + 00 00 + zlib_string2 +
    ...
    
    Where the zlib strings are the result of compressing each subpacket and removing the zlib header
    */
    //so a little header is added before the zlib strings,(number of subpackets = numberSubpackets+1) + 00 00
    ByteBuffer bb1 = ByteBuffer.allocate(4);
    bb1.order(ByteOrder.LITTLE_ENDIAN);
    bb1.putShort((short)(numberSubpackets+1));
    bb1.put(new byte[] {0,0}); 
    jobload.write(bb1.array());
    
    // Then add the zlib strings one by one
    // "<size> 00 00 <zlib_string>"
    for(int kk=0; kk<numberSubpackets;kk++)
    {
      // first portion to compress
      byte[] tmpsub = Arrays.copyOfRange(raw_job, kk*0x40000, (kk+1)*0x40000); 
      
      compress_sub(tmpsub, jobload);
    }
    
    // last portion to compress (remaining bytes)
    byte[] tmpsub = Arrays.copyOfRange(raw_job, numberSubpackets*0x40000,numberSubpackets*0x40000+(remainder+2)*4 ); 
    
    compress_sub(tmpsub, jobload);
    
    return jobload.toByteArray();
  }
  
  /**
   * Compresses a byte array/subpacket with zlib, removes the first two bytes of the result
   * writes the new size and the resulting zlib string
   * zlib_string.length + 00 00 + zlib_string
   * to a ByteArrayOutputStream
   * @param tmpsub Array to compress
   * @param jobload ByteArrayOutputStream where the result will be written
   */
  
  private void compress_sub(byte [] tmpsub, ByteArrayOutputStream jobload) throws IOException
  {
    // begin compression
      Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
      deflater.setInput(tmpsub);
      deflater.finish();
      ByteArrayOutputStream bos = new ByteArrayOutputStream(tmpsub.length);
      byte[] buffer = new byte[1024];
      while(!deflater.finished())
      {
                    int bytesCompressed = deflater.deflate(buffer);
                    bos.write(buffer,0,bytesCompressed);
      }
      try
      {
        bos.close();
      }
      catch(IOException ioe)
      {
        System.out.println("Error while closing the stream : " + ioe);
      }

      //get the compressed byte array from output stream
      byte[] zlibString = bos.toByteArray();
      
      // add the size of the zlib string and 00 00
      ByteBuffer bb2 = ByteBuffer.allocate(4);
      bb2.order(ByteOrder.LITTLE_ENDIAN);
      bb2.putShort((short)(zlibString.length-2));
      bb2.putShort((short)0);

      // "<size of zlib string>  00 00"
      jobload.write(bb2.array());

      // write the result without the first two bytes
      jobload.write(Arrays.copyOfRange(zlibString,2,zlibString.length));
  }
  
  /**
   * Interpolator, given an initial point (x_start,y_start) and a destination point (x_dest,y_dest)
 with a given power and speed, x+ direction is to the right and
 y+ direction is downwards, at the machine table.
   * @param x_start initial x coordinate
   * @param x_dest destination x coordinate
   * @param y_start initial y coordinate
   * @param y_dest destination y coordinate
   * @param power as a percentage
   * @param speed in steps/sec
   * @return array of bytes with the machine commands, each command consist of 4 bytes
   */
  private byte[] line(double x_start,double x_dest,double y_start,double y_dest,double power, double speed)throws IOException
  { 
    ByteArrayOutputStream lineCmds = new ByteArrayOutputStream();
    double d = 0d; // distance of movement
    double speed_x = 0d; // average speed on x axis, signed
    double speed_y = 0d; // average speed on y axis, signed
    double x_expected = 0; // expected position on x axis
    double y_expected = 0; // expected position on y axis
    int x = 0; // position on x axis
    int y = 0; // position on y axis
    double t_increment = 1d/2000d; // time increments, the machine card executes 2000 commands per sec
    byte steps_x = 0; // steps to do on x axis, in one command, may be more than 1
    byte steps_y = 0; // steps to do on y axis, in one command, may be more than 1
    byte[] tempCmd = new byte[4]; // temporal byte array to build commands
    
    power = power * 255d / 100d;  // from percentage to byte value
    
    // to relative movement in steps
    x_dest = Math.round((x_dest-x_start)*1000);
    y_dest = Math.round((y_dest-y_start)*1000);
    
    // distance
    d = Math.sqrt(x_dest*x_dest + y_dest*y_dest);
    
    if(d==0d)
    {
      return new byte[] {}; // no line no commands
    }
    
    //speed components
    speed_x = x_dest * speed/d;
    speed_y = y_dest * speed/d;
    
    // the machine executes commands at a rate of 2000 commands per second
    // so the actual speed of the laser is modified by adding more or less
    // no move commands {0,0,0,ff}
    while(!((x_dest==x)&(y_dest==y)))
    {
      // if one coordinate reaches its end, then there is no move on that component
      if(y==y_dest)
        speed_y=0d;
      if(x==x_dest)
        speed_x=0d;
      
      // theorical position, real position x y should be very close
      x_expected = x_expected + t_increment*speed_x;
      y_expected = y_expected + t_increment*speed_y;
      
      // dont go further check
      if(Math.abs(x_expected)>Math.abs(x_dest)) 
        x_expected = x_dest;
      if(Math.abs(y_expected)>Math.abs(y_dest))
        y_expected = y_dest;
      
      // amount of steps to do in this cycle
      steps_x = (byte)(x_expected-x);
      steps_y = (byte)(y_expected-y);
      
      // the first two bits of the first byte, indicate direction on x y
      tempCmd[0]=0;
      if(steps_x>0)
        tempCmd[0]+=1;
      if(steps_y>0)
        tempCmd[0]+=2;
      
      tempCmd[1]=(byte)Math.abs(steps_x); // the second byte sets the magnitude of movement in x axis
      tempCmd[2]=(byte)Math.abs(steps_y); // the third byte sets the magnitude of movement in y axis
      tempCmd[3]=(byte)power; // the fourth byte sets power of laser
      
      lineCmds.write(tempCmd.clone()); 
      
      //update real relative position
      x += steps_x;
      y += steps_y;
    }
    return lineCmds.toByteArray();
  }
  
  
  /**
   * Sends text to a BufferedOutputStream, useful for sending text commands to machine card.
   */
  public void sendTextCmd(String textCmd, BufferedOutputStream out)throws IOException, InterruptedException
  {
    System.out.println("Sending command: "+textCmd);
    out.write(textCmd.getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }
  
  
  /**
   * Waits for response from a BufferedInputStream and prints the response.
   */
  public void receiveResponse(BufferedInputStream in)throws IOException
  {
    byte[] inmsg = new byte[512];
    int n=0;
    inmsg[n] = (byte) in.read(); // TODO: set a timeout
    while(((in.available()) != 0)&(n<512))
    {
      n++;
      inmsg[n]=(byte)in.read();
    }
    System.out.println(new String(inmsg));
  }
  
  @Override
  public FloatPowerSpeedProperty getLaserPropertyForVectorPart() {
      return new FloatPowerSpeedProperty();
  }
  
  /**
   * Returns a list of all supported resolutions (in DPI)
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]{100.0,200.0,500.0,1000.0});
  }

  protected Double BedWidth = 500d;
  /**
   * Returns the width of the laser-bed. 
   */
  @Override
  public double getBedWidth()
  {
    return (double)BedWidth;
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
  
  protected Double BedHeight = 300d;
  /**
   * Returns the height of the laser-bed. 
   */
  @Override
  public double getBedHeight()
  {
    return (double)BedHeight;
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
    return "Full Spectrum Cutter";
  }
  
  protected Integer LaserPowerMax = 180;
  
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

  /**
   * Copies the current instance with all config settings, because
   * it is used for save- and restoring
   */
  @Override
  public FullSpectrumCutter clone() {
    FullSpectrumCutter clone = new FullSpectrumCutter();
    clone.copyProperties(this);
    return clone;
  }

  private static String[] settingAttributes = new String[]
  {
    SETTING_HOST,
    SETTING_MAX_VECTOR_CUT_SPEED,
    SETTING_MAX_VECTOR_MOVE_SPEED,
    SETTING_MAX_POWER,
    SETTING_BED_WIDTH,
    SETTING_BED_HEIGHT
  };
  
  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOST.equals(attribute)) {
      return this.getHostname();
    }else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)){
      return this.getMaxVectorCutSpeed();}
    else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)){
      return this.getMaxVectorMoveSpeed();
    }else if (SETTING_MAX_POWER.equals(attribute)){
      return this.getLaserPowerMax();
    }else if (SETTING_BED_WIDTH.equals(attribute)){
      return this.getBedWidth();
    }else if (SETTING_BED_HEIGHT.equals(attribute)){
      return this.getBedHeight();
    } 
    return null;
  }
  
  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOST.equals(attribute)) {
      this.setHostname((String) value);
    }else if (SETTING_MAX_VECTOR_CUT_SPEED.equals(attribute)){
      this.setMaxVectorCutSpeed((Integer) value);
      }else if (SETTING_MAX_VECTOR_MOVE_SPEED.equals(attribute)){
      this.setMaxVectorMoveSpeed((Integer) value);
    }else if (SETTING_MAX_POWER.equals(attribute)){
      this.setLaserPowerMax((Integer) value);
    }else if (SETTING_BED_HEIGHT.equals(attribute)){
      this.setBedHeigth((Double) value);
    }else if (SETTING_BED_WIDTH.equals(attribute)){
      this.setBedWidth((Double) value);
    } 
  }
  
  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }
  
}
