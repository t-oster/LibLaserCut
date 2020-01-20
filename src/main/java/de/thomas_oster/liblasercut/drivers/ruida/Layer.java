/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2017 - 2020 Klaus Kämpf <kkaempf@suse.de>
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

package de.thomas_oster.liblasercut.drivers.ruida;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import de.thomas_oster.liblasercut.drivers.ruida.ByteStream;

/*
 * Vector
 *
 * absolute or relative move or line
 */

class Vector
{
  private boolean isMove; /* false = line, true = move */
  private boolean isAbsolute; /* false = relative */
  private double x;
  private double y;
  public Vector(double x, double y, boolean as_move, boolean as_absolute) {
    this.x = x;
    this.y = y;
    this.isMove = as_move;
    this.isAbsolute = as_absolute;
  }
  public double x() { return this.x; }
  public double y() { return this.y; }
  public boolean is_move() { return this.isMove; }
  public boolean is_absolute() { return this.isAbsolute; }
}


/**
 * Layer
 *
 * Represents a set of data with the same power, frequency, and speed
 *
 * - accumulates vectors (move, cut) in vectors Array
 */

public class Layer
{
  private static final int MAX_POWER = 80; // maximal power in %

  private int number;
  /* layer properties */
  private double top_left_x = 0.0;
  private double top_left_y = 0.0;
  private double bottom_right_x = 0.0;
  private double bottom_right_y = 0.0;
  private int min_power = 0;
  private int max_power = 0;
  private double speed = 0.0;
  private double frequency = 0.0;
  private double focus = 0.0;
  private int red = 0;
  private int green = 0;
  private int blue = 0;
  private double travel_distance = 0.0;
  private double xsim = 0.0;
  private double ysim = 0.0;
  private double max_x = 0.0;
  private double max_y = 0.0;
  private List<Vector> vectors = new ArrayList<Vector>();
  private ByteStream stream;

  /**
   * create new Layer
   * @number : -1 - 'frame layer' - overall size, no vectors
   *           0..255 - normal layer
   */
  public Layer(int number)
  {
    if (number < -1) {
      throw new IllegalArgumentException("Layer number < -1");
    }
    if (number > 255) {
      throw new IllegalArgumentException("Layer number > 255");
    }
    this.number = number;
  }
  /*
   * vectors present ?
   */
  public boolean hasVectors()
  {
    return this.vectors.size() > 0;
  }
  /*
   * set layer number
   */
  public void setNumber(int number)
  {
    this.number = number;
  }
  /*
   * Layer dimensions
   */
  public void setDimensions(double top_left_x, double top_left_y, double bottom_right_x, double bottom_right_y)
  {
    System.out.println("Layer.dimensions(" + this.number + ": " + top_left_x + ", " + top_left_y + ", " + bottom_right_x + ", " + bottom_right_y + ")");
    if (top_left_x < 0) {
      throw new IllegalArgumentException("Layer top_left_x < 0");
    }
    if (top_left_y < 0) {
      throw new IllegalArgumentException("Layer top_left_y < 0");
    }
    if (bottom_right_x < 0) {
      throw new IllegalArgumentException("Layer bottom_right_x < 0");
    }
    if (bottom_right_x < top_left_x) {
      throw new IllegalArgumentException("Layer bottom_right_x < top_left_x");
    }
    if (bottom_right_y < 0) {
      throw new IllegalArgumentException("Layer bottom_right_y < 0");
    }
    if (bottom_right_y < top_left_y) {
      throw new IllegalArgumentException("Layer bottom_right_y < top_left_y");
    }
    this.top_left_x = top_left_x * 1000.0;
    this.top_left_y = top_left_y * 1000.0;
    this.bottom_right_x = bottom_right_x * 1000.0;
    this.bottom_right_y = bottom_right_y * 1000.0;
  }

