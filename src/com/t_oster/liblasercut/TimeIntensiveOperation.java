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

import java.util.LinkedList;
import java.util.List;

/**
 * This class is for easy support for Progress Listeners just extend this class
 * and use the fireProgressChanged and fireTaskNameChanged method.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public abstract class TimeIntensiveOperation
{

  //has to be initialized in the getter, because it will be
  //null after deserialization
  private List<ProgressListener> listeners = null;

  private List<ProgressListener> getListeners()
  {
    if (listeners == null)
    {
      listeners = new LinkedList<ProgressListener>();
    }
    return listeners;
  }

  public void addProgressListener(ProgressListener l)
  {
    synchronized(getListeners())
    {
      getListeners().add(l);
    }
  }

  public void removeProgressListener(ProgressListener l)
  {
    synchronized(getListeners())
    {
      getListeners().remove(l);
    }
  }

  public void fireProgressChanged(int progress)
  {
    synchronized(getListeners())
    {
      for (ProgressListener l : getListeners())
      {
        l.progressChanged(this, progress);
      }
    }
  }

  public void fireTaskChanged(String name)
  {
    synchronized(getListeners())
    {
      for (ProgressListener l : getListeners())
      {
        l.taskChanged(this, name);
      }
    }
  }
  private int progress = 0;

  protected void setProgress(int progress)
  {
    if (progress != this.progress)
    {
      this.progress = progress;
      this.fireProgressChanged(this.progress);
    }
  }
}
