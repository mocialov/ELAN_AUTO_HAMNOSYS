package nl.mpi.avf.player;

import java.awt.Dimension;
import java.awt.Image;

/**
 * An interface providing getters and setters for the current image and its media time
 * and for the aspect ratio of the video display.
 *  
 * @author Han Sloetjes
 */
public interface JAVFComponent {

	/**
	 * Sets the aspect ratio for the image display
	 * @param aspectRatio the new aspect ratio (w/h) for the display of the video
	 */
	public void setAspectRatio(float aspectRatio);
	
	/**
	 * Resets the aspect ratio of the displayed images to the natural, encoded aspect ratio
	 */
	public void resetAspectRatio();
	
	/**
	 * @param time the media time in milliseconds 
	 * @param image the corresponding image
	 */
	public void setImage(long time, Image image);
	
	/**
	 * Sets the (current) time and image to be displayed.
	 * 
	 * @param timeInSeconds the media time in seconds
	 * @param image the corresponding image
	 */
	public void setImage(double timeInSeconds, Image image);
	
	/**
	 * Sets (current) time and the image to display.
	 * 
	 * @param timedImage the TimedImage object containing the time and the image
	 */
	public void setImage(TimedImage timedImage);
	
	/**
	 * @return the time in milliseconds of the current image
	 */
	public long getCurrentImageTime();
	
	/**
	 * @return the time in seconds of the current image
	 */
	public double getCurrentImageTimeSeconds();
	
	/**
	 * 
	 * @return the current image, the image at the current time
	 */
	public Image getCurrentImage();
	
	/**
	 * @param naturalSize the natural width and height of the video
	 */
	public void setNaturalVideoSize(Dimension naturalSize);
	
	
}
