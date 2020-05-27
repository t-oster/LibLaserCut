/*
  This file is part of LibLaserCut.
  Copyright (C) 2020 Max Gaukler <development@maxgaukler.de>

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

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * PrintStream that uses LF ('\n') as line separator on all platforms
 * and has sensible defaults for writing G-Code to machines
 * (US_ASCII character set, automatic flush).
 */
public class LinefeedPrintStream extends PrintStream
{
  private final boolean autoFlush;
  public LinefeedPrintStream(OutputStream out)
  {
    this(out, true, StandardCharsets.US_ASCII);
  }
  public LinefeedPrintStream(OutputStream out, boolean autoFlush, Charset charset)
  {
    super(out, autoFlush, charset);
    this.autoFlush = autoFlush;
  }
  
  // Unfortunately, we can't override the private PrintStream.newLine() method,
  // so we have to override anything that would call.

  @Override
  public void println()
  {
    print("\n");
    if (autoFlush)
    {
      flush();
    }
  }

  @Override
  public void println(Object x)
  {
    print(x);
    println();
  }

  @Override
  public void println(String x)
  {
    print(x);
    println();  }

  @Override
  public void println(char[] x)
  {
    print(x);
    println();
  }

  @Override
  public void println(double x)
  {
    print(x);
    println();
  }

  @Override
  public void println(float x)
  {
    print(x);
    println();
  }

  @Override
  public void println(long x)
  {
    print(x);
    println();
  }

  @Override
  public void println(int x)
  {
    print(x);
    println();
  }

  @Override
  public void println(char x)
  {
    print(x);
    println();
  }

  @Override
  public void println(boolean x)
  {
    print(x);
    println();
  }
}
