/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2013 Thomas Oster <thomas.oster@rwth-aachen.de>
 * RWTH Aachen University - 52062 Aachen, Germany
 *
 *     LibLaserCut is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     LibLaserCut is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with LibLaserCut.  If not, see <http://www.gnu.org/licenses/>.
 **/
package com.t_oster.liblasercut.drivers;

import com.apple.crypto.provider.Debug;
import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.JobPart;
import com.t_oster.liblasercut.LaserCutter;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.PowerSpeedFocusFrequencyProperty;
import com.t_oster.liblasercut.ProgressListener;
import com.t_oster.liblasercut.Raster3dPart;
import com.t_oster.liblasercut.RasterPart;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorCommand.CmdType;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Util;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class MPC6515Cutter extends LaserCutter
{

  private static Double[] SUPPORTED_RESOLUTIONS = new Double[]{200d,500d,1000d};
  private static final String BED_WIDTH = "bed width";
  private static final String BED_HEIGHT = "bed height";
  private static String[] PROPERTIES = new String[]{
    BED_WIDTH,
    BED_HEIGHT,
  };
 
  private double bedWidth = 300;
  private double bedHeight = 210;
  
  private double firstX = 0.0, firstY = 0.0, prevX = 0.0, prevY = 0.0;
  private double bboxMinX = 0.0, bboxMinY = 0.0, bboxMaxX = 0.0, bboxMaxY = 0.0;
  boolean firstVector;
  
  @Override
  public double getBedWidth()
  {
    return bedWidth;
  }

  public void setBedWidth(double bedWidth)
  {
    this.bedWidth = bedWidth;
  }

  @Override
  public double getBedHeight()
  {
    return bedHeight;
  }

  public void setBedHeight(double bedHeight)
  {
    this.bedHeight = bedHeight;
  }

  private void molWriteHeader(RandomAccessFile out) throws IOException
  {
    out.seek(0);
    // 0: Size of entire file in bytes (must be patched later)
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 4: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000022));
    // 8: Unknown, fixed value
    out.write(0x02);
    // 9: Unknown, fixed value
    out.write(0x02);
    // a: Unknown, fixed value
    out.write(0x01);
    // b: Unknown, fixed value
    out.write(0x04);
    // c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000001));
    // 10: This word changes when the size of the file changes, i.e. when lines are added
    // This line corresponds to the number of "move relative" commands in the entire file counting
    // "unknown 07" and "unknown 09" as well. Is that coincidence? What would this value be good for?
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 14: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000001));
    // 18: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 1c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00000000));
    // 20: Describing the stepper resolution in x and y
    out.writeInt(Integer.reverseBytes(-20833));
    out.writeInt(Integer.reverseBytes(-20833));
    // 28: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x00481095));
    // 2c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(0x471a0000));
    // Unknown bunch of zeros, likely reserved for later use
    out.seek(0x00000070);
    // 70: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(1));
    // 74: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(2));
    // 78: Unknown, fixed value, maybe the number of blocks in this file?
    out.writeInt(Integer.reverseBytes(3));
    // 7c: Unknown, fixed value
    out.writeInt(Integer.reverseBytes(5));
  }
  
  private void molWriteFloat(RandomAccessFile out, float v) throws IOException
  {
    // LEETRO float: [eeeeeeee|smmmmmmm|mmmmmmm0|00000000]
    //     IEEE 754: [seeeeeee|emmmmmmm|mmmmmmmm|mmmmmmmm]
    int i = Float.floatToRawIntBits(v);
    
    int ieeeMantissa = ( i & 0x7fffff );
    int ieeeExponent = ( (i>>23) & 0xff );
    int ieeeSign     = ( (i>>31) & 1 );
    
    int c30Mantissa = ieeeMantissa;
    int c30Exponent = (ieeeExponent==0) ? -128 : ieeeExponent - 127;
    int c30Sign = ieeeSign; // ??? float = -float
    
    i = (c30Mantissa & 0x7fffff) | ((c30Sign & 1) << 23) | ((c30Exponent & 0xff) << 24);
    
    out.writeInt(Integer.reverseBytes(i));
  }

  private void molWritePercent(RandomAccessFile out, float v) throws IOException
  {
    molWriteFloat(out, v*208.33f);
  }

  private void molWriteInt(RandomAccessFile out, int a) throws IOException
  {
    out.writeInt(Integer.reverseBytes(a));
  }

  private void molWriteMm(RandomAccessFile out, float v) throws IOException
  {
    molWriteInt(out, (int)(v*208.33f));
  }

  private void molWriteBytes(RandomAccessFile out, int arg0, int arg1, int arg2, int arg3) throws IOException
  {
    out.write(arg0); out.write(arg1); out.write(arg2); out.write(arg3);
  }

  // begin subroutine n
  private void molCmdBeginSub(RandomAccessFile out, int a) throws IOException
  {
    out.write(0x48); out.write(0x00); out.write(0x30); out.write(1);
    out.writeInt(Integer.reverseBytes(a));
  }

  // end subroutine n
  private void molCmdEndSub(RandomAccessFile out, int a) throws IOException
  {
    out.write(0x48); out.write(0x00); out.write(0x40); out.write(1);
    out.writeInt(Integer.reverseBytes(a));
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, int arg0, int arg1) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    out.writeInt(Integer.reverseBytes(arg0));
    out.writeInt(Integer.reverseBytes(arg1));
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, int arg0, int arg1, float arg2, float arg3, float arg4, float arg5, float arg6) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    out.writeInt(Integer.reverseBytes(arg0));
    out.writeInt(Integer.reverseBytes(arg1));
    molWriteFloat(out, arg2);
    molWriteFloat(out, arg3);
    molWriteFloat(out, arg4);
    molWriteFloat(out, arg5);
    molWriteFloat(out, arg6);
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, int arg0) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    out.writeInt(Integer.reverseBytes(arg0));
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, float arg0, float arg1, float arg2, float arg3, int arg4) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    molWriteFloat(out, arg0);
    molWriteFloat(out, arg1);
    molWriteFloat(out, arg2);
    molWriteFloat(out, arg3);
    out.writeInt(Integer.reverseBytes(arg4));
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, float arg0, float arg1, float arg2) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    molWriteFloat(out, arg0);
    molWriteFloat(out, arg1);
    molWriteFloat(out, arg2);
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, int arg0, float arg1, float arg2) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    out.writeInt(Integer.reverseBytes(arg0));
    molWriteFloat(out, arg1);
    molWriteFloat(out, arg2);
  }

  // unkonwn command
  private void molCmd(RandomAccessFile out, int maj, int min, int fill, int n, float arg0, float arg1, float arg2, float arg3, float arg4, float arg5, int arg6) throws IOException
  {
    out.write(maj); out.write(min); out.write(fill); out.write(n);
    molWriteFloat(out, arg0);
    molWriteFloat(out, arg1);
    molWriteFloat(out, arg2);
    molWriteFloat(out, arg3);
    molWriteFloat(out, arg4);
    molWriteFloat(out, arg5);
    out.writeInt(Integer.reverseBytes(arg6));
  }

  private void molCmdAccelerate(RandomAccessFile out) throws IOException
  {
    molCmd(out, 0x01, 0x46, 0x00, 0x01,  1);
  }
  
  private void molCmdDecelerate(RandomAccessFile out) throws IOException
  {
    molCmd(out, 0x01, 0x46, 0x00, 0x01,  2);
  }
  
  private void molSetSpeed(RandomAccessFile out, float a, float b, float c) throws IOException
  {
    molCmd(out, 0x01, 0x03, 0x00, 0x03,  a, b, c);
  }
  
  private void molMoveRelative(RandomAccessFile out, int a, float b, float c) throws IOException
  {
    molCmd(out, 0x00, 0x60, 0x02, 0x03,  a, b, c);
  }
  
  // unkonwn command 00
  private void molCmd00(RandomAccessFile out, int a, float b, float c) throws IOException
  {
    out.write(0x48); out.write(0x00); out.write(0x50); out.write(0x80);
    out.writeInt(Integer.reverseBytes(3));
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }
  
  // unkonwn command 00
  private void molCmd00(RandomAccessFile out, int a) throws IOException
  {
    out.write(0x48); out.write(0x00); out.write(0x50); out.write(0x80);
    out.writeInt(Integer.reverseBytes(1));
    out.writeInt(Integer.reverseBytes(a));
  }
  
  // unkonwn command 01
  private void molCmd01(RandomAccessFile out, int a, float b) throws IOException
  {
    out.write(0x48); out.write(1); out.write(0x60); out.write(0x80);
    out.writeInt(Integer.reverseBytes(2));
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
  }
  
  // unkonwn command 01
  private void molCmd01(RandomAccessFile out, int a, float b, float c, float d, float e, float f) throws IOException
  {
    out.write(0x48); out.write(1); out.write(0x60); out.write(0x80);
    out.writeInt(Integer.reverseBytes(6));
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
    molWriteFloat(out, c);
    molWriteFloat(out, d);
    molWriteFloat(out, e);
    molWriteFloat(out, f);
  }
  
  // unkonwn command 03
  private void molCmd03(RandomAccessFile out, float a, float b, float c) throws IOException
  {
    out.write(0x41); out.write(0x03); out.write(0x00); out.write(3);
    molWriteFloat(out, a);
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }
  
  // unkonwn command 05
  private void molCmd05(RandomAccessFile out) throws IOException
  {
    out.write(0x48); out.write(5); out.write(0x20); out.write(0);
  }
  
  // unkonwn command 06
  private void molCmd06(RandomAccessFile out) throws IOException
  {
    out.write(0x48); out.write(6); out.write(0x20); out.write(0);
  }
  
  // unkonwn command 07
  private void molCmd07(RandomAccessFile out) throws IOException
  {
    out.write(0x48); out.write(7); out.write(0x20); out.write(0);
  }
  
  // unkonwn command 08
  private void molCmd08(RandomAccessFile out) throws IOException
  {
    out.write(0x48); out.write(8); out.write(0x20); out.write(0);
  }
  
  // unkonwn command 09
  private void molCmd09(RandomAccessFile out, int a) throws IOException
  {
    out.write(0x48); out.write(0x09); out.write(0x20); out.write(1);
    out.writeInt(Integer.reverseBytes(a));
  }
  
  // unkonwn command 0d
  private void molCmd0d(RandomAccessFile out, int a) throws IOException
  {
    out.write(0x46); out.write(0x0d); out.write(0x00); out.write(1);
    out.writeInt(Integer.reverseBytes(a));
  }
  
  // unkonwn command 0e
  private void molCmd0e(RandomAccessFile out, float a, float b, float c) throws IOException
  {
    out.write(0x46); out.write(0x0e); out.write(0); out.write(3);
    molWriteFloat(out, a);
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }
  
  // unkonwn command 60
  private void molCmd60(RandomAccessFile out, int a, float b, float c) throws IOException
  {
    out.write(0x40); out.write(0x60); out.write(0x04); out.write(3);
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }
  
  // unkonwn command 60
  private void molCmd60_2(RandomAccessFile out, int a, float b, float c) throws IOException
  {
    out.write(0x40); out.write(0x60); out.write(0x02); out.write(3);
    out.writeInt(Integer.reverseBytes(a));
    molWriteFloat(out, b);
    molWriteFloat(out, c);
  }
  
  private void molCmdMoveRelative(RandomAccessFile out, double dx, double dy, boolean cut) throws IOException
  {
    double len = Math.sqrt(dx*dx+dy*dy);
    molCmdAccelerate(out); // 01 46 00 01 01 00 00 00
    molSetSpeed(out, 5.0f, 200.0f, 700.0f); // 01 03 00 03 b3 23 38 0a 00 c2 22 0f 40 6a 0e 11
    molMoveRelative(out, 772, (float)(0.25*dx), (float)(0.25*dy));     // 00 60 02 03 04 03 00 00 00 00 00 00 c8 e8 ff ff
    molSetSpeed(out, 200.0f, 200.0f, 700.0f);
    molMoveRelative(out, 772, (float)(0.5*dx), (float)(0.5*dy));     // 00 60 02 03 04 03 00 00 00 00 00 00 c8 e8 ff ff
    molCmdDecelerate(out); // 01 46 00 01 02 00 00 00
    molSetSpeed(out, 5.0f, 200.0f, 700.0f);
    molMoveRelative(out, 772, (float)(0.25*dx), (float)(0.25*dy));     // 00 60 02 03 04 03 00 00 00 00 00 00 c8 e8 ff ff
  }
  
  private void molWriteConfig(RandomAccessFile out) throws IOException
  {
    //The configuration chunk still has a bunch of unknown commands, few of them seem to change
    //for a single machine configuration.  
    out.seek(0x200);
  
    // 200: Unknown, fixed value
    molCmd05(out);
    // 204: Unknown, fixed value, the default driver for the X-axis is #4 (maybe related?)
    molCmd01(out, 4, 4.0f);
    // 214: Unknown, fixed value, the default driver for the Y-axis is #3 (maybe related?)
    molCmd01(out, 3, 3.0f);
    // 224: Unknown, fixed value
    molCmd08(out);
    // 228: Unknown, fixed value
    molCmd07(out);
    // 22c: Unknown
    // arg1 is unknown, but repeats in 'unknown 11'
    // arg2 is the start speed for all head movements as describen in the settings
    // arg3 is the maximum speed for moving around "quickly"
    // arg4 is the acceleration value to get to the above speed (space acc)
    // arg5 is the value for acceleration from the settings
    // arg6 is unknown and nowhere to be found, probably the slow acceleration default
    molCmd01(out, 603, 5.0f, 200.0f, 700.0f, 500.0f, 350.0f);
    // 24c: Set the Stepper sizes
    // arg1 is the number of steps required in X direction
    // arg2 is the same for Y
    // arg3 is likely the same in Z direction
    molCmd0e(out, 208.333f, 208.333f, 800.0f);
    // 25c: Unknown, fixed value, maybe explicitly switching the laser off?
    molCmd09(out, 0);
    // 264: Object origin. Cutting a 100x100 object on a 900x600 table would move the laser head
    // to the top right corner of a centered work piece. (772 see "move rel")
    molCmd60(out, 772, 500.005f, 350.002f); // FIXME: patch me later!
    // 274: Motion parameters:
    // arg1 is the initial speed
    // arg2 is the maximum speed 
    // arg3 is the acceleration
    molCmd03(out, 5.0f, 200.0f, 700.0f);
    // 284: Object size. Our test object is 100x100, so this command moves to the bottom left corner.
    molCmd60_2(out, 772, -100.0f, -100.0f); // FIXME: patch me later
    // 294: Unknown, fixed value
    molCmd00(out, 3, 2.0f, 2.0f);
    // 2a8: Unknown, fixed value, 603 also appears at 0x0000022c
    molCmd00(out, 11);
    // Unknown, fixed value
    molCmd06(out);
  }
  
  private void molFixHeader(RandomAccessFile out) throws IOException
  {
    // align file to the next 512 byte boundary for easy USB transfer
    int len = (int)out.length();
    len = (len+511)&0xfffffe00;
    if (len>(int)out.length()) {
      out.seek(len-1);
      out.write(0);
    }
    // Size of entire file in bytes
    out.seek(0x00000000);
    out.writeInt(Integer.reverseBytes(len));
    // 10: This word changes when the size of the file changes, i.e. when lines are added
    // This line corresponds to the number of "move relative" commands in the entire file counting
    // "unknown 07" and "unknown 09" as well. Is that coincidence? What would this value be good for?
    out.seek(0x00000010);
    out.writeInt(Integer.reverseBytes(0x00000000/*FIXME*/));
  }
    
  private void molFixConfig(RandomAccessFile out) throws IOException
  {
    // FIXME: fill this!
    out.seek(0x00000200);
    
    // 284: Object size. Our test object is 100x100, so this command moves to the bottom left corner.
    out.seek(0x00000284);
    molCmd60_2(out, 772, (float)-firstX, (float)-firstY); // FIXME: sign?
  }
  
  private void molWriteBounds(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000400);
  }
  
  private void molFixBounds(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000400);
    molCmdBeginSub(out, 1);
    // 00000408: : unknown 05:  603, 1041(5.00%), 41666(200.00%), 145833(700.00%), 
    //                     104166(500.00%), 72916(350.00%)
    molCmd(out,  0x48, 0x01, 0x60, 0x80,  6, 603, 5.0f, 200.0f, 700.0f, 500.0f, 350.0f);

    // 00000428: : command block follows, 112 words (form 00000430 to 000005f0)
    molCmd(out, 0x46, 0x09, 0x00, 0x80,  0x00000070);

    molCmdMoveRelative(out, bboxMinX-bboxMaxX, 0.0f, false);
    molCmdMoveRelative(out, 0.0f, bboxMinY-bboxMaxY, false);
    molCmdMoveRelative(out, bboxMaxX-bboxMinX, 0.0f, false);
    molCmdMoveRelative(out, 0.0f, bboxMaxY-bboxMinY, false);
    
    //This is where the block ends
    int n = (int) out.getChannel().position();
    out.seek(0x00000428);
    molCmd(out, 0x46, 0x09, 0x00, 0x80,  (n-0x00000428-8)/4);
    out.seek(n);

    molCmdEndSub(out, 1);
  }
  
  // molMoveRel(out, 5.0f, 200.0f, 5.0f, 700.0f, -100.0f, 0.0f);
  private void molMoveRel(RandomAccessFile out, double startSpeed, double maxSpeed, double endSpeed, double accel, double dx, double dy)
     throws IOException
  {
    double lenRampUp   = 0.5 * (maxSpeed-startSpeed) * (maxSpeed+startSpeed) / accel;
    double lenRampDown = 0.5 * (maxSpeed-endSpeed)   * (maxSpeed+endSpeed)   / accel;
    double len = Math.sqrt(dx*dx+dy*dy);
    double lenConst = len - lenRampUp - lenRampDown;
    
    if (len<1.0)
      return;
    
    if (lenConst>=5.0) { // huh?
      lenRampUp = lenRampUp / len;
      lenConst = lenConst / len;
      lenRampDown = lenRampDown /len;
      // 0x00000430: accelerate
      molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
      molWriteInt(out, 0x00000001);
      // 0x00000438: set speeds
      molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
      molWriteFloat(out, (float)startSpeed);
      molWriteFloat(out, (float)maxSpeed);
      molWriteFloat(out, (float)accel);
      // 0x00000448: move rel
      molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
      molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);
      molWriteInt(out, (int)(dx*lenRampUp));
      molWriteInt(out, (int)(dy*lenRampUp));
      // 0x00000458: set speed
      molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
      molWriteFloat(out, (float)maxSpeed);
      molWriteFloat(out, (float)maxSpeed);
      molWriteFloat(out, (float)accel);
      // 0x00000468: move rel
      molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
      molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);
      molWriteInt(out, (int)(dx - dx*lenRampUp - dx*lenRampDown));
      molWriteInt(out, (int)(dy - dy*lenRampUp - dy*lenRampDown));
      // 0x00000478: decelerate
      molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
      molWriteInt(out, 2);
      // 0x00000480: set speeds
      molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
      molWriteFloat(out, (float)endSpeed);
      molWriteFloat(out, (float)maxSpeed);
      molWriteFloat(out, (float)accel);
      // 0x00000490: move rel
      molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
      molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);
      molWriteInt(out, (int)(dx*lenRampDown));
      molWriteInt(out, (int)(dy*lenRampDown));
    } else {
      double scale = (lenRampUp+lenConst/2.0) / lenRampUp;
      Debug.println("LenConst is " + Double.toString(lenConst) + "  Scale: " + Double.toString(scale));
      //lenRampUp = (lenRampUp+lenConst/2.0) / len;
      //lenRampDown = (lenRampDown+lenConst/2.0) /len;      
      //maxSpeed = startSpeed + lenRampUp*accel;
      //maxSpeed = Math.sqrt(lenRampUp*accel*2.0 + startSpeed*startSpeed);
      lenRampUp   =   (lenRampUp+lenConst/2.0) / len;
      lenRampDown = (lenRampDown+lenConst/2.0) / len;
      maxSpeed = (maxSpeed-startSpeed)*scale + startSpeed;

      if (Math.abs(dx*lenRampUp)>=1.0||Math.abs(dy*lenRampUp)>1.0) {
        // 0x00000430: accelerate
        molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
        molWriteInt(out, 0x00000001);
        // 0x00000438: set speeds
        molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
        molWriteFloat(out, (float)startSpeed);
        molWriteFloat(out, (float)maxSpeed);
        molWriteFloat(out, (float)accel);
        // 0x00000448: move rel
        molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
        molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);
        molWriteInt(out, (int)(dx*lenRampUp));
        molWriteInt(out, (int)(dy*lenRampUp));
      }
      if (Math.abs(dx*lenRampDown)>=1.0||Math.abs(dy*lenRampDown)>=1.0) {
        // 0x00000478: decelerate
        molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
        molWriteInt(out, 2);
        // 0x00000480: set speeds
        molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
        molWriteFloat(out, (float)endSpeed);
        molWriteFloat(out, (float)maxSpeed);
        molWriteFloat(out, (float)accel);
        // 0x00000490: move rel
        molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
        molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);
        molWriteInt(out, (int)(dx*lenRampDown));
        molWriteInt(out, (int)(dy*lenRampDown));
      }
    }    
  }
  
  // 
  // .MOL file header
  // 
  private void molWriteBlock0000(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000000);
    molWriteInt(out, 0x00001000);  // 0000: file size in bytes
    molWriteInt(out, 34);  // 0004: ?
    molWriteBytes(out, 0x02, 0x02, 0x01, 0x04);  // 0008: ?
    molWriteInt(out, 1);  // 000c: ?
    //molWriteInt(out, 41);  // 0010: somehow related to the file size or the number of commands
    molWriteInt(out, 2000);  // 0010: somehow related to the file size or the number of commands
    molWriteInt(out, 1);  // 0014: ?
    molWriteInt(out, 0);  // 0018: ?
    molWriteInt(out, 0);  // 001c: ?
    molWriteInt(out, -20833);  // 0020: steps per 100mm in x direction
    molWriteInt(out, -20833);  // 0024: steps per 100mm in y direction
    molWriteInt(out, 0x00481095);  // 0028: ?
    molWriteInt(out, 0x471a0000);  // 002c: ?
    molWriteInt(out, 0);  // 0030: 0
    molWriteInt(out, 0);  // 0034: 0
    molWriteInt(out, 0);  // 0038: 0
    molWriteInt(out, 0);  // 003c: 0
    molWriteInt(out, 0);  // 0040: 0
    molWriteInt(out, 0);  // 0044: 0
    molWriteInt(out, 0);  // 0048: 0
    molWriteInt(out, 0);  // 004c: 0
    molWriteInt(out, 0);  // 0050: 0
    molWriteInt(out, 0);  // 0054: 0
    molWriteInt(out, 0);  // 0058: 0
    molWriteInt(out, 0);  // 005c: 0
    molWriteInt(out, 0);  // 0060: 0
    molWriteInt(out, 0);  // 0064: 0
    molWriteInt(out, 0);  // 0068: 0
    molWriteInt(out, 0);  // 006c: 0
    molWriteInt(out, 1);  // 0070: ?
    molWriteInt(out, 2);  // 0074: ?
    molWriteInt(out, 3);  // 0078: ?
    molWriteInt(out, 5);  // 007c: ?
  }

  // 
  // .MOL file fixup
  // 
  private void molFixBlock0000(RandomAccessFile out) throws IOException
  {
    // align file to the next 512 byte boundary for easy USB transfer
    int len = (int)out.length();
    int blockLen = (len+511)&0xfffffe00;
    if (len<blockLen) {
      out.seek(blockLen-4);
      molWriteInt(out, 0);
    }
    out.seek(0);
    molWriteInt(out, blockLen);
    // FIXME: we must also fixup the movement counter in 0x00000010!
  }

  // 
  // .MOL file data block at 0x00000200
  // 
  private void molWriteBlock0200(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000200);
  // 0200: Unknown Command 1
    molWriteBytes(out, 0x48, 0x05, 0x20, 0x00);
  // 0204: Unknown Command 2
    molWriteBytes(out, 0x48, 0x01, 0x60, 0x80);
    molWriteInt(out, 2);  // Length = 2 words
    molWriteInt(out, 4);
    molWriteFloat(out, 4f);
  // 0214: Unknown Command 2
    molWriteBytes(out, 0x48, 0x01, 0x60, 0x80);
    molWriteInt(out, 2);  // Length = 2 words
    molWriteInt(out, 3);
    molWriteFloat(out, 3f);
  // 0224: Unknown Command 3
    molWriteBytes(out, 0x48, 0x08, 0x20, 0x00);
  // 0228: Unknown Command 4
    molWriteBytes(out, 0x48, 0x07, 0x20, 0x00);
  // 022c: Unknown Command 2
    molWriteBytes(out, 0x48, 0x01, 0x60, 0x80);
    molWriteInt(out, 6);  // Length = 6 words
    molWriteInt(out, 603);
    molWriteFloat(out, 1041f); // (5%) start speed for all head movements
    molWriteFloat(out, 41666f); // (200%) maximum speed for moving around quickly
    molWriteFloat(out, 145833f); // (700%) acceleration value to get to the above speed
    molWriteFloat(out, 104166f); // (500%) value for acceleration from the settings
    molWriteFloat(out, 72916f); // (350%) 
  // 024c: steps required
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x03);
    molWriteFloat(out, 208.333f);  // x steps per mm
    molWriteFloat(out, 208.333f);  // y steps per mm
    molWriteFloat(out, 800f);  // z steps per mm
  // 025c: Unknown Command 6
    molWriteBytes(out, 0x48, 0x09, 0x20, 0x01);
    molWriteInt(out, 0);
  // 0264: Artwork Origin
    molWriteBytes(out, 0x40, 0x60, 0x04, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);  // Select motion axes
    molWriteInt(out, 104166); // (500.005mm) table width + artwork width / 2
    molWriteInt(out, 72916); // (350.002mm) table height + artwork height / 2
  // 0274: Set Speed
    molWriteBytes(out, 0x41, 0x03, 0x00, 0x03);
    molWriteFloat(out, 1041f); // (5%) start
    molWriteFloat(out, 41666f); // (200%) max
    molWriteFloat(out, 145833f); // (700%) accel
  // 0284: Start Corner
    molWriteBytes(out, 0x40, 0x60, 0x02, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);  // Select motion axes
    molWriteInt(out, -20833); // (-100mm) left
    molWriteInt(out, -20833); // (-100mm) bottom
  // 0294: Unknown Command 10
    molWriteBytes(out, 0x48, 0x00, 0x50, 0x80);
    molWriteInt(out, 3);  // Length = 3 words
    molWriteInt(out, 3);
    molWriteInt(out, 0x81000000);
    molWriteInt(out, 0x81000000);
  // 02a8: Unknown Command 10
    molWriteBytes(out, 0x48, 0x00, 0x50, 0x80);
    molWriteInt(out, 1);  // Length = 1 words
    molWriteInt(out, 603);
  // 02b4: Unknown Command 12
    molWriteBytes(out, 0x48, 0x06, 0x20, 0x00);
  }

  private void molFixBlock0200(RandomAccessFile out) throws IOException
  {
  // 0264: Artwork Origin
    out.seek(0x00000264);
    molWriteBytes(out, 0x40, 0x60, 0x04, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);  // Select motion axes
    molWriteMm(out, (float)(0.5*(bedWidth+(bboxMaxX-bboxMinX)))); // (500.005mm) (table width + artwork width) / 2
    molWriteMm(out, (float)(0.5*(bedHeight+(bboxMaxY-bboxMinY)))); // (350.002mm) (table height + artwork height) / 2
  // 0284: Start Corner
    out.seek(0x00000284);
    molWriteBytes(out, 0x40, 0x60, 0x02, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);  // Select motion axes
    molWriteMm(out, (float)(bboxMinX-bboxMaxX)); // (-100mm) left
    molWriteMm(out, (float)(bboxMinY-bboxMaxY)); // (-100mm) bottom
  }

  // 
  // .MOL file data block at 0x00000400
  // 
  private void molWriteBlock0400(RandomAccessFile out) throws IOException
  {
    // data is written in FixBlock
  }
  
  // 
  // .MOL file data block at 0x00000400
  // 
  private void molFixBlock0400(RandomAccessFile out) throws IOException
  {
    double wdt = bboxMaxX - bboxMinX, hgt = bboxMaxY - bboxMinY;
    out.seek(0x00000400);
  // 0400: Begin Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x30, 0x01);
    molWriteInt(out, 1);
  // 0408: Unknown Command 2
    molWriteBytes(out, 0x48, 0x01, 0x60, 0x80);
    molWriteInt(out, 6);  // Length = 6 words
    molWriteInt(out, 603);
    molWriteFloat(out, 1041f); // (5%) start speed for all head movements
    molWriteFloat(out, 41666f); // (200%) maximum speed for moving around quickly
    molWriteFloat(out, 145833f); // (700%) acceleration value to get to the above speed
    molWriteFloat(out, 104166f); // (500%) value for acceleration from the settings
    molWriteFloat(out, 72916f); // (350%) 
  // 0428: Begin Motion Block
  // 0428: Begin Motion Block
    molWriteBytes(out, 0x46, 0x09, 0x00, 0x80);
    molWriteInt(out, 112);  // FIXME: number of words in motion block
  // motion  
    molMoveRel(out, 1041f, 41666f, 1473.12f, 145833f, -wdt*208.33, 0f); // (5%, 200%, 7%, 700%, -100mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1473.12f, 145833f, 0f, -hgt*208.33); // (7%, 200%, 7%, 700%, 0mm, -100mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1473.12f, 145833f, wdt*208.33, 0f); // (7%, 200%, 7%, 700%, 100mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1041f, 145833f, 0f, hgt*208.33); // (7%, 200%, 5%, 700%, 0mm, 100mm [rcr])
  // 05f0: End Of Motion Block (started at 0430, 112 words)
    out.seek(0x0000042c);  // fix the motion block word count
    molWriteInt(out, 112);  // calculated size
    out.seek(0x000005f0);  // continue here
  // 05f0: End Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x40, 0x01);
    molWriteInt(out, 1);
  }

  // 
  // .MOL file data block at 0x00000600
  // 
  private void molWriteBlock0600(RandomAccessFile out) throws IOException
  {
    // data is written in FixBlock
  }
    
  // 
  // .MOL file data block at 0x00000600
  // 
  private void molFixBlock0600(RandomAccessFile out) throws IOException
  {
    double wdt = bboxMaxX - bboxMinX, hgt = bboxMaxY - bboxMinY;
    out.seek(0x00000600);
  // 0600: Begin Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x30, 0x01);
    molWriteInt(out, 2);
  // 0608: Unknown Command 2
    molWriteBytes(out, 0x48, 0x01, 0x60, 0x80);
    molWriteInt(out, 6);  // Length = 6 words
    molWriteInt(out, 603);
    molWriteFloat(out, 1041f); // (5%) start speed for all head movements
    molWriteFloat(out, 41666f); // (200%) maximum speed for moving around quickly
    molWriteFloat(out, 145833f); // (700%) acceleration value to get to the above speed
    molWriteFloat(out, 104166f); // (500%) value for acceleration from the settings
    molWriteFloat(out, 72916f); // (350%) 
  // 0628: Laser Power
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x05);
    molWriteInt(out, 4000);  // (40.000000%) corner power
    molWriteInt(out, 4000);  // (40.000000%) maximum power
    molWriteFloat(out, 1041f); // (5%) cutting start speed
    molWriteFloat(out, 5208f); // (25%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  // 0640: Laser Power Long
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x07);
    molWriteInt(out, 4000);  // (40.000000%) corner power
    molWriteInt(out, 4000);  // (40.000000%) maximum power
    molWriteInt(out, 4000);  // (40.000000%) ?
    molWriteInt(out, 4000);  // (40.000000%) ?
    molWriteFloat(out, 1041f); // (5%) cutting start speed
    molWriteFloat(out, 5208f); // (25%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  // 0660: Begin Motion Block
    molWriteBytes(out, 0x46, 0x09, 0x00, 0x80);
    molWriteInt(out, 144);  // number of words in motion block
    molMoveRel(out, 1041f, 5208f, 1014.73f, 104166f, 417f, 417f); // (5%, 25%, 5%, 500%, 2.00163mm, 2.00163mm [rcr])
  // 06d8: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 1014.73f, 5208f, 1473.12f, 104166f, (-wdt-4.0)*208.33, 0f); // (5%, 25%, 7%, 500%, -104.003mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1473.12f, 104166f, 0f, (-hgt-4.0)*208.33); // (7%, 25%, 7%, 500%, 0mm, -104.003mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1473.12f, 104166f, (wdt+4.0)*208.33, 0f); // (7%, 25%, 7%, 500%, 104.003mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1041f, 104166f, 0f, (hgt+4.0)*208.33); // (7%, 25%, 5%, 500%, 0mm, 104.003mm [rcr])
  // 08a0: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  // 08a8: End Of Motion Block (started at 0668, 144 words)
    out.seek(0x00000664);  // fix the motion block word count
    molWriteInt(out, 144);  // calculated size
    out.seek(0x000008a8);  // continue here
  // 08a8: End Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x40, 0x01);
    molWriteInt(out, 2);
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    // This is a first try in Java. All data is saved into ~/RUN.MOL until we figure out the USB communication
    
    pl.taskChanged(this, "checking...");
    checkJob(job);
    
    pl.progressChanged(this, 20);
    pl.taskChanged(this, "buffering...");
    
    // - delete the file
    try {
      File f = new File("/Users/matt/RUN.MOL");
      if (f.exists()) {
        f.delete();
      }
    } catch (Exception e) {
    }
    
    // - create a random access file (some data can only be patched after the entire file was written)
    File file;
    file = new File("/Users/matt/RUN.MOL");
    RandomAccessFile out = new RandomAccessFile(file, "rw");
    
    // taken from MOL file 0030.MOL
    molWriteBlock0000(out);
    
    molWriteBlock0200(out);
    
    molWriteBlock0400(out);
    
    molWriteBlock0600(out);


  // 
  // .MOL file data block at 0x00000a00
  // 
    out.seek(0x00000a00);
  // 0a00: Begin Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x30, 0x01);
    molWriteInt(out, 3);
  // 0a08: Unknown Command 13
    molWriteBytes(out, 0x46, 0x0d, 0x00, 0x01);
    molWriteInt(out, 3000);
  // 0a10: Unknown Command 14
    molWriteBytes(out, 0x41, 0x4a, 0x00, 0x02);
    molWriteInt(out, 4);
    molWriteInt(out, 0);
  // 0a1c: Unknown Command 15
    molWriteBytes(out, 0x41, 0x4b, 0x00, 0x01);
    molWriteInt(out, 516);
  // 0a24: Unknown Command 14
    molWriteBytes(out, 0x41, 0x4a, 0x00, 0x02);
    molWriteInt(out, 3);
    molWriteInt(out, 0);
  // 0a30: Unknown Command 15
    molWriteBytes(out, 0x41, 0x4b, 0x00, 0x01);
    molWriteInt(out, 515);
  // 0a38: Laser Power
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x05);
    molWriteInt(out, 4000);  // (40.000000%) corner power
    molWriteInt(out, 4000);  // (40.000000%) maximum power
    molWriteFloat(out, 1041f); // (5%) cutting start speed
    molWriteFloat(out, 20833f); // (100%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  // 0a50: Laser Power Long
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x07);
    molWriteInt(out, 4000);  // (40.000000%) corner power
    molWriteInt(out, 4000);  // (40.000000%) maximum power
    molWriteInt(out, 3000);  // (30.000000%) ?
    molWriteInt(out, 5000);  // (50.000000%) ?
    molWriteFloat(out, 1041f); // (5%) cutting start speed
    molWriteFloat(out, 20833f); // (100%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  // 0a70: Begin Motion Block
    molWriteBytes(out, 0x46, 0x09, 0x00, 0x80);
    molWriteInt(out, 290);  // number of words in motion block
  // 0a78: Blower On/Off
    molWriteBytes(out, 0x06, 0x0b, 0x00, 0x01);
    molWriteBytes(out, 0x01, 0x02, 0x00, 0x00);