  /**
   * vector
   *
   * compute max_x, max_y, and travel_distance
   *
   */
  public void vectorTo(double x, double y, boolean as_move) throws RuntimeException
  {
    boolean as_absolute;
    Vector vector;

    /* convert to µm for Ruida */
    x = x * 1000.0;
    y = y * 1000.0;
//    System.out.println("\tLayer.vector("+vectors.size()+")To(" + x + ", " + y + ")" + " as " + ((as_move)?"move":"line"));
    double dx = x - xsim;
    double dy = y - ysim;
    System.out.println("  dx " + dx + ", dy " + dy);
    if ((dx == 0) && (dy == 0)) {
//      System.out.println("\tno move - skip");
      return;
    }
    double distance = Math.sqrt(dx*dx + dy*dy);
    if (vectors.size() % 10 == 0) {                  /* enforce absolute every 10 vectors */
      as_absolute = true;
    }
    else {
      as_absolute = Math.max(Math.abs(dx), Math.abs(dy)) > 8191;
    }
//    System.out.println("    distance " + distance + ", absolute? " + as_absolute);

    travel_distance += distance;

    // estimate the new real position
    xsim += dx;
    ysim += dy;
    max_x = Math.max(max_x, xsim);
    max_y = Math.max(max_y, ysim);
    if (as_absolute) {
      vector = new Vector(x, y, as_move, true);
    }
    else {
      vector = new Vector(dx, dy, as_move, false);
    }
    vectors.add(vector);
  }

  public double getTravelDistance()
  {
    return this.travel_distance / 1000.0;
  }

  /**
   * property setters
   */

  public void setSpeed(double speed)
  {
    this.speed = speed * 1000;
  }
  public void setFrequency(double frequency)
  {
//    System.out.println("Layer.setFrequency(" + frequency + ")");
    this.frequency = frequency;
  }
  public void setFocus(double focus)
  {
//    System.out.println("Layer.setFocus(" + focus + ")");
    this.focus = focus;
  }
  private int limitPower(String what, int power)
  {
    if (power < 0) {
      System.out.println(what + " < 0");
      power = 0;
    }
    else if (power > MAX_POWER) {
      System.out.println(String.format("%s > max power(%d) !", what, MAX_POWER));
      power = MAX_POWER;
    }
    return power;
  }
  /**
   * set min power in %
   */
  public void setMinPower(int power)
  {
//    System.out.println("Layer "+ this.number + ", Min Power " + power);
    this.min_power = limitPower("min power", power);
  }
  /**
   * set max power in %
   */
  public void setMaxPower(int power)
  {
//    System.out.println("Layer "+ this.number + ", Max Power " + power);
    this.max_power = limitPower("max power", power);
  }
  /**
   * set RGB for preview display
   */
  public void setRGB(int red, int green, int blue) throws RuntimeException
  {
    if (this.number == -1) {
      throw new RuntimeException("Layer.setRGB for frame layer");
    }
    this.red = red & 0xff;
    this.green = green & 0xff;
    this.blue = blue & 0xff;
  }

  /* ------------------------------------------------------------------------*/

  /*
   * ByteStream related
   *
   */

  /**
   * write layer as bounding box to out
   *
   */
  public void writeBoundingBoxTo(ByteStream stream) throws IOException
  {
    /* overall dimensions */
    /**
     * Overall dimensions
     * Top_Left_E7_07 0.0mm 0.0mm                      e7 03 00 00 00 00 00 00 00 00 00 00
     * Bottom_Right_E7_07 52.0mm 53.0mm                e7 07 00 00 03 16 20 00 00 03 1e 08
     * Top_Left_E7_50 0.0mm 0.0mm                      e7 50 00 00 00 00 00 00 00 00 00 00
     * Bottom_Right_E7_51 52.0mm 53.0mm                e7 51 00 00 03 16 20 00 00 03 1e 08
     */
    stream.hex("E703").absolute(top_left_x).absolute(top_left_y);
    stream.hex("E707").absolute(max_x).absolute(max_y);
    stream.hex("E750").absolute(top_left_x).absolute(top_left_y);
    stream.hex("E751").absolute(max_x).absolute(max_y);
    stream.hex("e7040001000100000000000000000000");
    stream.hex("e70500");
  }

