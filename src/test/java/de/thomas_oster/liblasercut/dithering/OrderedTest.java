/*
  This file is part of LibLaserCut.

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
package de.thomas_oster.liblasercut.dithering;

import de.thomas_oster.liblasercut.BlackWhiteRaster;
import de.thomas_oster.liblasercut.GreyscaleRaster;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderedTest
{
  /** Raster of one grey value. */
  private static GreyscaleRaster uniform(final int width, final int height, final int grey)
  {
    return new GreyscaleRaster()
    {
      public int getWidth() { return width; }
      public int getHeight() { return height; }
      public int getGreyScale(int x, int y) { return grey; }
      public void setGreyScale(int x, int y, int value) { }
    };
  }

  private static int blackPixels(BlackWhiteRaster raster)
  {
    int count = 0;
    for (int y = 0; y < raster.getHeight(); y++)
    {
      for (int x = 0; x < raster.getWidth(); x++)
      {
        if (raster.isBlack(x, y))
        {
          count++;
        }
      }
    }
    return count;
  }

  @Test
  public void whiteStaysWhite() throws InterruptedException
  {
    BlackWhiteRaster result = new Ordered().dither(uniform(32, 32, 255));
    assertEquals(0, blackPixels(result));
  }

  @Test
  public void blackStaysBlack() throws InterruptedException
  {
    BlackWhiteRaster result = new Ordered().dither(uniform(32, 32, 0));
    assertEquals(32 * 32, blackPixels(result));
  }
}
