package mpi.eudico.client.annotator.player;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import nl.mpi.avf.frame.AVFFrameGrabber;
import nl.mpi.avf.player.AVFBaseMediaPlayer;
import nl.mpi.avf.player.AVFNativeMediaPlayer;
import nl.mpi.avf.player.JAVFComponent;
import nl.mpi.avf.player.JAVFMediaPlayer;
import nl.mpi.avf.player.JAVFPlayer;
import nl.mpi.avf.player.JAVFPlayerException;
import mpi.eudico.client.annotator.ElanLayoutManager;
import static mpi.eudico.client.annotator.util.ClientLogger.LOG;
import mpi.eudico.client.mediacontrol.ControllerManager;
import mpi.eudico.server.corpora.clomimpl.abstr.MediaDescriptor;
import mpi.eudico.util.TimeFormatter;
// TODO many elements have been copied from JMMFMediaPlayer, maybe an abstract media player 
// implementation with the common elements makes sense?

/**
 * An ELAN media player based on a JNI and AVFoundation based media player which performs 
 * the media decoding on the native side (native libraries/framework) and the rendering 
 * on the Java side (on a Canvas or JPanel).   
 * 
 * @author Han Sloetjes
 *
 */
public class JAVFELANMediaPlayer extends ControllerManager implements ElanMediaPlayer, 
VideoScaleAndMove, VideoFrameGrabber {
	//private AVFNativeMediaPlayer javfPlayer;
	private AVFBaseMediaPlayer javfPlayer;
	private AVFFrameGrabber frameGrabber;
	// video rendering by native component or Java based rendering
	private boolean nativeRendering = true;
	
	private MediaDescriptor mediaDescriptor;
	private long offset = 0L;
	private long stopTime;
	private long duration;// media duration minus offset
	private long origDuration;// the original media duration
	private float cachedVolume = 1.0f;
	private float cachedRate = 1.0f;
	private float curSubVolume;
	private boolean mute;
	private float origAspectRatio = 0;
	private float aspectRatio = 0;
	
	private double millisPerSample;
    private boolean frameRateAutoDetected = true;
	/** if true frame forward and frame backward always jump to the begin
	 * of the next/previous frame, otherwise it jumps with the frame duration */
	private boolean frameStepsToFrameBegin = false;
	
	// 
	private ElanLayoutManager layoutManager;
	private VideoMouseAdapter mouseAdapter;
	//
	private ReentrantLock playLock = new ReentrantLock();
	private Condition playCondition;
	private AtomicBoolean playingFlag;
	private EndOfMediaWatcher endWatcher;
	private AtomicBoolean playSelectionFlag;
	
	
	/**
	 * Constructor.
	 * 
	 * @param mediaDescriptor the descriptor containing the url of the file
	 * @throws NoPlayerException wrapper for any error or exception that prevents the media
	 * from being played, usually because the file is not supported 
	 */
	public JAVFELANMediaPlayer(MediaDescriptor mediaDescriptor) throws NoPlayerException {
		this (mediaDescriptor, true);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param mediaDescriptor the descriptor containing the url of the file
	 * @throws NoPlayerException wrapper for any error or exception that prevents the media
	 * from being played, usually because the file is not supported 
	 */
	public JAVFELANMediaPlayer(MediaDescriptor mediaDescriptor, boolean nativeRendering) throws NoPlayerException {
		this.mediaDescriptor = mediaDescriptor;
		this.nativeRendering = nativeRendering;
		//this.nativeRendering = false;
		offset = mediaDescriptor.timeOrigin;
//        String urlString = mediaDescriptor.mediaURL;
        // add checks for http/https??
//        if (urlString.startsWith("file:") &&
//                !urlString.startsWith("file:///")) {
//            urlString = urlString.substring(5);
//        }
		try {
			//JAVFPlayer.setLogLevel(Level.ALL);
			if (this.nativeRendering) {
				javfPlayer = new AVFNativeMediaPlayer(mediaDescriptor.mediaURL);
			} else {
				javfPlayer = new JAVFMediaPlayer(mediaDescriptor.mediaURL);
			}
			
			logMediaInfo();

		} catch (JAVFPlayerException jpe) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("JAVFMedia Player cannot handle the file: " + jpe.getMessage());
			}
			throw new NoPlayerException("JAVFMedia Player error: " + jpe.getMessage());
		}
		playLock = new ReentrantLock();
		playCondition = playLock.newCondition();
		playingFlag = new AtomicBoolean();
		endWatcher = new EndOfMediaWatcher(this, playLock, playCondition, playingFlag, 250);
		endWatcher.start();
		playSelectionFlag = new AtomicBoolean();
	}

	private void logMediaInfo() {
		if (LOG.isLoggable(Level.INFO)) {
			StringBuilder sb = new StringBuilder("JAVF Media Player:\n");
			sb.append(String.format("\tFile: %s\n", mediaDescriptor.mediaURL));
			sb.append(String.format("\tHas Video: %b\n", (javfPlayer.getVisualComponent() != null)));
			if (javfPlayer.getVisualComponent() != null) {
				Dimension origSize = javfPlayer.getOriginalSize();
				if (origSize != null) {
					sb.append(String.format("\tVideo Size: %d x %d\n", origSize.width, 
							origSize.height));
				}
				//sb.append(String.format("\tVideo Image Size: %d x %d\n", javfPlayer.getVideoImageWidth(), 
				//		javfPlayer.getVideoImageHeight()));
				sb.append(String.format("\tFrame Duration: %f sec., Frame Rate: %f\n", javfPlayer.getFrameDuration(), 
						javfPlayer.getFrameRate()));
			}
			sb.append(String.format("\tMedia Duration: %s", TimeFormatter.toString(javfPlayer.getDuration())));
			//sb.append(String.format("\tMedia Duration Seconds: %f", javfPlayer.getDurationSeconds()));
			LOG.info(sb.toString());
		}
	}
	
	@Override
	public void preferencesChanged() {
		// TODO Auto-generated method stub

	}

	@Override
	public MediaDescriptor getMediaDescriptor() {
		return mediaDescriptor;
	}

	@Override
	public void start() {
		if (javfPlayer.isPlaying()) {
			return;
		}
		javfPlayer.start();
		startControllers();
		if (javfPlayer.getRate() != cachedRate) {
			javfPlayer.setRate(cachedRate);
			setControllersRate(cachedRate);
		}
		// notify the thread that checks if the end of media has been reached
//		playingFlag.set(true);
		playLock.lock();
		try {
			playingFlag.set(true);
			playCondition.signal();
		} finally {
			playLock.unlock();
		}
	}

	@Override
	public void stop() {
		// the following (!javfPlayer.isPlaying()) alone is not reliable at the end of media; 
		// when the rate is 0 isPlaying returns false
		if (!javfPlayer.isPlaying() && javfPlayer.getMediaTime() != javfPlayer.getDuration()) {
			if (playingFlag.get()) {
				playingFlag.set(false);
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Playing flag inconsistency: flag is true, player is not playing");
				}
			}
			playSelectionFlag.set(false);
			return;
		}
		javfPlayer.stop();
		stopControllers();
		
		setControllersMediaTime(javfPlayer.getMediaTime());
		// if there is a thread running that checks the end-of-media condition
		// stop it here too by changing the playingFlag
		playingFlag.set(false);
		playSelectionFlag.set(false);
	}

	/**
	 * The embedded native player returns true if the play back rate is unequal 0,
	 * false if the rate is 0. It does not reflect a started, stopped or paused
	 * state. The rate after reaching the end of media depends on a setting in
	 * the native player and probably is 0 (paused).
	 * This player currently returns the value of the embedded player, ignoring
	 * its own playing flag.
	 *  
	 * @return true if the player is playing, false otherwise
	 */
	@Override
	public boolean isPlaying() {
		return javfPlayer.isPlaying();
	}

	@Override
	public void playInterval(long startTime, long stopTime) {
		if (javfPlayer.isPlaying()) {
			return;
		}
		
		setMediaTime(startTime);
		setStopTime(stopTime);
		playSelectionFlag.set(true);
		new PlaySelectionThread(this, playSelectionFlag, stopTime, 20).start();
		start();
	}

	/**
	 * Sets the stop time for playing a selection or interval. The embedded player
	 * does not support a stop time or playing an interval, so the stopping is 
	 * handled by a PlaySelectionThread.
	 * 
	 * @param stopTime the stop time in milliseconds
	 */
	@Override
	public void setStopTime(long stopTime) {
		this.stopTime = stopTime;
//		javfPlayer.setStopTime(this.stopTime + offset);		
		setControllersStopTime(this.stopTime);
	}

	@Override
	public void setOffset(long offset) {
		long diff = this.offset - offset;
        this.offset = offset;
        mediaDescriptor.timeOrigin = offset;
        
        if (origDuration == 0) {
        	origDuration = javfPlayer.getDuration();
        }
        duration = origDuration - offset;
        stopTime += diff;
//        setStopTime(stopTime);//??
	}

	@Override
	public long getOffset() {
		return offset;
	}

	@Override
	public void nextFrame() {
		if (javfPlayer.isPlaying()) {
			stop();
		}

		javfPlayer.frameForward(frameStepsToFrameBegin);
		// if synchronous, the new time can be fetched now
		long nextTime = javfPlayer.getMediaTime();
		setControllersMediaTime(nextTime - offset);
	}

	@Override
	public void previousFrame() {
		if (javfPlayer.isPlaying()) {
			stop();
		}

		javfPlayer.frameBackward(frameStepsToFrameBegin);
		// if synchronous, the new time can be fetched now
		long nextTime = javfPlayer.getMediaTime();
		setControllersMediaTime(nextTime - offset);
	}

	@Override
	public void setFrameStepsToFrameBegin(boolean stepsToFrameBegin) {
		frameStepsToFrameBegin = stepsToFrameBegin;
	}

	@Override
	public void setMediaTime(long time) {
		if (javfPlayer.isPlaying()) {
			stop();
		}
		
		if (time < 0) {
			time = 0;
		}
		if (time > duration) {// incorporates the offset
			time = duration;
		}

		javfPlayer.setMediaTime(time + offset);
		setControllersMediaTime(time);
	}

	@Override
	public long getMediaTime() {
		return javfPlayer.getMediaTime() - offset;
	}

	/**
	 * For this player setting the rate to anything else than 0 starts the player.
	 * So cache the rate if the player is not playing and apply it the next time
	 * the player is started.
	 * 
	 * @param rate the new play back rate
	 */
	@Override
	public void setRate(float rate) {
		// stop? check initialization state?
		cachedRate = rate;
		if (javfPlayer.isPlaying()) {
			javfPlayer.setRate(rate);
			setControllersRate(rate);
		}
	}

	/**
	 * The paused or stopped native player returns 0. This interferes with
	 * what ELAN expects for updating sliders etc. (the rate as it is set
	 * for play back (normal, slow motion or fast) by the user).
	 * Therefore return the cached rate if the native player returns 0.
	 * 
	 *  @return the rate as returned by the player when playing, otherwise 
	 *  the rate as set by the user
	 */
	@Override
	public float getRate() {
		float curRate = javfPlayer.getRate();
		if (curRate > 0) {
			return curRate;
		} else {
			return cachedRate;
		}
//		return javfPlayer.getRate();
	}

	@Override
	public boolean isFrameRateAutoDetected() {
		return frameRateAutoDetected;
	}

	@Override
	public long getMediaDuration() {
		if (duration <= 0) {
			if (origDuration == 0) {
				origDuration = javfPlayer.getDuration();
			}
			duration = origDuration - offset;
		}
		
		return duration;
	}

	@Override
	public float getVolume() {
		return javfPlayer.getVolume();
	}

	@Override
	public void setVolume(float level) {
		cachedVolume = level;
		// check initialization state?
		javfPlayer.setVolume(level);
	}

	@Override
	public void setSubVolume(float level) {
		curSubVolume = level;
	}

	@Override
	public float getSubVolume() {
		return curSubVolume;
	}

	@Override
	public void setMute(boolean mute) {
		this.mute = mute;
	}

	@Override
	public boolean getMute() {
		return mute;
	}

	@Override
	public void setLayoutManager(ElanLayoutManager layoutManager) {
		this.layoutManager = layoutManager;

		if (javfPlayer.getVisualComponent() != null) {
			// the mouse adapter adds itself as listener
			mouseAdapter = new VideoMouseAdapter(this, layoutManager, javfPlayer.getVisualComponent());
		}
	}

	@Override
	public Component getVisualComponent() {
		return javfPlayer.getVisualComponent();
	}

	@Override
	public int getSourceWidth() {
		Dimension d = javfPlayer.getOriginalSize();
		if (d != null) {
			return d.width;
		}
		return 0;
	}

	@Override
	public int getSourceHeight() {
		Dimension d = javfPlayer.getOriginalSize();
		if (d != null) {
			return d.height;
		}
		return 0;
	}

	@Override
	public float getAspectRatio() {
		if (aspectRatio != 0) {
			return aspectRatio;
		}
		Dimension origSize = javfPlayer.getOriginalSize();
		if (origSize != null) {
			if (origSize.getHeight() > 0) {
				origAspectRatio = (float) (origSize.getWidth() / origSize.getHeight());
				aspectRatio = origAspectRatio;
			}
		} else if (javfPlayer instanceof JAVFMediaPlayer) {
			if ( ((JAVFMediaPlayer) javfPlayer).getVideoImageHeight() > 0)
			origAspectRatio = ((JAVFMediaPlayer) javfPlayer).getVideoImageWidth() / 
				(float) ((JAVFMediaPlayer) javfPlayer).getVideoImageHeight();
			aspectRatio = origAspectRatio;
		}
		
		return aspectRatio;
	}

	@Override
	public void setAspectRatio(float aspectRatio) {
		this.aspectRatio = aspectRatio;
		// update the visual component
		if (javfPlayer.getVisualComponent() instanceof JAVFComponent) {
			((JAVFComponent) javfPlayer.getVisualComponent()).setAspectRatio(aspectRatio);
			if (layoutManager != null) {
				layoutManager.doLayout();
			}
		}
	}

	@Override
	public double getMilliSecondsPerSample() {
		if (millisPerSample == 0.0) {
			millisPerSample = javfPlayer.getFrameDuration();
			if (millisPerSample == 0.0) {
				millisPerSample = 40.0;
				frameRateAutoDetected = false;
			}
		}
		return millisPerSample;
	}

	@Override
	public void setMilliSecondsPerSample(long milliSeconds) {
		if (!frameRateAutoDetected) {
			millisPerSample = milliSeconds;
		}
	}

	@Override
	public void updateLocale() {
		if (mouseAdapter != null) {
			mouseAdapter.updateLocale();
		}
	}

	/**
	 * @return the description of the actual player,
	 *   Java with AV Foundation Player or 
	 */
	@Override
	public String getFrameworkDescription() {
		if (javfPlayer instanceof JAVFMediaPlayer) {
			return "AV Foundation Player with Java Rendering";
		} else {
//			javfPlayer instanceof AVFNativeMediaPlayer
			return "AV Foundation Player with Native Rendering";
		}
//		return "JAVF - Java with AV Foundation Player";
	}

	/**
	 * Stops and unloads the player, deletes resources.
	 */
	@Override
	public void cleanUpOnClose() {
		if (javfPlayer.isPlaying()) {
			stop();
		}
		if (mouseAdapter != null) {
			//mouseAdapter.disconnect();
			mouseAdapter = null;
		}
		javfPlayer.deletePlayer();
	}
	// implementation interface for scaling and panning of the video,
	// forwards to the wrapped player
	@Override
	public float getVideoScaleFactor() {
		return javfPlayer.getVideoScaleFactor();
	}

	@Override
	public void setVideoScaleFactor(float scaleFactor) {
		javfPlayer.setVideoScaleFactor(scaleFactor);
	}

	@Override
	public void repaintVideo() {
		javfPlayer.repaintVideo();
	}

	@Override
	public int[] getVideoBounds() {
		return javfPlayer.getVideoBounds();
	}

	@Override
	public void setVideoBounds(int x, int y, int w, int h) {
		javfPlayer.setVideoBounds(x, y, w, h);	
	}

	@Override
	public void moveVideoPos(int dx, int dy) {
		javfPlayer.moveVideoPos(dx, dy);
	}

	
	//##### VideoFrameGrabber interface #####
	/**
	 * Tries to grab the video frame at the current media time. The image 
	 * has the size as encoded in the video.
	 * 
	 * @return the current video frame or null if an error occurs
	 */
	@Override
	public Image getCurrentFrameImage() {
		if (frameGrabber == null) {
			frameGrabber = new AVFFrameGrabber(mediaDescriptor.mediaURL);
		}
		
		return frameGrabber.getVideoFrameImage(getMediaTime());
	}

	/**
	 * Forwards to the wrapped player.
	 * 
	 * @param time the requested media time
	 * @return the video frame or null if an error occurs
	 */
	@Override
	public Image getFrameImageForTime(long time) {
		if (frameGrabber == null) {
			frameGrabber = new AVFFrameGrabber(mediaDescriptor.mediaURL);
		}
		
		return frameGrabber.getVideoFrameImage(time);
	}
}
