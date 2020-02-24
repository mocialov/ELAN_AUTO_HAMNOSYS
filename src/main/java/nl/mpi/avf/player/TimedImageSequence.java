package nl.mpi.avf.player;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class to store a sequence of timed images: video frame images with their media time 
 */
public class TimedImageSequence implements Comparable<TimedImageSequence> {
	private final Logger LOG = Logger.getLogger("AVF");
	private List<TimedImage> timeImaList;
	private double timePerImage = 0; 
	private double mediaDuration = 0;
	
	/**
	 * Constructor, creates an empty list for timed images
	 */
	public TimedImageSequence() {
		timeImaList = new ArrayList<TimedImage>(50);
	}

	/**
	 * @param times an array of time values, the first element is expected to have 
	 * the lowest value and the time values should be continuous, equidistant 
	 * (no gaps or jumps allowed).
	 * 
	 * @param images an array of images, each one corresponding to the time at the 
	 * same index in the times array
	 */
	public TimedImageSequence(double[] times, Image[] images) {
		super();
//		if (times.length != images.length) {
//			throw new IllegalArgumentException(
//					"The times array and the images array must have the same length");
//		}
		fillList(times, images);
		calcTimePerImage();
	}

	/**
	 * Used to handle end of media event; the end-of-media time would normally be the begin
	 * time of the frame after the last frame. To prevent attempts to load a sequence after the
	 * last video frame, the end of media is detected and the last frame is returned.
	 * 
	 * @param mediaDuration the total media duration of the video
	 */
	public void setMediaDuration(double mediaDuration) {
		this.mediaDuration = mediaDuration;
	}
	
	/**
	 * 
	 * @return the media time of the first image in the sequence, or -1 if there are no images
	 */
	public double getFirstTime() {
		if (timeImaList != null && !timeImaList.isEmpty()) {
			return timeImaList.get(0).t;
		}
		
		return -1;
	}
	
	/**
	 * Maybe this should return the time of the last non-null image?
	 * 
	 * @return the media time of the last image in the sequence, or -1 if there are no images
	 */
	public double getLastTime() {
		if (timeImaList != null && !timeImaList.isEmpty()) {
			return timeImaList.get(timeImaList.size() - 1).t;
		}

		return -1;
	}
	
	/**
	 * 
	 * @param time the media time to check
	 * @return true if the image corresponding to the media time is in this sequence,
	 * false otherwise
	 */
	public boolean hasTime(double time) {
		if (timeImaList != null && !timeImaList.isEmpty()) {
			return time >= timeImaList.get(0).t &&
					(time < timeImaList.get(timeImaList.size() - 1).t + timePerImage ||
							// additional condition for the case the time >= media duration
							(time > mediaDuration - timePerImage && 
									timeImaList.get(timeImaList.size() - 1).t >= mediaDuration - timePerImage));
		}

		return false;
	}
	
	/**
	 * Sets the new set of images and corresponding times of this sequence
	 * @param times the array of media times
	 * @param images the array of corresponding images
	 */
	public void setSequence(double[] times, Image[] images) {
//		if (times.length != images.length) {
//			throw new IllegalArgumentException(
//					"The times array and the images array must have the same length");
//		}
		fillList(times, images);
		calcTimePerImage();		
	}
	
	/**
	 * 
	 * @param time the media time to find the image for
	 * @return the image corresponding to the media time or null
	 */
	public Image getImageForTime(double time) {
//		if (timeImaList == null && timeImaList.isEmpty()) {
//			return null;
//		}
//		if (time < timeImaList.get(0) .t || time > timeImaList.get(timeImaList.size() - 1).t + timePerImage) {
//			return null;
//		}
		int index = indexForTime(time);
		if (index < 0) {
			return null;
		}
		return timeImaList.get(index).img;
	}
	
	/**
	 * TODO: Should have one call that returns an image and its start time. 
	 * Maybe introduce a Time-Image object.
	 *  
	 * @param image the image to find the corresponding time for
	 * @return the media time or -1 if the image is not in the sequence
	 */
	public double getStartTimeOfImage(Image image) {
		if (timeImaList == null || timeImaList.isEmpty()) {
			return -1;
		}
		
		for (int i = 0; i < timeImaList.size(); i++) {
			if (timeImaList.get(i).img == image) {
				return timeImaList.get(i).t;
			}
		}
		
		return -1;
	}
	
