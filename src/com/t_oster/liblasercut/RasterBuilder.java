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

package com.t_oster.liblasercut;

import com.t_oster.liblasercut.VectorCommand.CmdType;
import java.util.Iterator;

/**
 * This class works as an iterable iterator based on certain states It's
 * mapped around the idea of a state machine and allocates nearly no memory in
 * the creation of rasters. It reuses the vector command interface. You can
 * iterate through it as if it was a list of VectorCommand.
 *
 * A reasonably sized raster can require allocating many gigs of memory if the
 * operations were preprocessed.
 *
 * You are asked to implement the PropertiesUpdate interface so that you can
 * decide what pixels of changing colors actually mean. This is then handed back
 * to you as a command.
 *
 */
public class RasterBuilder implements Iterable<VectorCommand>, Iterator<VectorCommand>
{

  public static final int X_AXIS = 0;
  public static final int TOP = 0;
  public static final int LEFT = 0;
  public static final int BIDIRECTIONAL = 0;
  public static final int SKIPPING = 0;
  public static final int Y_AXIS = 1;
  public static final int BOTTOM = 2;
  public static final int RIGHT = 4;
  public static final int UNIDIRECTIONAL = 8;
  public static final int NO_SKIP = 16;

  public static final int SERPENTINE_TRANVERSE_X_FROM_TOP_LEFT_SKIPPING_BLANK_LINES = X_AXIS | TOP | LEFT | BIDIRECTIONAL | SKIPPING;

  static final int COMMAND_UNCALCULATED = 0;
  static final int COMMAND_PROPERTY = 1;
  static final int COMMAND_CUT_TO = 2;
  static final int COMMAND_MOVE_TO = 4;
  static final int COMMAND_FINISHED = 8;

  private static final int STATE_NOT_INITIALIZED = 0;
  private static final int STATE_MOVED_TO_START = 1;
  private static final int STATE_PASS_ENDED = 2;

  private static final int STATE_MOVING_RIGHT = 10;
  private static final int STATE_MOVING_LEFT = 11;
  private static final int STATE_MOVING_TOP = 12;
  private static final int STATE_MOVING_BOTTOM = 13;

  private static final int STATE_LINESTEP_RIGHT = 20;
  private static final int STATE_LINESTEP_LEFT = 21;
  private static final int STATE_LINESTEP_TOP = 22;
  private static final int STATE_LINESTEP_BOTTOM = 23;

  VectorCommand vector_command;
  AbstractLaserProperty property;
  PropertiesUpdate provider;

  int state = STATE_NOT_INITIALIZED;
  int command_status = COMMAND_UNCALCULATED;

  RasterElement image;
  int transversal = SERPENTINE_TRANVERSE_X_FROM_TOP_LEFT_SKIPPING_BLANK_LINES;
  int skip_pixel_value;

  private int y_position, x_position, dy, dx, begin, end, higher_bound, lower_bound, pixel;
  private int overscan;

  ProgressListener progress;
  double offsetX, offsetY;

  public RasterBuilder(RasterElement image, PropertiesUpdate provider, int transversal, int skipvalue, int overscan)
  {
    this.image = image;
    this.provider = provider;
    this.transversal = transversal;
    this.skip_pixel_value = skipvalue;
    this.overscan = overscan;
  }

  public void setProgressListener(ProgressListener progress)
  {
    this.progress = progress;
  }

  public void setOffsetPosition(double offsetX, double offsetY)
  {
    this.offsetX = offsetX;
    this.offsetY = offsetY;
  }

  @Override
  public Iterator<VectorCommand> iterator()
  {
    return this;
  }

  public void reset()
  {
    command_status = STATE_NOT_INITIALIZED;
  }

  @Override
  public boolean hasNext()
  {
    // call calculate until it produces a command status.
    while (command_status == COMMAND_UNCALCULATED)
    {
      calculate();
    }
    return command_status != COMMAND_FINISHED;
  }

  @Override
  public VectorCommand next()
  {
    if (command_status == COMMAND_FINISHED)
    {
      return null;
    }

    if ((command_status & COMMAND_PROPERTY) != 0)
    {
      command_status ^= COMMAND_PROPERTY;
      vector_command.type = CmdType.SETPROPERTY;
    }
    else if ((command_status & COMMAND_MOVE_TO) != 0)
    {
      command_status ^= COMMAND_MOVE_TO;
      vector_command.type = CmdType.MOVETO;
      vector_command.setX(x_position + offsetX);
      vector_command.setY(y_position + offsetY);
    }
    else if ((command_status & COMMAND_CUT_TO) != 0)
    {
      command_status ^= COMMAND_CUT_TO;
      vector_command.type = CmdType.LINETO;
      vector_command.setX(x_position + offsetX);
      vector_command.setY(y_position + offsetY);
    }
    return vector_command;
  }

