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


/**
 * This class implements a dummy driver that accepts laserjobs and prints debug information about them.
 * You can use it to test the VisiCut GUI without having a real lasercutter.
 *
 * @author Max Gaukler <development@maxgaukler.de>, based on the LAOS driver by Thomas Oster <thomas.oster@rwth-aachen.de>
 */
package com.t_oster.liblasercut.drivers;


import com.t_oster.liblasercut.*;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import javax.swing.JFileChooser;
/**
 *
 * @author Icetea
 */
public class ExportSVG extends LaserCutter
{
 
  private static final String SETTING_BEDWIDTH = "Laserbed width";
  private static final String SETTING_BEDHEIGHT = "Laserbed height";
  private static final String SETTING_RUNTIME = "Fake estimated run-time in seconds (-1 to disable)";
  private static final String SETTING_SVG_OUTDIR = "SVG Debug output directory (set empty to disable)";
  String savePath;
  /**
   * SVG output creator, mostly for testing vector-sorting
   */
  class SVGWriter  {
    private double xPrev,xNow,yPrev,yNow;
    private StringBuilder svg = new StringBuilder();
    private boolean vectorPathActive=false;
    private boolean partActive=false;
    private int idCounter=0;
    private int partCounter=0;
    private LaserCutter cutter;
    private double dpi;
    final JFileChooser saveFileChooser = new JFileChooser();
    int returnVal;
    
    public SVGWriter(LaserCutter cutter) {
      this.cutter = cutter;
      returnVal = saveFileChooser.showSaveDialog(null);
      savePath = saveFileChooser.getSelectedFile().getAbsolutePath();
    }

    /**
     * start a new JobPart
     * @param title some string that will be included in the group ID
     * @param dpi 
     */
    public void startPart(String title, double dpi) {
      endPart();
      partCounter += 1;
      this.dpi=dpi;
      this.partActive=true;
      svg.append("<g style=\"fill:none;stroke:#000000;stroke-width:0.1mm;\" id=\"");
      svg.append("visicut-part").append(partCounter).append("-");
      svg.append(title.replaceAll("[^a-zA-Z0-9]","_"));
      svg.append("\">\n");
    }
    
    /**
     * end a JobPart
     */
    public void endPart() {
      moveTo(0,0); // end path
      if (partActive) {
        partActive=false;
        svg.append("</g>\n");
      }
    }
    
    private void setLocation(int x, int y) {
      xPrev=xNow;
      yPrev=yNow;
      double factor = 25.4/dpi; // convert units to millimeters
      xNow=x*factor;
      yNow=y*factor;
    }
    
    /**
     * move to somewhere with laser off
     * @param x
     * @param y 
     */
    void moveTo(int x, int y) {
      setLocation(x,y);
      if (vectorPathActive) {
        // end the previous path
        svg.append("\"/>\n");
        vectorPathActive=false;
      }
    }

    /**
     * move to somewhere with laser on
     * @param x
     * @param y 
     */
    void lineTo(int x, int y) {
      setLocation(x,y);
      if (!partActive) {
        throw new RuntimeException("lineTo called outside of a part!");
      }
      if (!vectorPathActive) {
        // start a new path
        vectorPathActive=true;
        svg.append("<path id=\"visicut-").append(idCounter).append("\" d=\"M ");
        idCounter += 1;
        svg.append(xPrev).append(",").append(yPrev).append(" ");
      }
      svg.append(xNow).append(",").append(yNow).append(" ");
    }

    /**
     * generate SVG output string and reset everything (delete all path data)
     * @return 
     */
    private String getSVG() {
      endPart();
      svg.insert(0,"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?> \n"
              + "<!-- Created by VisiCut Debug output -->\n"
              + "<svg xmlns:svg=\"http://www.w3.org/2000/svg\" "
              + "xmlns=\"http://www.w3.org/2000/svg\" "
              + "width=\"" + cutter.getBedWidth() + "mm\" "
              + "height=\"" + cutter.getBedHeight() + "mm\" "
              + "viewBox=\"0 0 " + cutter.getBedWidth() + " " + cutter.getBedHeight() + "\" "
              + "version=\"1.1\" id=\"svg\"> \n");
      svg.append("</svg>\n");
      String result=svg.toString();
      svg = new StringBuilder();
      idCounter=0;
      return result;
    }
    
    /**
     * store a String into a file
     * @param path the filename
     * @param str the content to be stored
     */
    private void storeString(String path, String str) {
      try {
        FileWriter f = new FileWriter(path);
        BufferedWriter b = new BufferedWriter(f);
        b.write(str);
        b.close();
      } catch (Exception e) {
        System.out.println("Could not write debug SVG: Exception: " + e);
      }
    }
    
    
    void store(String pathSVG) {
      if (pathSVG == null || pathSVG.isEmpty()) {
        System.out.println("Not writing debug SVG - no output directory set (edit lasercutter settings to change)");
      } else {
        System.out.println("storing SVG debug output to "+pathSVG);
        String svgString=getSVG();
        storeString(pathSVG,svgString);
      }
    }
    
  }
  
