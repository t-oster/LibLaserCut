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
  Known Limitations:
  - If there is Raster and Raster3d Part in one job, the speed from 3d raster
  is taken for both and eventually other side effects:
  IT IS NOT RECOMMENDED TO USE 3D-Raster and Raster in the same Job
 */

package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.platform.Util;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class EpilogZing extends EpilogCutter
{
  
  public EpilogZing()
  {
  }
  
  public EpilogZing(String hostname)
  {
    super(hostname);
  }
  
  @Override
  public String getModelName()
  {
    return "Epilog ZING";
  }

  private static final double[] RESOLUTIONS = new double[]
  {
    100, 200, 250, 400, 500, 1000
  };
  
  @Override
  public List<Double> getResolutions()
  {
    List<Double> result = new LinkedList<>();
    for (double r : RESOLUTIONS)
    {
      result.add(r);
    }
    return result;
  }
  
  @Override
  public EpilogZing clone()
  {
    EpilogZing result = new EpilogZing();
    result.copyProperties(this);
    return result;
  }

  //We need this methods for XMLEncoder to work properly
  @Override
  public boolean isAutoFocus()
  {
    return super.isAutoFocus();
  }
  
  @Override
  public void setAutoFocus(boolean b)
  {
    super.setAutoFocus(b);
  }
  
  @Override
  public void setBedHeight(double bh)
  {
    super.setBedHeight(bh);
  }
  
  @Override
  public double getBedHeight()
  {
    return super.getBedHeight();
  }
  
  @Override
  public void setBedWidth(double bh)
  {
    super.setBedWidth(bh);
  }
  
  @Override
  public double getBedWidth()
  {
    return super.getBedWidth();
  }
  
  @Override
  public void setHostname(String host)
  {
    super.setHostname(host);
  }
  
  @Override
  public String getHostname()
  {
    return super.getHostname();
  }
  
  @Override
  public int getPort()
  {
    return super.getPort();
  }
  
  @Override
  public void setPort(int p)
  {
    super.setPort(p);
  }

  @Override
  public boolean isHideSoftwareFocus()
  {
    return super.isHideSoftwareFocus();
  }

  @Override
  public void setHideSoftwareFocus(boolean b)
  {
    super.setHideSoftwareFocus(b);
  }

  @Override
  public int estimateJobDuration(LaserJob job)
  {
    // It's not entirely clear how these values were determined. They are probably calibrated for the Epilog Zing.
    double PX2MM_500DPI = Util.px2mm(1, 500);
    // millimeters / second
    double VECTOR_MOVESPEED_X = PX2MM_500DPI * 20000d / 4.5;
    double VECTOR_MOVESPEED_Y = PX2MM_500DPI * 10000d / 2.5;
    double VECTOR_LINESPEED = PX2MM_500DPI * 20000d / 36.8;

    // Extra time (in millis) per raster line.
    double RASTER_LINEOFFSET = 0.08d;
    double RASTER_LINESPEED = PX2MM_500DPI * 100000d / ((268d / 50) - RASTER_LINEOFFSET);
    //TODO: The Raster3d values are not tested yet, they're just copies
    double RASTER3D_LINEOFFSET = RASTER_LINEOFFSET;
    double RASTER3D_LINESPEED = RASTER_LINESPEED;

    return estimateJobDuration(job, VECTOR_MOVESPEED_X, VECTOR_MOVESPEED_Y, VECTOR_LINESPEED, RASTER_LINEOFFSET, RASTER_LINESPEED, RASTER3D_LINEOFFSET, RASTER3D_LINESPEED);
  }
}
