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
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.drivers.GenericGcodeDriver;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class implements a driver for the laser cutter fork of Marlin.
 * 
 * @author quillford
 */
public class Marlin extends GenericGcodeDriver {

  public Marlin()
  {
    //set some Marlin specific defaults
    setIdentificationLine("start");
    setWaitForOKafterEachLine(true);
    setBaudRate(115200);
    setLineend("CRLF");
    setInitDelay(0);
    setPreJobGcode(getPreJobGcode()+",G28 XY,M5");
    setPostJobGcode(getPostJobGcode()+",M5,G28 XY");
    setSerialTimeout(35000);
    setBlankLaserDuringRapids(false);
    setSpindleMax(100.0); // marlin interprets power from 0-100 instead of 0-1
    
    //Marlin has no way to upload over the network so remove the upload url text
    setHttpUploadUrl("");
    setHost("");
  }
  
  /**
   * Adjust defaults after deserializing driver from an old version of XML file
   */
  @Override
  protected void setKeysMissingFromDeserialization()
  {
    // added field spindleMax, needs to be set to 100.0 for Marlin
    // but xstream initializes it to 0.0 when it is missing from XML
    if (this.spindleMax <= 0.0) this.spindleMax = 100.0;
  }
  
  @Override
  public String getIdentificationLine()
  {
    return("start");
  }

  @Override
  public String[] getPropertyKeys()
  {
    List<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.remove(GenericGcodeDriver.SETTING_IDENTIFICATION_STRING);
    result.remove(GenericGcodeDriver.SETTING_WAIT_FOR_OK);
    result.remove(GenericGcodeDriver.SETTING_LINEEND);
    result.remove(GenericGcodeDriver.SETTING_INIT_DELAY);
    result.remove(GenericGcodeDriver.SETTING_HTTP_UPLOAD_URL);
    result.remove(GenericGcodeDriver.SETTING_HOST);
    result.remove(GenericGcodeDriver.SETTING_SPINDLE_MAX);
    result.remove(GenericGcodeDriver.SETTING_BLANK_LASER_DURING_RAPIDS);
    return result.toArray(new String[0]);
  }
  
  /**
   * Waits for the Identification line and returns null if it's alright
   * Otherwise it returns the wrong line
   * @return
   * @throws IOException 
   */
 @Override
  protected String waitForIdentificationLine(ProgressListener pl) throws IOException
  {
    if (getIdentificationLine() != null && getIdentificationLine().length() > 0)
    {
      String line = waitForLine();
        if (line.startsWith(getIdentificationLine()))
        {//we received the identification line ("start"), now we have to skip the rest of Marlin's dump
          while(!(waitForLine().startsWith("echo:SD")))
          {
           //do nothing and wait until Marlin has dumped all of the settings
          }
          return null;
        }
    }
    return null;
  }

  @Override
  public String getModelName()
  {
    return "Marlin";
  }

  @Override
  public Marlin clone()
  {
    Marlin clone = new Marlin();
    clone.copyProperties(this);
    return clone;
  }

}