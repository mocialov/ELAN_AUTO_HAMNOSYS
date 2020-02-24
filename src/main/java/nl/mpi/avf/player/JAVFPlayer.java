package nl.mpi.avf.player;

import java.nio.ByteBuffer;
import java.util.logging.Level;

/**
 * Implementation of an AudioVideo Foundation based media player which uses a native
 * AVAssetReader for decoding images from a video track and returning them in a byte buffer.
 * This player provides the methods to get the decoded pixel buffers, other classes (should)
 * take care of converting the buffers to images and of rendering the images synchronized 
 * to the audio (i.e. to the clock of the native player).
 * 
 * Note: the (quite generic) name of this class stems from the time when this was the only 
 * AVFoundation based player in this package. The native counterpart is named "AVFCustomPlayer",
 * also quite generic. This is the "decode natively, render in Java" variant. 
 * 
 * @author Han Sloetjes
 * 
 * @see AVFNativePlayer
 */
public class JAVFPlayer extends AVFBasePlayer {
	
	/**
	 * A few of the possible native pixel buffer formats to be used for decoding,
	 * not all supported yet.
	 */
	public enum AV_PIXEL_FORMAT {
		AV_24RGB,
		AV_32BGRA,
		AV_32ARGB,
		AV_Gray, // ??
		AV_422YpCbCr8 // ?? Bi-Planar
	}
	
	/**
	 * Constructor with media path as a string
	 * 
	 * @param mediaPath the path to a media source
	 */
	public JAVFPlayer(String mediaPath) throws JAVFPlayerException {
		super(mediaPath);
		
		if (id > 0) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Player type: JAVFPlayer - rendering in Java");
			}
		}
	}

	/**
	 * Deletes the native player, resets the id.
	 */
	@Override
	public void deletePlayer() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("JAVFPlayer - deleting native resources");
		}
		deletePlayer(id);
		// make sure no more calls are made to this player
		id = -id;
	}
	
	/**
	 * 
	 * @return the number of bytes per row of pixels of a frame image
	 */
	public int getBytesPerRow() {
		return getBytesPerRow(id);
	}
	
	/**
	 * 
	 * @return the number of bytes used to encode one pixel
	 */
	public int getBytesPerPixel() {
		return getBytesPerPixel(id);
	}
	
	/**
	 * 
	 * @return the width in pixels of the video image buffer
	 */
	public int getVideoImageWidth() {
		return getVideoImageWidth(id);
	}
	
	/**
	 * 
	 * @return the height in pixels of the video image buffer
	 */
	public int getVideoImageHeight() {
		return getVideoImageHeight(id);
	}
	
	/**
	 * 
	 * @return a (short) String representation of the current pixel format used when 
	 * decoding video images (e.g. 24RGB etc.) 
	 */
	public String getPixelFormat() {
		return getPixelFormat(id);
	}
	
	/**
	 * 
	 * @param pixFormat the new pixel format to be used for decoding images
	 * 
	 * @return true if the pixel format was successfully set, false otherwise
	 */
	public boolean setPixelFormat(AV_PIXEL_FORMAT pixFormat) {
		return setPixelFormat(id, pixFormat.toString());
	}
	
	/**
	 * Gets a sequence of decoded video images.
	 * 
	 * @param sampleTimeBegin the media time of the first image in the sequence, in milliseconds
	 * @param sampleTimeEnd the media time of the last image in the sequence, in milliseconds
	 * @param buffer the direct byte buffer into which the bytes should be copied
	 * @return an array of time values in seconds corresponding to the images (bytes) loaded into the buffer 
	 */
	public double[] getVideoFrameSequence(long sampleTimeBegin, 
			long sampleTimeEnd, ByteBuffer buffer) {
		return getVideoFrameSequence(id, sampleTimeBegin, sampleTimeEnd, buffer);
	}
	
	/**
	 * Gets a sequence of decoded video images.
	 * 
	 * @param sampleTimeBegin the media time of the first image in the sequence, in seconds
	 * @param sampleTimeEnd the media time of the last image in the sequence, in seconds
	 * @param buffer the direct byte buffer into which the bytes should be copied
	 * 
	 * @return an array of time values in seconds corresponding to the images (bytes) loaded into the buffer 
	 */
	public double[] getVideoFrameSequenceSeconds(double sampleTimeBegin, 
			double sampleTimeEnd, ByteBuffer buffer) {
		return getVideoFrameSequenceSeconds(id, sampleTimeBegin, sampleTimeEnd, buffer);
	}
	
// #####  native methods, mostly private #####
	
	// initializes the native counter part player, returns the id for subsequent calls to the native player
	@Override
	native long initPlayer(String mediaURL);
	
	// delete all native resources associated with this player
	@Override
	native void deletePlayer(long id);
	
	private native int getBytesPerRow(long id);
	private native int getBytesPerPixel(long id);
	private native int getVideoImageWidth(long id); //the width in pixels of the video image buffer  
	private native int getVideoImageHeight(long id);//the height in pixels of the video image buffer
	
	private native String getPixelFormat(long id);
	private native boolean setPixelFormat(long id, String pixelFormat);
	// the return value is an array of time values corresponding to the bytes loaded into the buffer 
	private native double[] getVideoFrameSequence(long id, long sampleTimeBegin, 
			long sampleTimeEnd, ByteBuffer buffer);
	// the return value is an array of time values corresponding to the bytes loaded into the buffer 
	private native double[] getVideoFrameSequenceSeconds(long id, double sampleTimeBegin, 
			double sampleTimeEnd, ByteBuffer buffer);
}
