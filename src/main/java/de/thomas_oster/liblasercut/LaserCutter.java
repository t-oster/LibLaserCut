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
package de.thomas_oster.liblasercut;

import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.PrintStream;

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
     * as a sanity check inside the sendJob mehtod
     * 
     * @param job
     * @throws IllegalJobException 
     */
    protected void checkJob(LaserJob job) throws IllegalJobException {
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
      this.sendJob(job, pl, new LinkedList<String>());
    }
    
    /**
     * Performs sanity checks on the LaserJob and sends it to the Cutter
     * @param job
     * @param pl A ProgressListener to give feedback about the progress
     * @throws IllegalJobException if the Job didn't pass the SanityCheck
     * @throws Exception  if there is a Problem with the Communication or Queue
     */
    public abstract void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception;

    public void saveJob(PrintStream fileOutputStream, LaserJob job) throws UnsupportedOperationException, IllegalJobException, Exception {
        System.err.println("Your driver does not implement saveJob(LaserJob job)");
        throw new UnsupportedOperationException("Your driver does not implement saveJob(LaserJob job)");
    }

    /**
     * If you lasercutter supports autofocus, override this method,
     * to let programs like VisiCut know, that they don't need to focus.
     * @return 
     */
    public boolean isAutoFocus() {
        return false;
    }

    /**
     * This calls sendJob(job, pl) with a default progress listener, which
     * just dumps everythong on the command line
     * @param job
     * @throws IllegalJobException
     * @throws Exception 
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
      List<String> warnings = new LinkedList<String>();
      this.sendJob(job, warnings);
      for(String w : warnings)
      {
        System.out.println("WARNING: "+w);
      }
    }

    /**
     * Returns the available Resolutions in DPI
     * @return 
     */
    public abstract List<Double> getResolutions();

    /**
     * Returns the Maximum width of a LaserJob in mm
     * @return 
     */
    public abstract double getBedWidth();

    /**
     * Returns the Maximum height of a LaserJob in mm
     * @return 
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
     * @see ShapeConverter.addShape() uses this value
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
     * @return 
     */
    public boolean canEstimateJobDuration() {
        return false;
    }

    /**
     * Returns an estimated time, how long the job would take
     * in seconds
     * @param job
     * @return 
     */
    public int estimateJobDuration(LaserJob job) {
        throw new RuntimeException("Method not implemented");
    }
    
  protected int estimateJobDuration(LaserJob job, double VECTOR_MOVESPEED_X, double VECTOR_MOVESPEED_Y, double VECTOR_LINESPEED, double RASTER_LINEOFFSET, double RASTER_LINESPEED, double RASTER3D_LINEOFFSET, double RASTER3D_LINESPEED)
  {
    //Holds the current Laser Head position in Pixels
    Point p = new Point(0, 0);

    double result = 0;//usual offset
    for (JobPart jp : job.getParts())
    {
      if (jp instanceof RasterPart)
      {
        RasterPart rp = (RasterPart) jp;
        Point sp = rp.getRasterStart();
        result += Math.max((double) (p.x - sp.x) / VECTOR_MOVESPEED_X,
          (double) (p.y - sp.y) / VECTOR_MOVESPEED_Y);
        double linespeed = ((double) RASTER_LINESPEED * rp.getLaserProperty().getSpeed()) / 100;
        ByteArrayList line = new ByteArrayList(rp.getRasterWidth());
        for (int y = 0; y < rp.getRasterHeight(); y++)
        {//Find any black point
          boolean lineEmpty = true;
      	  rp.getRasterLine(y, line);
          for (byte b : line)
          {
            if (b != 0)
            {
              lineEmpty = false;
              break;
            }
          }
          if (!lineEmpty)
          {
            int w = rp.getRasterWidth();
            result += (double) RASTER_LINEOFFSET + (double) w / linespeed;
            p.x = sp.y % 2 == 0 ? sp.x + w : sp.x;
            p.y = sp.y + y;
          }
          else
          {
            result += RASTER_LINEOFFSET;
          }
        }
      }
      if (jp instanceof Raster3dPart)
      {
        Raster3dPart rp = (Raster3dPart) jp;
        Point sp = rp.getRasterStart();
        result += Math.max((double) (p.x - sp.x) / VECTOR_MOVESPEED_X,
          (double) (p.y - sp.y) / VECTOR_MOVESPEED_Y);
        double linespeed = ((double) RASTER3D_LINESPEED * rp.getLaserProperty().getSpeed()) / 100;
      	ByteArrayList line = new ByteArrayList(rp.getRasterWidth());
        for (int y = 0; y < rp.getRasterHeight(); y++)
        {//Check if
          boolean lineEmpty = true;
          rp.getRasterLine(y, line);
          for (byte b : line)
          {
            if (b != 0)
            {
              lineEmpty = false;
              break;
            }
          }
          if (!lineEmpty)
          {
            int w = rp.getRasterWidth();
            result += (double) RASTER3D_LINEOFFSET + (double) w / linespeed;
            p.x = sp.y % 2 == 0 ? sp.x + w : sp.x;
            p.y = sp.y + y;
          }
        }
      }
      if (jp instanceof VectorPart)
      {
        double speed = VECTOR_LINESPEED;
        VectorPart vp = (VectorPart) jp;
        for (VectorCommand cmd : vp.getCommandList())
        {
          switch (cmd.getType())
          {
            case SETPROPERTY:
            {
              speed = VECTOR_LINESPEED * cmd.getProperty().getSpeed() / 100;
              break;
            }
            case MOVETO:
              result += Math.max((double) (p.x - cmd.getX()) / VECTOR_MOVESPEED_X,
                (double) (p.y - cmd.getY()) / VECTOR_MOVESPEED_Y);
              p = new Point(cmd.getX(), cmd.getY());
              break;
            case LINETO:
              double dist = distance(cmd.getX(), cmd.getY(), p);
              p = new Point(cmd.getX(), cmd.getY());
              result += dist / speed;
              break;
          }
        }
      }
    }
    return (int) result;
  }
  
  private double distance(double x, double y, Point p)
  {
    return Math.sqrt(Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2));
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

    public abstract String getModelName();

    /**
     * Converts a raster image (B&W or greyscale) into a series of vector
     * instructions suitable for printing. Lets non-raster-native cutters
     * emulate this functionality using gcode.
     * @param rp the raster job to convert
     * @param resolution resolution to output job at
     * @param bidirectional cut in both directions
     * @return a VectorPart job of VectorCommands
     */
    protected VectorPart convertRasterizableToVectorPart(RasterizableJobPart rp, double resolution, boolean bidirectional)
    {
      VectorPart result = new VectorPart(rp.getLaserProperty(), resolution);
      for (int y = 0; y < rp.getRasterHeight(); y++)
      {
        if (rp.lineIsBlank(y) == false)
        {
          Point lineStart = rp.getStartPosition(y);

          //move to prestart
          int x = rp.firstNonWhitePixel(y);
          int overscan = Math.round((float)Util.mm2px(this.getRasterPadding() * (rp.cutDirectionleftToRight ? 1 : -1), resolution));

          result.moveto(lineStart.x + x + rp.cutCompensation() - overscan, lineStart.y);

          //move to the first point of the scanline
          result.setProperty(rp.getPowerSpeedFocusPropertyForColor(255));
          result.lineto(lineStart.x + x + rp.cutCompensation(), lineStart.y);

          while(!rp.hasFinishedCuttingLine(x, y))
          {
            result.setProperty(rp.getPowerSpeedFocusPropertyForPixel(x, y));
            x = rp.nextColorChange(x, y);
            result.lineto(lineStart.x + x + rp.cutCompensation(), lineStart.y);
          }

          // move to post-end
          result.setProperty(rp.getPowerSpeedFocusPropertyForColor(255));
          result.lineto(lineStart.x + x + rp.cutCompensation() + overscan, lineStart.y);

          if (bidirectional) rp.toggleRasteringCutDirection();
        }
      }
      return result;
    }
    
    /**
     * Intented for use in the clone mehtod. Copies all properties
     * of that to this
     * @param that 
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
     * adding a default in here, then no matter what your constructor/initializer
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
