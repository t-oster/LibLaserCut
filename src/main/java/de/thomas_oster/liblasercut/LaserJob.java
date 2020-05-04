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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.platform.Util;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaserJob
{

  private final String title;
  private final String name;
  private final String user;
  private double startX = 0;
  private double startY = 0;
  // position of origin after applying start point - see applyStartPoint()
  private double transformedOriginX = 0;
  private double transformedOriginY = 0;
  private final List<JobPart> parts = new LinkedList<>();
  private boolean autoFocusEnabled = true;

  public LaserJob(String title, String name, String user)
  {
    this.title = title;
    this.name = name;
    this.user = user;
  }

  /**
   * Sets a custom offset on the job. The values are considered to be in
   * mm and measured from the top-left corner of the laserbed.
   * As a result, all coordinates cx,cy in the job will be corrected
   * to cx-x,cy-y
   */
  public void setStartPoint(double x, double y)
  {
    startX = x;
    startY = y;
  }

  public double getStartX()
  {
    return startX;
  }

  public double getStartY()
  {
    return startY;
  }
  
  /**
   * Get the X-coordinate of the origin (top-left corner of laser bed) in mm.
   * Initially 0, but changes if applyStartPoint() is used.
   * Use this as an offset for checking if a path is outside the laser bed.
   * 
   * The allowed range of X coordinates is:
   * {@code
   * getTransformedOriginX()  <= x <= getTransformedOriginX() + LaserCutter.getBedWidth()
   * }
   */
  public double getTransformedOriginX()
  {
    return transformedOriginX;
  }
  
  /**
   * Get the Y-coordinate of the origin in mm.
   * See getTransformedOriginX().
   */
  public double  getTransformedOriginY()
  {
    return transformedOriginY;
  }

  public String getTitle()
  {
    return title;
  }

  public String getName()
  {
    return name;
  }

  public String getUser()
  {
    return user;
  }

  public void addPart(JobPart p)
  {
    this.parts.add(p);
  }

  public void removePart(JobPart p)
  {
    this.parts.remove(p);
  }

  public List<JobPart> getParts()
  {
    return parts;
  }

  /**
   * This method will subtract the start-point coordinates
   * from all parts of the job (in the corresponding resolution)
   * and then set the start-point to 0,0. This way multiple calls
   * to this method won't result in corrupted jobs.
   * 
   * The applied offset is saved in getTransformedOriginX(), getTransformedOriginY().
   */
  public void applyStartPoint()
  {
    if (startX != 0 || startY != 0)
    {
      for (JobPart p : this.getParts())
      {
        if (p instanceof VectorPart)
        {
          for (VectorCommand c : ((VectorPart) p).getCommandList())
          {
            if (c.getType().equals(VectorCommand.CmdType.LINETO) || c.getType().equals(VectorCommand.CmdType.MOVETO))
            {
              c.setX((int) (c.getX() - Util.mm2inch(startX)*p.getDPI()));
              c.setY((int) (c.getY() - Util.mm2inch(startY)*p.getDPI()));
            }
          }
        }
        else if (p instanceof RasterPart)
        {
          RasterPart rp = (RasterPart) p;
          rp.start.x -= (int) (Util.mm2inch(startX)*p.getDPI());
          rp.start.y -= (int) (Util.mm2inch(startY)*p.getDPI());
        }
        else if (p instanceof Raster3dPart)
        {
          Raster3dPart rp = (Raster3dPart) p;
          rp.start.x -= (int) (Util.mm2inch(startX)*p.getDPI());
          rp.start.y -= (int) (Util.mm2inch(startY)*p.getDPI());
        }
      }
      transformedOriginX = -startX;
      transformedOriginY = -startY;
      startX = 0;
      startY = 0;
    }
  }

  /**
   * Sets whether autofocus is enabled for this job.
   * No-op for lasers that do not support it.
   */
  public void setAutoFocusEnabled(boolean b) {
    autoFocusEnabled = b;
  }

  /** Indicates whether autofocus is enabled for this job, assuming the cutter supports it. */
  public boolean isAutoFocusEnabled() {
    return autoFocusEnabled;
  }
}
