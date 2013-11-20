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
package com.t_oster.liblasercut.laserscript;

import org.mozilla.javascript.ClassShutter;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class ScriptingSecurity implements ClassShutter
{
  private static ScriptingSecurity instance;
  private static String[] allowedClasses = new String[]{
    "adapter",
    "com.t_oster",
    "java.lang.Double",
    "java.lang.Float",
    "java.lang.Integer",
    "java.lang.String",
    "java.lang.Boolean"
  };
  
  public static ScriptingSecurity getInstance()
  {
    if (instance == null)
    {
      instance = new ScriptingSecurity();
    }
    return instance;
  }
  
    private boolean locked = false;

  public boolean isLocked()
  {
    return locked;
  }

  public void setLocked(boolean locked)
  {
    this.locked = locked;
  }
  
  private ScriptingSecurity()
  {
  }

  @Override
  public boolean visibleToScripts(String className)
  {
    if (locked)
    {
      for (String prefix : allowedClasses)
      {
        if(className.startsWith(prefix))
        {
          return true;
        }
      }
      System.err.println("ScriptingSecurity: LaserScript tried to access forbidden class: "+className);
      return false;
    }
		return true;
  }
}
