package nl.mpi.avf.player;

import java.awt.Component;
import java.awt.Dimension;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A basic media player that encapsulates an AVFBasePlayer.
 * This player can be used for audio files, 
 * 
 * @author Han Sloetjes
 *
 */
public class AVFBaseMediaPlayer {
	// package private
	final static Logger LOG = Logger.getLogger("AVF");
	AVFBasePlayer avfPlayer;
	String mediaPath;
	
	// members used by subclasses that support video and video zooming and panning
	float videoScaleFactor = 1f;
	int vdx = 0, vdy = 0;
	// the current x and y position (between 0 and 1) in the video that is located at the top left corner of the canvas
	double vxToTlcPerc = 0.0d; // video x coordinate at the left top corner as a percentage
	double vyToTlcPerc = 0.0d;
	
	/**
	 * Constructor.
	 * 
	 * @param mediaPath the url of the media file
	 */
	public AVFBaseMediaPlayer(String mediaPath) throws JAVFPlayerException {
		if (mediaPath == null) {
			throw new JAVFPlayerException("The media path is null");
		}
		this.mediaPath = mediaPath;
		// maybe do this in a static block
		if (LOG.getLevel() != null) {
			AVFBasePlayer.setLogLevel(LOG.getLevel());
		}
		
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("Media URL for AVFPlayer: " + this.mediaPath);
		}
		/*
		String urlString = this.mediaPath;
//       add checks for http/https??
		if (urlString.startsWith("file:")) {
			urlString = urlString.substring(5);
			if (urlString.startsWith("///")) {
				urlString = urlString.substring(2);	
			}
		}
		this.mediaPath = urlString;
		*/
		/*
		try {
			URI mediaURI = new URI(this.mediaPath);
			this.mediaPath = mediaURI.getPath();
		} catch (URISyntaxException use) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Cannot create URI for path:  " + use.getMessage());
			}
		}
		*/

