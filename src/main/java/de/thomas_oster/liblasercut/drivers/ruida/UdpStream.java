/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2018 - 2020 Klaus Kämpf <kkaempf@suse.de>
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


public class UdpStream extends OutputStream
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
