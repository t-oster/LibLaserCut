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
package de.thomas_oster.liblasercut;

/**
 * This class represents a defined Raster of greyscale pixels
 * which means it can be seen as 2 dimensional array of bytes.
 *
 * It has a subset of the routines of the java.awt.BufferedImage
 * so this could easy be used with an adapter.
 * It was chosen to use this Interface rather than the BufferedImage
 * because there are some platforms (ie Android) where BufferedImage
 * is not available but other Image classes which can be used.
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public interface GreyscaleRaster
{
  int getWidth();

  /**
   * get greyscale value of pixel
   * @return greyscale value: 0 (black) ... 255 (white)
   */
  int getGreyScale(int x, int y);

  /**
   * set greyscale value of pixel
   * @param grey greyscale value: 0 (black) ... 255 (white) 
   */
  void setGreyScale(int x, int y, int grey);

  int getHeight();
}
