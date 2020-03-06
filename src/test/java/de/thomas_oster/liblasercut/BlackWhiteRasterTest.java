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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.thomas_oster.liblasercut;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author thommy
 */
public class BlackWhiteRasterTest
{

  public BlackWhiteRasterTest()
  {
  }

  @BeforeClass
  public static void setUpClass() throws Exception
  {
  }

  @AfterClass
  public static void tearDownClass() throws Exception
  {
  }

  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }

  @Test
  public void testByteRepresentation()
  {
    BlackWhiteRaster ras = new BlackWhiteRaster(500, 600);
    assertEquals(500, ras.getWidth());
    assertEquals(600, ras.getHeight());
    for (int x = 0; x < ras.getWidth(); x++)
    {
      for (int y = 0; y < ras.getHeight(); y++)
      {
        ras.setBlack(x, y, true);
      }
    }
    for (int x = 0; x < ras.getWidth(); x++)
    {
      for (int y = 0; y < ras.getHeight(); y++)
      {
        assertTrue(ras.isBlack(x,y));
      }
    }
    // raw bytes: 1 = black, 0 = white, 8 pixels per byte
    assertEquals((byte) 0xff, ras.getByte(17, 42));
    ras.setBlack(8*17+1, 42, false);
    assertEquals((byte) 0b1011_1111, ras.getByte(17, 42));
    for (int x = 0; x < ras.getWidth(); x++)
    {
      for (int y = 0; y < ras.getHeight(); y++)
      {
        ras.setBlack(x, y, false);
      }
    }
    for (int x = 0; x < ras.getWidth(); x++)
    {
      for (int y = 0; y < ras.getHeight(); y++)
      {
        assertFalse(ras.isBlack(x,y));
      }
    }
    for (int x = 0; x < ras.getWidth(); x++)
    {
      for (int y = 0; y < ras.getHeight(); y++)
      {
        boolean black = ((Math.random() * 10) % 2 == 1);
        ras.setBlack(x, y, black);
        assertEquals(black, ras.isBlack(x, y));
        // setGreyScale is mapped to {0, 255}.
        // almost black -> black
        ras.setGreyScale(x, y, 42);
        assertEquals(0, ras.getGreyScale(x, y));
        assertTrue(ras.isBlack(x, y));
        assertEquals(1, ras.getPixel(x, y)); // note: meaning of getPixel() is opposite of isBlack()
        // black
        ras.setGreyScale(x, y, 0);
        assertEquals(0, ras.getGreyScale(x, y));
        assertTrue(ras.isBlack(x, y));
        // white
        ras.setGreyScale(x, y, 255);
        assertEquals(255, ras.getGreyScale(x, y));
        assertFalse(ras.isBlack(x, y));
        // almost white
        ras.setGreyScale(x, y, 200);
        assertEquals(255, ras.getGreyScale(x, y));
        assertFalse(ras.isBlack(x, y));
        assertEquals(0, ras.getPixel(x, y)); // note: meaning of getPixel() is opposite of isBlack()
      }
    }
  }
}