  /**
   * write layer properties to stream
   *
   */
  public void writePropertiesTo(ByteStream stream)
  {
//    System.out.println("Layer.writePropertiesTo(" + this.number + ", min pow " + min_power + ", max pow " + max_power + ") ");
    this.stream = stream;
    layerSpeed(speed);
    laserPower(1, min_power, max_power);
    layerColor();
    layerCa41();
    dimensions(top_left_x, top_left_y, bottom_right_x, bottom_right_y);
  }

  /**
   * write vstream as layer to stream
   *
   */
  public void writeVectorsTo(ByteStream stream)
  {
    this.stream = stream;
//    System.out.println("Layer.writeVectorsTo(" + vectors.size() + " to layer " + this.number + ") ");
    this.stream.hex("ca0100");
    prio(this.number);
    blowOn();
    speedC9(speed);
    power(1, min_power, max_power);

    /* start vector mode */
    this.stream.hex("ca030f");
    this.stream.hex("ca1000");
    vectors.forEach((vector) -> {
      if (vector.is_absolute()) {
        /* absolute vector */
        double x = vector.x();
        double y = vector.y();
        if (vector.is_move()) {
          moveAbs(x, y);
        }
        else {
          cutAbs(x, y);
        }
      }
      else {
        /* relative vector */
        double dx = vector.x();
        double dy = vector.y();
        boolean as_move = vector.is_move();
        if (dx == 0) {
          if (as_move) {
            moveVert(dy);
          }
          else {
            cutVert(dy);
          }
        }
        else if (dy == 0) {
          if (as_move) {
            moveHoriz(dx);
          }
          else {
            cutHoriz(dx);
          }
        }
        else {
          if (as_move) {
            moveRel(dx, dy);
          }
          else {
            cutRel(dx, dy);
          }
        }
      }
    });
  }


  /**
   * Move absolute
   */
  private void moveAbs(double x, double y)
  {
    this.stream.hex("88").absolute(x).absolute(y);
  }

  /**
   * Move relative
   */
  private void moveRel(double x, double y)
  {
    this.stream.hex("89").relativeSigned(x).relativeSigned(y);
  }

  /**
   * Move relative horizontal
   */
  private void moveHoriz(double x)
  {
    this.stream.hex("8A").relativeSigned(x);
  }

  /**
   * Move relative vertical
   */
  private void moveVert(double y)
  {
    this.stream.hex("8B").relativeSigned(y);
  }

  /**
   * Cut relative horizontal
   */
  private void cutHoriz(double x)
  {
    this.stream.hex("AA").relativeSigned(x);
  }

  /**
   * Cut relative vertical
   */
  private void cutVert(double y)
  {
    this.stream.hex("AB").relativeSigned(y);
  }

  /**
   * Cut relative
   */
  private void cutRel(double x, double y)
  {
    this.stream.hex("A9").relativeSigned(x).relativeSigned(y);
  }

  /**
   * Cut absolute
   */
  private void cutAbs(double x, double y)
  {
    this.stream.hex("A8").absolute(x).absolute(y);
  }

  /**
   * power (per laser)
   */
  private void laserPower(int laser, int min_power, int max_power) throws RuntimeException
  {
    if (this.number == -1) {
      throw new RuntimeException("Layer.laserPower for frame layer");
    }
    switch (laser) {
    case 1:
      this.stream.hex("C631").integer(this.number).percent(min_power);
      this.stream.hex("C632").integer(this.number).percent(max_power);
      break;
    case 2:
      this.stream.hex("C641").integer(this.number).percent(min_power);
      this.stream.hex("C642").integer(this.number).percent(max_power);
      break;
    case 3:
      this.stream.hex("C635").integer(this.number).percent(min_power);
      this.stream.hex("C636").integer(this.number).percent(max_power);
      break;
    case 4:
      this.stream.hex("C637").integer(this.number).percent(min_power);
      this.stream.hex("C638").integer(this.number).percent(max_power);
      break;
    default:
      throw new RuntimeException("Illegal 'laser' value in Layer.laserPower");
    }
  }

