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

/*
  Author: Sven Jung <sven.jung@rwth-aachen.de>
 */

package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.*;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;

/**
 *
 * @author Sven Jung
 */
public class MakeBlockXYPlotter extends LaserCutter
{

  private enum ToolState {
    ON, OFF
  }
  
  /*
   * Internal Settings
  */
  private final boolean debug = false; // print to command line
  private static final String MODELNAME = "MakeBlockXYPlotter";
  private double addSpacePerRasterLine = 0.5;
  private String hostname = ""; 
  private double bedWidth = 300;
  private double bedHeight = 210;
  private int delayRate = 5000;
  private int powerRate = 255;
  private String usedTool = "PEN"; // PEN, Laser
  private final List<Double> resolutions = Arrays.asList(64d // fine liner
  );
  
  private transient int chosenDelay;
  private transient int chosenPower;
  private transient ToolState toolState;
  
  private transient PrintWriter w = null;
  private transient BufferedReader portReader = null;
  private transient BufferedOutputStream out = null;
  private transient SerialPort port = null;
    
  /*
   * Global Settings
  */
  private static final String SETTING_HOSTNAME = "Target port:// or file://";
  private static final String SETTING_RASTER_WHITESPACE = "Additional space per Raster line (mm)";
  private static final String SETTING_BEDWIDTH = "Laserbed width (mm)";
  private static final String SETTING_BEDHEIGHT = "Laserbed height (mm)";
  private static final String SETTING_DELAY_RATE = "Max. Delay Rate (abs. us)";
  private static final String SETTING_POWER_RATE = "Max. Power Rate (abs. pwm)";
  private static final String SETTING_TOOL = "Tool (PEN, LASER)";
  private static final String[] settingAttributes = new String[]{
    SETTING_HOSTNAME,
    SETTING_RASTER_WHITESPACE,
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_DELAY_RATE,
    SETTING_POWER_RATE,
    SETTING_TOOL
  };
  
  
  /**
   * Get the value of MODELNAME
   * 
   * @return the value of MODELNAME
   */
  @Override
  public String getModelName() {
    return MODELNAME;
  }
  
  @Override
  public List<Double> getResolutions() {
    return resolutions;
  }
  
  @Override
  public MakeBlockXYPlotterProperty getLaserPropertyForVectorPart() {
    return new MakeBlockXYPlotterProperty(this.usedTool.equals("LASER")); // show power and speed only if laser
  }

  @Override
  public MakeBlockXYPlotterProperty getLaserPropertyForRasterPart()
  {
    return new MakeBlockXYPlotterProperty(this.usedTool.equals("LASER")); // show power and speed only if laser
  }
  
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
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  
  private void generateInitializationGCode() throws Exception {
    // reset internal state
    chosenDelay = 0;
    chosenPower = 0;
    toolState = null;

    toolOff();
    this.sendCommand("G28 X Y");//move to 0 0
  }

  private void generateShutdownGCode() throws Exception {
    //back to origin and shutdown
    toolOff();
    this.sendCommand("G28 X Y");//move to 0 0
  }
  
  private void toolOff() throws Exception {
    if(toolState != ToolState.OFF) {
      if(usedTool.equals("PEN")) {
        this.sendCommand("M1 90");
        this.sendCommand(String.format("M3 %d", 0)); // to ensure fastest speed
      } else if(usedTool.equals("LASER")) {
        this.sendCommand(String.format("M4 %d", 0));
        this.sendCommand(String.format("M3 %d", 0)); // to move faster with tool off
      } else {
        throw new Exception("Tool " + this.usedTool + " not supported!");
      }
      toolState = ToolState.OFF;
    }
  }
  
  private void toolOn() throws Exception {
    if(toolState != ToolState.ON) {
      if(usedTool.equals("PEN")) {
        this.sendCommand(String.format("M3 %d", 0)); // to ensure fastest speed
        this.sendCommand("M1 130");
      } else if(usedTool.equals("LASER")) {
        this.sendCommand(String.format("M3 %d", (int) ((double) delayRate * this.chosenDelay / 100)));
        this.sendCommand(String.format("M4 %d", (int) ((double) powerRate * this.chosenPower / 100)));
      } else {
        throw new Exception("Tool " + this.usedTool + " not supported!");
      }
      toolState = ToolState.ON;
    }
  }
  
  private void setDelay(int value) throws Exception{
    // saves just the chosen delay value
    // delay of the plotter really set on toolOn(), to move faster with tool off
    if(usedTool.equals("LASER")) { // property option only supported if laser
      if (value != chosenDelay) {
        chosenDelay = value;
        if (toolState == ToolState.ON)
        {
          toolOff();
          toolOn();
        }
      }
    }
  }
  
