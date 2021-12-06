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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.OutputStream;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public abstract class LaserCutter implements Cloneable, Customizable {

    /**
     * Checks the given job. It throws exceptions if
     * - job size is bigger than laser bed size
     * - job resolution is not supported
     * This method is supposed to be used (in addition of own sanity checks)
     * as a sanity check inside the sendJob method
     */
    protected void checkJob(LaserJob job) throws IllegalJobException {
        if (job.isRotaryAxisEnabled())
        {
          if (!this.isRotaryAxisSupported())
          {
             throw new IllegalJobException("Rotary axis is enabled, but not supported by this cutter.");
          }
          if (!(Double.isFinite(job.getRotaryAxisDiameterMm()) &&
            job.getRotaryAxisDiameterMm() >= 1 &&
            job.getRotaryAxisDiameterMm() <= 1000))
          {
            throw new IllegalJobException("Rotary axis diameter must be within 1 and 1000mm.");
          }
        }
        for (JobPart p : job.getParts()) {
            boolean pass = false;
            for (double d : this.getResolutions()) {
                if (d == p.getDPI()) {
                    pass = true;
                    break;
                }
            }
            if (!pass) {
                throw new IllegalJobException("Resolution of " + p.getDPI() + " is not supported");
            }
            if (p.getMinX() < 0 || p.getMinY() < 0) {
                throw new IllegalJobException("The Job exceeds the laser-bed on the top or left edge");
            }
            double maxX = Util.px2mm(p.getMaxX(), p.getDPI());
            double maxY = Util.px2mm(p.getMaxY(), p.getDPI());
            if (maxX > this.getBedWidth() || maxY > this.getBedHeight()) {
                throw new IllegalJobException("The Job is too big (" + maxX + "x" + maxY + ") for the Laser bed (" + this.getBedWidth() + "x" + this.getBedHeight() + ")");
            }
        }
    }

    public void sendJob(LaserJob job, ProgressListener pl) throws IllegalJobException, Exception
    {
      this.sendJob(job, pl, new LinkedList<>());
    }
    
    /**
     * Performs sanity checks on the LaserJob and sends it to the Cutter
     * @param pl A ProgressListener to give feedback about the progress
     * @throws IllegalJobException if the Job didn't pass the SanityCheck
     * @throws Exception  if there is a Problem with the Communication or Queue
     */
    public abstract void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception;

    public void saveJob(OutputStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception {
        System.err.println("Your driver does not implement saveJob(LaserJob job)");
        throw new UnsupportedOperationException("Your driver does not implement saveJob(LaserJob job)");
    }

    /**
     * If you lasercutter supports autofocus, override this method,
     * to let programs like VisiCut know, that they don't need to focus.
     */
    public boolean isAutoFocus() {
        return false;
    }

    /**
     * Return if rotary engrave unit (rotary axis) is supported
     */
    public boolean isRotaryAxisSupported()
    {
      return false;
    }


    /**
     * This calls sendJob(job, pl) with a default progress listener, which
     * just dumps everythong on the command line
     */
    public void sendJob(LaserJob job, List<String> warnings) throws IllegalJobException, Exception {
        this.sendJob(job, new ProgressListener() {

            @Override
            public void progressChanged(Object source, int percent) {
                System.out.println("" + percent + "%");
            }

            @Override
            public void taskChanged(Object source, String taskName) {
                System.out.println(taskName + "...");
            }
        }, warnings);
    }
    
    public void sendJob(LaserJob job) throws IllegalJobException, Exception
    {
      List<String> warnings = new LinkedList<>();
      this.sendJob(job, warnings);
      for(String w : warnings)
      {
        System.out.println("WARNING: "+w);
      }
    }

    /**
     * Returns the available Resolutions in DPI
     */
    public abstract List<Double> getResolutions();

    /**
     * Returns the Maximum width of a LaserJob in mm
     */
    public abstract double getBedWidth();

    /**
     * Returns the Maximum height of a LaserJob in mm
     */
    public abstract double getBedHeight();

    /**
     * Returns the required precision in px for interpolating curves as a poly-line.
     * The default is 1, which means curves may be approximated within a tolerance of
     * up to +- (1 inch / current DPI value) .
     *
     * A smaller value may be required if the driver does additional calculations
     * with the path, such as computing an acceleration-limited velocity profile.
     *
     * A larger value may be helpful for cutters with very limited CPU power,
     * which will slow down if a path has a high density of points per length.
     * @see de.thomas_oster.liblasercut.utils.ShapeConverter#addShape(java.awt.Shape, VectorPart, LaserCutter) uses this value
     * @return tolerance in machine pixels
     */
    public double getRequiredCurvePrecision() {
      return 1;
    }

    /**
     * Override this method, return true and override the
     * estimateJobDuration-method to allow Programs to use
     * your driver to estimate the duration of a job before
     * executing
     */
    public boolean canEstimateJobDuration() {
        return false;
    }

    /**
     * Returns an estimated time, how long the job would take
     * in seconds
     */
    public int estimateJobDuration(LaserJob job) throws IllegalJobException {
        throw new RuntimeException("Method not implemented");
    }
  
    /**
     * Returns an estimated time, how long the job would take
     * in seconds.
     * 
     * This calculation neglects acceleration, it assumes
     * <code>duration = length * speed</code> for vectors
     * and <code>duration = number_of_lines * (width * speed + offset)</code> for engrave.
     * All values are in millimeters, seconds or millimeters per second.
     * @param job LaserJob
     * @param moveSpeedX non-cutting (move) speed in mm/s in X direction 
     * @param moveSpeedY non-cutting (move) speed in mm/s in Y direction 
     * @param vectorLineSpeed cutting speed in mm/s if speed is set to 100
     * @param rasterExtraTimePerLine additional time per engrave line in seconds
     * @param rasterLineSpeed engrave speed in mm/s if speed is set to 100
     * @param raster3dExtraTimePerLine additional time per engrave3d line in seconds
     * @param raster3dLineSpeed engrave3d speed in mm/s if speed is set to 100
     */
  protected int estimateJobDuration(LaserJob job, double moveSpeedX, double moveSpeedY, double vectorLineSpeed, double rasterExtraTimePerLine, double rasterLineSpeed, double raster3dExtraTimePerLine, double raster3dLineSpeed)
  {
    /*
      Helper object for computing the duration of lineto() / moveto() commands
     */
    class TimeComputation
    {
      // we must store the current point in mm, not in px, because DPI may be different in each job part.
      private Point currentPointMm = new Point(0,0);
      /**
       * Move from last point to point p, return travel time.
       * @param p Point in px
       * @param px2mm conversion factor from px to mm
       */
      public double moveTime(Point p, double px2mm)
      {
        Point pMm = p.scale(px2mm);
        Point deltaMm = pMm.subtract(currentPointMm);
        currentPointMm = pMm;
        return Math.max(Math.abs(deltaMm.x) / moveSpeedX,
          Math.abs(deltaMm.y) / moveSpeedY);
      }
      
      /**
       * Cut/engrave line from last point to point p, return travel time.
       * @param p Point in px
       * @param px2mm conversion factor from px to mm
       */
      public double lineTime(Point p, double px2mm, double speed)
      {
        Point pMm = p.scale(px2mm);
        double time = currentPointMm.hypotTo(pMm) / speed;
        currentPointMm = pMm;
        return time;
      }
    }
    
    TimeComputation h = new TimeComputation();
    double result = 0;
    for (JobPart jp : job.getParts())
    {
      double px2mm = Util.px2mm(1, jp.getDPI());
      if (jp instanceof RasterizableJobPart)
      {
        RasterizableJobPart rp = (RasterizableJobPart) jp;
        double offset = rp instanceof RasterPart ? rasterExtraTimePerLine : raster3dExtraTimePerLine;
        double linespeed = rp instanceof RasterPart ? rasterLineSpeed : raster3dLineSpeed;
        Point sp = rp.getRasterStart();
        result += h.moveTime(sp, px2mm);
        linespeed = linespeed * rp.getLaserProperty().getSpeed() / 100;
        int w = rp.getRasterWidth();
        for (int y = 0; y < rp.getRasterHeight(); y++)
        {
          if (!rp.lineIsBlank(y))
          {
            result += offset + w * px2mm / linespeed;
          }
          else
          {
            // blank line
            result += offset;
            // this is highly simplified -- actually, large blank regions are skipped and therefore faster
          }
        }
        // For simplicity, we neglect the move time from the end of engraving.
      }
      if (jp instanceof VectorPart)
      {
        double speed = vectorLineSpeed;
        VectorPart vp = (VectorPart) jp;
        for (VectorCommand cmd : vp.getCommandList())
        {
          switch (cmd.getType())
          {
            case SETPROPERTY:
              speed = vectorLineSpeed * cmd.getProperty().getSpeed() / 100;
              break;
            case MOVETO:
              result += h.moveTime(new Point(cmd.getX(), cmd.getY()), px2mm);
              break;
            case LINETO:
              result += h.lineTime(new Point(cmd.getX(), cmd.getY()), px2mm, speed);
              break;
          }
        }
      }
    }
    return (int) result;
  }
  
    public LaserProperty getLaserPropertyForVectorPart() {
        return new PowerSpeedFocusFrequencyProperty();
    }

    public LaserProperty getLaserPropertyForRasterPart() {
        return new PowerSpeedFocusProperty();
    }

    public LaserProperty getLaserPropertyForRaster3dPart() {
        return new PowerSpeedFocusProperty();
    }

    public double getRasterPadding() {
      return 5;
    }

    public boolean getRasterPaddingAllowNegativeSpace() {
      return false;
    }

    public abstract String getModelName();
    

    /**
     * Converts a raster image (B&W or greyscale) into a series of vector
     * instructions suitable for printing. Lets non-raster-native cutters
     * emulate this functionality using gcode.
     * @param rp the raster job to convert
     * @param job the laser job (as context; will not be modified)
     * @param bidirectional cut in both directions
     * @param useMoveToForWhitePixels Handling of white (non-engraved) pixels:
     *    False: Recommended. Use lineto() with 0% power. Many lasercutters have
     *           smoother movement with lineto() instead of moveto.
     *    True:  Use moveto() commands. This is a necessary workaround for
     *           lasercutters that don't properly support scaling down power to
     *           0%, for example if lineto() always uses full power. 
     *           Results in the Engrave3D mode may be suboptimal.
     * @param useMoveToForPadding Handling of padding area left and right 
     *    of the actual engraving:
     *    see preferMoveTo. (Width of the overscan area is set by getRasterPadding()).
     * 
     * @return a VectorPart job of VectorCommands
     */
    protected VectorPart convertRasterizableToVectorPart(RasterizableJobPart rp, LaserJob job, boolean bidirectional, boolean useMoveToForWhitePixels, boolean useMoveToForPadding)
    {
      double resolution = rp.getDPI();
      // NOTE: The resolution of rp is also the resolution of the returned VectorPart.
      VectorPart result = new VectorPart(rp.getLaserProperty(), resolution);

      int leftLimitPx = 0;
      if (this.getRasterPaddingAllowNegativeSpace())
      {
        leftLimitPx = (int) Util.mm2px(job.getTransformedOriginX() - this.getRasterPadding(), resolution);
      }
      else
      {
        leftLimitPx = (int) Util.mm2px(job.getTransformedOriginX(), resolution);
      }

      System.out.println("leftLimitPx: " + leftLimitPx);
      int rightLimitPx = (int) Util.mm2px(job.getTransformedOriginX() + getBedWidth(), resolution);
      for (int y = 0; y < rp.getRasterHeight(); y++)
      {
        if (rp.lineIsBlank(y)){
          continue;
        }
        Point lineStart = rp.getStartPosition(y);

        //move to prestart
        int x = rp.firstNonWhitePixel(y);
        int overscan = Math.round((float)Util.mm2px(this.getRasterPadding() * (rp.cutDirectionleftToRight ? 1 : -1), resolution));
        double preStartX = lineStart.x + x + rp.cutCompensation() - overscan;
        preStartX = Math.min(rightLimitPx, Math.max(leftLimitPx, preStartX));

        result.moveto(preStartX, lineStart.y);

        //move to the first point of the scanline
        if (!useMoveToForPadding)
        {
          result.setProperty(rp.getPowerSpeedFocusPropertyForColor(255)); 
        }
        result.linetoOrMoveto(lineStart.x + x + rp.cutCompensation(), lineStart.y, !useMoveToForPadding);


        while(!rp.hasFinishedCuttingLine(x, y))
        {
          int color = rp.getImage().getGreyScale(x, y);
          // for non-white pixels, we always need to use lineto(). For white pixels, respect useMoveToForWhitePixels.
          boolean useLineto = color < 255 || !useMoveToForWhitePixels;
          if (useLineto) 
          {
            result.setProperty(rp.getPowerSpeedFocusPropertyForColor(color));
          }
          x = rp.nextColorChange(x, y);
          result.linetoOrMoveto(lineStart.x + x + rp.cutCompensation(), lineStart.y, useLineto);
        }

        // move to post-end
        double postEndX = lineStart.x + x + rp.cutCompensation() + overscan;
        postEndX = Math.min(rightLimitPx, Math.max(leftLimitPx, postEndX));
        if (!useMoveToForPadding)
        {
          result.setProperty(rp.getPowerSpeedFocusPropertyForColor(255));
        }
        result.linetoOrMoveto(postEndX, lineStart.y, !useMoveToForPadding);

        if (bidirectional) rp.toggleRasteringCutDirection();
      }
      return result;
    }
    
    /**
     * Intended for use in the clone method. Copies all properties
     * of that to this
     */
    protected void copyProperties(LaserCutter that)
    {
      for (String prop : that.getPropertyKeys())
      {
        setProperty(prop, that.getProperty(prop));
      }
    }
    
    /**
     * Adjust defaults after deserializing driver from XML
     * Use this if you add new fields to a driver and need them to be properly
     * initialized to *non-falsy* values before use.
     * 
     * i.e. you add a new key that by default isn't 0/0.0/false/"". Without
     * adding a default in here, then no matter what your constructor/initialyzer
     * does, it will always be set to a falsey value after deserializing an old
     * XML file.
     */
    protected void setKeysMissingFromDeserialization()
    {
    }
    
    @Override
    public abstract LaserCutter clone();
  
    /**
     * Called by XStream when deserializing XML settings files. Hook here to
     * call setKeysMissingFromDeserialization.
     */
    private Object readResolve() {
      setKeysMissingFromDeserialization();
      return this;
    }
}