  private void calculate()
  {
    switch (state)
    {
      case STATE_NOT_INITIALIZED:
        moveToStart();
        break;
      case STATE_MOVED_TO_START:
        initializeFirstLine();
        break;

      case STATE_MOVING_LEFT:
        moveLeft();
        break;
      case STATE_MOVING_TOP:
        moveTop();
        break;
      case STATE_MOVING_RIGHT:
        moveRight();
        break;
      case STATE_MOVING_BOTTOM:
        moveBottom();
        break;

      case STATE_LINESTEP_LEFT:
        linestepAtLeftEdge();
        break;
      case STATE_LINESTEP_TOP:
        linestepAtTopEdge();
        break;
      case STATE_LINESTEP_RIGHT:
        linestepAtRightEdge();
        break;
      case STATE_LINESTEP_BOTTOM:
        linestepAtBottomEdge();
        break;

      case STATE_PASS_ENDED:
        passEnded();
        break;
    }
  }

  private void passEnded()
  {
    //we only perform 1 pass ever.
    command_status = COMMAND_FINISHED;
  }

  private void moveToStart()
  {
    property = new AbstractLaserProperty();
    vector_command = new VectorCommand();

    vector_command.operands = new double[2];
    vector_command.property = property;

    if ((transversal & RIGHT) != 0)
    {
      x_position = image.getWidth() - 1;
      dx = -1;
    }
    else
    {
      x_position = 0;
      dx = 1;
    }

    if ((transversal & BOTTOM) != 0)
    {
      y_position = image.getHeight() - 1;
      dy = -1;
    }
    else
    {
      y_position = 0;
      dy = 1;
    }
    command_status = COMMAND_MOVE_TO;
    state = STATE_MOVED_TO_START;
  }

  private void initializeFirstLine()
  {
    if ((transversal & Y_AXIS) != 0)
    {
      if ((transversal & BOTTOM) != 0)
      {
        initGoingTop();
      }
      else
      {
        initGoingBottom();
      }
    }
    else
    {
      if ((transversal & RIGHT) != 0)
      {
        initGoingLeft();
      }
      else
      {
        initGoingRight();
      }
    }
  }

  private boolean checkPassFinishedXAxis()
  {
    if (!inrange_y(y_position))
    {
      state = STATE_PASS_ENDED;
      return true;
    }
    return false;
  }

  private boolean checkPassFinishedYAxis()
  {
    if (!inrange_x(x_position))
    {
      state = STATE_PASS_ENDED;
      return true;
    }
    return false;
  }

  private boolean initializeXAxisLine()
  {
    lower_bound = leftMostNotEqual(y_position, skip_pixel_value);
    if ((lower_bound == -1) && ((transversal & NO_SKIP) == 0))
    {
      //This is a blank line. Keep stepping.
      y_position += dy;
      return true;
    }
    higher_bound = rightMostNotEqual(y_position, skip_pixel_value);
    return false;
  }

  private boolean initializeYAxisLine()
  {
    lower_bound = topMostNotEqual(x_position, skip_pixel_value);
    if ((lower_bound == -1) && ((transversal & NO_SKIP) == 0))
    {
      //This is a blank line. Keep stepping.
      x_position += dx;
      return true;
    }
    higher_bound = bottomMostNotEqual(x_position, skip_pixel_value);
    return false;
  }

  private void initGoingLeft()
  {
    if (checkPassFinishedXAxis())
    {
      return;
    }
    if (initializeXAxisLine())
    {
      return;
    }

    end = lower_bound - overscan;
    begin = higher_bound + overscan;

    if (inrange_y(y_position + dy))
    {
      //If the next line in the dy direction also has a further end. We stop there.
      end = Math.min(end, leftMostNotEqual(y_position + dy, skip_pixel_value));
    }

    command_status = COMMAND_MOVE_TO;
    state = STATE_MOVING_LEFT;
  }

