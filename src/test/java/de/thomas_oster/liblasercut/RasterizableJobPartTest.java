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
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.properties.AbstractLaserProperty;
import de.thomas_oster.liblasercut.properties.FloatPowerSpeedFocusProperty;
import de.thomas_oster.liblasercut.properties.LaserProperty;
import de.thomas_oster.liblasercut.utils.BufferedImageAdapter;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for RasterizableJobPart
 * Michael Adams <zap@michaeladams.org>
 */
public class RasterizableJobPartTest
{
  /**
   * Test of getLaserProperty method, of class RasterizableJobPart.
   */
  @Test
  public void testGetLaserProperty()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    FloatPowerSpeedFocusProperty expResult = new FloatPowerSpeedFocusProperty();
    expResult.setPower(100.0f);
    expResult.setSpeed(100.0f);
    expResult.setFocus(0.0f);
    LaserProperty result = instance.getLaserProperty();
    assertEquals(expResult, result);
  }

  /**
   * Test of getRasterHeight method, of class RasterizableJobPart.
   */
  @Test
  public void testGetRasterHeight()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    int expResult = 6;
    int result = instance.getRasterHeight();
    assertEquals(expResult, result);
  }

  /**
   * Test of getRasterWidth method, of class RasterizableJobPart.
   */
  @Test
  public void testGetRasterWidth()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    int expResult = 9;
    int result = instance.getRasterWidth();
    assertEquals(expResult, result);
  }

  /**
   * Test of lineIsBlank method, of class RasterizableJobPart.
   */
  @Test
  public void testLineIsBlank()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();

    assertFalse(instance.lineIsBlank(0));
    assertFalse(instance.lineIsBlank(1));
    assertFalse(instance.lineIsBlank(2));
    assertFalse(instance.lineIsBlank(3));
    assertTrue(instance.lineIsBlank(4));
  }

   /**
   * Test of lineIsBlank method, of class RasterizableJobPart.
   */
  
  @Test
  public void testGetRasterLine()
  {
    RasterElement element = getTest8bitRasterElement();
    AbstractLaserProperty laserProperty = new AbstractLaserProperty();
    laserProperty.addProperty("power", 0); // laser property must have a "power" property to avoid warnings (engraving doesn't make sense if you can't change the power)
    RasterPart instance = new RasterPart(new GreyRaster(element),
      laserProperty.clone(), new Point(0,0), 500.0f);
    List<Byte> line0 = instance.getRasterLine(0);
    assertEquals((byte)line0.get(0),(byte)-1);
    assertEquals((byte)line0.get(1),(byte)-1);
    assertEquals((byte)line0.get(2),(byte)0);
    assertEquals((byte)line0.get(3),(byte)0);
    assertEquals((byte)line0.get(4),(byte)-1);
    assertEquals((byte)line0.get(5),(byte)0);
    assertEquals((byte)line0.get(6),(byte)0);
    assertEquals((byte)line0.get(7),(byte)0);
    assertEquals((byte)line0.get(8),(byte)-1);
    RasterElement element1bit = getTest1bitRasterElement();
    RasterPart instance1bit = new RasterPart(new GreyRaster(element1bit),
      laserProperty.clone(), new Point(0,0), 500.0f);
    line0 = instance1bit.getRasterLine(0);
    assertEquals((byte)line0.get(0), (byte)0xc8); //0b11001000
    assertEquals((byte)line0.get(1), (byte)0x80); //0b10000000
  }

  
  /**
   * Test of setRasteringCutDirection method, of class RasterizableJobPart.
   */
  @Test
  public void testSetRasteringCutDirection()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals("left to right", instance.getRasteringCutDirection());
    
    instance.toggleRasteringCutDirection();
    assertEquals("right to left", instance.getRasteringCutDirection());
  }

  /**
   * Test of cutCompensation method, of class RasterizableJobPart.
   */
  @Test
  public void testCutCompensation()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals(0, instance.cutCompensation());
    
    instance.toggleRasteringCutDirection();
    assertEquals(1, instance.cutCompensation());
  }

  /**
   * Test of hasFinishedCuttingLine method, of class RasterizableJobPart.
   */
  @Test
  public void testHasFinishedCuttingLine()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    // cut -> --XX-XXX-
    instance.setRasteringCutDirectionLeftToRight();
    // pixels 0-7 are part of the job
    for (int i=0; i<=7; i++) assertFalse(instance.hasFinishedCuttingLine(i, 0));
    // once you get to pixel 8, the line is cut
    assertTrue(instance.hasFinishedCuttingLine(8, 0));
    
    // cut <- ---X--XX-
    instance.toggleRasteringCutDirection();
    // coords 8-3 are part of the job
    for (int i=8; i>=3; i--) assertFalse(instance.hasFinishedCuttingLine(i, 1));
    // once you get to pixels 2-0 (heading left), the line is cut
    for (int i=2; i>=0; i--) assertTrue(instance.hasFinishedCuttingLine(i, 1));
  }

  /**
   * Test of firstNonWhitePixel method, of class RasterizableJobPart.
   */
  @Test
  public void testFirstNonWhitePixel()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    // cut -> --XX-XXX-
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals(2, instance.firstNonWhitePixel(0));
    
    // cut <- ---X--XX-
    instance.toggleRasteringCutDirection();
    assertEquals(7, instance.firstNonWhitePixel(1));
    
    // cut -> 303333333
    instance.toggleRasteringCutDirection();
    assertEquals(0, instance.firstNonWhitePixel(2));
    
    // cut <- 007777777
    instance.toggleRasteringCutDirection();
    assertEquals(8, instance.firstNonWhitePixel(3));
  }

  /**
   * Test of leftMostNonWhitePixel method, of class RasterizableJobPart.
   */
  @Test
  public void testLeftMostNonWhitePixel()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    
    // --XX-XXX-
    assertEquals(2, instance.leftMostNonWhitePixel(0));
    
    // ---X--XX-
    assertEquals(3, instance.leftMostNonWhitePixel(1));
    
    // 303333333
    assertEquals(0, instance.leftMostNonWhitePixel(2));
    
    // 007777777
    assertEquals(2, instance.leftMostNonWhitePixel(3));
  }

  /**
   * Test of lastNonWhitePixel method, of class RasterizableJobPart.
   */
  @Test
  public void testLastNonWhitePixel()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    // cut -> --XX-XXX-
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals(7, instance.lastNonWhitePixel(0));
    
    // cut <- ---X--XX-
    instance.toggleRasteringCutDirection();
    assertEquals(3, instance.lastNonWhitePixel(1));
    
    // cut -> 303333333
    instance.toggleRasteringCutDirection();
    assertEquals(8, instance.lastNonWhitePixel(2));
    
    // cut <- 007777777
    instance.toggleRasteringCutDirection();
    assertEquals(2, instance.lastNonWhitePixel(3));
  }

  /**
   * Test of rightMostNonWhitePixel method, of class RasterizableJobPart.
   */
  @Test
  public void testRightMostNonWhitePixel()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    
    // --XX-XXX-
    assertEquals(7, instance.rightMostNonWhitePixel(0));
    
    // ---X--XX-
    assertEquals(7, instance.rightMostNonWhitePixel(1));
    
    // 303333333
    assertEquals(8, instance.rightMostNonWhitePixel(2));
    
    // 007777777
    assertEquals(8, instance.rightMostNonWhitePixel(3));
  }

  /**
   * Test of nextColorChange method, of class RasterizableJobPart.
   */
  @Test
  public void testNextColorChange()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    // cut -> --XX-XXX-
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals(2, instance.nextColorChange(0, 0));
    assertEquals(2, instance.nextColorChange(1, 0));
    assertEquals(4, instance.nextColorChange(2, 0));
    assertEquals(4, instance.nextColorChange(3, 0));
    assertEquals(5, instance.nextColorChange(4, 0));
    assertEquals(8, instance.nextColorChange(5, 0));
    
    // cut <- ---X--XX-
    instance.toggleRasteringCutDirection();
    assertEquals(7, instance.nextColorChange(8, 1));
    assertEquals(5, instance.nextColorChange(7, 1));
    assertEquals(5, instance.nextColorChange(6, 1));
    assertEquals(3, instance.nextColorChange(5, 1));
    assertEquals(3, instance.nextColorChange(4, 1));
    assertEquals(2, instance.nextColorChange(3, 1));
  }

  /**
   * Test of nextColorChangeHeadingRight method, of class RasterizableJobPart.
   */
  @Test
  public void testNextColorChangeHeadingRight()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    
    // --XX-XXX-
    assertEquals(2, instance.nextColorChangeHeadingRight(0, 0));
    assertEquals(2, instance.nextColorChangeHeadingRight(1, 0));
    assertEquals(4, instance.nextColorChangeHeadingRight(2, 0));
    assertEquals(4, instance.nextColorChangeHeadingRight(3, 0));
    assertEquals(5, instance.nextColorChangeHeadingRight(4, 0));
    assertEquals(8, instance.nextColorChangeHeadingRight(5, 0));
    
    // ---X--XX-
    assertEquals(3, instance.nextColorChangeHeadingRight(0, 1));
    assertEquals(3, instance.nextColorChangeHeadingRight(1, 1));
    assertEquals(3, instance.nextColorChangeHeadingRight(2, 1));
    assertEquals(4, instance.nextColorChangeHeadingRight(3, 1));
    assertEquals(6, instance.nextColorChangeHeadingRight(4, 1));
    assertEquals(6, instance.nextColorChangeHeadingRight(5, 1));
    assertEquals(8, instance.nextColorChangeHeadingRight(6, 1));
    assertEquals(8, instance.nextColorChangeHeadingRight(7, 1));
    
    // 3-3333333
    assertEquals(9, instance.nextColorChangeHeadingRight(7, 2));
  }

  /**
   * Test of nextColorChangeHeadingLeft method, of class RasterizableJobPart.
   */
  @Test
  public void testNextColorChangeHeadingLeft()
  {
    RasterizableJobPart instance = new RasterizableJobPartImpl();
    
    // --XX-XXX-
    assertEquals(7, instance.nextColorChangeHeadingLeft(8, 0));
    assertEquals(4, instance.nextColorChangeHeadingLeft(7, 0));
    assertEquals(4, instance.nextColorChangeHeadingLeft(6, 0));
    assertEquals(4, instance.nextColorChangeHeadingLeft(5, 0));
    assertEquals(3, instance.nextColorChangeHeadingLeft(4, 0));
    assertEquals(1, instance.nextColorChangeHeadingLeft(3, 0));
    assertEquals(1, instance.nextColorChangeHeadingLeft(2, 0));
    
    // ---X--XX-
    assertEquals(7, instance.nextColorChangeHeadingLeft(8, 1));
    assertEquals(5, instance.nextColorChangeHeadingLeft(7, 1));
    assertEquals(5, instance.nextColorChangeHeadingLeft(6, 1));
    assertEquals(3, instance.nextColorChangeHeadingLeft(5, 1));
    assertEquals(3, instance.nextColorChangeHeadingLeft(4, 1));
    assertEquals(2, instance.nextColorChangeHeadingLeft(3, 1));
    assertEquals(-1, instance.nextColorChangeHeadingLeft(2, 1));
    assertEquals(-1, instance.nextColorChangeHeadingLeft(1, 1));
    
    // 3-3333333
    assertEquals(1, instance.nextColorChangeHeadingLeft(2, 2));
    assertEquals(0, instance.nextColorChangeHeadingLeft(1, 2));
    assertEquals(-1, instance.nextColorChangeHeadingLeft(0, 2));
  }

  /**
   * Test of getStartPosition method, of class RasterizableJobPart.
   */
  @Test
  public void testGetStartPosition()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    instance.setRasteringCutDirectionLeftToRight();
    assertEquals(new Point(0, 0), instance.getStartPosition(0));
    
    instance.toggleRasteringCutDirection();
    assertEquals(new Point(0, 1), instance.getStartPosition(1));
  }

  /**
   * Test of getPowerSpeedFocusPropertyForPixel method, of class RasterizableJobPart.
   */
  @Test
  public void testGetPowerSpeedFocusPropertyForPixel()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    Raster3dPart raster = instance.toRaster3dPart();
    
    // --XX-XXX-
    assertEquals(propertyForPower(0), raster.getPowerSpeedFocusPropertyForPixel(0, 0));
    assertEquals(propertyForPower(0), raster.getPowerSpeedFocusPropertyForPixel(1, 0));
    assertEquals(propertyForPower(100), raster.getPowerSpeedFocusPropertyForPixel(2, 0));
    
    // 303333333 - 3 = quite dark = 75% power
    assertEquals(propertyForPower(75), raster.getPowerSpeedFocusPropertyForPixel(0, 2));
    assertEquals(propertyForPower(0), raster.getPowerSpeedFocusPropertyForPixel(1, 2));
    assertEquals(propertyForPower(75), raster.getPowerSpeedFocusPropertyForPixel(2, 2));
  }
  
  private FloatPowerSpeedFocusProperty propertyForPower(int power)
  {
    FloatPowerSpeedFocusProperty prop = new FloatPowerSpeedFocusProperty();
    prop.setFocus(0.0f);
    prop.setSpeed(100.0f);
    prop.setPower(power);
    return prop;
  }

  /**
   * Test of getPowerSpeedFocusPropertyForColor method, of class RasterizableJobPart.
   */
  @Test
  public void testGetPowerSpeedFocusPropertyForColor()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    Raster3dPart raster = instance.toRaster3dPart();
    
    assertEquals(propertyForPower(100), raster.getPowerSpeedFocusPropertyForColor(0));
    assertEquals(propertyForPower(50), raster.getPowerSpeedFocusPropertyForColor(127));
    assertEquals(propertyForPower(0), raster.getPowerSpeedFocusPropertyForColor(255));
  }
  
  /**
   * Test the tricky case of a pixel on the very right edge
   */
  @Test
  public void testPixelOnRightHandEdge()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    instance.setRasteringCutDirectionLeftToRight();
    int y = 2;
    int x = 8;
    // 3-3333333
    x = instance.nextColorChange(x, y);
    assertEquals(9, x);
    
    boolean done = instance.hasFinishedCuttingLine(x, y);
    assertTrue(done);
  }
  
  /**
   * Test the tricky case of a pixel on the very left edge
   */
  @Test
  public void testPixelOnLeftHandEdge()
  {
    RasterizableJobPartImpl instance = new RasterizableJobPartImpl();
    
    instance.setRasteringCutDirectionRightToLeft();
    int y = 5;
    int x = 1;
    // FF0000000
    x = instance.nextColorChange(x, y);
    assertEquals(-1, x);
    
    boolean done = instance.hasFinishedCuttingLine(x, y);
    assertTrue(done);
  }
  

  
  public static class RasterizableJobPartImpl extends RasterizableJobPart
  {
    public RasterizableJobPartImpl()
    {
      // set image data
      BufferedImage imageData = new BufferedImage(9, 6, BufferedImage.TYPE_BYTE_GRAY);
      BufferedImageAdapter image = new BufferedImageAdapter(imageData);
      
      // key...
      // 255 = white = don't laser = -
      // 0 = black = laser lots! = X
      
      // fist row (B&W) => --XX-XXX-
      image.setGreyScale(0, 0, 255);
      image.setGreyScale(1, 0, 255);
      image.setGreyScale(2, 0, 0);
      image.setGreyScale(3, 0, 0);
      image.setGreyScale(4, 0, 255);
      image.setGreyScale(5, 0, 0);
      image.setGreyScale(6, 0, 0);
      image.setGreyScale(7, 0, 0);
      image.setGreyScale(8, 0, 255);
      // second row (B&W) => ---X--XX-
      image.setGreyScale(0, 1, 255);
      image.setGreyScale(1, 1, 255);
      image.setGreyScale(2, 1, 255);
      image.setGreyScale(3, 1, 0);
      image.setGreyScale(4, 1, 255);
      image.setGreyScale(5, 1, 255);
      image.setGreyScale(6, 1, 0);
      image.setGreyScale(7, 1, 0);
      image.setGreyScale(8, 1, 255);
      // third row (greyscale) => 3-3333333
      image.setGreyScale(0, 2, 63);
      image.setGreyScale(1, 2, 255);
      image.setGreyScale(2, 2, 63);
      image.setGreyScale(3, 2, 63);
      image.setGreyScale(4, 2, 63);
      image.setGreyScale(5, 2, 63);
      image.setGreyScale(6, 2, 63);
      image.setGreyScale(7, 2, 63);
      image.setGreyScale(8, 2, 63);
      // fourth row (greyscale) => --7777777
      image.setGreyScale(0, 3, 255);
      image.setGreyScale(1, 3, 255);
      image.setGreyScale(2, 3, 127);
      image.setGreyScale(3, 3, 127);
      image.setGreyScale(4, 3, 127);
      image.setGreyScale(5, 3, 127);
      image.setGreyScale(6, 3, 127);
      image.setGreyScale(7, 3, 127);
      image.setGreyScale(8, 3, 127);
      // fifth row (blank) => 000000000
      image.setGreyScale(0, 4, 255);
      image.setGreyScale(1, 4, 255);
      image.setGreyScale(2, 4, 255);
      image.setGreyScale(3, 4, 255);
      image.setGreyScale(4, 4, 255);
      image.setGreyScale(5, 4, 255);
      image.setGreyScale(6, 4, 255);
      image.setGreyScale(7, 4, 255);
      image.setGreyScale(8, 4, 255);
      // sixth row => FF0000000
      image.setGreyScale(0, 5, 0);
      image.setGreyScale(1, 5, 0);
      image.setGreyScale(2, 5, 255);
      image.setGreyScale(3, 5, 255);
      image.setGreyScale(4, 5, 255);
      image.setGreyScale(5, 5, 255);
      image.setGreyScale(6, 5, 255);
      image.setGreyScale(7, 5, 255);
      image.setGreyScale(8, 5, 255);      
      // set image data
      this.image = image;

      // set start point
      this.start = new Point(0, 0);
    }
    
    public Raster3dPart toRaster3dPart()
    {
      return new Raster3dPart(this.image, this.getLaserProperty(), this.start, this.getDPI());
    }
    
    public String getRasteringCutDirection()
    {
      return cutDirectionleftToRight
        ? "left to right"
        : "right to left";
    }
    
    public void setRasteringCutDirectionLeftToRight() {
      cutDirectionleftToRight = true;
    }
    
    public void setRasteringCutDirectionRightToLeft() {
      cutDirectionleftToRight = false;
    }
    
    @Override
    public double getMaxY()
    {
      throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public double getMaxX()
    {
      throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public double getMinY()
    {
      throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public double getMinX()
    {
      throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public LaserProperty getLaserProperty()
    {
      FloatPowerSpeedFocusProperty prop = new FloatPowerSpeedFocusProperty();
      prop.setPower(100.0f);
      prop.setSpeed(100.0f);
      prop.setFocus(0.0f);
      return prop;
    }

    @Override
    public FloatPowerSpeedFocusProperty getPowerSpeedFocusPropertyForColor(int color)
    {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getDPI()
    {
      return 72.0;
    }

    @Override
    public void getRasterLine(int line, List<Byte> result)
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getBitsPerRasterPixel()
    {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
  }
  
  public static RasterElement getTest8bitRasterElement() {
    RasterElement image = new RasterElement(9,6,8);
      
      // key...
      // 255 = white = don't laser = -
      // 0 = black = laser lots! = X
      
      // fist row (B&W) => --XX-XXX-
      image.setPixel(0, 0, 255);
      image.setPixel(1, 0, 255);
      image.setPixel(2, 0, 0);
      image.setPixel(3, 0, 0);
      image.setPixel(4, 0, 255);
      image.setPixel(5, 0, 0);
      image.setPixel(6, 0, 0);
      image.setPixel(7, 0, 0);
      image.setPixel(8, 0, 255);
      // second row (B&W) => ---X--XX-
      image.setPixel(0, 1, 255);
      image.setPixel(1, 1, 255);
      image.setPixel(2, 1, 255);
      image.setPixel(3, 1, 0);
      image.setPixel(4, 1, 255);
      image.setPixel(5, 1, 255);
      image.setPixel(6, 1, 0);
      image.setPixel(7, 1, 0);
      image.setPixel(8, 1, 255);
      // third row (greyscale) => 3-3333333
      image.setPixel(0, 2, 63);
      image.setPixel(1, 2, 255);
      image.setPixel(2, 2, 63);
      image.setPixel(3, 2, 63);
      image.setPixel(4, 2, 63);
      image.setPixel(5, 2, 63);
      image.setPixel(6, 2, 63);
      image.setPixel(7, 2, 63);
      image.setPixel(8, 2, 63);
      // fourth row (greyscale) => --7777777
      image.setPixel(0, 3, 255);
      image.setPixel(1, 3, 255);
      image.setPixel(2, 3, 127);
      image.setPixel(3, 3, 127);
      image.setPixel(4, 3, 127);
      image.setPixel(5, 3, 127);
      image.setPixel(6, 3, 127);
      image.setPixel(7, 3, 127);
      image.setPixel(8, 3, 127);
      // fifth row (blank) => 000000000
      image.setPixel(0, 4, 255);
      image.setPixel(1, 4, 255);
      image.setPixel(2, 4, 255);
      image.setPixel(3, 4, 255);
      image.setPixel(4, 4, 255);
      image.setPixel(5, 4, 255);
      image.setPixel(6, 4, 255);
      image.setPixel(7, 4, 255);
      image.setPixel(8, 4, 255);
      // sixth row => FF0000000
      image.setPixel(0, 5, 0);
      image.setPixel(1, 5, 0);
      image.setPixel(2, 5, 255);
      image.setPixel(3, 5, 255);
      image.setPixel(4, 5, 255);
      image.setPixel(5, 5, 255);
      image.setPixel(6, 5, 255);
      image.setPixel(7, 5, 255);
      image.setPixel(8, 5, 255);
      return image;
  }
  
  public static RasterElement getTest1bitRasterElement() {
    RasterElement image = new RasterElement(9,6,1);
      
      
      // fist row (B&W) => 110010001
      image.setPixel(0, 0, 1);
      image.setPixel(1, 0, 1);
      image.setPixel(2, 0, 0);
      image.setPixel(3, 0, 0);
      image.setPixel(4, 0, 1);
      image.setPixel(5, 0, 0);
      image.setPixel(6, 0, 0);
      image.setPixel(7, 0, 0);
      image.setPixel(8, 0, 1);
      // second row (B&W) => 111011001
      image.setPixel(0, 1, 1);
      image.setPixel(1, 1, 1);
      image.setPixel(2, 1, 1);
      image.setPixel(3, 1, 0);
      image.setPixel(4, 1, 1);
      image.setPixel(5, 1, 1);
      image.setPixel(6, 1, 0);
      image.setPixel(7, 1, 0);
      image.setPixel(8, 1, 1);
      // third row (greyscale) => 111111111
      image.setPixel(0, 2, 1);
      image.setPixel(1, 2, 1);
      image.setPixel(2, 2, 1);
      image.setPixel(3, 2, 1);
      image.setPixel(4, 2, 1);
      image.setPixel(5, 2, 1);
      image.setPixel(6, 2, 1);
      image.setPixel(7, 2, 1);
      image.setPixel(8, 2, 1);
      // fourth row (greyscale) => 110000000
      image.setPixel(0, 3, 1);
      image.setPixel(1, 3, 1);
      image.setPixel(2, 3, 0);
      image.setPixel(3, 3, 0);
      image.setPixel(4, 3, 0);
      image.setPixel(5, 3, 0);
      image.setPixel(6, 3, 0);
      image.setPixel(7, 3, 0);
      image.setPixel(8, 3, 0);
      // fifth row (blank) => 111111111
      image.setPixel(0, 4, 1);
      image.setPixel(1, 4, 1);
      image.setPixel(2, 4, 1);
      image.setPixel(3, 4, 1);
      image.setPixel(4, 4, 1);
      image.setPixel(5, 4, 1);
      image.setPixel(6, 4, 1);
      image.setPixel(7, 4, 1);
      image.setPixel(8, 4, 1);
      // sixth row => 001111111
      image.setPixel(0, 5, 0);
      image.setPixel(1, 5, 0);
      image.setPixel(2, 5, 1);
      image.setPixel(3, 5, 1);
      image.setPixel(4, 5, 1);
      image.setPixel(5, 5, 1);
      image.setPixel(6, 5, 1);
      image.setPixel(7, 5, 1);
      image.setPixel(8, 5, 1);
      return image;
  }
}
