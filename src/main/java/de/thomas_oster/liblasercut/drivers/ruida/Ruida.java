/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2017 - 2020 Klaus Kämpf <kkaempf@suse.de>
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

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
/** either gnu.io or purejavacomm implement the SerialPort. Same API. **/
// import gnu.io.*;
// import purejavacomm.*;
import de.thomas_oster.liblasercut.drivers.ruida.Layer;
import de.thomas_oster.liblasercut.drivers.ruida.ByteStream;
import de.thomas_oster.liblasercut.drivers.ruida.Serial;
import de.thomas_oster.liblasercut.drivers.ruida.UdpStream;

/**
 * Support for ThunderLaser lasers, just vector cuts.
 *
/*  Based on FullSpectrumCutter
 *
 * @author Klaus Kämpf <kkaempf@suse.de>
 */

public class Ruida
{
  public static final int DEST_PORT = 50200; // fixed UDB port
  public static final int NETWORK_TIMEOUT = 3000;
  public static final int SOURCE_PORT = 40200; // used by rdworks in Windows
  private String name;
  /* overall bounding dimensions */
  private double boundingWidth = 0.0;
  private double boundingHeight = 0.0;
  private int boundingLayer = -1; // number of largest layer
  /* Layers */
  private ArrayList<Layer> layers;
  /* current layer */
  private Layer layer = null;
  /* output */
  private enum OutputTo { UNKNOWN, SERIAL, FILE, NETWORK }
  private OutputTo output_to = OutputTo.UNKNOWN;
  private OutputStream out;
  private Serial serial;
  private File file;
  private ByteStream stream;
  /* pseudo-colors
   * black, red, green, blue, yellow, magenta, cyan, white
   */
  private static final int[] red =   {0, 100,   0,   0, 100, 100,   0, 100 };
  private static final int[] green = {0,   0, 100,   0, 100,   0, 100, 100 };
  private static final int[] blue =  {0,   0,   0, 100,   0, 100, 100, 100 };

  public Ruida()
  {
    System.out.println("Ruida() constructor");
    this.stream = new ByteStream("Ruida");
  }

  public void setName(String name)
  {
    this.name = name;
  }

  /**
   * open file output connection
   * @sets out
   */
  public void openFile(String filename) throws IOException, Exception
  {
    System.out.println("Ruida.open - normal disk file \"" + filename + "\"");
    file = new File(filename);
    // a normal disk file
    out = new FileOutputStream(file);
    output_to = OutputTo.FILE;
  }
  
  /**
   * open network output connection
   * @sets out
   */
  public void openNetwork(String hostname) throws IOException, Exception
  {
    out = new UdpStream(hostname, DEST_PORT);
    output_to = OutputTo.NETWORK;
  }

  /**
   * open USB output connection
   * @sets out
   */
  public void openUsb(String device) throws IOException, Exception
  {
    if (!(serial instanceof Serial)) { // not open yet
      // the usb device, hopefully
      //
      try {
        System.out.println("Ruida.open - serial " + device);
        serial = new Serial();
        serial.open(device);
        out = serial.outputStream();
        stream.hex("DA000004"); // identify
        serial.read(16);
        output_to = OutputTo.SERIAL;
      }
      catch (Exception e) {
        System.out.println("Looks like '" + device + "' is not a serial device");
        throw e;
      }
    }
  }

  public void close() throws IOException, Exception
  {
    System.out.println("Ruida.close()");
    switch (output_to) {
    case SERIAL:
      serial.close();
      serial = null;
      // FALLTHRU
    case FILE:
    case NETWORK:
      out.close();
    }
    layers = null;
    output_to = OutputTo.UNKNOWN;
  }


  public void write() throws IOException
  {
    System.out.println("Ruida: write()");
    double travel_distance = 0.0;
    int layers_with_vectors = 0;
    upload();
    writeHeader();
    /* bounding box */
    layer = layers.get(this.boundingLayer);
    layer.writeBoundingBoxTo(stream);
    System.out.println("Ruida: " + layers.size() + " layers");
    /* layer declarations */
    for (int i = 0; i < layers.size(); i++)
    {
      layer = layers.get(i);
      if (layer.hasVectors()) {
        ByteStream s;
        layer.setNumber(layers_with_vectors);
        System.out.println("Layer " + i + " writing properties");
        layer.writePropertiesTo(stream);
        layers_with_vectors += 1;
      }
      else {
        System.out.println("Layer " + i + " has no vectors");
      }
    }
    layerCount(layers_with_vectors - 2);
    /* layer definitions */
    for (int i = 0; i < layers.size(); i++)
    {
      layer = layers.get(i);
      if (layer.hasVectors()) {
        ByteStream s;
        System.out.println("Layer " + i + " writing vectors");
        layer.writeVectorsTo(stream);
        travel_distance += layer.getTravelDistance();
      }
    }
    writeFooter(travel_distance);
    stream.writeTo(out);
  }

  public String getModelName()
  {
    System.out.println("Ruida.getModelName()");
    try {
      return new String(read("DA00057F")); // Version
    }
    catch (IOException e) {
      return "Failed";
    }
  }

