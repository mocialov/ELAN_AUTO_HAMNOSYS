package nl.mpi.avf.player;

import java.awt.Image;
/**
 * Class which combines an image and a time value. 
 */
class TimedImage {
	double t;
	Image img;
	
	/**
	 * @param t the time in seconds
	 * @param img the image corresponding to this media time
	 */
	public TimedImage(double t, Image img) {
		super();
		this.t = t;
		this.img = img;
	}		
}