  private void setPower(int value) throws Exception{
    // saves just the chosen power value
    // power of the laser really set on toolOn()
    if(usedTool.equals("LASER")) { // property option only supported if laser
      if (value != chosenPower) {
        chosenPower = value;
        if (toolState == ToolState.ON)
        {
          toolOff();
          toolOn();
        }
      }
    }
  }
  
  private void move(double x, double y, double resolution) throws Exception{
    toolOff();
    this.sendCommand(String.format(Locale.US, "G0 X%f Y%f", Util.px2mm(x, resolution), Util.px2mm(y, resolution)));
  }

  private void line(double x, double y, double resolution) throws Exception{
    toolOn();
    this.sendCommand(String.format(Locale.US, "G1 X%f Y%f", Util.px2mm(x, resolution), Util.px2mm(y, resolution)));
  }
  
  private void generateVectorGCode(VectorPart vp, double resolution, ProgressListener pl, int startProgress, int maxProgress) throws UnsupportedEncodingException, Exception {
    int i = 0;
    int progress;
    int max = vp.getCommandList().length;
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        case MOVETO:
          double x = cmd.getX();
          double y = cmd.getY();
          this.move(x, y, resolution);
          break;
        case LINETO:
          x = cmd.getX();
          y = cmd.getY();
          this.line(x, y, resolution);
          break;
        case SETPROPERTY: // called once per part to set chosen properties
          MakeBlockXYPlotterProperty p = (MakeBlockXYPlotterProperty) cmd.getProperty(); // only set with LASER tool
          // ensure percent power
          int pPercent = (int) p.getPower();
          pPercent = pPercent<0?0:pPercent;
          pPercent = pPercent>100?100:pPercent;
          this.setPower(pPercent);
          // ensure percent speed
          int sPercent = (int) p.getSpeed();
          sPercent = sPercent<0?0:sPercent;
          sPercent = sPercent>100?100:sPercent;
          int dPercent = 100-sPercent; // convert speed to delay
          this.setDelay(dPercent);
          break;
      }
      i++;
      progress = (startProgress + (int) (i*(double) maxProgress/max));
      pl.progressChanged(this, progress);
    }
  }
  
  private void connect() throws NoSuchPortException, PortInUseException, Exception {
    if(!this.debug){
      if (this.hostname.startsWith("port://")) {
        String portString = this.hostname.replace("port://", "");
        
        try{
          CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(portString);
          port = (SerialPort) cpi.open("VisiCut", 2000);
        }
        catch(Exception e) {
          throw new Exception("Port '"+portString+"' is not available.");
        }
        
        if (port == null)
        {
          throw new Exception("Error: Could not Open COM-Port '"+portString+"'");
        }
        if (!(port instanceof SerialPort))
        {
          throw new Exception("Port '"+portString+"' is not a serial port.");
        }
        port.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        out = new BufferedOutputStream(port.getOutputStream());
        portReader = new BufferedReader(new InputStreamReader(port.getInputStream()));
        
        // wake up firmware
        String command = "\r\n\r\n";
        out.write(command.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        Thread.sleep(2000);
        portReader.readLine(); // "ok"
        portReader.readLine(); // "ok"
        
        this.checkVersion();
      }
      else if (hostname.startsWith("file://")) {
        String filename = this.hostname.replace("file://", "");
        try {
          w = new PrintWriter(filename);
        }
        catch(Exception e) {
          throw new Exception(String.format("No correct absolute file path: %s Exception %s", this.hostname, e));
        }
      }
      else {
        throw new Exception(String.format("Unknown hostname: %s", this.hostname));
      }
    }
  }
  
  private void disconnect() throws Exception{
    if(w != null) {
      w.close();
      w = null;
    }
    
    if(out != null) {
      out.close();
      out = null;
    }
    
    if(port != null){
      port.close();
      port = null;
    }
  }
  
  private void checkResponse(String command, String response, String expectedAnswer) throws Exception {
    if(!response.toLowerCase().contains(expectedAnswer.toLowerCase())) {
        throw new Exception(String.format("Got wrong response to command \"%s\":\n\"%s\" instead of \"%s\"", command, response, expectedAnswer));
      }
  }
  private void sendCommand(String command) throws Exception {
    this.send(command);
    
    if(!debug) {
      if (this.hostname.startsWith("port://")) {
        String resp = this.receive();
        this.checkResponse(command, resp, "ok");
      }    
    }
  }
  
  private void checkVersion() throws Exception {
    // check if firmware matches implemented protocol
    this.send("M115");
    
    if(!debug) {
      if (this.hostname.startsWith("port://")) {
        String resp = this.receive();
        this.checkResponse("Version", resp, "Version");
        String resp2 = this.receive();
        this.checkResponse("Version", resp2, "ok");
      }    
    }
  }
  
  private void send(String command) throws Exception {
    if(!debug) {
      if (this.hostname.startsWith("port://") || savingToFile) {
        // send
        String sendString = command + "\n";
        out.write(sendString.getBytes(StandardCharsets.US_ASCII));
        out.flush();
      }
      else if (hostname.startsWith("file://")) {
        w.println(command);
      }
      else {
        throw new Exception(String.format("Unknown hostname: %s", this.hostname));
      }
      
    } else {
      System.out.println(command);
    }
  }
  
  private String receive() throws Exception{
    if(!debug) {
      if (this.hostname.startsWith("port://")) {
            String line;
        try {
          line = portReader.readLine();
          line = line.replace("\n", "").replace("\r", "");
          return line;
        } catch(IOException e) { 
          throw new IOException("IO Exception, e.g. timeout");
        }
      }
    }
    return "";
  }
  
  public void sendGCode(LaserJob job, ProgressListener pl) throws Exception
  {
    this.generateInitializationGCode();
    int startProgress = 20;
    pl.progressChanged(this, startProgress);
    int i = 0;
    int progress = startProgress;
    int max = job.getParts().size();
    for (JobPart p : job.getParts())
    {
      if (p instanceof RasterizableJobPart)
      {
        p = convertRasterizableToVectorPart((RasterizableJobPart) p, job, true, true, true);
      }
      if (p instanceof VectorPart)
      {
        this.generateVectorGCode((VectorPart) p, p.getDPI(), pl, progress, ((int) ((i+1)*(double) 80/max)));
      }
      i++;
      progress = (startProgress + (int) (i*(double) 80/max));
      pl.progressChanged(this, progress);
    }
    this.generateShutdownGCode();
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    this.chosenPower = 0;
    this.chosenDelay = 0;
    this.toolState = ToolState.ON; // assume worst case, set to OFF in initialization code
    pl.progressChanged(this, 0); 
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "connecting");
    this.connect();
    pl.taskChanged(this, "sending");
    this.sendGCode(job, pl);
    pl.taskChanged(this, "disconnecting");
    this.disconnect();
    pl.taskChanged(this, "sent");
    pl.progressChanged(this, 100);
  }

  // FIXME: this variable is a super ugly hack that should be ripped out and burned with fire
  private transient boolean savingToFile = false;
  
  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws Exception
  {
    ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (BufferedOutputStream bufferedStream = new BufferedOutputStream(bytesOut)) {
      out = bufferedStream;
      savingToFile = true;
      sendGCode(job, new ProgressListenerDummy());
    }
    finally
    {
      savingToFile = false;
    }
    fileOutputStream.write(bytesOut.toByteArray());
  }

  @Override
  public LaserCutter clone()
  {
    MakeBlockXYPlotter clone = new MakeBlockXYPlotter();
    clone.addSpacePerRasterLine = addSpacePerRasterLine;
    clone.hostname = hostname;
    clone.bedWidth = bedWidth;
    clone.bedHeight = bedHeight;
    clone.delayRate = delayRate;
    clone.powerRate = powerRate;
    clone.usedTool = usedTool;
    return clone;
  }

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_HOSTNAME.equals(attribute)) {
      return this.hostname;
    } else if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      return this.addSpacePerRasterLine;
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.bedWidth;
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.bedHeight;
    } else if (SETTING_DELAY_RATE.equals(attribute)) {
      return this.delayRate;
    } else if (SETTING_POWER_RATE.equals(attribute)) {
      return this.powerRate;
    } else if (SETTING_TOOL.equals(attribute)) {
      return this.usedTool;
    } 
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_HOSTNAME.equals(attribute)) {
      this.hostname = (String) value;
    } else if (SETTING_RASTER_WHITESPACE.equals(attribute)) {
      this.addSpacePerRasterLine = (Double) value;
    } else if (SETTING_BEDWIDTH.equals(attribute)) {
      this.bedWidth = (Double) value;
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.bedHeight = (Double) value;
    } else if (SETTING_DELAY_RATE.equals(attribute)) {
      this.delayRate = (Integer) value;
    } else if (SETTING_POWER_RATE.equals(attribute)) {
      this.powerRate = (Integer) value;
    } else if (SETTING_TOOL.equals(attribute)) {
      this.usedTool = (String) value;
    }  
  }
  
}