  @Override
  public String getModelName() {
    return "ExportSVG";
  }
  


  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    pl.progressChanged(this, 0);
    BufferedOutputStream out;
    pl.taskChanged(this, "checking job");
    checkJob(job);
    job.applyStartPoint();
    pl.taskChanged(this, "sending");
    pl.taskChanged(this, "sent.");
    SVGWriter svg = new SVGWriter(this); // SVG debug output
    System.out.println("dummy-driver got LaserJob: ");
    // TODO don't just print the parts and settins, but also the commands
    // TODO improve SVG-debug output: support bitmaps, add animation
     for (JobPart p : job.getParts())
        {
          svg.startPart(p.getClass().getSimpleName(), p.getDPI());
         if (p instanceof VectorPart)
          {
            System.out.println("VectorPart");
            for (VectorCommand cmd : ((VectorPart) p).getCommandList())
            {
              if (cmd.getType() == VectorCommand.CmdType.SETPROPERTY)
              {
                
                if (!(cmd.getProperty() instanceof PowerSpeedFocusFrequencyProperty))
                {
                  throw new IllegalJobException("This driver expects Power,Speed,Frequency and Focus as settings");
                }
                System.out.println(((PowerSpeedFocusFrequencyProperty) cmd.getProperty()).toString());
              } else if (cmd.getType() == VectorCommand.CmdType.LINETO) {
                System.out.println("LINETO \t" + cmd.getX() + ", \t" + cmd.getY());
                svg.lineTo(cmd.getX(),cmd.getY());
              } else if (cmd.getType() == VectorCommand.CmdType.MOVETO) {
                System.out.println("MOVETO \t" + cmd.getX() + ", \t" + cmd.getY());
                svg.moveTo(cmd.getX(),cmd.getY());
              }
           }
            
          }
     /*     if (p instanceof RasterPart)
          {
            System.out.println("RasterPart");
            // TODO add raster output for SVG debug output
            RasterPart rp = ((RasterPart) p);
            if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
            {
              throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
            }
            System.out.println(((PowerSpeedFocusProperty) rp.getLaserProperty()).toString());

          }
          if (p instanceof Raster3dPart)
          {
            System.out.println("Raster3dPart");
            Raster3dPart rp = (Raster3dPart) p;
            if (rp.getLaserProperty() != null && !(rp.getLaserProperty() instanceof PowerSpeedFocusProperty))
            {
              throw new IllegalJobException("This driver expects Power,Speed and Focus as settings");
            }
            System.out.println(((PowerSpeedFocusProperty) rp.getLaserProperty()).toString());
          } */
      }
      System.out.println("end of job.");
     // svg.store(svgOutdir);
      svg.store(savePath);
      pl.progressChanged(this, 100);
  }

  @Override
  public int estimateJobDuration(LaserJob job)
  {
      // instead of really calculating some duration, just print the number configured from the settings
      // if <0, act as if the driver can not calculate a job duration
      if (!canEstimateJobDuration()) {
          throw new RuntimeException("cannot estimate job duration (dummy driver: fake runtime is set to negative value)");
      }
      // return bogus value to test codepaths of GUI
      return fakeRunTime;
  }

  private List<Double> resolutions;

  protected int fakeRunTime = -1;
  @Override
  public boolean canEstimateJobDuration() {
        return (fakeRunTime >= 0);
    }

  @Override
  public List<Double> getResolutions() {
    if (resolutions == null) {
      resolutions = Arrays.asList(new Double[]{
                500d
              });
    }
    return resolutions;
  }
  protected double bedWidth = 250;

  /**
   * Get the value of bedWidth
   *
   * @return the value of bedWidth
   */
  @Override
  public double getBedWidth() {
    return bedWidth;
  }

  /**
   * Set the value of bedWidth
   *
   * @param bedWidth new value of bedWidth
   */
  public void setBedWidth(double bedWidth) {
    this.bedWidth = bedWidth;
  }
  protected double bedHeight = 280;

  /**
   * Get the value of bedHeight
   *
   * @return the value of bedHeight
   */
  @Override
  public double getBedHeight() {
    return bedHeight;
  }

  /**
   * Set the value of bedHeight
   *
   * @param bedHeight new value of bedHeight
   */
  public void setBedHeight(double bedHeight) {
    this.bedHeight = bedHeight;
  }
  
  public String svgOutdir="./test";
  
  private static String[] settingAttributes = new String[]{
    SETTING_BEDWIDTH,
    SETTING_BEDHEIGHT,
    SETTING_RUNTIME,
    SETTING_SVG_OUTDIR
  };

  @Override
  public String[] getPropertyKeys() {
    return settingAttributes;
  }

  @Override
  public Object getProperty(String attribute) {
    if (SETTING_BEDWIDTH.equals(attribute)) {
      return this.getBedWidth();
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      return this.getBedHeight();
    } else if (SETTING_RUNTIME.equals(attribute)) {
      return this.fakeRunTime;
    } else if (SETTING_SVG_OUTDIR.equals(attribute)) {
      return this.svgOutdir;
    }
    return null;
  }

  @Override
  public void setProperty(String attribute, Object value) {
    if (SETTING_BEDWIDTH.equals(attribute)) {
      this.setBedWidth((Double) value);
    } else if (SETTING_BEDHEIGHT.equals(attribute)) {
      this.setBedHeight((Double) value);
    } else if (SETTING_RUNTIME.equals(attribute)) {
       this.fakeRunTime=Integer.parseInt(value.toString());
    } else if (SETTING_SVG_OUTDIR.equals(attribute)) {
      this.svgOutdir=value.toString();
    }

  }

  @Override
  public LaserCutter clone() {
    ExportSVG clone = new ExportSVG();
    clone.bedHeight = bedHeight;
    clone.bedWidth = bedWidth;
    clone.fakeRunTime = this.fakeRunTime;
    return clone;
  }
}

