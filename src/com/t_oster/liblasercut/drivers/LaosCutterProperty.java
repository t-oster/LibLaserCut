/**
 * This file is part of VisiCut.
 * Copyright (C) 2012 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 * 
 *     VisiCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *    VisiCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with VisiCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.FloatPowerSpeedFocusFrequencyProperty;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosCutterProperty extends FloatPowerSpeedFocusFrequencyProperty {
  
  private boolean ventilation = true;

  /**
   * Get the value of ventilation
   *
   * @return the value of ventilation
   */
  public boolean getVentilation()
  {
    return ventilation;
  }

  /**
   * Set the value of ventilation
   *
   * @param ventilation new value of ventilation
   */
  public void setVentilation(boolean ventilation)
  {
    this.ventilation = ventilation;
  }
  private boolean purge = true;

  /**
   * Get the value of purge
   *
   * @return the value of purge
   */
  public boolean getPurge()
  {
    return purge;
  }

  /**
   * Set the value of purge
   *
   * @param purge new value of purge
   */
  public void setPurge(boolean purge)
  {
    this.purge = purge;
  }

  @Override
  public String[] getPropertyNames()
  {
    String[] s = super.getPropertyNames();
    String[] result = new String[s.length+2];
    System.arraycopy(s, 0, result, 0, s.length);
    result[s.length] = "ventilation";
    result[s.length+1] = "purge";
    return result;
  }

  @Override
  public Object getProperty(String name)
  {
    if ("ventilation".equals(name))
    {
      return (Boolean) this.getVentilation();
    }
    else if ("purge".equals(name))
    {
      return (Boolean) this.getPurge();
    }
    else
    {
      return super.getProperty(name);
    }
  }

  @Override
  public void setProperty(String name, Object value)
  {
    if ("ventilation".equals(name))
    {
      this.setVentilation((Boolean) value);
    }
    else if ("purge".equals(name))
    {
      this.setPurge((Boolean) value);
    }
    else
    {
      super.setProperty(name, value);
    }
  }

  @Override
  public Class getPropertyClass(String name)
  {
    if ("ventilation".equals(name) || "purge".equals(name))
    {
      return Boolean.class;
    }
    else
    {
      return super.getPropertyClass(name);
    }
  }

  
}
