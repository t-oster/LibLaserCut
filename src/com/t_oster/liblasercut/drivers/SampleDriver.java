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
import java.util.Arrays;
import java.util.List;

/**
 * This class should act as a starting-point, when implementing a new Lasercutter driver.
 * It will take a Laserjob and just output the Vecor-Parts as G-Code.
 * 
 * The file contains comments prefixed with "#<step>" which should guide you in the process
 * of creating custom drivers. Also read the information in the Wiki on
 * https://github.com/t-oster/VisiCut/wiki/
 * 
 * #1: Create a new JavaClass, which extends the com.t_oster.liblasercut.drivers.LaserCutter class
 * #2: Implement all abstract methods. Each of them is explained in this example.
 * #3: In Order to see your driver in VisiCut, add your class to the getSupportedDrivers() method
 * in the com.t_oster.liblasercut.LibInfo class (src/com/t_oster/liblasercut/LibInfo.java)
 * 
 * @author Thomas Oster
 */
public class SampleDriver extends LaserCutter
{

  /**
   * This is the core method of the driver. It is called, whenever VisiCut wants your driver
   * to send a job to the lasercutter.
   * @param job This is an LaserJob object, containing all information on the job, which is to be sent
   * @param pl Use this object to inform VisiCut about the progress of your sending action. 
   * @param warnings If you there are warnings for the user, you can add them to this list, so they can be displayed by VisiCut
   * @throws IllegalJobException Throw this exception, when the job is not suitable for the current machine
   * @throws Exception 
   */
  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    //let's check the job for some errors
    checkJob(job);
    
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
               * Move the laserhead (laser on) from the current position to the x/y position of this command. All coordinates are in dots respecting
               * to the job resolution
               */
              double x = Util.px2mm(cmd.getX(), p.getDPI());
              double y = Util.px2mm(cmd.getY(), p.getDPI());
              System.out.printf("G01 X%f Y%f\n", x, y);
              break;
            }
            case MOVETO:
            {
              /**
               * Move the laserhead (laser off) from the current position to the x/y position of this command. All coordinates are in mm
               */
              double x = Util.px2mm(cmd.getX(), p.getDPI());
              double y = Util.px2mm(cmd.getY(), p.getDPI());
              System.out.printf("G00 X%f Y%f\n", x, y);
              break;
            }
            case SETPROPERTY:
            {
              /**
               * Change properties of current laser-actions (e.g. speed, frequency, power... whatever your driver supports)
               */
              LaserProperty prop = cmd.getProperty();
              System.out.println("Changing Device Parameters:");
              for (String key : prop.getPropertyKeys())
              {
                String value = prop.getProperty(key).toString();
                System.out.println("  "+key+"="+value);
              }
              break;
            }
          }
        }
      }
    }
  }
  
  /**
   * This method should return an Object of a class extending LaserProperty.
   * A LaserProperty represents all settings for your device like power,speed and frequency
   * which are necessary for a certain job-type (e.g. a VectorPart).
   * See the different classes for examples. We will just use the default,
   * supporting power,speed focus and frequency.
   * @return 
   */
  @Override
  public LaserProperty getLaserPropertyForVectorPart() {
      return new PowerSpeedFocusFrequencyProperty();
  }
  

  /**
   * This method should return a list of all supported resolutions (in DPI)
   * @return 
   */
  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(new Double[]{100.0,200.0,500.0,1000.0});
  }

  /**
   * This method should return the width of the laser-bed. You can have
   * a config-setting in order to have different sizes for each instance of 
   * your driver. For simplicity we just assume a width of 600mm
   * @return 
   */
  @Override
  public double getBedWidth()
  {
    return 600;
  }

  /**
   * This method should return the height of the laser-bed. You can have
   * a config-setting in order to have different sizes for each instance of 
   * your driver. For simplicity we just assume a height of 300mm
   * @return 
   */
  @Override
  public double getBedHeight()
  {
    return 300;
  }

  /**
   * This method should return a name for this driver.
   * @return 
   */
  @Override
  public String getModelName()
  {
    return "Sample Driver";
  }

  /**
   * This method must copy the current instance with all config settings, because
   * it is used for save- and restoring
   * @return 
   */
  @Override
  public LaserCutter clone()
  {
    SampleDriver clone = new SampleDriver();
    //TODO: copy all settings to the clone if present.
    return clone;
  }

  /**
   * The next mehtod allow for a generic GUI with settings for an instance of this
   * driver to be created. For simplicity, this driver does not support any
   * properties. Look at the other implementations for reference.
   * @return 
   */
  @Override
  public String[] getPropertyKeys()
  {
    return new String[0];
  }

  @Override
  public void setProperty(String key, Object value)
  {
    //should never be called
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public Object getProperty(String key)
  {
    //should never be called
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }
  
}