  private void initGoingTop()
  {
    if (checkPassFinishedYAxis())
    {
      return;
    }

    if (initializeYAxisLine())
    {
      return;
    }

    end = lower_bound - overscan;
    begin = higher_bound + overscan;

    if (inrange_x(x_position + dx))
    {
      //If the next line in the dx direction also has a further end. We stop there.
      end = Math.min(end, topMostNotEqual(x_position + dx, skip_pixel_value));
    }

    command_status = COMMAND_MOVE_TO;
    state = STATE_MOVING_TOP;
  }

  private void initGoingRight()
  {
    if (checkPassFinishedXAxis())
    {
      return;
    }

    if (initializeXAxisLine())
    {
      return;
    }

    begin = lower_bound - overscan;
    end = higher_bound + overscan;

    if (inrange_y(y_position + dy))
    {
      //If the next line in the dy direction also has a further end. We stop there.
      end = Math.max(end, rightMostNotEqual(y_position + dy, skip_pixel_value));
    }

    command_status = COMMAND_MOVE_TO;
    state = STATE_MOVING_RIGHT;
  }

  private void initGoingBottom()
  {
    if (checkPassFinishedYAxis())
    {
      return;
    }

    if (initializeYAxisLine())
    {
      return;
    }

    begin = lower_bound - overscan;
    end = higher_bound + overscan;

    if (inrange_x(x_position + dx))
    {
      //If the next line in the dx direction also has a further end. We stop there.
      end = Math.max(end, bottomMostNotEqual(x_position + dx, skip_pixel_value));
    }

    command_status = COMMAND_MOVE_TO;
    state = STATE_MOVING_BOTTOM;
  }

  private void updateProgress(int current, int max, int step)
  {
    if (progress != null)
    {
      if (step > 0)
      {
        progress.progressChanged(this, ((100 * current)  / max));
      }
      else
      {
        progress.progressChanged(this, ((100 * (max - current)) / max));
      }
    }
  }

  private void linestepAtLeftEdge()
  {
    y_position += dy;
    updateProgress(y_position, image.getHeight(), dy);
    initGoingRight();
  }

  private void linestepAtTopEdge()
  {
    x_position += dx;
    updateProgress(x_position, image.getWidth(), dx);
    initGoingBottom();
  }

  private void linestepAtRightEdge()
  {
    y_position += dy;
    updateProgress(y_position, image.getHeight(), dy);
    initGoingLeft();
  }

  private void linestepAtBottomEdge()
  {
    x_position += dx;
    updateProgress(x_position, image.getWidth(), dx);
    initGoingTop();
  }

  private void getUpdatedPixelAtLocation()
  {
    pixel = skip_pixel_value;
    if (inrange_x(x_position) && inrange_y(y_position))
    {
      pixel = image.getPixel(x_position, y_position);
    }

    command_status = 0;
    if (provider != null)
    {
      command_status |= COMMAND_PROPERTY;
      provider.update(property, pixel);
      vector_command.property = property;
    }
  }

  private void commitPosition()
  {
    if ((provider == null) && (pixel == skip_pixel_value))
    {
      command_status |= COMMAND_MOVE_TO;
    }
    else
    {
      command_status |= COMMAND_CUT_TO;
    }
  }

  private void moveLeft()
  {
    getUpdatedPixelAtLocation();
    x_position = nextColorChangeHeadingLeft(x_position, y_position, end);
    x_position = Math.max(x_position,end);
    commitPosition();

    if (end < x_position)
    {
      state = STATE_MOVING_LEFT;
    }
    else
    {
      state = STATE_LINESTEP_LEFT;
    }
  }

  private void moveTop()
  {
    getUpdatedPixelAtLocation();
    y_position = nextColorChangeHeadingTop(x_position, y_position, end);
    y_position = Math.max(y_position,end);
    commitPosition();

    if (end < y_position)
    {
      state = STATE_MOVING_TOP;
    }
    else
    {
      state = STATE_LINESTEP_TOP;
    }
  }

  private void moveRight()
  {
    getUpdatedPixelAtLocation();
    x_position = nextColorChangeHeadingRight(x_position, y_position, end);
    x_position = Math.min(x_position,end);
    commitPosition();

    if (x_position < end)
    {
      state = STATE_MOVING_RIGHT;
    }
    else
    {
      state = STATE_LINESTEP_RIGHT;
    }
  }

  private void moveBottom()
  {
    getUpdatedPixelAtLocation();
    y_position = nextColorChangeHeadingBottom(x_position, y_position, end);
    y_position = Math.min(y_position,end);
    commitPosition();

    if (y_position < end)
    {
      state = STATE_MOVING_BOTTOM;
    }
    else
    {
      state = STATE_LINESTEP_BOTTOM;
    }
  }

