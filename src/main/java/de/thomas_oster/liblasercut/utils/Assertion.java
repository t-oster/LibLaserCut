/*
  This file is part of LibLaserCut.
  Copyright (C) 2021 Max Gaukler <development@maxgaukler.de>

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
package de.thomas_oster.liblasercut.utils;

/**
 * Helper class to add runtime checks for checks that should never fail except if something goes horribly wrong
 */
public class Assertion
{

  // somehow, "assert" has no effect, so we use this:
  // TODO do it properly (TM)
  public static void assertThat(boolean mustBeTrue)
  {
    if (!mustBeTrue)
    {
      // somehow, AssertionError is not caught!
      RuntimeException ex = new RuntimeException("assertion failed");
      try
      {
        ex = new RuntimeException("assertion failed: " + ex.getStackTrace()[1].toString());
      }
      catch (Exception ee)
      {
        // failed to set message...
      }
      throw ex;
    }
  }
}