	/**
	 * 
	 * @param time the media time to find the TimedImage for
	 * @return the corresponding TimedImage object or null
	 */
	public TimedImage getTimedImageForTime(double time) {
//		if (timeImaList == null || timeImaList.isEmpty()) {
//			return null;
//		}
//		if (time < timeImaList.get(0) .t || time > timeImaList.get(timeImaList.size() - 1).t + timePerImage) {
//			return null;
//		}
		int index = indexForTime(time);
		if (index < 0) {
			return null;
		}
		return timeImaList.get(index);
	}
	
	/**
	 * 
	 * @param time the media time
	 * @return the index in the sequence of the corresponding image or -1
	 */
	private int indexForTime(double time) {
		if (timeImaList == null || timeImaList.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < timeImaList.size() - 1; i++) {
			if (time >= timeImaList.get(i).t && time < timeImaList.get(i + 1).t) {
				return i;
			}
		}
		
		if (time >= timeImaList.get(timeImaList.size() - 1).t && time < timeImaList.get(timeImaList.size() - 1).t
				+ timePerImage) {
			return timeImaList.size() - 1;
		}
		// the last frame in the media
		if (time > mediaDuration - timePerImage &&
				timeImaList.get(timeImaList.size() - 1).t >= mediaDuration - timePerImage ) {
			return timeImaList.size() - 1;
		}
		
		return -1;
	}
	
	private void calcTimePerImage() {
		if (timePerImage == 0) {
			if (timeImaList != null && timeImaList.size() >= 2) {
				timePerImage = timeImaList.get(timeImaList.size() - 1).t - 
						timeImaList.get(timeImaList.size() - 2).t; 
				if (timePerImage < 0) {
					timePerImage = 0;
				}
			}
		}
	}
	
	/**
	 * Clears the list and fills it again with TimedImage objects for those
	 * elements in the arrays with a time value greater than -1
	 * 
	 * @param times the array of media times
	 * @param images the array of images
	 */
	private void fillList(double[] times, Image[] images) {
		timeImaList.clear();
		int numMissed = 0;
		for (int i = 0; i < times.length && i < images.length; i++) {
			if (times[i] < 0) {
				// skip this one, maybe break the loop
				if (LOG.isLoggable(Level.FINE) && numMissed == 0) {
					LOG.fine(String.format("Image at index %d has invalid time value %f", i, times[i]));
				}
				numMissed++;
				// break or continue?
				continue;
			}
			if (images[i] == null) {
				// skip this one, maybe break the loop
				if (LOG.isLoggable(Level.FINE) && numMissed == 0) {
					LOG.fine(String.format("Image at index %d is null", i));
				}
				numMissed++;
				// break or continue?
				continue;
			}
			// check if the image != null?
			timeImaList.add(new TimedImage(times[i], images[i]));
		}
		
		if (LOG.isLoggable(Level.FINE) && numMissed > 0) {
			LOG.info(String.format("Sequence has %d invalid time values", numMissed));
		}
	}
	
	/**
	 * Copies the contents of this sequence into the other sequence
	 * 
	 * @param otherSeq the sequence to copy into
	 */
	public void copySequenceTo(TimedImageSequence otherSeq) {
		if (otherSeq != null) {
			if (timeImaList != null) {
				otherSeq.setSequence(timeImaList);
			}
		}
	}
	
	/**
	 * 
	 * @param timeImageList the list to copy into this sequence
	 */
	void setSequence(List<TimedImage> timeImageList) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Updating the sequence");
		}
		
		if (timeImageList != null) {
			timeImaList.clear();
			timeImaList.addAll(timeImageList);
		}
	}
	
	/**
	 * To be called when the player is closed and disposed. 
	 * Release resources as far as possible. 
	 */
	public void clear() {
		if (timeImaList != null) {
			for (TimedImage ti : timeImaList) {
				ti.img.flush();
			}
			timeImaList.clear();
		}
	}
	
	/**
	 * Compares two sequences based on the first image time 
	 * greater than -1
	 * 
	 * @param otherSeq the sequence to compare this one with
	 */
	@Override
	public int compareTo(TimedImageSequence otherSeq) {
		if (otherSeq == null) {
			return -1;
		}
		double d = otherSeq.getFirstTime();
		
		if (this.timeImaList != null && !this.timeImaList.isEmpty()) {
			if (d < 0) {
				return -1;
			}
			if (timeImaList.get(0).t < d) {
				return -1;
			} 
			if (timeImaList.get(0).t > d) {
				return 1;
			}
		} else {
			if (d == -1) {
				return 0;
			}
			return 1;
		}
		
		return 0;
	}
}