/*    
  // 0a80: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 1041f, 20833f, 936.9f, 104166f, 20833f, 0f); // (5%, 100%, 4%, 500%, 100mm, 0mm [rcr])
  // 0af8: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  // 0b00: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 936.9f, 20833f, 936.9f, 104166f, 0f, 20833f); // (4%, 100%, 4%, 500%, 0mm, 100mm [rcr])
  // 0b78: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  // 0b80: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 936.9f, 20833f, 936.9f, 104166f, -20833f, 0f); // (4%, 100%, 4%, 500%, -100mm, 0mm [rcr])
  // 0bf8: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  // 0c00: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 936.9f, 20833f, 936.9f, 104166f, 0f, -20833f); // (4%, 100%, 4%, 500%, 0mm, -100mm [rcr])
  // 0c78: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
    molMoveRel(out, 936.9f, 20763.3f, 1469.88f, 145833f, 2083f, 2083f); // (4%, 100%, 7%, 700%, 9.99856mm, 9.99856mm [rr])
  // 0cd0: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 1469.88f, 20833f, 936.9f, 104166f, 8334f, 0f); // (7%, 100%, 4%, 500%, 40.0038mm, 0mm [rcr])
  // 0d48: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
    molMoveRel(out, 936.9f, 35407.2f, 936.9f, 145833f, -8334f, 2084f); // (4%, 170%, 4%, 700%, -40.0038mm, 10.0034mm [rr])
  // 0da0: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 936.9f, 20833f, 936.9f, 104166f, 8334f, 0f); // (4%, 100%, 4%, 500%, 40.0038mm, 0mm [rcr])
  // 0e18: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
    molMoveRel(out, 936.9f, 35406.7f, 936.9f, 145833f, -8334f, 2083f); // (4%, 170%, 4%, 700%, -40.0038mm, 9.99856mm [rr])
  // 0e70: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
    molMoveRel(out, 936.9f, 20833f, 1041f, 104166f, 8334f, 0f); // (4%, 100%, 5%, 500%, 40.0038mm, 0mm [rcr])
*/

    // find the size of the entire artwork
    firstVector = true;
    for (JobPart p : job.getParts())
    {
      if (p instanceof VectorPart)
      {
        double resolution = p.getDPI();
        for (VectorCommand c : ((VectorPart) p).getCommandList())
        {
          switch (c.getType())
          {
            case LINETO:
            case MOVETO:
            {
              double dx, x = Util.px2mm(c.getX(), resolution);
              double dy, y = Util.px2mm(c.getY(), resolution);
              if (firstVector) {
                firstVector = false;
                bboxMinX = bboxMaxX = firstX = prevX = x;
                bboxMinY = bboxMaxY = firstY = prevY = y;
              } else {
                if (x<bboxMinX) bboxMinX = x;
                if (x>bboxMaxX) bboxMaxX = x;
                if (y<bboxMinY) bboxMinY = y;
                if (y>bboxMaxY) bboxMaxY = y;
              }
              break;
            }
          }
        }
      }
    }
    // generate the commands to draw the artwork
    firstVector = true;
    int bailCnt = 9999999; // 10, 100, 1000(F), 500(F), 300(F), 200, 250, 275, 290, 295(F), 293(F), 292(F), 291(OK)
    for (JobPart p : job.getParts())
    {
      if (bailCnt<=0) break;
      if (p instanceof RasterPart)
      {
        throw new IllegalJobException("The MPC6515 currently does not support engraving");
      }
      else if (p instanceof Raster3dPart)
      {
        throw new IllegalJobException("The MPC6515 currently does not support 3d engraving");
      }
      if (p instanceof VectorPart)
      {
        double resolution = p.getDPI();
        for (VectorCommand c : ((VectorPart) p).getCommandList())
        {
          if (bailCnt<=0) break;
          switch (c.getType())
          {
            case LINETO:
            case MOVETO:
            {
              // TODO: Motion Block must be *less* than 512 words (just start a new block)!
              bailCnt--;
              double dx, x = Util.px2mm(c.getX(), resolution);
              double dy, y = Util.px2mm(c.getY(), resolution);
              if (firstVector) {
                firstVector = false;
                firstX = prevX = x;
                firstY = prevY = y;
                if (firstX!=bboxMinX || firstY!=bboxMinY) {
                  molMoveRel(out, 936.9f, 20833f, 936.9f, 145833f, (firstX-bboxMinX)*208.33, (firstY-bboxMinY)*208.33);
                }
              } else {
                dx = x - prevX; prevX = x;
                dy = y - prevY; prevY = y;
                if (dx!=0.0 || dy!=0.0) {
                  if (c.getType()==VectorCommand.CmdType.LINETO) {
                    // Laser On
                    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
                    molWriteInt(out, 1);
                    /// move slowly
                    molMoveRel(out, 936.9f, 25.0*208.33, 936.9f, 104166f, dx*208.33, dy*208.33);
                    // Laser Off
                    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
                    molWriteInt(out, 0);
                  } else {
                    molMoveRel(out, 936.9f, 35406.7f, 936.9f, 145833f, dx*208.33, dy*208.33);
                  }
                }
              }
              break;
            }
            case SETPROPERTY:
            {
              //TODO: WRITE IN MOL FORMAT
              //PowerSpeedFocusFrequencyProperty prop = (PowerSpeedFocusFrequencyProperty) c.getProperty();
              //out.write(10);
              //out.write(prop.getPower());//power in 0-100
              //out.write(prop.getSpeed());//speed in 0-100
              //out.write((int) prop.getFocus());//focus in mm
              //out.write(prop.getFrequency());//frequency in Hz
              break;
            }
          }
        }
      }
    }
  // 0ee8: Laser On/Off
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  // 0ef0: Blower On/Off
    molWriteBytes(out, 0x06, 0x0b, 0x00, 0x01);
    molWriteBytes(out, 0x00, 0x02, 0x00, 0x00);
  // 0ef8: Blower On/Off
    molWriteBytes(out, 0x06, 0x0b, 0x00, 0x01);
    molWriteBytes(out, 0x00, 0x02, 0x00, 0x00);
  // 0f00: End Of Motion Block (started at 0a78, 290 words)
  // a70: Command block used for repetition. Interestingly, the blower commands are inside the block.
    int n = (int) out.getChannel().position();
    out.seek(0x00000a70);
    molCmd(out, 0x46, 0x09, 0x00, 0x80,  (n-0x00000a70-8)/4);
    out.seek(n);    
  // 0f00: End Subroutine
    molWriteBytes(out, 0x48, 0x00, 0x40, 0x01);
    molWriteInt(out, 3);    

    molFixBlock0600(out);
    
    molFixBlock0400(out);
    
    molFixBlock0200(out);
    
    molFixBlock0000(out);
    
    out.close();
    
    pl.progressChanged(this, 80);
    pl.taskChanged(this, "sending...");
    //TODO: contents of buffer.toByteArray() to laser-cutter
    pl.progressChanged(this, 100);
  }

  @Override
  public List<Double> getResolutions()
  {
    return Arrays.asList(SUPPORTED_RESOLUTIONS);
  }

  @Override
  public String getModelName()
  {
    return "MPC6515";
  }

  @Override
  public LaserCutter clone()
  {
    MPC6515Cutter o = new MPC6515Cutter();
    for (String k : this.getPropertyKeys())
    {
      o.setProperty(k, this.getProperty(k));
    }
    return o;
  }

  @Override
  public String[] getPropertyKeys()
  {
    return PROPERTIES;
  }

  @Override
  public void setProperty(String key, Object value)
  {
    if (BED_WIDTH.equals(key))
    {
      setBedWidth((Double) value);
    }
    else if (BED_HEIGHT.equals(key))
    {
      setBedHeight((Double) value);
    }
    else
    {
      System.err.println("ERROR: Unknown property '"+key+"' for MPC6151 driver");
    }
  }

  @Override
  public Object getProperty(String key)
  {
    if (BED_WIDTH.equals(key))
    {
      return getBedWidth();
    }
    else if (BED_HEIGHT.equals(key))
    {
      return getBedHeight();
    }
    else
    {
      System.err.println("ERROR: Unknown property '"+key+"' for MPC6151 driver");
    }
    return null;
  }

}
