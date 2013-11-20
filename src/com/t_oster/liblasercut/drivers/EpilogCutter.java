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
/**
 * Known Limitations:
 * - If there is Raster and Raster3d Part in one job, the speed from 3d raster
 * is taken for both and eventually other side effects:
 * IT IS NOT RECOMMENDED TO USE 3D-Raster and Raster in the same Job
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.*;
import com.t_oster.liblasercut.platform.Point;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
abstract class EpilogCutter extends LaserCutter
{

  public static boolean SIMULATE_COMMUNICATION = false;
  public static final int NETWORK_TIMEOUT = 3000;
  /* Resolutions in DPI */

  private static final int MINFOCUS = -500;//Minimal focus value (not mm)
  private static final int MAXFOCUS = 500;//Maximal focus value (not mm)
  private static final double FOCUSWIDTH = 0.0252;//How much mm/unit the focus values are
  private String hostname = "10.0.0.1";
  private int port = 515;
  private boolean autofocus = false;
  private transient InputStream in;
  private transient OutputStream out;

  private int mm2focus(float mm)
  {
    return (int) (mm / FOCUSWIDTH);
  }

  private float focus2mm(int focus)
  {
    return (float) (focus * FOCUSWIDTH);
  }

  public EpilogCutter()
  {
  }

  public EpilogCutter(String hostname)
  {
    this.hostname = hostname;
  }

  public String getHostname()
  {
    return this.hostname;
  }

  public void setHostname(String hostname)
  {
    this.hostname = hostname;
  }

  @Override
  public boolean isAutoFocus()
  {
    return this.autofocus;
  }

  public void setAutoFocus(boolean af)
  {
    this.autofocus = af;
  }

  private void waitForResponse(int expected) throws IOException, Exception
  {
    waitForResponse(expected, 3);
  }

  private void waitForResponse(int expected, int timeout) throws IOException, Exception
  {
    if (SIMULATE_COMMUNICATION)
    {
      return;
    }
    int result;
    out.flush();
    for (int i = 0; i < timeout * 10; i++)
    {
      if (in.available() > 0)
      {
        result = in.read();
        if (result == -1)
        {
          throw new IOException("End of Stream");
        }
        if (result != expected)
        {
          throw new Exception("unexpected Response: " + result);
        }
        return;
      }
      else
      {
        Thread.sleep(100 * timeout);
      }
    }
    throw new Exception("Timeout");

  }

  private byte[] generatePjlHeader(LaserJob job, double resolution) throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    /* Print the printer job language header. */
    out.printf("\033%%-12345X@PJL JOB NAME=%s\r\n", job.getTitle());
    out.printf("\033E@PJL ENTER LANGUAGE=PCL\r\n");
    if (this.isAutoFocus())
    {
      /* Set autofocus on. */
      out.printf("\033&y1A");
    }
    else
    {
      /* Set autofocus off. */
      out.printf("\033&y0A");
    }
    /* Set focus to 0. */
    out.printf("\033&y0C");
    /* UNKNOWN */
    out.printf("\033&y0Z");
    /* Left (long-edge) offset registration.  Adjusts the position of the
     * logical page across the width of the page.
     */
    out.printf("\033&l0U");
    /* Top (short-edge) offset registration.  Adjusts the position of the
     * logical page across the length of the page.
     */
    out.printf("\033&l0Z");
    /* Resolution of the print. Number of Units/Inch*/
    out.printf("\033&u%dD", (int) resolution);
    /* X position = 0 */
    out.printf("\033*p0X");
    /* Y position = 0 */
    out.printf("\033*p0Y");
    return result.toByteArray();
  }

  private byte[] generatePjlFooter() throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");

    /* Footer for printer job language. */
    /* Reset */
    out.printf("\033E");
    /* Exit language. */
    out.printf("\033%%-12345X");
    /* End job. */
    out.printf("@PJL EOJ \r\n");
    return result.toByteArray();
  }

  private void sendPjlJob(LaserJob job, byte[] pjlData) throws UnknownHostException, UnsupportedEncodingException, IOException, Exception
  {
    String localhost;
    try
    {
      localhost = java.net.InetAddress.getLocalHost().getHostName();
    }
    catch (UnknownHostException e)
    {
      localhost = "unknown";
    }
    PrintStream out = new PrintStream(this.out, true, "US-ASCII");
    out.print("\002\n");
    waitForResponse(0);
    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    PrintStream stmp = new PrintStream(tmp, true, "US-ASCII");
    stmp.printf("H%s\n", localhost);
    stmp.printf("P%s\n", job.getUser());
    stmp.printf("J%s\n", job.getTitle());
    stmp.printf("ldfA%s%s\n", job.getName(), localhost);
    stmp.printf("UdfA%s%s\n", job.getName(), localhost);
    stmp.printf("N%s\n", job.getTitle());
    out.printf("\002%d cfA%s%s\n", tmp.toByteArray().length, job.getName(), localhost);
    waitForResponse(0);
    out.write(tmp.toByteArray());
    out.append((char) 0);
    waitForResponse(0);
    /* Send the Job length and name to the queue */
    out.printf("\003%d dfA%s%s\n", pjlData.length, job.getName(), localhost);
    waitForResponse(0);
    /* Send the real PJL Job */
    out.write(pjlData);
    waitForResponse(0);
  }

  private void connect() throws IOException, SocketTimeoutException
  {
    if (SIMULATE_COMMUNICATION)
    {
      out = System.out;
    }
    else
    {
      Socket connection = new Socket();
      connection.connect(new InetSocketAddress(hostname, port), NETWORK_TIMEOUT);
      in = new BufferedInputStream(connection.getInputStream());
      out = new BufferedOutputStream(connection.getOutputStream());
    }
  }

  private void disconnect() throws IOException
  {
    if (!SIMULATE_COMMUNICATION)
    {
      in.close();
      out.close();
    }
  }

  @Override
  protected void checkJob(LaserJob job) throws IllegalJobException
  {
    super.checkJob(job);
    for (JobPart p : job.getParts())
    {
      if (p instanceof VectorPart)
      {
        for (VectorCommand cmd : ((VectorPart) p).getCommandList())
        {
          if (cmd.getType() == VectorCommand.CmdType.SETPROPERTY)
          {
            if (!(cmd.getProperty() instanceof PowerSpeedFocusFrequencyProperty))
            {
              throw new IllegalJobException("This driver expects Power,Speed,Frequency and Focus as settings");
            }
            float focus = ((PowerSpeedFocusFrequencyProperty) cmd.getProperty()).getFocus();
            if (mm2focus(focus) > MAXFOCUS || (mm2focus(focus)) < MINFOCUS)
            {
              throw new IllegalJobException("Illegal Focus value. This Lasercutter supports values between"
                + focus2mm(MINFOCUS) + "mm to " + focus2mm(MAXFOCUS) + "mm.");
            }
          }
        }
      }
      if (p instanceof RasterPart)
      {
        RasterPart rp = ((RasterPart) p);
        if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
        {
          throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
        }
        float focus = rp.getLaserProperty() == null ? 0 : ((PowerSpeedFocusProperty) rp.getLaserProperty()).getFocus();
        if (mm2focus(focus) > MAXFOCUS || (mm2focus(focus)) < MINFOCUS)
        {
          throw new IllegalJobException("Illegal Focus value. This Lasercutter supports values between"
            + focus2mm(MINFOCUS) + "mm to " + focus2mm(MAXFOCUS) + "mm.");
        }
      }
      if (p instanceof Raster3dPart)
      {
        Raster3dPart rp = (Raster3dPart) p;
        if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
        {
          throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
        }
        float focus = rp.getLaserProperty() == null ? 0 : ((PowerSpeedFocusProperty) rp.getLaserProperty()).getFocus();
        if (mm2focus(focus) > MAXFOCUS || (mm2focus(focus)) < MINFOCUS)
        {
          throw new IllegalJobException("Illegal Focus value. This Lasercutter supports values between"
            + focus2mm(MINFOCUS) + "mm to " + focus2mm(MAXFOCUS) + "mm.");
        }
      }
    }
  }

  public void realSendJob(LaserJob job, ProgressListener pl, int number, int count) throws UnsupportedEncodingException, IOException, UnknownHostException, Exception
  {
    job.applyStartPoint();
    String nb = count > 1 ? "("+number+"/"+count+")" : "";
    pl.taskChanged(this, "generating"+nb);
    //Generate all the data
    byte[] pjlData = generatePjlData(job);
    pl.progressChanged(this, (int) ((double) 40*number/count));
    //connect to lasercutter
    pl.taskChanged(this, "connecting"+nb);
    connect();
    pl.progressChanged(this, (int) ((double) 60*number/count));
    //send job
    pl.taskChanged(this, "sending"+nb);
    sendPjlJob(job, pjlData);
    pl.progressChanged(this, (int) ((double) 90*number/count));
    //disconnect
    disconnect();
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, SocketTimeoutException, UnsupportedEncodingException, IOException, UnknownHostException, Exception
  {
    pl.progressChanged(this, 0);
    pl.taskChanged(this, "checking job");
    //Perform santiy checks
    checkJob(job);
    //split the job because epilog doesn't support many combinations
    List<List<JobPart>> jobs = new LinkedList<List<JobPart>>();
    List<JobPart> toDo = job.getParts();
    while(!toDo.isEmpty())
    {
      List<JobPart> currentSplit = new LinkedList<JobPart>();
      if (toDo.get(0) instanceof Raster3dPart)
      {//raster3d part stands alone
        currentSplit.add(toDo.get(0));
        toDo.remove(0);
      }
      else
      {//vector parts can be prepended by one raster part, but one job has
       //to have the same resolution everywhere (??? if you have time, feel
       //free to experiment)
        double currentDpi = toDo.get(0).getDPI();
        if (toDo.get(0) instanceof RasterPart)
        {
          currentSplit.add(toDo.get(0));
          toDo.remove(0);
        }
        while (!toDo.isEmpty() && toDo.get(0) instanceof VectorPart && toDo.get(0).getDPI() == currentDpi)
        {
          currentSplit.add(toDo.get(0));
          toDo.remove(0);
        }
      }
      jobs.add(currentSplit);
    }
    int number = 0;
    int size = jobs.size();
    if (size > 1)
    {
      warnings.add("The job had to be split into "+size+" jobs.");
    }
    for(List<JobPart> current : jobs)
    {
      number++;
      LaserJob j = new LaserJob((size > 1 ? "("+number+"/"+size+")" : "" )+job.getTitle(), job.getName(), job.getUser());
      j.setStartPoint(job.getStartX(), job.getStartY());
      for (JobPart p:current)
      {
        j.addPart(p);
      }
      this.realSendJob(j, pl, number, size);
    }
    pl.progressChanged(this, 100);
  }

  @Override
  abstract public List<Double> getResolutions();

  /**
   * Encodes the given line of the given image in TIFF Packbyte encoding
   */
  public List<Byte> encode(List<Byte> line)
  {
    int idx = 0;
    int r = line.size();
    List<Byte> result = new LinkedList<Byte>();
    while (idx < r)
    {
      int p;
      p = idx + 1;
      while (p < r && p < idx + 128 && line.get(p) == line.get(idx))
      {
        p++;
      }
      if (p - idx >= 2)
      {
        // run length
        result.add((byte) (1 - (p - idx)));
        result.add((byte) line.get(idx));
        idx = p;
      }
      else
      {
        p = idx;
        while (p < r && p < idx + 127
          && (p + 1 == r || line.get(p)
          != line.get(p + 1)))
        {
          p++;
        }
        result.add((byte) (p - idx - 1));
        while (idx < p)
        {
          result.add((byte) (line.get(idx++)));
        }
      }
    }
    return result;
  }

  private byte[] generateRaster3dPCL(Raster3dPart rp) throws UnsupportedEncodingException, IOException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    if (rp != null)
    {
      PowerSpeedFocusProperty prop = (PowerSpeedFocusProperty) rp.getLaserProperty();
      /* PCL/RasterGraphics resolution. */
      out.printf("\033*t%dR", (int) rp.getDPI());
      /* Raster Orientation: Printed in current direction */
      out.printf("\033*r0F");
      /* Raster power */
      out.printf("\033&y%dP", prop.getPower());
      /* Raster speed */
      out.printf("\033&z%dS", prop.getSpeed());
      /* Focus */
      out.printf("\033&y%dA", mm2focus(prop.getFocus()));

      out.printf("\033*r%dT", rp != null ? rp.getMaxY() : 10);//height);
      out.printf("\033*r%dS", rp != null ? rp.getMaxX() : 10);//width);
            /* Raster compression:
       *  2 = TIFF encoding
       *  7 = TIFF encoding, 3d-mode,
       *
       * Wahrscheinlich:
       * 2M = Bitweise, also 1=dot 0=nodot (standard raster)
       * 7MLT = Byteweise 0= no power 100=full power (3d raster)
       */
      out.printf("\033*b%dMLT", 7);
      /* Raster direction (1 = up, 0=down) */
      out.printf("\033&y%dO", 0);
      /* start at current position */
      out.printf("\033*r1A");
      Point sp = rp.getRasterStart();
      boolean leftToRight = true;
      for (int y = 0; y < rp.getRasterHeight(); y++)
      {
        List<Byte> line = rp.getInvertedRasterLine(y);
        for (int n = 0; n < line.size(); n++)
        {//Apperantly the other power settings are ignored, so we have to scale
          int x = line.get(n);
          x = x >= 0 ? x : 256 + x;
          int scalex = x * prop.getPower() / 100;
          byte bx = (byte) (scalex < 128 ? scalex : scalex - 256);
          line.set(n, bx);
        }
        //Remove leading zeroes, but keep track of the offset
        int jump = 0;

        while (line.size() > 0 && line.get(0) == 0)
        {
          line.remove(0);
          jump++;
        }
        if (line.size() > 0)
        {
          out.printf("\033*p%dX", sp.x + jump);
          out.printf("\033*p%dY", sp.y + y);
          if (leftToRight)
          {
            out.printf("\033*b%dA", line.size());
          }
          else
          {
            out.printf("\033*b%dA", -line.size());
            Collections.reverse(line);
          }
          line = encode(line);
          int len = line.size();
          int pcks = len / 8;
          if (len % 8 > 0)
          {
            pcks++;
          }
          out.printf("\033*b%dW", pcks * 8);
          for (byte s : line)
          {
            out.write(s);
          }
          for (int k = 0; k < 8 - (len % 8); k++)
          {
            out.write((byte) 128);
          }
          leftToRight = !leftToRight;
        }
      }
      out.printf("\033*rC");       // end raster
    }
    return result.toByteArray();
  }

  private byte[] generateDummyRaster(JobPart jp) throws UnsupportedEncodingException
  {
    PowerSpeedFocusProperty prop = new PowerSpeedFocusProperty();
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    /* PCL/RasterGraphics resolution. */
    out.printf("\033*t%dR", (int) jp.getDPI());
    /* Raster Orientation: Printed in current direction */
    out.printf("\033*r0F");
    /* Raster power */
    out.printf("\033&y%dP", prop.getPower());
    /* Raster speed */
    out.printf("\033&z%dS", prop.getSpeed());
    /* Focus */
    out.printf("\033&y%dA", mm2focus(prop.getFocus()));

    out.printf("\033*r%dT", jp.getMaxY());//height);
    out.printf("\033*r%dS", jp.getMaxX());//width);
        /* Raster compression:
     *  2 = TIFF encoding
     *  7 = TIFF encoding, 3d-mode,
     *
     * Wahrscheinlich:
     * 2M = Bitweise, also 1=dot 0=nodot (standard raster)
     * 7MLT = Byteweise 0= no power 100=full power (3d raster)
     */
    out.printf("\033*b2M");
    /* Raster direction (1 = up, 0=down) */
    out.printf("\033&y%dO", 0);
    /* start at current position */
    out.printf("\033*r1A");
    out.printf("\033*rC");       // end raster
    return result.toByteArray();
  }

  private byte[] generateRasterPCL(RasterPart rp) throws UnsupportedEncodingException, IOException
  {
    PowerSpeedFocusProperty prop = (PowerSpeedFocusProperty) rp.getLaserProperty();
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    /* PCL/RasterGraphics resolution. */
    out.printf("\033*t%dR", (int) rp.getDPI());
    /* Raster Orientation: Printed in current direction */
    out.printf("\033*r0F");
    /* Raster power */
    out.printf("\033&y%dP", prop.getPower());
    /* Raster speed */
    out.printf("\033&z%dS", prop.getSpeed());
    /* Focus */
    out.printf("\033&y%dA", mm2focus(prop.getFocus()));

    out.printf("\033*r%dT", rp.getMaxY());//height);
    out.printf("\033*r%dS", rp.getMaxX());//width);
        /* Raster compression:
     *  2 = TIFF encoding
     *  7 = TIFF encoding, 3d-mode,
     *
     * Wahrscheinlich:
     * 2M = Bitweise, also 1=dot 0=nodot (standard raster)
     * 7MLT = Byteweise 0= no power 100=full power (3d raster)
     */
    out.printf("\033*b2M");
    /* Raster direction (1 = up, 0=down) */
    out.printf("\033&y%dO", 0);
    /* start at current position */
    out.printf("\033*r1A");

    if (rp != null)
    {
      Point sp = rp.getRasterStart();
      boolean leftToRight = true;
      for (int y = 0; y < rp.getRasterHeight(); y++)
      {
        List<Byte> line = rp.getRasterLine(y);
        //Remove leading zeroes, but keep track of the offset
        int jump = 0;
        while (line.size() > 0 && line.get(0) == 0)
        {
          line.remove(0);
          jump++;
        }
        //Remove trailing zeroes
        while (line.size() > 0 && line.get(line.size()-1) == 0)
        {
          line.remove(line.size()-1);
        }
        if (line.size() > 0)
        {
          out.printf("\033*p%dX", sp.x + jump * 8);
          out.printf("\033*p%dY", sp.y + y);
          if (leftToRight)
          {
            out.printf("\033*b%dA", line.size());
          }
          else
          {
            out.printf("\033*b%dA", -line.size());
            Collections.reverse(line);
          }
          line = encode(line);
          int len = line.size();
          int pcks = len / 8;
          if (len % 8 > 0)
          {
            pcks++;
          }
          /**
           * Number of Pixels in a row??
           * or b2m%dW for TIFF encoding?
           * Or number of Bytes in a row? who knows
           * in ctrl-cut its number of packed bytes
           */
          out.printf("\033*b%dW", pcks * 8);
          for (byte s : line)
          {
            out.write(s);
          }
          for (int k = 0; k < 8 - (len % 8); k++)
          {
            out.write((byte) 128);
          }
          leftToRight = !leftToRight;
        }
      }
    }
    out.printf("\033*rC");       // end raster
    return result.toByteArray();
  }

  private byte[] generateDummyVector(double dpi) throws UnsupportedEncodingException
  {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    out.printf("\033%%1B");// Start HLGL
    out.printf("IN;PU0,0;");
    //Reset Focus to 0
    out.printf("WF%d;", 0);
    return result.toByteArray();
  }

  private byte[] generateVectorPCL(VectorPart vp) throws UnsupportedEncodingException
  {
    //TODO: Test if the resolution settings have an effect
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(result, true, "US-ASCII");
    /* Resolution of the print. Number of Units/Inch*/
    out.printf("\033%%1B");// Start HLGL
    out.printf("IN;PU0,0;");

    if (vp != null)
    {
      Integer currentPower = null;
      Integer currentSpeed = null;
      Integer currentFrequency = null;
      Float currentFocus = null;
      VectorCommand.CmdType lastType = null;
      for (VectorCommand cmd : vp.getCommandList())
      {
        if (lastType != null && lastType == VectorCommand.CmdType.LINETO && cmd.getType() != VectorCommand.CmdType.LINETO)
        {
          out.print(";");
        }
        switch (cmd.getType())
        {
          case SETPROPERTY:
          {
            PowerSpeedFocusFrequencyProperty p = (PowerSpeedFocusFrequencyProperty) cmd.getProperty();
            if (currentFocus == null || !currentFocus.equals(p.getFocus()))
            {
              out.printf("WF%d;", mm2focus(p.getFocus()));
              currentFocus = p.getFocus();
            }
            if (currentFrequency == null || !currentFrequency.equals(p.getFrequency()))
            {
              out.printf("XR%04d;", p.getFrequency());
              currentFrequency = p.getFrequency();
            }
            if (currentPower == null || !currentPower.equals(p.getPower()))
            {
              out.printf("YP%03d;", p.getPower());
              currentPower = p.getPower();
            }
            if (currentSpeed == null || !currentSpeed.equals(p.getSpeed()))
            {
              out.printf("ZS%03d;", p.getSpeed());
              currentSpeed = p.getSpeed();
            }
            break;
          }
          case MOVETO:
          {
            out.printf("PU%d,%d;", cmd.getX(), cmd.getY());
            break;
          }
          case LINETO:
          {
            if (lastType == null || lastType != VectorCommand.CmdType.LINETO)
            {
              out.printf("PD%d,%d", cmd.getX(), cmd.getY());
            }
            else
            {
              out.printf(",%d,%d", cmd.getX(), cmd.getY());
            }
            break;
          }
        }
        lastType = cmd.getType();
      }
    }
    //Reset Focus to 0
    out.printf("WF%d;", 0);
    return result.toByteArray();
  }

  private byte[] generatePjlData(LaserJob job) throws UnsupportedEncodingException, IOException
  {
    /* Generate complete PJL Job */
    ByteArrayOutputStream pjlJob = new ByteArrayOutputStream();
    PrintStream wrt = new PrintStream(pjlJob, true, "US-ASCII");

    wrt.write(generatePjlHeader(job, job.getParts().get(0).getDPI()));
    if (! (job.getParts().get(0) instanceof RasterPart))
    {//we need an empty raster part as begin of all jobs
      wrt.write(generateDummyRaster(job.getParts().get(0)));
    }
    for (JobPart p : job.getParts())
    {
      if (p instanceof VectorPart)
      {
        wrt.write(generateVectorPCL((VectorPart) p));
      }
      else if (p instanceof RasterPart)
      {
        wrt.write(generateRasterPCL((RasterPart) p));
      }
      else if (p instanceof Raster3dPart)
      {
        wrt.write(generateRaster3dPCL((Raster3dPart) p));
      }
    }
    if (! (job.getParts().get(job.getParts().size()-1) instanceof VectorPart))
    {
      wrt.write(generateDummyVector(job.getParts().get(job.getParts().size()-1).getDPI()));
    }
    wrt.write(generatePjlFooter());
    /* Pad out the remainder of the file with 0 characters. */
    for (int i = 0; i < 4096; i++)
    {
      wrt.append((char) 0);
    }
    wrt.flush();
    return pjlJob.toByteArray();
  }

  public int getPort()
  {
    return this.port;
  }

  public void setPort(int Port)
  {
    this.port = Port;
  }

  @Override
  public Object getProperty(String attribute)
  {
    if ("Hostname".equals(attribute))
    {
      return this.getHostname();
    }
    else if ("AutoFocus".equals(attribute))
    {
      return (Boolean) this.isAutoFocus();
    }
    else if ("Port".equals(attribute))
    {
      return (Integer) this.getPort();
    }
    else if ("BedWidth".equals(attribute))
    {
      return (Double) this.getBedWidth();
    }
    else if ("BedHeight".equals(attribute))
    {
      return (Double) this.getBedHeight();
    }
    return null;
  }
  protected double bedWidth = 600;

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
  protected double bedHeight = 300;

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

  @Override
  public void setProperty(String attribute, Object value)
  {
    if ("Hostname".equals(attribute))
    {
      this.setHostname((String) value);
    }
    else if ("AutoFocus".endsWith(attribute))
    {
      this.setAutoFocus((Boolean) value);
    }
    else if ("Port".equals(attribute))
    {
      this.setPort((Integer) value);
    }
    else if ("BedWidth".equals(attribute))
    {
      this.setBedWidth((Double) value);
    }
    else if ("BedHeight".equals(attribute))
    {
      this.setBedHeight((Double) value);
    }
  }
  private static String[] attributes = new String[]
  {
    "Hostname", "Port", "BedWidth", "BedHeight", "AutoFocus"
  };

  @Override
  public String[] getPropertyKeys()
  {
    return attributes;
  }

  @Override
  public boolean canEstimateJobDuration()
  {
    return true;
  }

  @Override
  public int estimateJobDuration(LaserJob job)
  {
    double VECTOR_MOVESPEED_X = 20000d / 4.5;
    double VECTOR_MOVESPEED_Y = 10000d / 2.5;
    double VECTOR_LINESPEED = 20000d / 36.8;
    double RASTER_LINEOFFSET = 0.08d;
    double RASTER_LINESPEED = 100000d / ((268d / 50) - RASTER_LINEOFFSET);
    //TODO: The Raster3d values are not tested yet, theyre just copies
    double RASTER3D_LINEOFFSET = 0.08;
    double RASTER3D_LINESPEED = 100000d / ((268d / 50) - RASTER3D_LINEOFFSET);

    //Holds the current Laser Head position in Pixels
    Point p = new Point(0, 0);

    double result = 0;//usual offset
    for (JobPart jp : job.getParts())
    {
      if (jp instanceof RasterPart)
      {
        RasterPart rp = (RasterPart) jp;
        Point sp = rp.getRasterStart();
        result += Math.max((double) (p.x - sp.x) / VECTOR_MOVESPEED_X,
          (double) (p.y - sp.y) / VECTOR_MOVESPEED_Y);
        double linespeed = ((double) RASTER_LINESPEED * ((PowerSpeedFocusProperty) rp.getLaserProperty()).getSpeed()) / 100;
        for (int y = 0; y < rp.getRasterHeight(); y++)
        {//Find any black point
          boolean lineEmpty = true;
          for (byte b : rp.getRasterLine(y))
          {
            if (b != 0)
            {
              lineEmpty = false;
              break;
            }
          }
          if (!lineEmpty)
          {
            int w = rp.getRasterWidth();
            result += (double) RASTER_LINEOFFSET + (double) w / linespeed;
            p.x = sp.y % 2 == 0 ? sp.x + w : sp.x;
            p.y = sp.y + y;
          }
          else
          {
            result += RASTER_LINEOFFSET;
          }
        }
      }
      if (jp instanceof Raster3dPart)
      {
        Raster3dPart rp = (Raster3dPart) jp;
        Point sp = rp.getRasterStart();
        result += Math.max((double) (p.x - sp.x) / VECTOR_MOVESPEED_X,
          (double) (p.y - sp.y) / VECTOR_MOVESPEED_Y);
        double linespeed = ((double) RASTER3D_LINESPEED * ((PowerSpeedFocusProperty) rp.getLaserProperty()).getSpeed()) / 100;
        for (int y = 0; y < rp.getRasterHeight(); y++)
        {//Check if
          boolean lineEmpty = true;
          for (byte b : rp.getRasterLine(y))
          {
            if (b != 0)
            {
              lineEmpty = false;
              break;
            }
          }
          if (!lineEmpty)
          {
            int w = rp.getRasterWidth();
            result += (double) RASTER3D_LINEOFFSET + (double) w / linespeed;
            p.x = sp.y % 2 == 0 ? sp.x + w : sp.x;
            p.y = sp.y + y;
          }
        }
      }
      if (jp instanceof VectorPart)
      {
        double speed = VECTOR_LINESPEED;
        VectorPart vp = (VectorPart) jp;
        for (VectorCommand cmd : vp.getCommandList())
        {
          switch (cmd.getType())
          {
            case SETPROPERTY:
            {
              speed = VECTOR_LINESPEED * ((PowerSpeedFocusFrequencyProperty) cmd.getProperty()).getSpeed() / 100;
              break;
            }
            case MOVETO:
              result += Math.max((double) (p.x - cmd.getX()) / VECTOR_MOVESPEED_X,
                (double) (p.y - cmd.getY()) / VECTOR_MOVESPEED_Y);
              p = new Point(cmd.getX(), cmd.getY());
              break;
            case LINETO:
              double dist = distance(cmd.getX(), cmd.getY(), p);
              p = new Point(cmd.getX(), cmd.getY());
              result += dist / speed;
              break;
          }
        }
      }
    }
    return (int) result;
  }

  private double distance(int x, int y, Point p)
  {
    return Math.sqrt(Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2));
  }

}
