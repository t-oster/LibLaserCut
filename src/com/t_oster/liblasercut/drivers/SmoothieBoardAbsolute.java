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

import com.t_oster.liblasercut.FloatPowerAbsSpeedFocusProperty;
import com.t_oster.liblasercut.LaserProperty;

public class SmoothieBoardAbsolute extends SmoothieBoard
{
  @Override
  public String getModelName()
  {
    return "Smoothieboard (absolute speeds)";
  }

  @Override
  public LaserProperty getLaserPropertyForVectorPart() {
    return new FloatPowerAbsSpeedFocusProperty((float) this.max_speed);
  }

  @Override
  public LaserProperty getLaserPropertyForRaster3dPart()
  {
    return new FloatPowerAbsSpeedFocusProperty((float) this.max_speed);
  }

  @Override
  public LaserProperty getLaserPropertyForRasterPart()
  {
    return new FloatPowerAbsSpeedFocusProperty((float) this.max_speed);
  }

  @Override
  protected void setSpeed(double speed)
  {
    this.nextSpeed = speed;
  }

  @Override
  public SmoothieBoardAbsolute clone()
  {
    SmoothieBoardAbsolute clone = new SmoothieBoardAbsolute();
    clone.copyProperties(this);
    return clone;
  }

}
