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
package com.t_oster.liblasercut;

import com.t_oster.liblasercut.platform.Util;
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
                throw new IllegalJobException("Resoluiton of " + p.getDPI() + " is not supported");
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

    public LaserProperty getLaserPropertyForVectorPart() {
        return new PowerSpeedFocusFrequencyProperty();
    }

    public LaserProperty getLaserPropertyForRasterPart() {
        return new PowerSpeedFocusProperty();
    }

    public LaserProperty getLaserPropertyForRaster3dPart() {
        return new PowerSpeedFocusProperty();
    }

    public abstract String getModelName();

    @Override
    public abstract LaserCutter clone();
}
