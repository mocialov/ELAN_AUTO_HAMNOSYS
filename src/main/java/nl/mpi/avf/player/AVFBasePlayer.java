package nl.mpi.avf.player;

import java.awt.Dimension;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A base class a media player based on the macOS native AudioVideo Foundation;
 * it creates an AVPlayer for the sound (the audio track) and the presentation clock. 
 * For video this class is extended by two classes, one for rendering of the video in Java,
 * one that adds a native player layer to a Java component.
 * 
 * This base player contains almost everything the actual players need, the "id" which is used to 
 * connect to the correct native player (more than one player instance can exist). 
 * Almost all methods in this class have a private native counterpart method which takes 
 * the "id" as an argument.   
 * 
 * @author Han Sloetjes
 * 
 * @see AVFNativePlayer
 * @see JAVFPlayer
 */
public class AVFBasePlayer {
	final static Logger LOG = Logger.getLogger("AVF");
	/** the id  of the native player, the key for retrieval of the player from a map */
	long id;
	String mediaPath;
	static boolean nativeLibLoaded = false;
	// it would be better to use an enum for these status related constants although
	// that would make the JNI a bit more complicated
	final int STATUS_UNKNOWN = 0;
	final int STATUS_READY = 1;
	final int STATUS_FAILED = 2;
	final int MAX_LOAD_TIME = 15000;
	// load library block
	static {
		try {
			System.loadLibrary("AVFPlayer");
			nativeLibLoaded = true;
		} catch (SecurityException se) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not load native library (libAVFPlayer.dylib): " + se.getMessage());
			}
		} catch (UnsatisfiedLinkError ule) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not load native library (libAVFPlayer.dylib): " + ule.getMessage());
			}
		} catch (Throwable t) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not load native library (libAVFPlayer.dylib): " + t.getMessage());
			}
		}
	}
	
	/**
	 * Constructor with media path
	 * 
	 * @param mediaPath the path to a media source
	 */
	public AVFBasePlayer(String mediaPath) throws JAVFPlayerException {
		super();
		this.mediaPath = mediaPath;
		if (mediaPath.startsWith("file:///")) {
			this.mediaPath = mediaPath.substring(5);
		}
		
		id = initPlayer(this.mediaPath);
		//System.out.println("Id for player: " + id);
		
		if (id <= 0) {
			// throw exception			
			throw new JAVFPlayerException("Failed to create a native AVPlayer");
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(String.format("New player created, ID: %d for path %s", id, this.mediaPath));
			}
		}
		
	}

	/**
	 * Deletes the native player, resets the id.
	 */
	public void deletePlayer() {
		deletePlayer(id);
		// make sure no more calls are made to this player
		id = -id;
	}
	
	/**
	 * Start the player.
	 */
	public void start() {
		start(id);
	}
	
	/**
	 * Stops the player; in practice this is equivalent to pausing the player.
	 */
	public void stop() {
		stop(id);
	}
	
	/**
	 * Pauses the player.
	 */
	public void pause() {
		pause(id);
	}
	
	/**
	 * 
	 * @return true if the player is playing, false otherwise. 
	 * The (native) player is considered to be playing if the playback rate is > 0
	 */
	public boolean isPlaying() {
		return isPlaying(id);
	}
	
	/**
	 * Sets the playback rate.
	 * 
	 * @param rate the new playback rate
	 */
	public void setRate(float rate) {
		setRate(id, rate);
	}
	
	/**
	 * 
	 * @return the current play back rate (the rate as it was set)
	 */
	public float getRate() {
		return getRate(id);
	}
	
	/**
	 * Sets the volume of the audio, a value between 0 and 1
	 * 
	 * @param volume the new volume
	 */
	public void setVolume(float volume) {
		setVolume(id, volume);
	}
	
	/**
	 * 
	 * @return the current audio volume of the player, between 0 and 1
	 */
	public float getVolume() {
		return getVolume(id);
	}
	
	/**
	 * 
	 * @return the current media time in milliseconds (rounded to a long value)
	 */
	public long getMediaTime() {
		return getMediaTime(id);
	}
	
	/**
	 * 
	 * @return the current media time in seconds (as a floating point value)
	 */
	public double getMediaTimeSeconds() {
		return getMediaTimeSeconds(id);
	}
	
	/**
	 * Requests the player to position the time pointer (the presentation clock) 
	 * to a new position.
	 * @param time the time to jump to, in milliseconds
	 */
	public void setMediaTime(long time) {
		setMediaTime(id, time);
	}
	
	/**
	 * Requests the player to position the time pointer (the presentation clock) 
	 * to a new position.
	 * @param time the time to jump to, in seconds
	 */
	public void setMediaTimeSeconds(double time) {
		setMediaTimeSeconds(id, time);
	}
	
	/**
	 * 
	 * @return true if at least one video track was found in the media file, 
	 * false otherwise
	 */
	public boolean hasVideo() {
		return hasVideo(id);
	}
	
	/**
	 * 
	 * @return true if at least one audio track was found in the media file,
	 * false otherwise
	 */
	public boolean hasAudio() {
		return hasAudio(id);
	}
	
	/**
	 * 
	 * @return the media duration in milliseconds
	 */
	public long getDuration() {
		return getDuration(id);
	}
	
	/**
	 * 
	 * @return the media duration in seconds
	 */
	public double getDurationSeconds() {
		return getDurationSeconds(id);
	}
	
	/**
	 * 
	 * @return the encoded frame rate (number of frames per second)
	 * @see #getTimePerFrame()
	 */
	public double getFrameRate() {
		return getFrameRate(id);
	}
	
	/**
	 * 
	 * @return the encoded duration of a single video frame in milliseconds
	 * @see #getFrameRate()
	 */
	public double getTimePerFrame() {
		return getTimePerFrame(id);
	}
	
	// if has video, information about first video track, video frames
	/**
	 * 
	 * @return the aspect ratio of the video, based on the encoded dimension
	 */
	public float getAspectRatio() {
		return getAspectRatio(id);
	}
	
	/**
	 * 
	 * @return the encoded dimension of the video images
	 */
	public Dimension getOriginalSize() {
		return getOriginalSize(id);
	}
	
	/**
	 * Informs the native player of a change in the video scale factor.
	 * If the value is 1, the video normally fills the entire area of 
	 * the video panel (respecting aspect ratio settings).
	 * 
	 * @param scale the video scaling factor, 1 of no scaling is applied
	 */
	public void setVideoScaleFactor(float scale) {
		// to be implemented by subclasses
	}

	/**
	 * If the video is scaled and only a sub-region of the video image can be 
	 * shown on/by the video panel, the x and y value determine the horizontal
	 * and vertical displacement of the image. w and h are calculated based 
	 * on panel size and video scale factor.
	 * (x, y) are the coordinates of the left top corner of the bounds/rectangle.
	 * In native code this might have too be translated/recalculated into the
	 * coordinates of the lower left corner.  
	 * 
	 * @param x the x coordinate of the top left corner of the bounds 
	 * @param y the y coordinate of the top left corner of the bounds
	 * @param w the width of the bounds (i.e. of the video image)
	 * @param h the height of the bounds (i.e of the video image)
	 */
	public void setVideoBounds(int x, int y, int w, int h) {
		// to be implemented by subclasses
	}
	
	/**
	 * 
	 * @return the current position and size of the scaled video image 
	 * relative to the video display panel
	 */
	public int[] getVideoBounds() {
		return null;
	}
	
	/**
	 * Try to force the video to repaint/display itself.
	 */
	public void repaintVideo() {
		// to be implemented by subclasses
	}
	
	/**
	 * Maps a {@link Level} to one of the <code>JAVFLogLevel</code> levels
	 * 
	 * @param level the new logging level
	 */
	public static void setLogLevel(Level level) {
		if (level == null) {
			return;
		}
		if (level == Level.ALL) {
			setJAVFLogLevel(JAVFLogLevel.ALL);
		} else if (level == Level.OFF) {
			setJAVFLogLevel(JAVFLogLevel.OFF);
		} else if (level.intValue() > Level.ALL.intValue() && level.intValue() <= Level.FINE.intValue()) {
			setJAVFLogLevel(JAVFLogLevel.FINE);
		} else if (level.intValue() > Level.FINE.intValue() && level.intValue() <= Level.INFO.intValue()) {
			setJAVFLogLevel(JAVFLogLevel.INFO);
		} else if (level.intValue() > Level.INFO.intValue() && level.intValue() <= Level.WARNING.intValue()) {
			setJAVFLogLevel(JAVFLogLevel.WARNING);
		} else if (level.intValue() > Level.WARNING.intValue()) {
			setJAVFLogLevel(JAVFLogLevel.OFF);
		}
	}
	