  /**
   * power (for current layer)
   */
  private void power(int laser, int min_power, int max_power) throws RuntimeException
  {
    switch (laser) {
    case 1:
      this.stream.hex("C601").percent(min_power);
      this.stream.hex("C602").percent(max_power);
      break;
    case 2:
      this.stream.hex("C621").percent(min_power);
      this.stream.hex("C622").percent(max_power);
      break;
    case 3:
      this.stream.hex("C605").percent(min_power);
      this.stream.hex("C606").percent(max_power);
      break;
    case 4:
      this.stream.hex("C607").percent(min_power);
      this.stream.hex("C608").percent(max_power);
      break;
    default:
      throw new RuntimeException("Illegal 'laser' value in Layer.power");
    }
  }

  /**
   * speed
   */
  private void speedC9(double speed) throws RuntimeException
  {
    this.stream.hex("C902").absolute(speed);
  }

  /**
   * speed (per layer)
   */
  private void layerSpeed(double speed) throws RuntimeException
  {
//    System.out.println("layerSpeed(" + speed + ")");
    if (this.number == -1) {
      throw new RuntimeException("Layer.laserSpeed for frame layer");
    }
    this.stream.hex("C904").integer(this.number).absolute(speed);
  }

  /**
   * blowOn
   * without, the laser does not turn off at the end of the job
   */
  private void blowOn()
  {
    this.stream.hex("ca0113");
  }

  /**
   * flagsCa01
   */
  private void flagsCa01(int flags)
  {
    this.stream.hex("ca01").integer(flags);
  }

  /**
   * prio
   */
  private void prio(int prio)
  {
    this.stream.hex("ca02").integer(prio);
  }

  private void dimensions(double top_left_x, double top_left_y, double bottom_right_x, double bottom_right_y)
  {
    /* per-layer dimensions */
    /**
     * Layer dimensions
     * Layer_Top_Left_E7_52 Layer:0 0.0mm 0.0mm        e7 52 00 00 00 00 00 00 00 00 00 00 00
     * Layer_Bottom_Right_E7_53 Layer:0 100.0mm 75.0mm e7 53 00 00 00 06 0d 20 00 00 04 49 78
     * Layer_Top_Left_E7_61 Layer:0 0.0mm 0.0mm        e7 61 00 00 00 00 00 00 00 00 00 00 00
     * Layer_Bottom_Right_E7_62 Layer:0 100.0mm 75.0mm e7 62 00 00 00 06 0d 20 00 00 04 49 78
     */
    this.stream.hex("E752").integer(this.number).absolute(top_left_x).absolute(top_left_y);
    this.stream.hex("E753").integer(this.number).absolute(bottom_right_x).absolute(bottom_right_y);
    this.stream.hex("E761").integer(this.number).absolute(top_left_x).absolute(top_left_y);
    this.stream.hex("E762").integer(this.number).absolute(bottom_right_x).absolute(bottom_right_y);
  }

  /**
   * 0..100 -> 0..255
   */
  private long normalizeColor(int color)
  {
    long normalized = Math.round(color * 2.55);
    return normalized;
  }

  private void layerColor()
  {
    long color = (normalizeColor(this.blue) << 16) + (normalizeColor(this.green) << 8) + normalizeColor(this.red);
    this.stream.hex("ca06").integer(this.number).absolute(color);
  }

  private void layerCa41()
  {
    this.stream.hex("ca41").integer(this.number).integer(0);
  }
}
