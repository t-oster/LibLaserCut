/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     LibLaserCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LibLaserCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with LibLaserCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.FloatPowerSpeedFocusFrequencyProperty;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosCutterProperty extends FloatPowerSpeedFocusFrequencyProperty {
  
  private boolean hidePurge = false;
  private boolean hideVentilation = false;
    
  private boolean ventilation = true;

  public LaosCutterProperty(boolean hidePurge, boolean hideVentilation)
  {
    this.hidePurge = hidePurge;
    this.hideVentilation = hideVentilation;
  }
  
  public LaosCutterProperty()
  {
    this(false, false);
  }
  
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
  public String[] getPropertyKeys()
  {
    String[] s = super.getPropertyKeys();
    if (this.hidePurge && this.hideVentilation)
    {
      return s;
    }
    String[] result = new String[s.length+ (this.hidePurge ? 0 : 1) + (this.hideVentilation ? 0 : 1)];
    System.arraycopy(s, 0, result, 0, s.length);
    int i = s.length;
    if (!this.hideVentilation)
    {
      result[i++] = "ventilation";
    }
    if (!this.hidePurge)
    {
      result[i++] = "purge";
    }
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
  public LaosCutterProperty clone()
  {
    LaosCutterProperty result = new LaosCutterProperty();
    for (String s:this.getPropertyKeys())
    {
      result.setProperty(s, this.getProperty(s));
    }
    return result;
  }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LaosCutterProperty other = (LaosCutterProperty) obj;
        if (this.ventilation != other.ventilation) {
            return false;
        }
        if (this.purge != other.purge) {
            return false;
        }
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.ventilation ? 1 : 0);
        hash = 97 * hash + (this.purge ? 1 : 0);
        hash = 97 * hash + super.hashCode();
        return hash;
    }
  
  
}
