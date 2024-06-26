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
package de.thomas_oster.liblasercut.examples;

import de.thomas_oster.liblasercut.BlackWhiteRaster;
import de.thomas_oster.liblasercut.BlackWhiteRaster.DitherAlgorithm;
import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.properties.PowerSpeedFocusFrequencyProperty;
import de.thomas_oster.liblasercut.properties.PowerSpeedFocusProperty;
import de.thomas_oster.liblasercut.RasterPart;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.drivers.EpilogZing;
import de.thomas_oster.liblasercut.utils.BufferedImageAdapter;
import de.thomas_oster.liblasercut.platform.Point;
import de.thomas_oster.liblasercut.platform.Util;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * This is an example app which lets you select an Image File,
 * dither algorithm and target size to raster the image in normal raster
 * mode
 *
 * PLEASE NOTE THAT THIS FILE IS CURRENTLY NOT WORKING DUE TO CHANGES IN THE LIBRARY
 * SINCE THIS WAS ONLY A DEMONSTRATION AND NOT PART OF THE LIBRARY OR VISICUT
 * IT IS CURRENTLY UNMAINTAINED
 *
 * @author oster
 */
public class PhotoPrint {

    private static void error(String text) {
        JOptionPane.showMessageDialog(null, text, "An Error occured", JOptionPane.OK_OPTION);
        System.exit(1);
    }

    public static void main(String[] args) throws IllegalJobException, SocketTimeoutException, Exception {
        JFileChooser importFileChooser = new JFileChooser();
        importFileChooser.showOpenDialog(null);
        File toImport = importFileChooser.getSelectedFile();
        BufferedImage img = null;
        if (toImport == null) {
            error("No file selected");
        }
        try {
            img = ImageIO.read(toImport);
        } catch (IOException ex) {
            error(ex.getMessage());
        }
        int dpi = 500;
        try {
            dpi = Integer.parseInt(JOptionPane.showInputDialog(null, "Please select DPI", "" + dpi));
        } catch (NumberFormatException e) {
            error(e.getMessage());
        }
        int width = 50;
        try {
            width = Integer.parseInt(JOptionPane.showInputDialog(null, "Please select width in mm", "" + width));
        } catch (NumberFormatException e) {
            error(e.getMessage());
        }
        int oWidth = (int) Util.mm2px(width, dpi);
        int oHeight = img.getHeight() * oWidth / img.getWidth();
        final BufferedImage scaledImg = new BufferedImage(oWidth, oHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImg.createGraphics();
        AffineTransform at =
                AffineTransform.getScaleInstance((double) oWidth / img.getWidth(),
                (double) oHeight / img.getHeight());
        g.drawRenderedImage(img, at);
        final BufferedImage outImg = new BufferedImage(scaledImg.getWidth(), scaledImg.getHeight(), BufferedImage.TYPE_INT_RGB);
        final var cbDa = new JComboBox<DitherAlgorithm>();
        for (DitherAlgorithm da : BlackWhiteRaster.DitherAlgorithm.values()) {
            cbDa.addItem(da);
        }
        final JPanel prev = new JPanel();
        final JCheckBox cbInvert = new JCheckBox("invert");
        JCheckBox cbCut = new JCheckBox("Cut out the image");
        prev.setLayout(new BoxLayout(prev, BoxLayout.Y_AXIS));
        ImageIcon imgIc = new ImageIcon();
        final BufferedImage buf = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        imgIc.setImage(buf);
        final JLabel lab = new JLabel(imgIc);
        final JSlider filter = new JSlider(-255, 255, 0);
        filter.addChangeListener(ce -> {
            int diff = filter.getValue();

        });
        prev.add(lab);
        prev.add(cbDa);
        prev.add(cbInvert);
        prev.add(cbCut);
        prev.add(filter);
        prev.add(new JLabel("Width: "+outImg.getWidth()+" Height: "+outImg.getHeight()+ " ("+Util.px2mm(outImg.getWidth(), dpi)+"x"+Util.px2mm(outImg.getHeight(), dpi)+"mm)"));
        final ActionListener list = ae -> {
            lab.setText("dithering...");
            lab.repaint();
            DitherAlgorithm da = (DitherAlgorithm) cbDa.getSelectedItem();
            BufferedImageAdapter ad = new BufferedImageAdapter(scaledImg);
            ad.setColorShift(filter.getValue());
            BlackWhiteRaster bw;
            try
            {
              bw = new BlackWhiteRaster(ad, da);
            }
            catch (InterruptedException ex)
            {
              throw new RuntimeException("this must not happen");
            }
            for (int y = 0; y < bw.getHeight(); y++) {
                for (int x = 0; x < bw.getWidth(); x++) {
                    outImg.setRGB(x, y, bw.isBlack(x, y) ^ cbInvert.isSelected() ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            Graphics2D g1 = buf.createGraphics();
            AffineTransform at1 =
                    AffineTransform.getScaleInstance((double) buf.getWidth() / bw.getWidth(),
                    (double) buf.getWidth() / bw.getWidth());
            g1.setColor(Color.WHITE);
            g1.fillRect(0, 0, buf.getWidth(), buf.getHeight());
            g1.drawRenderedImage(outImg, at1);
            lab.setText("");
            prev.repaint();
        };
        filter.addChangeListener(ce -> {
            if (!filter.getValueIsAdjusting()) {
                list.actionPerformed(null);
            }
        });
        cbInvert.addActionListener(list);
        cbDa.addActionListener(list);
        cbDa.setSelectedIndex(0);
        if (JOptionPane.showConfirmDialog(
                null, prev, "Waehlen Sie einen Algorithmus aus", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION) {
            EpilogZing instance = new EpilogZing("137.226.56.228");
            //JComboBox material = new JComboBox();
            //for (MaterialProperty mp : instance.getMaterialPropertys()) {
            //    material.addItem(mp);
            //}
            //JOptionPane.showMessageDialog(null, material);
            //TODO: repair Material Selection
            RasterPart rp = new RasterPart(new BlackWhiteRaster(new BufferedImageAdapter(outImg), BlackWhiteRaster.DitherAlgorithm.AVERAGE), new PowerSpeedFocusProperty(), new Point(0, 0), dpi);
            VectorPart vp = null;
            if (cbCut.isSelected()) {
                vp = new VectorPart(new PowerSpeedFocusFrequencyProperty(), dpi);
                vp.moveto(0, 0);
                vp.lineto(outImg.getWidth(), 0);
                vp.lineto(outImg.getWidth(), outImg.getHeight());
                vp.lineto(0, outImg.getHeight());
                vp.lineto(0, 0);
            }

            LaserJob job = new LaserJob("PhotoPrint", "123", "bla");
            job.addPart(rp);
            job.addPart(vp);
            instance.sendJob(job);
            JOptionPane.showMessageDialog(null, "Please press START on the Lasercutter");
        }
    }
}
