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
 * Copyright 2016 Google Inc.
 */
package com.t_oster.liblasercut;

import java.util.AbstractList;
import java.util.Collections;

/**
 * A specialized class to support the usage of List<Byte> within this library.
 * It is efficient for adds at the end of the list and removes from the 
 * start or end of the list.
 * Adds or removes in the middle of the list are not efficient, but those
 * operations are not used in this library.
 */
public class ByteArrayList extends AbstractList<Byte> {

  /** The underlying byte data.  */
  private byte[] data = null;

  /**
   * The index into the underlying array for the first element of the list.
   * This may shift as items are removed or added from the front.
   */
  private int start = 0;

  /** Current size of data in the list */
  private int size = 0;

  /**
   * Most uses of ByteArrayList know their size up front, but some may add
   * a few elements onto the beginning or end.  A small additive growth is 
   * sufficient to support them.  It is preferred for callers to hold onto
   * and reuse the ByteArrayList to avoid reallocation.
   */
  private static final int GROW_SIZE = 32;

  /**
   * Create a new list for the target expected size.
   */
  public ByteArrayList(int expectedSize) {
    data = new byte[expectedSize];
  }

  @Override
  public Byte get(int index) {
    return data[start + index];
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void add(int index, Byte v) {
    if (start + size >= data.length) {
      // Grow at the end
      byte[] newdata = new byte[data.length + GROW_SIZE];
      System.arraycopy(data, 0, newdata, 0, data.length);
      data = newdata;
    }
    if (index == size) {
      data[start + index] = v;
      size++;
    } else if (index == 0) {
      if (start == 0) {
	// Grow at the beginning
	byte[] newdata = new byte[data.length + GROW_SIZE];
	System.arraycopy(data, 0, newdata, GROW_SIZE, data.length);
	data = newdata;
	start = GROW_SIZE;
      }
      start--;
      data[start] = v;
      size++;
    } else {
      // Adding in the middle is inefficient, but is not actually used
      // in this library.  This implementation is here for completeness.
      System.arraycopy(data, start + index,
		       data, start + index + 1, size - index);
      data[start + index] = v;
      size++;
    }
  }

  @Override
  public Byte set(int index, Byte v) {
    byte p = data[start + index];
    data[start + index] = v;
    return p;
  }

  @Override
  public Byte remove(int index) {
    byte v = data[start + index];
    size -= 1;
    if (index == 0) {
      start += 1;
    } else if (index < size) {
      // Removing from the middle is inefficient, but is not actually used
      // in this library.  This implementation is here for completeness.
      System.arraycopy(data, start + index + 1,
		       data, start + index, size - index);
    }
    return v;
  }
  /**
   * If the list is interpreted as a bitstring, shift it x bits to the left,
   * keeping the lenght constant.
   * The bit order is as if you would write out the bytes from get(0) to get(N):
   * byte:    get(0)     get(1)     get(2)     ...
   * byte:    0b11000000 0b01111000 0b10000000 ...
   * This is consistent with the pixel order in RasterizablePart.getByte(), and specifically with in BlackWhiteRaster.getByte().
   * pixels:    BBWWWWWW   WBBBBWWW ...
   * leftShiftBits(1) shifts the pixels 1 to the left, deleting the leftmost pixel:
   *          0b10000000 0b11110001 ...
   * @param shift number of bits to shift to the left (positive). Negative = shift to the right.
   */
  public void leftShiftBits(int shift) {
    if (shift >= 0) {
      while (shift >= 8) {
        // to shift 8 bits to the left, remove the first byte, and append a zero byte
        remove(0);
        add((byte) 0);
        shift -= 8;
      }
      if (shift == 0) {
        // nothing left to do
        return;
      }
      // now shift the remaining 1 ... 7 bits to the left
      assert 0 < shift && shift < 8;
      ByteArrayList l = new ByteArrayList(data.length);
      for (int i=0; i < size(); i++)
      {
        byte nextByte;
        if (i +1 < size()) {
          nextByte = get(i+1);
        } else {
          nextByte = 0;
        }
        l.add((byte) ((((get(i) & 0xFF) << shift) | ((nextByte & 0xFF) >> (8-shift)))&0xFF));
      }
      this.data = l.data;
      this.size = l.size;
      this.start = l.start;
    } else {
      // this is stupid and inefficient, but it works and is currently not used.
      reverseBits();
      leftShiftBits(-shift);
      reverseBits();
    }
  }

  /**
   * reverse all bits, so that the leftmost bit of the first byte becomes the rightmost bit of the last byte
   * In pseudocode, this is (List<byte>) (((List<bit>) bytelist).reverse).
   * The bit order is as defined in leftShiftBits.
   */
  public void reverseBits() {
    // first, flip the bit order in-place
      for (int i = 0; i < size(); i++)
      {
        byte b = get(i);
        int bFlipped = 0;
        for (int j = 0; j < 8; j++)
        {
          if ((b & (1 << j)) != 0)
          {
            bFlipped |= 1 << (7 - j);
          }
        }
        set(i, (byte) bFlipped);
      }
      // then, flip the list order
      Collections.reverse(this);
  }

  @Override
  public void clear() {
    start = 0;
    size = 0;
  }

  /**
   * Clear the list for a new expected size.  This will reallocate the array
   * if needed, and also set internal state so that any extra space for growth
   * beyond the expected size is split evenly between the beginning and end
   * of the underlying array.
   */
  public void clear(int newExpectedSize) {
    if (data.length < newExpectedSize) {
      data = new byte[newExpectedSize];
    }
    start = (data.length - newExpectedSize) / 2;
    size = 0;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder("[");
    for (int i = start; i < start + size; i++) {
      b.append(data[i]+", ");
    }
    b.append("(" + start +" free at head, " +
	     (data.length - start - size) + " free at tail)]");
    return b.toString();
  }
}
