package mpi.eudico.client.annotator.export;

import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.Preferences;

import mpi.eudico.client.annotator.gui.FileChooser;
import mpi.eudico.client.annotator.util.FileExtension;
import mpi.eudico.client.annotator.util.FileUtility;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import javax.swing.JOptionPane;


/**
 * A class to export a BufferedImage to file.
 *
 * @author Han Sloetjes
 */
public class ImageExporter {
	Frame parentFrame = null;
	
    /**
     * Creates a new ImageExporter instance
     */
    public ImageExporter() {
    }

    /**
     * Creates a new ImageExporter instance
     */
    public ImageExporter(Frame frame) {
    	parentFrame = frame;
    }
    
    /**
     * Shows a save dialog with .jpg, .bmp and .png as file filters and  writes the
     * image to file.
     *
     * @param image the image to save
     */
    public void exportImage(Image image) {
    	exportImage(image, null, null);
    }
    
    /**
     * Shows a save dialog with .jpg, .bmp and .png as file filters and  writes the
     * image to file.
     *
     * @param image the image to save
     * @param mediaFileNPath the name or path of the video file in case the image was 
     * extracted from a video, can be null
     * @param mediaTime the media time corresponding to the video frame, can be null
     */
    public void exportImage(Image image, String mediaFilePath, Long mediaTime) {
        BufferedImage img;

        if (!(image instanceof BufferedImage)) {
            img = imageToBufferedImage(image);
        } else {
			img = (BufferedImage) image;
        }

        if (img == null) {
            JOptionPane.showMessageDialog(parentFrame,
                ElanLocale.getString("ImageExporter.Message.NoImage"),
                ElanLocale.getString("Message.Warning"),
                JOptionPane.WARNING_MESSAGE);

            return;
        }

    	String filePath = null;
    	String mediaFileName = null;
    	// construct file name
    	if (mediaFilePath != null) {
    		filePath = FileUtility.urlStringToPath(mediaFilePath);
    		mediaFileName = FileUtility.fileNameFromPath(filePath);
    		mediaFileName = FileUtility.dropExtension(mediaFileName);
    		if (mediaFileName != null && mediaTime != null) {
    			mediaFileName = mediaFileName + "_" + mediaTime.toString();
    		}
    	}
        
    	String imageFormat = Preferences.getString("LastImageFormat", null);
        if (imageFormat != null) {
        	if (mediaFileName != null) {
        		mediaFileName = mediaFileName + "." + imageFormat;
        	}
        }
        
    	// construct initial destination folder
        /*
        String saveDir = Preferences.getString("MediaDir", null);
        
        if (saveDir == null) {
        	if (filePath != null) {
        		saveDir = FileUtility.directoryFromPath(filePath);
        	} else {
        		saveDir = System.getProperty("user.dir");
        	}
        }
		*/
        //
        FileChooser chooser = new FileChooser(parentFrame);
        /*
        if (saveDir != null) {
        	chooser.setCurrentDirectory(saveDir);
        }
        */
        if (mediaFileName != null) {
        	chooser.createAndShowFileDialog(null, FileChooser.SAVE_DIALOG, null, FileExtension.IMAGE_EXT, "LastImageDir", mediaFileName);
        } else {
        	chooser.createAndShowFileDialog(null, FileChooser.SAVE_DIALOG, FileExtension.IMAGE_EXT, "LastImageDir");
        }

        String imageIOType = "jpg";
        File saveFile = chooser.getSelectedFile();

        if (saveFile != null) {
        	String fileName = saveFile.getAbsolutePath();
            String lowerFileName = fileName.toLowerCase();

            if (lowerFileName.endsWith("png")) {
            	imageIOType = "png";
            } else if (lowerFileName.endsWith("bmp")) {
              	imageIOType = "bmp";
            } else if (!lowerFileName.endsWith("jpg") &&
                !lowerFileName.endsWith("jpeg")) {
                fileName += ".jpg";
            }
            
            Preferences.set("LastImageFormat", imageIOType, null);
            
            final File newSaveFile = new File(fileName);
            
            // Cocoa QT currently delivers png images with alpha. Remove alpha in case of saving as jpg or bmp
            // implement a more elegant way of fixing the transparency later...
            if (img.getColorModel().hasAlpha() && !imageIOType.equals("png")) {
               	BufferedImage flatImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
               	flatImg.getGraphics().drawImage(img, 0, 0, null);
               	img = flatImg;
            }
                
            try {
            	ImageIO.write(img, imageIOType, newSaveFile);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                JOptionPane.showMessageDialog(parentFrame,
                ElanLocale.getString("ExportDialog.Message.Error"),
                ElanLocale.getString("Message.Error"),
                JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private BufferedImage imageToBufferedImage(Image image) {
        if (image == null) {
            return null;
        }

        BufferedImage bufImg = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics g = bufImg.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return bufImg;
    }
}
