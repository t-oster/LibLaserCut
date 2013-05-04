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

/*
 * TODO:
 * - check if x and/or y coordinates must be flipped
 * - after the previous point, make sure that the bounding boxes correspond to the new coordinates
 * - the origin is wrong! How does it relate to setting the machines origin?
 * - VERIFY: allow speed change at any time
 * - VERIFY: allow power setting change at any time
 * - VERIFY: additional parameter for "corner power"
 * - remove unused functions
 * - rename remaining functions if generic
 * - option for MOL filename
 * - connect to the USB uploader (option for executable path)
 * - read settings from machine and update configuration
 * - machine photo
 * - line offset for cutting!
 * - what is the use of the USB camera option?
 * - part distribution
 * - optional machine margin
 * - optional preferred material position
 * - test for Z control, z endstop?
 * - test remaining parameters
 * - convert position information to integer format throughout to keep 100% precision in relative movements
 */

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
import static com.t_oster.liblasercut.VectorCommand.CmdType.LINETO;
import static com.t_oster.liblasercut.VectorCommand.CmdType.MOVETO;
import static com.t_oster.liblasercut.VectorCommand.CmdType.SETPROPERTY;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Util;
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
  private static final String CORNER_POWER_FACTOR = "corner power factor";
  private static String[] PROPERTIES = new String[]{
    BED_WIDTH,
    BED_HEIGHT,
    CORNER_POWER_FACTOR,
  };
 
  private double bedWidth = 900;
  private double bedHeight = 600;
  private double cornerPowerFactor = 0.8;

  private double currentPower = 40;
  private double currentSpeed = 80;
  
  private double firstX = 0.0, firstY = 0.0, prevX = 0.0, prevY = 0.0;
  private double bboxMinX = 0.0, bboxMinY = 0.0, bboxMaxX = 0.0, bboxMaxY = 0.0, bboxWidth, bboxHeight;
  boolean firstVector;
  
  private int motionBlockStart = -1;
  private int motionBlockSize;
  private int nMotionBlocks;
  private int nMoveRelative;
  
  
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

  private void molCmdBeginMotionBlock(RandomAccessFile out) throws IOException
  {
    molWriteBytes(out, 0x46, 0x09, 0x00, 0x80);
    molWriteInt(out, -1);  // number of words in motion block
    motionBlockStart = (int)out.getChannel().position();
    motionBlockSize = 0;
    nMotionBlocks++;
  }

  private void molCmdEndMotionBlock(RandomAccessFile out) throws IOException
  {
    int n = (int) out.getChannel().position();
    out.seek(motionBlockStart-8);
    molCmd(out, 0x46, 0x09, 0x00, 0x80,  (n-motionBlockStart)/4);
    out.seek(n);    
  }
  
  private boolean molInMotionBlock()
  {
    return (motionBlockStart!=-1);
  }

  private void molCmdTestMotionBlock(RandomAccessFile out, int nWords) throws IOException
  {
    if (motionBlockSize + nWords >= 512) {
      molCmdEndMotionBlock(out);
      molCmdBeginMotionBlock(out);
    }
    motionBlockSize += nWords;
  }
  
  private void molCmdLaserOn(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 1);
  }

  private void molCmdLaserOff(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x06, 0x06, 0x00, 0x01);
    molWriteInt(out, 0);
  }
  
  private void molCmdBlowerOn(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x06, 0x0b, 0x00, 0x01);
    molWriteBytes(out, 0x01, 0x02, 0x00, 0x00);
  }

  private void molCmdBlowerOff(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x06, 0x0b, 0x00, 0x01);
    molWriteBytes(out, 0x00, 0x02, 0x00, 0x00);
  }
  
  private void molCmdAccelerate(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
    molWriteInt(out, 1);
  }

  private void molCmdDecelerate(RandomAccessFile out) throws IOException
  {
    molCmdTestMotionBlock(out, 2);
    molWriteBytes(out, 0x01, 0x46, 0x00, 0x01);
    molWriteInt(out, 2);
  }

  private void molCmdBeginSubroutine(RandomAccessFile out, int n) throws IOException
  {
    molWriteBytes(out, 0x48, 0x00, 0x30, 0x01);
    molWriteInt(out, n);
  }

  private void molCmdEndSubroutine(RandomAccessFile out, int n) throws IOException
  {
    molWriteBytes(out, 0x48, 0x00, 0x40, 0x01);
    molWriteInt(out, n);
  }

  private void molCmdSetSpeeds(RandomAccessFile out, double aMin, double aMax, double aAccel) throws IOException
  {
    molCmdTestMotionBlock(out, 4);
    molWriteBytes(out, 0x01, 0x03, 0x00, 0x03);
    molWriteFloat(out, (float)aMin);
    molWriteFloat(out, (float)aMax);
    molWriteFloat(out, (float)aAccel);
  }
  
  private void molCmdMoveRelative(RandomAccessFile out, int dx, int dy) throws IOException
  {
    molCmdTestMotionBlock(out, 4);
    molWriteBytes(out, 0x00, 0x60, 0x02, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00); // x and y axis
    molWriteInt(out, dx);
    molWriteInt(out, dy);
    nMoveRelative++;
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
    
    if (lenConst>=1.0) {
      lenRampUp = lenRampUp / len;
      lenConst = lenConst / len;
      lenRampDown = lenRampDown /len;
      molCmdAccelerate(out);
      molCmdSetSpeeds(out, startSpeed, maxSpeed, accel);
      molCmdMoveRelative(out, (int)(dx*lenRampUp), (int)(dy*lenRampUp));
      molCmdSetSpeeds(out, maxSpeed, maxSpeed, accel);
      molCmdMoveRelative(out, (int)(dx - dx*lenRampUp - dx*lenRampDown), (int)(dy - dy*lenRampUp - dy*lenRampDown));
      molCmdDecelerate(out);
      molCmdSetSpeeds(out, endSpeed, maxSpeed, accel);
      molCmdMoveRelative(out, (int)(dx*lenRampDown), (int)(dy*lenRampDown));
    } else {
      double scale = (lenRampUp+lenConst/2.0) / lenRampUp;
      lenRampUp   =   (lenRampUp+lenConst/2.0) / len;
      lenRampDown = (lenRampDown+lenConst/2.0) / len;
      maxSpeed = (maxSpeed-startSpeed)*scale + startSpeed;

      if (Math.abs(dx*lenRampUp)>=1.0||Math.abs(dy*lenRampUp)>1.0) {
        molCmdAccelerate(out);
        molCmdSetSpeeds(out, startSpeed, maxSpeed, accel);
        molCmdMoveRelative(out, (int)(dx*lenRampUp), (int)(dy*lenRampUp));
      }
      if (Math.abs(dx*lenRampDown)>=1.0||Math.abs(dy*lenRampDown)>=1.0) {
        molCmdDecelerate(out);
        molCmdSetSpeeds(out, endSpeed, maxSpeed, accel);
        molCmdMoveRelative(out, (int)(dx*lenRampDown), (int)(dy*lenRampDown));
      }
    }    
  }

  private void molSetPowerAndSpeed(RandomAccessFile out, double cornerPower, double maxPower, double startSpeed, double maxSpeed) throws IOException
  {
  // Laser Power
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x05);
    molWriteInt(out, (int)(cornerPower*100));  // (40.000000%) corner power
    molWriteInt(out, (int)(maxPower*100));  // (40.000000%) maximum power
    molWriteFloat(out, (float)(startSpeed*208.33)); // (5%) cutting start speed
    molWriteFloat(out, (float)(maxSpeed*208.33)); // (100%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  // Laser Power Long
    molWriteBytes(out, 0x46, 0x0e, 0x00, 0x07);
    molWriteInt(out, (int)(cornerPower*100));  // (40.000000%) corner power
    molWriteInt(out, (int)(maxPower*100));  // (40.000000%) maximum power
    molWriteInt(out, (int)(cornerPower*100));  // (40.000000%) corner power (second head?)
    molWriteInt(out, (int)(maxPower*100));  // (40.000000%) maximum power (second head?)
    molWriteFloat(out, (float)(startSpeed*208.33)); // (5%) cutting start speed
    molWriteFloat(out, (float)(maxSpeed*208.33)); // (100%) maximum cutting speed
    molWriteInt(out, 0);  // unknown
  }
  
  private void molChangePowerAndSpeed(RandomAccessFile out, double cornerPower, double maxPower, double startSpeed, double maxSpeed) throws IOException
  {
    boolean inBlock = molInMotionBlock();
    if (inBlock) {
      molCmdLaserOff(out);
      molCmdBlowerOff(out);
      molCmdEndMotionBlock(out);
    }
    molSetPowerAndSpeed(out, cornerPower, maxPower, startSpeed, maxSpeed);
    if (inBlock) {
      molCmdBeginMotionBlock(out);
    }
  }

  // 
  // .MOL file header
  // 
  private void molWriteBlock0000(RandomAccessFile out) throws IOException
  {
    nMotionBlocks = 0;
    nMoveRelative = 0;
    
    out.seek(0x00000000);
    molWriteInt(out, 0x00001000);  // 0000: file size in bytes
    molWriteInt(out, 34);  // 0004: ?
    molWriteBytes(out, 0x02, 0x02, 0x01, 0x04);  // 0008: ?
    molWriteInt(out, 1);  // 000c: ?
    //molWriteInt(out, 41);  // 0010: somehow related to the file size or the number of commands
    molWriteInt(out, 2000);  // 0010: somehow related to the file size or the number of commands
    molWriteInt(out, 0x00000101);  // 0014: ? also 257 (bitfield?) -> user origin is top right!
    //molWriteInt(out, 0x00000001);  // 0014: ? also 257 (bitfield?) -> fixed origin
    // 201, 401, 801, 100, 102: top right relative: no change
    // 001, (somewhat) center relative
    
    molWriteInt(out, (int)(100*-208.33));  // 0018: ? (no change)
    molWriteInt(out, (int)(100*-208.33));  // 001c: ?
    molWriteInt(out, (int)(bboxWidth*-208.33));   // 0020: artwork width in steps
    molWriteInt(out, (int)(bboxHeight*-208.33));  // 0024: artwork height in steps
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
    molWriteInt(out, 1);  // 0070: block number of 200 block (home position and start vector)
    molWriteInt(out, 2);  // 0074: block number of 400 block (test outline)
    molWriteInt(out, 3);  // 0078: block number of 600 block (cut outline)
    molWriteInt(out, 5);  // 007c: block number of A00 block (cut artwork)
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
    out.seek(4);
    molWriteInt(out, nMotionBlocks + 30); // 0004: ???? seems to loosely correspond
    out.seek(0x0010);
    molWriteInt(out, nMoveRelative); // 0010: ???? seems to loosely correspond
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
    // Verify setting for origin andsize, coordinate flipping, etc.
    //molWriteMm(out, (float)(0.5*(bedWidth+bboxWidth))); // FIXME: (500.005mm) (table width + artwork width) / 2
    //molWriteMm(out, (float)(0.5*(bedHeight+bboxHeight))); // FIXME: (350.002mm) (table height + artwork height) / 2
    molWriteMm(out, (float)100); // FIXME: (500.005mm) (table width + artwork width) / 2
    // if user origin or fixed origin is set, this seems to do nothing (is there some offset origin?)
    molWriteMm(out, (float)100); // FIXME: (350.002mm) (table height + artwork height) / 2
    nMoveRelative++;
  // 0274: Set Speed
    molWriteBytes(out, 0x41, 0x03, 0x00, 0x03);
    molWriteFloat(out, 1041f); // (5%) start
    molWriteFloat(out, 41666f); // (200%) max
    molWriteFloat(out, 145833f); // (700%) accel
  // 0284: Start Corner
    // TODO: Verify setting for origin and size, coordinate flipping, etc.
    molWriteBytes(out, 0x40, 0x60, 0x02, 0x03);
    molWriteBytes(out, 0x04, 0x03, 0x00, 0x00);  // Select motion axes
    molWriteMm(out, (float)(-bboxWidth)); // FIXME: (-100mm) left
    molWriteMm(out, (float)(-bboxHeight)); // FIXME: (-100mm) bottom
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
  }

  // 
  // .MOL file data block at 0x00000400
  // 
  private void molWriteBlock0400(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000400);
    molCmdBeginSubroutine(out, 1);
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
    molCmdBeginMotionBlock(out);
  // motion  
    // TODO: verify that the correct origin is used, verify if correct after flipping coordinates
    molMoveRel(out, 1041f, 41666f, 1473.12f, 145833f, -bboxWidth*208.33, 0f); // FIXME: (5%, 200%, 7%, 700%, -100mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1473.12f, 145833f, 0f, -bboxHeight*208.33); // FIXME: (7%, 200%, 7%, 700%, 0mm, -100mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1473.12f, 145833f, bboxWidth*208.33, 0f); // FIXME: (7%, 200%, 7%, 700%, 100mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 41666f, 1041f, 145833f, 0f, bboxHeight*208.33); // FIXME: (7%, 200%, 5%, 700%, 0mm, 100mm [rcr])
  // 05f0: End Of Motion Block (started at 0430, 112 words)
    molCmdEndMotionBlock(out);
    molCmdEndSubroutine(out, 1);
  }
  
  // 
  // .MOL file data block at 0x00000400
  // 
  private void molFixBlock0400(RandomAccessFile out) throws IOException
  {
  }

  // 
  // .MOL file data block at 0x00000600
  // 
  private void molWriteBlock0600(RandomAccessFile out) throws IOException
  {
    out.seek(0x00000600);
    molCmdBeginSubroutine(out, 2);
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
    molCmdBeginMotionBlock(out);
    molMoveRel(out, 1041f, 5208f, 1014.73f, 104166f, 417f, 417f); // (5%, 25%, 5%, 500%, 2.00163mm, 2.00163mm [rcr])
    molCmdLaserOn(out); // 06d8
    // TODO: verify that the correct origin is used, verify if correct after flipping coordinates
    molMoveRel(out, 1014.73f, 5208f, 1473.12f, 104166f, (-bboxWidth-4.0)*208.33, 0f); // FIXME: (5%, 25%, 7%, 500%, -104.003mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1473.12f, 104166f, 0f, (-bboxHeight-4.0)*208.33); // FIXME: (7%, 25%, 7%, 500%, 0mm, -104.003mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1473.12f, 104166f, (bboxWidth+4.0)*208.33, 0f); // FIXME: (7%, 25%, 7%, 500%, 104.003mm, 0mm [rcr])
    molMoveRel(out, 1473.12f, 5208f, 1041f, 104166f, 0f, (bboxHeight+4.0)*208.33); // FIXME: (7%, 25%, 5%, 500%, 0mm, 104.003mm [rcr])
    molCmdLaserOff(out); // 08a0
  // 08a8: End Of Motion Block (started at 0668, 144 words)
    molCmdEndMotionBlock(out);
    molCmdEndSubroutine(out, 2);
  }
    
  // 
  // .MOL file data block at 0x00000600
  // 
  private void molFixBlock0600(RandomAccessFile out) throws IOException
  {
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception
  {
    // This is a first try in Java. All data is saved into ~/RUN.MOL until we figure out the USB communication
    currentPower = 40.0;
    currentSpeed = 80.0;
    motionBlockStart = -1;
    
    pl.taskChanged(this, "checking...");
    checkJob(job);
    
    // find the size of the entire artwork
    firstVector = true;
    for (JobPart p : job.getParts())
    {
      // FIXME: use job.getMinX(), job.getMaxX(), etc.
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
              double dy, y = this.bedHeight - Util.px2mm(c.getY(), resolution);
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
    bboxWidth  = bboxMaxX - bboxMinX;
    bboxHeight = bboxMaxY - bboxMinY;
    
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
    molCmdBeginSubroutine(out, 3);
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
  // first vector will set the speed and start the motion block!
    
    // generate the commands to draw the artwork
    firstVector = true;
    for (JobPart p : job.getParts())
    {
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
          switch (c.getType())
          {
            case LINETO:
            case MOVETO:
            {
              double dx, x = Util.px2mm(c.getX(), resolution);
              double dy, y = this.bedHeight - Util.px2mm(c.getY(), resolution);
              // FIXME: job.getStartX() job.getStartY(), what do they do? Will that help us here?
              if (firstVector) {
                firstVector = false;
                // 0a38: Laser Power
                Debug.println("Setting power and speed");
                molSetPowerAndSpeed(out, currentPower*cornerPowerFactor, currentPower, 5.0, currentSpeed);
                // 0a70: Begin Motion Block
                molCmdBeginMotionBlock(out);
                molCmdBlowerOn(out);
                // TODO: what exactly is the origin at this point?
                firstX = prevX = x;
                firstY = prevY = y;
                if (firstX!=bboxMinX || firstY!=bboxMinY) {
                  // TODO: if this is a LINETO, move to the origin, then cut to the coordinate
                  molMoveRel(out, 936.9f, 20833f, 936.9f, 145833f, (firstX-bboxMinX)*208.33, (firstY-bboxMinY)*208.33);
                }
              } else {
                dx = x - prevX; prevX = x;
                dy = y - prevY; prevY = y;
                if (dx!=0.0 || dy!=0.0) {
                  if (c.getType()==VectorCommand.CmdType.LINETO) {
                    molCmdLaserOn(out);
                    molMoveRel(out, 936.9f, currentSpeed*208.33, 936.9f, 104166f, dx*208.33, dy*208.33);
                    molCmdLaserOff(out);
                  } else {
                    molMoveRel(out, 936.9f, 35406.7f, 936.9f, 145833f, dx*208.33, dy*208.33);
                  }
                }
              }
              break;
            }
            case SETPROPERTY:
            {
              PowerSpeedFocusFrequencyProperty prop = (PowerSpeedFocusFrequencyProperty) c.getProperty();
              currentPower = prop.getPower(); //power in 0-100
              currentSpeed = prop.getSpeed(); //speed in 0-100
              Debug.println("Set Power to " + Double.toString(currentPower) + "  Speed to " + Double.toString(currentSpeed));
              //out.write(prop.getPower());//power in 0-100
              //out.write(prop.getSpeed());//speed in 0-100
              //out.write((int) prop.getFocus());//focus in mm
              //out.write(prop.getFrequency());//frequency in Hz
              if (!firstVector) {
                Debug.println("Changing power and speed");
                molChangePowerAndSpeed(out, currentPower*cornerPowerFactor, currentPower, 5.0, currentSpeed);
              }
              break;
            }
          }
        }
      }
    }
    if (molInMotionBlock()) {
      // don't write this blcok if no vectors were sent at all...
      molCmdLaserOff(out);
      molCmdBlowerOff(out);
      molCmdEndMotionBlock(out);
    }
    molCmdEndSubroutine(out, 3);

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
    else if (CORNER_POWER_FACTOR.equals(key))
    {
      cornerPowerFactor = (Double)value;
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
    else if (CORNER_POWER_FACTOR.equals(key))
    {
      return cornerPowerFactor;
    }
    else
    {
      System.err.println("ERROR: Unknown property '"+key+"' for MPC6151 driver");
    }
    return null;
  }

}
