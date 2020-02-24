package nl.mpi.avf.player;

import java.awt.Component;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import nl.mpi.avf.player.JAVFPlayer.AV_PIXEL_FORMAT;

/**
 * A media player that encapsulates a JAVFPlayer, (possibly) a canvas /
 * visual component for video and controller for loading the video images.
 * 
 * @author Han Sloetjes
 *
 */
public class JAVFMediaPlayer extends AVFBaseMediaPlayer {
	private JAVFPlayer javfPlayer;// the player as JAVFPlayer
	private JAVFVisualComponent visualComponent;
	//private JAVFVisualCanvas visualComponent;
	private JAVFVideoLoadController videoController;
	
	// for the rendering thread
	private AtomicBoolean playingFlag = new AtomicBoolean();
	private ReentrantLock renderingLock;
	private Condition playingCondition;
	private RenderClockWatcher clockWatcher;
	
	/**
	 * Constructor.
	 * 
	 * @param mediaPath the url of the media file
	 */
	public JAVFMediaPlayer(String mediaPath) throws JAVFPlayerException {
		super(mediaPath);
		
		// log some information the player detected
	}
	
	
	
	@Override
	void initMediaPlayer() throws JAVFPlayerException {
		try {
			avfPlayer = new JAVFPlayer(mediaPath);
			javfPlayer = (JAVFPlayer) avfPlayer;
		} catch (JAVFPlayerException je) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning(String.format("Cannot create %s, message: %s", JAVFPlayer.class.getName(), 
						je.getMessage()));
			}
			throw je;
		}
		
		if (avfPlayer.hasVideo()) {
			visualComponent = new JAVFVisualComponent();
			//visualComponent = new JAVFVisualCanvas();
			visualComponent.setNaturalVideoSize(avfPlayer.getOriginalSize());
			
			String javfBufferLengthProp = System.clearProperty("JAVFPlayer.FrameBufferLengthMS");
			int javfBufferMs = -1;
			if (javfBufferLengthProp != null) {
				try {
					javfBufferMs = Integer.parseInt(javfBufferLengthProp);
				} catch (NumberFormatException nfe) {
					if (LOG.isLoggable(Level.INFO)) {
						LOG.info("Invalid value for JAVFPlayer.FrameBufferLengthMS property: " + javfBufferLengthProp);
					}
				}
			}
			// accept any value > 0, useful or not
			if (javfBufferMs > 0) {
				videoController = new JAVFVideoLoadController(this, javfBufferMs);
			} else {
				videoController = new JAVFVideoLoadController(this);
			}
			
			renderingLock = new ReentrantLock();
			playingCondition = renderingLock.newCondition();
			// create a rendering thread that watches the presentation clock 
			// (i.e. the player's media time) in a loop, checks every n milliseconds if a new 
			// frame has to be displayed
			clockWatcher = new RenderClockWatcher(5);
			clockWatcher.start();
			
		} else {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info(String.format("The media file %s has no video track", mediaPath));
			}
		}
	}



	/**
	 * Returns the visual component.
	 * 
	 * @return the canvas or component for the player, can be null.
	 */
	public Component getVisualComponent() {
		return visualComponent;
	}
	
	/**
	 * Returns the visual component as a {@link JAVFComponent}. This interface provides
	 * additional methods to set and get the current image and media time and aspect ratio.
	 * 
	 * @return the canvas or component that implements the {@link JAVFComponent} interface
	 */
	public JAVFComponent getVideoComponent() {
		return visualComponent;
	}
	
	/**
	 * Starts the player
	 */
	@Override
	public void start() {
		if (avfPlayer.isPlaying()) {
			return;
		}

		// prepare renderer, possibly a buffer has to be loaded
		// start video renderer
		if (videoController != null) {
			videoController.prepareForPlay(avfPlayer.getMediaTime());
			// create / start a player media time watcher
			playingFlag.set(true);
			renderingLock.lock();
			try {
				playingCondition.signal();
			} finally {
				renderingLock.unlock();
			}
		} else {
			avfPlayer.start();
		}
	}

	/**
	 * Pauses the player.
	 */
	@Override
	public void stop() {
		if (!avfPlayer.isPlaying()) {
			return;
		}

//		avfPlayer.stop();
		if (videoController != null) {
			playingFlag.set(false);
		} else {
			avfPlayer.stop();
		}
	}
	
	/**
	 * Pauses the media player and waits until the player reached the paused state.
	 */
	/*
	 * @Override
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
	*/
	
	/**
	 * Moves the playhead a frame forward.
	 * 
	 * @param toFrameBegin if true, it is tried to place the playhead at the beginning of
	 * the next video frame, otherwise the media time is increased with the duration of one frame
	 */
	/*
	@Override
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
	*/
	
	/**
	 * Moves the playhead a frame backward.
	 * 
	 * @param toFrameBegin if true, it is tried to place the playhead at the beginning of
	 * the previous video frame, otherwise the media time is decreased with the duration of one frame
	 */
	/*
	@Override
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
	*/
	
	/**
	 * Moves the playhead of the player to the requested media time.
	 * 
	 * @param mediaTimeMS the new media time in milliseconds
	 */
	@Override
	public void setMediaTime(long mediaTimeMS) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		avfPlayer.setMediaTime(mediaTimeMS);
		if (videoController != null) {
			videoController.timeUpdateMs(mediaTimeMS);
		}
	}
	
	/**
	 * Moves the playhead of the player to the requested media time.
	 * 
	 * @param mediaTime the new media time in seconds
	 */
	@Override
	public void setMediaTimeSeconds(double mediaTime) {
		if (avfPlayer.isPlaying()) {
			stopAndWait();
		}
		avfPlayer.setMediaTimeSeconds(mediaTime);
		if (videoController != null) {
			videoController.timeUpdateSeconds(mediaTime);
		}
	}
	
	/**
	 * Sets the native decoder pixel buffer format and the type of buffered images to be created
	 * on the Java side of the player. These two settings need to be in sync and 
	 * this method tries to set the pixel format of the native player first and then updates
	 * the setting of the image producer accordingly. These settings can influence the
	 * performance of the decoding and rendering.
	 * 
	 * @param imagePreset the new decoding and image encoding settings
	 * @return true if the settings were successfully applied, false otherwise
	 */
	public boolean setPixelFormatAndImageType(JAVFImageProducer.IMAGE_PRESETS imagePreset) {
		if (videoController == null) {
			return false;
		}
		boolean success = false;
		switch(imagePreset) {
		case AV_24RGB_DATABUFFER_BYTE:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_24RGB);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_24RGB_DATABUFFER_BYTE);
			}
			break;
		case AV_24RGB_DATABUFFER_INT:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_24RGB);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_24RGB_DATABUFFER_INT);
			}
			
			break;
		case AV_32ARGB_DATABUFFER_BYTE:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_32ARGB);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_32ARGB_DATABUFFER_BYTE);
			}
			break;
		case AV_32ARGB_DATABUFFER_INT:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_32ARGB);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_32ARGB_DATABUFFER_INT);
			}
			break;
		case AV_32BGRA_DATABUFFER_BYTE:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_32BGRA);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_32BGRA_DATABUFFER_BYTE);
			}
			break;
		case AV_32BGRA_DATABUFFER_INT:
			success = javfPlayer.setPixelFormat(AV_PIXEL_FORMAT.AV_32BGRA);
			if (success) {
				videoController.getImageProducer().setImagePreset(
						JAVFImageProducer.IMAGE_PRESETS.AV_32BGRA_DATABUFFER_INT);
			}
			break;
		default:
			break;
		}
		return success;
	}
	
	/**
	 * Called when the player is going to be closed and resources need to be released
	 * as much as possible.
	 * This deletes the native player, stops the thread that checks the media time 
	 * (the presentation clock) clears cached video images.
	 */
	@Override
	public void deletePlayer() {
		javfPlayer.deletePlayer();
		// check if load controller and clock watcher are stopped, interrupt
		// and set to null etc.
		
		if (clockWatcher != null) {
			clockWatcher.interrupt();
			clockWatcher = null;
		}
		if (videoController != null) {
			// force cleaning of cache
			videoController.close();
			videoController = null;
		}
	}
	
	/**
	 * A thread that checks the native media player's clock and informs the video controller
	 * of the progress and informs this (the enclosing) media player if the end of media
	 * is reached.
	 */
	protected class RenderClockWatcher extends Thread {
		private final int SLEEP;
		private double lastTimeSec = 0.0d;
		// the native player can have different settings for the action at the end of media
		// this flag determines whether this watcher should stop/pause the player when
		// end of media is detected. If there is a wrapper player with player controls it
		// is better for that player to pause the player (and update UI controls) 
		private boolean shouldStopPlayerAtEnd = false;

		/**
		 * @param sleepTime the time to sleep before checking the media time again 
		 * and updating the renderer
		 */
		public RenderClockWatcher(int sleepTime) {
			super();
			SLEEP = sleepTime;
		}
		
		/**
		 * @param sleepTime the time to sleep before checking the media time again 
		 * and updating the renderer
		 * @param stopPlayerAtEnd if true this watch thread can stop the player when the end of
		 * media has been reached
		 */
		public RenderClockWatcher(int sleepTime, boolean stopPlayerAtEnd) {
			super();
			SLEEP = sleepTime;
			shouldStopPlayerAtEnd = stopPlayerAtEnd;
		}

		@Override
		public void run() {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("RenderClockWatcher start");
			}
			while (!isInterrupted()) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("RenderClockWatcher not interrupted, acquiring lock");
				}
				renderingLock.lock();
				try {
					if (!playingFlag.get()) {
						if (avfPlayer.isPlaying()) {
							avfPlayer.stop();// this can lead to inconsistencies in UI controls
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Stop flagged: " + avfPlayer.getMediaTimeSeconds());
							}
						}
						try {
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("RenderClockWatcher into wait state");
							}							
							playingCondition.await();
						} catch (InterruptedException ie) {
							// log and ignore exception?
						}
					}
					
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("RenderClockWatcher entering watch loop");
					}
					
					while (playingFlag.get()) {
						double cts = avfPlayer.getMediaTimeSeconds();
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("RenderClockWatcher current time: " + cts);
						}
						
						if (cts != lastTimeSec) {
							videoController.timeUpdateSeconds(cts);
							lastTimeSec = cts;
						}
						
						if (cts == avfPlayer.getDurationSeconds()) {
							if (avfPlayer.isPlaying()) {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("RenderClockWatcher end of media time, player isPlaying: " + cts);
								}
								if (shouldStopPlayerAtEnd) {
									JAVFMediaPlayer.this.stop();// end of media stop
								}
							} else {
								if (LOG.isLoggable(Level.FINEST)) {
									LOG.finest("RenderClockWatcher end of media time player stopped: " + cts);
								}
								
								playingFlag.set(false);
							}
						}
						if (!avfPlayer.isPlaying() && cts != avfPlayer.getDurationSeconds()) {
							avfPlayer.start();
							if (LOG.isLoggable(Level.FINEST)) {
								LOG.finest("Start time: " + cts);
							}
						}
						try {
							Thread.sleep(SLEEP);
						} catch (InterruptedException iee) {
							// log and ignore
						}
					}
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("Left watch loop");
					}
				} finally {
					renderingLock.unlock();
				}
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("End of main loop");
				}
			}
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("End of thread life");
			}
		}		
	}// RenderClockWatcher
	