		initMediaPlayer();
	}
	
	/**
	 * Returns the visual component for video.
	 * 
	 * @return the base player returns null 
	 */
	public Component getVisualComponent() {
		return null;
	}
	
	/**
	 * The base implementation creates an (audio only) AVFBasePlayer.
	 * 
	 * @throws JAVFPlayerException any exception that can occur while creating a native player 
	 */
	void initMediaPlayer() throws JAVFPlayerException {
		try {
			avfPlayer = new AVFBasePlayer(mediaPath);
		} catch (JAVFPlayerException je) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(String.format("Cannot create %s, message: %s", AVFBasePlayer.class.getName(), 
						je.getMessage()));
			}
			throw je;
		}
	}
	
	/**
	 * Starts the player.
	 */
	public void start() {
		if (avfPlayer != null) {
			avfPlayer.start();
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Cannot start AVFPlayer: null");
			}
		}
	}
	
	/**
	 * Stops/pauses the player.
	 */
	public void stop() {
		if (avfPlayer != null) {
			avfPlayer.stop();
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Cannot stop AVFPlayer: null");
			}
		}
	}
	
	/**
	 * Pauses the media player and waits until the player reached the paused state.
	 */
	void stopAndWait() {		
		if (avfPlayer.isPlaying()) {
			stop();
			// limit waiting time
			int count = 0;
			while (avfPlayer.isPlaying() && count < 50) {
				try {
					Thread.sleep(5);
					count++;
				} catch (InterruptedException ie) {}
			}
		}
	}
	
	/**
	 * Moves the playhead a frame forward.
	 * NOte: for audio files this not applicable as it is.
	 * 
	 * @param toFrameBegin if true, it is tried to place the playhead at the beginning of
	 * the next video frame, otherwise the media time is increased with the duration of one frame
	 */
	public void frameForward(boolean toFrameBegin) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		double curTime = avfPlayer.getMediaTimeSeconds();
		double targetTime = curTime + avfPlayer.getTimePerFrame() / 1000;
		if (targetTime > avfPlayer.getDurationSeconds()) {
			targetTime = avfPlayer.getDurationSeconds();
		}
		setMediaTimeSeconds(targetTime);		
	}
	
	/**
	 * Moves the playhead a frame backward.
	 * Note: for audio files this not applicable as it is.
	 * 
	 * @param toFrameBegin if true, it is tried to place the playhead at the beginning of
	 * the previous video frame, otherwise the media time is decreased with the duration of one frame
	 */
	public void frameBackward(boolean toFrameBegin) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		
		double curTime = avfPlayer.getMediaTimeSeconds();
		double targetTime = curTime - avfPlayer.getTimePerFrame() / 1000;
		if (targetTime < 0) {
			targetTime = 0.0d;
		}
		setMediaTimeSeconds(targetTime);		
	}
	
	/**
	 * Moves the playhead of the player to the requested media time.
	 * 
	 * @param mediaTimeMS the new media time in milliseconds
	 */
	public void setMediaTime(long mediaTimeMS) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		avfPlayer.setMediaTime(mediaTimeMS);

	}
	
	/**
	 * Moves the playhead of the player to the requested media time.
	 * 
	 * @param mediaTime the new media time in seconds
	 */
	public void setMediaTimeSeconds(double mediaTime) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		avfPlayer.setMediaTimeSeconds(mediaTime);
	}
	
	/**
	 * Sets the play back rate.
	 * 
	 * @param rate the new play back rate
	 */
	public void setRate(float rate) {
		avfPlayer.setRate(rate);
	}
	
	/**
	 * Sets the audio volume
	 * @param volume the volume level, between 0 and 1
	 */
	public void setVolume(float volume) {
		avfPlayer.setVolume(volume);
	}
	
	/**
	 * Called when the player is going to be closed and resources need to be released
	 * as much as possible.
	 * This deletes the native player, stops the thread that checks the media time 
	 * (the presentation clock) clears cached video images.
	 */
	public void deletePlayer() {
		avfPlayer.deletePlayer();
	}

	/*######### Wrapper getters for getter methods in JAVFPlayer ########*/
	// remove all avfPlayer != null checks because it is created in the constructor
	// of JAVFMediaPlayer and an exception is thrown if the JAVFPlayer cannot be created
	
	public boolean isPlaying() {
		return avfPlayer.isPlaying();
	}
	
	public long getMediaTime() {
		return avfPlayer.getMediaTime();
	}
	
	public double getMediaTimeSecond() {
		return avfPlayer.getMediaTimeSeconds();
	}
	
	public long getDuration() {
		return avfPlayer.getDuration();
	}
	
	public double getDurationSeconds() {
		return avfPlayer.getDurationSeconds();
	}
	
	public double getFrameDuration() {
		return avfPlayer.getTimePerFrame();
	}
	
	public double getFrameRate() {
		return avfPlayer.getFrameRate();
	}
	
	public float getRate() {
		return avfPlayer.getRate();
	}
	
	public float getVolume() {
		return avfPlayer.getVolume();
	}
	
	/**
	 * In the case of audio files, this will return null.
	 * 
	 * @return the encoded video dimension, or null
	 */
	public Dimension getOriginalSize() {
		return avfPlayer.getOriginalSize();
	}
	
	/**
	 * Sets the video scale or zoom level.
	 * 
	 * @param scaleFactor the new scale factor, should be greater than or equal to 1
	 */
	public void setVideoScaleFactor(float scaleFactor) {
		this.videoScaleFactor = scaleFactor;
	}
	
	/**
	 * 
	 * @return the current scale factor, the default is 1.0
	 */
	public float getVideoScaleFactor() {
		return videoScaleFactor;
	}
	
	/**
	 * Tries to force a repaint of the video.
	 */
	public void repaintVideo() {
		//stub
	}
	
	/**
	 * Set the position of the scaled video image relative to the video display panel.
	 * Only has an effect if the video scaling > 1.
	 * 
	 * <pre>
	 * (x,y)                 w
	 * +------------------------------------------+
	 * | image                                    |
	 * |                                          |
	 * |    +--------------------------+          |
	 * |    | panel                    |          |
	 * |    |                          |          |
	 * |    |                          |          | h
	 * |    |                          |          |
	 * |    |                          |          |
	 * |    +--------------------------+          |
	 * |                                          |
	 * +------------------------------------------+
	 * </pre>
	 * 
	 * @param x x coordinate of the left top corner of the image, <= 0 and >= videoPanel.width - w 
	 * @param y y coordinate of the left top corner of the image, <= 0 and >= videoPanel.height - h 
	 * @param w the width of the image, > width of video panel 
	 * @param h the height of the image, > height of video panel
	 */
	public void setVideoBounds(int x, int y, int w, int h) {
		if (videoScaleFactor == 1) {
			return;
		}
		// to be implemented by subclasses
	}
	
	/**
	 * Stub implementation, returns null.
	 * @return the current position and size of the video image, null if the scaling is 1
	 */
	public int[] getVideoBounds() {
		// to be implemented by subclasses
		return null;
	}
	
	/**
	 * Moves the scaled video image relative to the video display panel.
	 * Empty stub implementation.
	 * 
	 * @param dx the number of pixels to move the image horizontally
	 * @param dy the number of pixels to move the image vertically
	 */
	public void moveVideoPos(int dx, int dy) {
		// to be implemented by subclasses
	}
	
	// could add a getCurrentImage method?
}