  private boolean inrange_y(int y)
  {
    return y < image.getHeight() && y >= 0;
  }

  private boolean inrange_x(int x)
  {
    return x < image.getWidth() && x >= 0;
  }

  
  /**
   * Finds the x coordinate for the left most pixel, since "start" depends on
   * what direction you are cutting in.
   *
   * @param y
   * @param v, seek value
   * @return x coordinate of left most non-matching pixel
   */
  protected int leftMostNotEqual(int y, int v)
  {
    for (int x = 0; x < image.getWidth(); x++)
    {
      int px = image.getPixel(x,y);
      if (px != v) return x;
    }
    return -1;
  }

    /**
   * Finds the y coordinate for the top most pixel, since "start" depends on
   * what direction you are cutting in.
   *
   * @param x
   * @param v, seek value
   * @return y coordinate of top most non-matching pixel
   */
  protected int topMostNotEqual(int x, int v)
  {
    for (int y = 0; y < image.getHeight(); y++)
    {
      int px = image.getPixel(x,y);
      if (px != v) return y;
    }
    return -1;
  }
  
  /**
   * Finds the x coordinate for the right most pixel, since "end" depends on
   * what direction you are cutting in.
   *
   * @param y, scanline to check
   * @param v, seek value
   * @return x coordinate of right most non-matching pixel
   */
  protected int rightMostNotEqual(int y, int v)
  {
    for (int x = image.getWidth()-1; x >= 0; x--)
    {
      int px = image.getPixel(x,y);
      if (px != v) return x;
    }
    return image.getWidth();
  }

  /**
   * Finds the y coordinate for the bottom most pixel, since "end" depends on
   * what direction you are cutting in.
   *
   * @param x, scanline to check
   * @param v, seek value
   * @return y coordinate of bottom most non-matching pixel
   */
  protected int bottomMostNotEqual(int x, int v)
  {
    for (int y = image.getHeight()-1; y >= 0; y--)
    {
      int px = image.getPixel(x,y);
      if (px != v) return y;
    }
    return image.getHeight();
  }
  
  /**
   * nextColorChange logic when heading <-
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingLeft(int x, int y, int def)
  {
    if (x <= -1) return def;
    if (x == 0) return -1;
    if (x == image.getWidth()) return image.getWidth()-1;
    if (image.getWidth() < x) return image.getWidth();
    
    
    int v = image.getPixel(x,y);
    for (int ix = x; ix >= 0; ix--)
    {
      int px = image.getPixel(ix,y);
      if (px != v) return ix;
    }
    return 0;
  }

    /**
   * nextColorChange logic when heading <-
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingTop(int x, int y, int def)
  {
    
    if (y <= -1) return def;
    if (y == 0) return -1;
    if (y == image.getHeight()) return image.getHeight()-1;
    if (image.getHeight() < y) return image.getHeight();
    
    
    int v = image.getPixel(x,y);
    for (int iy = y; iy >= 0; iy--)
    {
      int px = image.getPixel(x,iy);
      if (px != v) return iy;
    }
    return 0;
  }

  
  /**
   * nextColorChange logic when heading ->
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingRight(int x, int y, int def)
  {
    if (x < -1) return -1;
    if (x == -1) return 0;
    if (x == image.getWidth()-1) return image.getWidth();
    if (image.getWidth() <= x) return def;
    
    int v = image.getPixel(x,y);
    for (int ix = x; ix < image.getWidth(); ix++)
    {
      int px = image.getPixel(ix,y);
      if (px != v) return ix;
    }
    return image.getWidth()-1;
  }
  
  
  /**
   * nextColorChange logic when heading ->
   *
   * @param x x coordinate to start scanning from
   * @param y y coordinate to start scanning from
   * @param def, default value to return if there are no changes.
   * @return x coordinate of the next different color in this row
   */
  public int nextColorChangeHeadingBottom(int x, int y, int def)
  {
    if (y < -1) return -1;
    if (y == -1) return 0;
    if (y == image.getHeight()-1) return image.getHeight();
    if (image.getHeight() <= y) return def;
    
    int v = image.getPixel(x,y);
    for (int iy = y; iy < image.getHeight(); iy++)
    {
      int px = image.getPixel(x,iy);
      if (px != v) return iy;
    }
    return image.getHeight()-1;
  }
  
  public interface PropertiesUpdate
  {
    public void update(AbstractLaserProperty properties, int pixel);
  }
}
