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
package com.t_oster.liblasercut.platform;

/**
 *
 * @author oster
 */
public class Util {

    public static double dpi2dpmm(double dpi)
    {
      //TODO: check
      return dpi / 25.4;
    }

    public static double dpmm2dpi(double dpmm)
    {
      return dpmm * 25.4;
    }

    public static double inch2mm(double inch) {
        return inch * 25.4;
    }

    public static double mm2inch(double mm) {
        return mm / 25.4;
    }

    public static double px2mm(double px, double dpi) {
        return inch2mm(px / dpi);
    }

    public static double mm2px(double mm, double dpi) {
        return mm2inch(mm) * dpi;
    }

    /**
     * Returns true iff the given objects are not equal
     * This method is used to avoid null checks
     * @param a
     * @param b
     * @return
     */
    public static boolean differ(Object a, Object b) {
        if (a == null ^ b == null) {
            return true;
        } else {
            if (a == null && b == null) {
                return false;
            } else {
                return !a.equals(b);
            }
        }
    }

    public static Byte reverseBitwise(Byte get) {
        return (byte) (Integer.reverse(get) >>> (Integer.SIZE - Byte.SIZE));
    }
}
