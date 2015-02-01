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
package com.t_oster.liblasercut;

import com.t_oster.liblasercut.drivers.*;

/**
 * This class contains Version information
 * etc from the Library
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LibInfo
{
  private static String VERSION = "visicut1.7";
  
  public static String getVersion()
  {
    return VERSION;
  }
  
  public static Class[] getSupportedDrivers()
  {
    return new Class[]{
      EpilogZing.class,
      EpilogHelix.class,
      LaosCutter.class,
      Lasersaur.class,
      Dummy.class,
      IModelaMill.class,
      SampleDriver.class,
      ExportSVG.class
    };
  }
}