  /**
   * startPart
   * starts a Raster, Raster3d, or VectorPart
   *
   * internally translated to Layer
   */
  public void startPart(double top_left_x, double top_left_y, double width, double height)
  {
    if (layers == null) {
      layers = new ArrayList<Layer>();
    }
    int number = layers.size(); // relative number of new layer
    if ((width > this.boundingWidth) || (height > this.boundingHeight)) {
      this.boundingWidth = width;
      this.boundingHeight = height;
      this.boundingLayer = number;
    }

    layer = new Layer(number);
    layer.setDimensions(top_left_x, top_left_y, top_left_x+width, top_left_y+height);
    if (number > 0) {
      // 'random' color
      layer.setRGB(red[number%8], green[number%8], blue[number%8]);
    }
    layers.add(layer);
  }

  /**
   * endPart
   * just here for completeness
   */
  public void endPart()
  {
    return;
  }

  public void setFocus(float focus)
  {
    layer.setFocus(focus);
  }

  public void setFrequency(int frequency)
  {
    layer.setFrequency(frequency);
  }

  public void setSpeed(int speed)
  {
    layer.setSpeed(speed);
  }

  public void setMinPower(int power)
  {
    layer.setMinPower(power);
  }

  public void setMaxPower(int power)
  {
    layer.setMaxPower(power);
  }

  public double getBedWidth() throws Exception
  {
    double value = 900.0;
//    System.out.println("Ruida.getBedWidth");
//    openUsb("/dev/ttyUSB0");
//    value = absValueAt(read("DA000026"), 0) / 1000.0;
//    close();
    return value;
  }

  public double getBedHeight() throws Exception
  {
    double value = 600.0;
//    System.out.println("Ruida.getBedHeight");
//    openUsb("/dev/ttyUSB0");
//    value = absValueAt(read("DA000036"), 0) / 1000.0;
//    close();
    return value;
  }

  /**
   * lineTo
   * coordinates in mm
   */
  public void lineTo(double x, double y) throws RuntimeException
  {
    layer.vectorTo(x, y, false);
  }

  /**
   * moveTo
   */
  public void moveTo(double x, double y) throws RuntimeException
  {
    layer.vectorTo(x, y, true);
  }

/*-------------------------------------------------------------------------*/

  private long absValueAt(byte[] data, int offset) throws Exception
  {
    if (data.length < offset + 5) {
      System.out.println("Insufficient data for absolute value");
      throw new Exception("Insufficient data for absolute value");
    }
    long result = 0;
    int factor = 1;
    for (int i = 4; i >= 0; i--) {
      int val = data[offset+i];
      result += val * factor;
      factor *= 127;
    }
    System.out.println(String.format("Ruida.absValueAt(%d) = %ld", offset, result));
    return result;
  }

  private byte[] read(String command) throws IOException
  {
    System.out.println("Ruida.read(" + command + ")");
    try {
      stream.hex(command).writeTo(out);
      byte[] data = ByteStream.decode(serial.read(32));
      if (data.length > 4) {
        return Arrays.copyOfRange(data, 4, data.length);
      }
      else {
        System.out.println("insufficient read !");
        return Arrays.copyOfRange(data, 0, 0);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      throw new IOException();
    }
  }

  /* upload as this.name */
  private void upload() throws IOException
  {
    /* PrepFilename, SetFilename, <filename>, 00 */
    stream.hex("E802").hex("E701").string(this.name).hex("00");
  }

  private void writeHeader() throws IOException
  {
    // identifier(); - where does this come from ?

    start();
    lightRed();
    feeding(0,0);
  }

  private void writeFooter(double travel_distance) throws IOException
  {
    workInterval(travel_distance);
    finish();
    stop();
    eof();
  }

  /**
   * layer count
   */
  private void layerCount(int count) throws IOException {
    if (count > 0) {
      stream.hex("CA22").integer(count);
    }
  }

  /**
   * Feeding
   */
  private void feeding(double x, double y) throws IOException {
    stream.hex("E706").absolute(x).absolute(y);
  }

  /**
   * write initial file identifier for model 644
   * @throws IOException
   */

  private void identifier() throws IOException
  {
    System.out.println("identifier() - where does this come from ?");
    stream.hex("D29BFA");
  }

  /**
   * start
   */
  private void start() throws IOException
  {
    stream.hex("F10200");
  }

  /**
   * lightRed
   */
  private void lightRed() throws IOException
  {
    stream.hex("D800");
  }

  /**
   * workInterval
   */
  private void workInterval(double distance) throws IOException
  {
    System.out.println("workInterval(" + distance + ")");
    stream.hex("da010620").absolute(distance).absolute(distance);
  }

  /**
   * finish
   */
  private void finish() throws IOException
  {
    stream.hex("EB");
  }

  /**
   * stop
   */
  private void stop() throws IOException
  {
    stream.hex("E700");
  }

  /**
   * eof
   */
  private void eof() throws IOException
  {
    stream.hex("D7");
  }

}
