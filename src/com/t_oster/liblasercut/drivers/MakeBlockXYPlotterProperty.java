/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import java.util.Arrays;
import java.util.LinkedList;

/**
 *
 * @author Sven
 */
public class MakeBlockXYPlotterProperty extends PowerSpeedFocusFrequencyProperty
{
  private boolean showPowerAndSpeed = false;
  
  public MakeBlockXYPlotterProperty(boolean showPowerAndSpeed) {
    this.showPowerAndSpeed = showPowerAndSpeed;
  }
  
  public MakeBlockXYPlotterProperty() {
    this(false);
  }
  
  @Override
  public String[] getPropertyKeys()
  {
    LinkedList<String> result = new LinkedList<String>();
    result.addAll(Arrays.asList(super.getPropertyKeys()));
    result.remove("focus");
    result.remove("frequency");
    
    if(!showPowerAndSpeed) {
      result.remove("power");
      result.remove("speed");
    }
    
    return result.toArray(new String[0]);
  }

  @Override
  public Object getProperty(String name)
  {
    return super.getProperty(name);
  }

  @Override
  public void setProperty(String name, Object value)
  {
    super.setProperty(name, value);
  }

  @Override
  public MakeBlockXYPlotterProperty clone()
  {
    MakeBlockXYPlotterProperty result = new MakeBlockXYPlotterProperty();
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
        final MakeBlockXYPlotterProperty other = (MakeBlockXYPlotterProperty) obj;
        if (this.showPowerAndSpeed != other.showPowerAndSpeed) {
            return false;
        }
        return super.equals(other);
    }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 59 * hash + (this.showPowerAndSpeed ? 1 : 0);
    return hash;
  }
}
