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

import com.t_oster.liblasercut.LaserJob;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class EpilogRadius extends EpilogCutter
{
  
  protected String portName = "LPT1";
  private CommPort comport;
  
  public EpilogRadius()
  {
  }
  
  public EpilogRadius(String hostname)
  {
    super(hostname);
  }
  
  @Override
  public String getModelName()
  {
    return "Epilog Radius";
  }
  
  private static final double[] RESOLUTIONS = new double[]
  {
     75, 150, 200, 300, 400, 600, 1200
  };
  
  @Override
  public List<Double> getResolutions()
  {
    List<Double> result = new LinkedList();
    for (double r : RESOLUTIONS)
    {
      result.add(r);
    }
    return result;
  }
  
  @Override
  public EpilogRadius clone()
  {
    EpilogRadius result = new EpilogRadius();
    result.setHostname(this.getHostname());
    result.setPort(this.getPort());
    result.setBedHeight(this.getBedHeight());
    result.setBedWidth(this.getBedWidth());
    result.setAutoFocus(this.isAutoFocus());
    result.setPortName(portName);
    return result;
  }

  @Override
  protected void disconnect() throws IOException
  {
    comport.close();
  }

  @Override
  protected void connect() throws IOException, SocketTimeoutException
  {
    try
    {
      CommPortIdentifier identifier = null;
      if ("auto".equals(portName)) {
        Enumeration e = CommPortIdentifier.getPortIdentifiers();
        while (e.hasMoreElements())
        {
          identifier = (CommPortIdentifier) e.nextElement();
          if (identifier.getPortType() == CommPortIdentifier.PORT_PARALLEL)
          {
            break;
          }
        }
      }
      else {
        identifier = CommPortIdentifier.getPortIdentifier(portName);
      }
      comport = identifier.open("VisiCut", 1000);
      out = comport.getOutputStream();
      in = comport.getInputStream();
    }
    catch (NoSuchPortException ex)
    {
      Logger.getLogger(EpilogRadius.class.getName()).log(Level.SEVERE, null, ex);
    }
    catch (PortInUseException ex)
    {
      Logger.getLogger(EpilogRadius.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  
  
  @Override
  protected void sendPjlJob(LaserJob job, byte[] pjlData) throws UnknownHostException, UnsupportedEncodingException, IOException, Exception
  {
    PrintStream out = new PrintStream(this.out, true, "US-ASCII");
    out.write(pjlData);
  }

  //We need this methods for XMLEncoder to work properly
  @Override
  public boolean isAutoFocus()
  {
    return super.isAutoFocus();
  }

  public String getPortName()
  {
    return portName;
  }

  public void setPortName(String portName)
  {
    this.portName = portName;
  }
  
  @Override
  public void setAutoFocus(boolean b)
  {
    super.setAutoFocus(b);
  }
  
  @Override
  public void setBedHeight(double bh)
  {
    super.setBedHeight(bh);
  }
  
  @Override
  public double getBedHeight()
  {
    return super.getBedHeight();
  }
  
  @Override
  public void setBedWidth(double bh)
  {
    super.setBedWidth(bh);
  }
  
  @Override
  public double getBedWidth()
  {
    return super.getBedWidth();
  }
  
  @Override
  public void setHostname(String host)
  {
    super.setHostname(host);
  }
  
  @Override
  public String getHostname()
  {
    return super.getHostname();
  }
  
  @Override
  public int getPort()
  {
    return super.getPort();
  }
  
  @Override
  public void setPort(int p)
  {
    super.setPort(p);
  }
}
