/*
  This file is part of LibLaserCut.
  Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
  Copyright (C) 2024 - 2025 Florian <florian@bsystems.de>
  
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

  This implementation is based on the generic GRBL driver with some bits
  from LAOS and LaserTools drivers.
 */
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.VectorCommand;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.properties.LaserProperty;
import de.thomas_oster.liblasercut.ProgressListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;

/**
 * This class implements a driver for Creality Falcon 2.
 *
 * @author Florian Boor <florian@bsystems.de>
 */
public class Falcon2 extends Grbl
{
  private static final String SETTING_SUPPORTS_VENTILATION = "Supports ventilation";
  
  public Falcon2()
  {
    // The Falcon2 is more or less a common GRBL device with some 
    // uncommon behaviour and some extra features.
    
    //set some grbl-specific defaults
    setLineend("CRLF");
    setIdentificationLine("Grbl");
    // Grbl uses "ok" flow control
    setWaitForOKafterEachLine(true);
    setPreJobGcode(getPreJobGcode()+",M3");
    // turn off laser before returning to home position
    setPostJobGcode("M5,"+getPostJobGcode());
    // disable air assist
    setPostJobGcode("M9,"+getPostJobGcode());
    // Grbl & MrBeam use 1000 for 100% PWM on the spindle/laser
    setSpindleMax(1000.0f);
    // Grbl doesn't turn off laser during G0 rapids
    setBlankLaserDuringRapids(true);
    // Grbl can take a while to answer if doing a lot of slow moves
    setSerialTimeout(10000);
 
    // For Falcon 2 - similar models might differ
    setBedWidth(400);
    setBedHeight(415);
    setMax_speed(6000);
    setTravel_speed(6000);
    
    // Bed orientation
    setFlipYaxis(true);
    
    // Devices are shipped with 'Air Assist' ventilation
    setSupportsVentilation(true);
    
    // blank these so that connect automatically uses serial first
    // No remote connectivity per default anyway.
    setHttpUploadUrl(null);
    setHost(null);
  }
  
  private boolean supportsVentilation = false;

  public final boolean getSupportsVentilation() {
    return supportsVentilation;
  }

  public final void setSupportsVentilation(boolean supportsVentilation) {
    this.supportsVentilation = supportsVentilation;
  }
  
  @Override
  public Object getProperty(String attribute) {
    if (SETTING_SUPPORTS_VENTILATION.equals(attribute)) {
      return this.getSupportsVentilation();
    }
    else {
      return super.getProperty(attribute);
    }
  }
  
  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_SUPPORTS_VENTILATION.equals(attribute)) {
      this.setSupportsVentilation((Boolean) value);
    }
    else {
      super.setProperty(attribute, value);
    }
  }

  @Override
  public LaosCutterProperty getLaserPropertyForVectorPart() {
    return new LaosCutterProperty(true, !this.supportsVentilation, true, true);
  }

  @Override
  public LaosEngraveProperty getLaserPropertyForRasterPart() {
    return new LaosEngraveProperty(true, !this.supportsVentilation, true, true);
  }

  @Override
  public LaosEngraveProperty getLaserPropertyForRaster3dPart() {
    return new LaosEngraveProperty(true, !this.supportsVentilation, true, true);
  }

  private Boolean currentVentilation = null;
  private void setVentilation(PrintStream out, boolean ventilation) {
    if (currentVentilation == null || !currentVentilation.equals(ventilation))
    {
      //M08 enables, M09 disables
      //TODO: add power controls
      out.printf(Locale.US, "M%d\n", ventilation ? 8 : 9);
      currentVentilation = ventilation;
      System.out.printf("ventilation to: "+ventilation+" \n pjob "+getPostJobGcode()+"\n");
    }
  }

  private void setCurrentProperty(PrintStream out, LaserProperty p) {
    if (p instanceof LaosCutterProperty) {
      LaosCutterProperty prop = (LaosCutterProperty) p;
      if (this.supportsVentilation) {
        setVentilation(out, prop.getVentilation());
      }
      setSpeed(prop.getSpeed());
      setPower(prop.getPower());
    }
    else {
      throw new RuntimeException("This driver accepts LaosCutter properties only");
    }
  }

  @Override
  protected void writeVectorGCode(VectorPart vp, double resolution) throws UnsupportedEncodingException, IOException {
    for (VectorCommand cmd : vp.getCommandList()) {
      switch (cmd.getType()) {
        // TODO: x,y should be changed to double because GCode has infinite vector resolution anyway
        case MOVETO:
          int x = (int) cmd.getX();
          int y = (int) cmd.getY();
          move(out, x, y, resolution);
          break;
        case LINETO:
          x = (int) cmd.getX();
          y = (int) cmd.getY();
          line(out, x, y, resolution);
          break;
        case SETPROPERTY:
          LaosCutterProperty p = (LaosCutterProperty) cmd.getProperty();
          this.setCurrentProperty(out, p);
          break;
      }
    }
  }
  
  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception {
    currentVentilation = null;
    super.saveJob(fileOutputStream,job);
  }
  
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    currentVentilation = null;
    super.sendJob(job, pl, warnings);
  }
  
  @Override
  public String getModelName() {
    return "Creality Falcon2";
  }
    
protected String waitForIdentificationLineInternal(ProgressListener pl) throws IOException {
    if (getIdentificationLine() != null && getIdentificationLine().length() > 0) {
      String line = "";
      for (int trials = 4; trials > 0; trials--) {
        line = waitForLine();
        if (line.startsWith(getIdentificationLine())) {
          return null;
        }
      }
      return line;
    }
    return null;
  }

  /**
   * Initializes Grbl, handling issuing of soft-reset and initial homing
   * (if desired & required).
   * @param pl Progress listener to update during connect/homing process
   * @return null on success, string if resulting string is not the expected id
   * @throws java.io.IOException 
   */
  @Override
  protected String waitForIdentificationLine(ProgressListener pl) throws IOException {
    // flush serial buffer
    while (in.ready()) { in.readLine(); }
    
    // send reset character to Grbl to get it to print out its welcome message
    pl.taskChanged(this, "Sending soft reset");
    out.write(0x18);
    
    String error = waitForIdentificationLineInternal(pl);
    
    if (error != null) 
      return error;
    
    // check if board is locked and if so home/unlock
    if (getAutoHome() == true) {
        pl.taskChanged(this, "Homing");
        sendLineWithoutWait("$H");
        
        // wait for "ok" or "[Caution: Unlocked]" followed by "ok"
        String line = waitForLine();
        if (!line.equals("ok"))
          line = waitForLine();
        if (!line.equals("ok"))
          throw new IOException("Homing cycle failed to complete");
    }
    
    return null;
  }
  
  @Override
  public Falcon2 clone() {
    Falcon2 clone = new Falcon2();
    clone.copyProperties(this);
    clone.supportsVentilation = supportsVentilation;
    return clone;
  }
}
