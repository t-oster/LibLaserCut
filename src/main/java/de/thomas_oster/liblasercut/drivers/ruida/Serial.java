/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 * Copyright (C) 2018 - 2020 Klaus Kämpf <kkaempf@suse.de>
 * Copyright (C) 2018 Juergen Weigert <juergen@fabmail.org>
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
package de.thomas_oster.liblasercut.drivers.ruida;

/** either gnu.io or purejavacomm implement the SerialPort. Same API. **/
// import gnu.io.*;
import purejavacomm.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Serial {

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
