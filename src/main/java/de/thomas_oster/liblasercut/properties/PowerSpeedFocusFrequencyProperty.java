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
package de.thomas_oster.liblasercut.properties;

/**
 * The LaserProperty holds all the parameters for parts of the LaserJob.
 * The Frequency value is ignored for Engraving operations
 *
 * @author oster
 */
public class PowerSpeedFocusFrequencyProperty extends PowerSpeedFocusProperty
{
  private static final String[] propertyNames = new String[]{"power", "speed", "focus", "frequency"};
  private static final String[] propertyNamesNoFocus = new String[]{"power", "speed", "frequency"};
  private int frequency = 5000;

  public PowerSpeedFocusFrequencyProperty()
  {
    // NOP
  }

  /** Make a new PowerSpeedFocusFrequencyProperty that optionally utilizes the focus setting */
  public PowerSpeedFocusFrequencyProperty(boolean hideFocus)
  {
    super(hideFocus);
  }

  public void setFrequency(int frequency)
  {
    frequency = Math.max(frequency, 10);
    frequency = Math.min(frequency, 5000);
    this.frequency = frequency;
  }

  public int getFrequency()
  {
    return frequency;
  }

  @Override
  public String[] getPropertyKeys()
  {
    if (isHideFocus()) {
      return propertyNamesNoFocus;
    } else {
      return propertyNames;
    }
  }

  @Override
  public Object getProperty(String name)
  {
    if ("frequency".equals(name))
    {
      return this.getFrequency();
    }
    else
    {
      return super.getProperty(name);
    }
  }
  
  @Override
  public void setProperty(String name, Object value)
  {
    if ("frequency".equals(name))
    {
      this.setFrequency((Integer) value);
    }
    else
    {
      super.setProperty(name, value);
    }
  }
  
  @Override
  public Object getMinimumValue(String name)
  {
    if ("frequency".equals(name))
    {
      return 100;
    }
    else
    {
      return super.getMinimumValue(name);
    }
  }
  
  @Override
  public Object getMaximumValue(String name)
  {
     if ("frequency".equals(name))
     {
       return 5000;
     }
     else
     {
       return super.getMaximumValue(name);
     }
  }

  @Override
  public Object[] getPossibleValues(String name)
  {
    if ("frequency".equals(name))
    {
      return null;
    }
    else
    {
      return super.getPossibleValues(name);
    }
  }

  @Override
  public PowerSpeedFocusFrequencyProperty clone()
  {
    PowerSpeedFocusFrequencyProperty p = new PowerSpeedFocusFrequencyProperty();
    p.frequency = this.frequency;
    p.setPower(getPower());
    p.setSpeed(getSpeed());
    p.setFocus(getFocus());
    p.setHideFocus(isHideFocus());
    return p;
  }

    @Override
    public int hashCode() {
      return 7*frequency + super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PowerSpeedFocusFrequencyProperty other = (PowerSpeedFocusFrequencyProperty) obj;
        if (this.frequency != other.frequency) {
            return false;
        }
        return super.equals(obj);
    }
  
  public String toString()
  {
      return "PowerSpeedFocusFrequencyProperty(power="+getPower()+",speed="+getSpeed()+",focus="+getFocus()+",frequency="+getFrequency()+")";
  }
}