// #####  native methods, mostly package private #####
	/**
	 * @param level the <code>level</code> of one of the JAVFLogLevel constants
	 */
	static native void setJAVFLogLevel(int level);
	/**
	 * @return the current native logging level
	 */
	static native int getJAVFLogLevel();
	// initializes the native counter part player, returns the id for subsequent calls to the native player
	native long initPlayer(String mediaURL);
	// returns one of the STATUS constants
	native int getPlayerLoadStatus (long id);
	// the error that occurred while creating a native player
	native String getPlayerError(long id);
	// delete all native resources associated with this player
	native void deletePlayer(long id);
	
	native void start(long id);
	native void stop(long id);
	native void pause(long id);
	native boolean isPlaying(long id);
	native int getState(long id); // get player state?
	
	native void setRate(long id, float rate);
	native float getRate(long id);
	native void setVolume(long id, float volume);
	native float getVolume(long id);
	
	native long getMediaTime(long id);
	native double getMediaTimeSeconds(long id);
	native void setMediaTime(long id, long time);
	native void setMediaTimeSeconds(long id, double time);
	
	native boolean hasVideo(long id);
	native boolean hasAudio(long id);
	native long getDuration(long id);
	native double getDurationSeconds(long id);
	native double getFrameRate(long id);
	native double getTimePerFrame(long id);
	
	// if has video, information about first video track, video frames
	native float getAspectRatio(long id);
	native Dimension getOriginalSize(long id);


}