/*######### Wrapper getters for getter methods in JAVFPlayer ########*/

	public int getBytesPerPixel() {
		return javfPlayer.getBytesPerPixel();
	}
	
	public int getBytesPerFrame() {
		return javfPlayer.getBytesPerRow() * javfPlayer.getVideoImageHeight();
	}
	
	public int getVideoImageWidth() { //the width in pixels of the video image buffer
		return javfPlayer.getVideoImageWidth();
	}
	
	public int getVideoImageHeight() {//the height in pixels of the video image buffer
		return javfPlayer.getVideoImageHeight();
	}
	
	@Override
	public Dimension getOriginalSize() {
		return javfPlayer.getOriginalSize();
	}
	
	// the return value is an array of time values corresponding to the bytes loaded into the buffer 
	public double[] getVideoFrameSequence(long sampleTimeBegin, 
			long sampleTimeEnd, ByteBuffer buffer) {
		return javfPlayer.getVideoFrameSequence(sampleTimeBegin, sampleTimeEnd, buffer);
	}
	
	// the return value is an array of time values corresponding to the bytes loaded into the buffer 
	public double[] getVideoFrameSequenceSeconds(double sampleTimeBegin, 
			double sampleTimeEnd, ByteBuffer buffer) {
		return javfPlayer.getVideoFrameSequenceSeconds(sampleTimeBegin, sampleTimeEnd, buffer);
	}
	
	/**
	 * 
	 * @return the pixel format as a string
	 */
	public String getPixelFormat() {
		return javfPlayer.getPixelFormat();
	}

	/**
	 * Sets the video scale or zoom level, informs the visual component of
	 * the new scale level.
	 * 
	 * @param scaleFactor the new zoom level
	 */
	@Override
	public void setVideoScaleFactor(float scaleFactor) {
		super.setVideoScaleFactor(scaleFactor);
		// update the image
		if (visualComponent != null) {
			visualComponent.setVideoScaleFactor(scaleFactor);
		}
	}

	/**
	 * Repaint the video panel.
	 */
	@Override
	public void repaintVideo() {
		javfPlayer.repaintVideo();
		if (visualComponent != null) {
			visualComponent.repaint();
		}
	}

	/**
	 * Sets the location of the video image relative to the canvas. The size is
	 * ignored.
	 * @see {@link AVFBaseMediaPlayer#setVideoBounds(int, int, int, int)}
	 */
	@Override
	public void setVideoBounds(int x, int y, int w, int h) {
		//System.out.println(String.format("Set %d, %d, %d, %d", x, y, w, h));
		if (visualComponent != null) {
			visualComponent.setVideoBounds(x, y, w, h);
		}
	}

	/**
	 * @return an array of size 4, the current [x,y,w,h] values of the video image,
	 * or null
	 */
	@Override
	public int[] getVideoBounds() {
		if (visualComponent != null) {
			if (getVideoScaleFactor() == 1) {
				return new int[] {0, 0, visualComponent.getWidth(), visualComponent.getHeight()};
			} else {
				return visualComponent.getVideoBounds();
			}
		}
		return null;
	}


	/**
	 * A request to move the position of the scaled video image relative to the canvas.
	 * 
	 * @param dx the number of pixels to move horizontally
	 * @param dy the number of pixels to move vertically
	 */
	@Override
	public void moveVideoPos(int dx, int dy) {
		if (visualComponent != null) {
			//System.out.println(String.format("Move %d, %d", dx, dy));
			if (dx != 0 || dy != 0) {
				visualComponent.moveVideoPosition(dx, dy);
			}
		}
	}
	
}
