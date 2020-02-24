package mpi.eudico.client.annotator.player;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import mpi.eudico.client.annotator.ElanLayoutManager;
import mpi.eudico.client.annotator.util.SystemReporting;
import mpi.eudico.client.mediacontrol.ControllerEvent;
import mpi.eudico.client.mediacontrol.ControllerListener;
import mpi.eudico.client.mediacontrol.ControllerManager;
import mpi.eudico.server.corpora.clomimpl.abstr.MediaDescriptor;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.AudioTrackInfo;
import uk.co.caprica.vlcj.media.VideoProjection;
import uk.co.caprica.vlcj.media.VideoTrackInfo;
import uk.co.caprica.vlcj.player.base.AudioApi;
import uk.co.caprica.vlcj.player.base.MediaApi;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.VideoApi;
import uk.co.caprica.vlcj.player.base.Viewpoint;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;


/**
 * The VLCJ based implementation of an ELAN media player.
 * This variant is based on VLCJ-4 (for VLC 3.x and later).
 */
public class VLCJ4MediaPlayer extends ControllerManager implements
        ElanMediaPlayer, NeedsCreateNewVisualComponent, 
        ControllerListener, VideoScaleAndMove, VideoFrameGrabber {

    private static final Logger logger = Logger.getLogger("VLC");
    private MediaPlayerFactory factory;
    /**
     * Player reference
     */
    private MediaPlayer player; //EmbeddedMediaPlayer or MediaPlayer
    private MediaApi media;
    private VideoTrackInfo firstVideoTrackInfo;
    
    /**
     * Display component
     */
    private EmbeddedMediaPlayerComponent playerComponent = null;
    private AudioPlayerComponent audioPlayerComponent = null;
    private CallbackMediaPlayerComponent callbackPlayerComponent = null;
    private Component videoSurfaceComponent = null;
    /**
     * Where to find the media file
     */
    private final MediaDescriptor mediaDescriptor;

    /**
     * The temporal offset between external and media time-stamps.
     */
    private long timeOffset;

    private static enum PlayMode {
        ToEnd, Interval
    }

    private final ReentrantLock modeLock = new ReentrantLock();
    /**
     * Current play mode
     */
    private PlayMode mode = PlayMode.ToEnd;
    private AtomicBoolean playingFlag;
    private AtomicBoolean playSelectionFlag;
    
	private ReentrantLock playLock = new ReentrantLock();
	private Condition playCondition;
	private EndOfMediaWatcher endWatcher;

    /**
     * Current playing interval. stopTime is adjusted for the timeOffset.
     */
    private long startTime, stopTime;
	private long duration;// media duration minus offset
	private long origDuration;// the original media duration
    /**
     */
    private Dimension videoSize;
	private static final Dimension fallbackVideoSize = new Dimension(352, 288);
	private float aspectRatio, origAspectRatio;
	private double msPerFrame = 0.0d;
	private boolean frameRateAutoDetected = false;
    /** 
     * if true frame forward and frame backward always jump to the begin
	 * of the next/previous frame, otherwise it jumps with the frame duration.
	 */
	private boolean frameStepsToFrameBegin = false;

	private boolean isVisual;
	private ElanLayoutManager layoutManager;
	private VideoMouseAdapter mouseAdapter;
	/**
	 * Setting the volume "too early", before it really starts playing, doesn't
	 * (always) work. Save the volume here, so that it can be applied later 
	 * when the movie starts playing.
	 */
	private int savedVolume;
    private float subVolume;
    private boolean mute;
    // cached media time and clock values, used to update the cross hair more
    // often than the native media player supports (getMediaTime returns the
    // begin time of the first frame in a buffer)
    private long lcmt;// lcmt = lastCachedMediaTime
	private BufferTimer bTimer = new BufferTimer();

	private float videoScaleFactor = 1f;
	private boolean isSpherical = false;
	private boolean isMpgvCodec;
	private int numVideoTracks;
	private int numAudioTracks;
	//private boolean hasVideo;

    static {
    	// A settable property: -Dnl.mpi.elan.vlcj=/Applications/VLC.app/Contents/MacOS/lib
    	final String vlcPath = System.getProperty("nl.mpi.elan.vlcj");
    	// TODO make this work under the new API with NativeDiscovery,
    	// requires creation of a service provider jar with a custom discovery implementation
    	if (vlcPath != null) {
    		if (logger.isLoggable(Level.WARNING)) {
    			logger.warning(String.format("Loading of native VLC libraries from custom directory "
    					+ "(\"%s\") is not supported yet", vlcPath));
    		}
    	}
    }

    /**
     * Create a VLCJ4MediaPlayer for a media URL
     *
     * @param mediaDescriptor the object containing the media URL
     *
     * @throws NoPlayerException if the the loaded media appears to have 0 
     * audio and 0 video tracks and a duration <= 0
     */
	public VLCJ4MediaPlayer(MediaDescriptor mediaDescriptor)
            throws NoPlayerException {
        this.mediaDescriptor = mediaDescriptor;
        isVisual = ! mediaDescriptor.mimeType.startsWith("audio/");

    	if (isVisual) {
    		if (!SystemReporting.isMacOS()) {
    			internalCreateNewVisualComponent(null);
    		} else {
    			internalCreateCallbackComponent(null);
    		}
            // At this point, no usable info is actually available yet.
            // seekable, playable, #video outputs, video size... all false or 0.
            // Therefore, we play a small bit of the media in a second media player,
            // which doesn't display anything on the screen, to find out the video size. 
    		tryHiddenPlayer(mediaDescriptor.mediaURL);     

    		Dimension dim = (videoSize != null) ? videoSize
                                                : fallbackVideoSize;
			origAspectRatio = (float)dim.getWidth() / (float)dim.getHeight();
			aspectRatio = origAspectRatio;
    	} else {
    		// create audio player
    		internalCreateAudioPlayer(null);
			aspectRatio = origAspectRatio = -1;
    	}
    	//
    	if (numAudioTracks == 0 && numVideoTracks == 0 && origDuration <= 0) {
    		cleanUpOnClose();// release what has already has been created
    		throw new NoPlayerException("VLC cannot play the file, no audio or video tracks found");
    	}
    	//
		if (logger.isLoggable(Level.INFO) ) {
			logger.log(Level.INFO, "Native Library Path {0}", factory.nativeLibraryPath());
		}

    	playingFlag = new AtomicBoolean();
    	playSelectionFlag = new AtomicBoolean();
		playLock = new ReentrantLock();
		playCondition = playLock.newCondition();
		endWatcher = new EndOfMediaWatcher(this, playLock, playCondition, playingFlag, 100);
		if (isVisual) {
			endWatcher.setEndOfMediaBufferMs(200);
		}
		endWatcher.start();
    }
    
	private void internalCreateNewVisualComponent(String [] prepareOptions) {
		// the following creates the MediaPlayerFactory
        playerComponent = new EmbeddedMediaPlayerComponent();
        factory = playerComponent.mediaPlayerFactory();
        player = playerComponent.mediaPlayer();
        media = player.media();
        videoSurfaceComponent = playerComponent.videoSurfaceComponent();
        // testing purposes
        /*
        MediaPlayerEventListener infoListener = new PlayerEventAdapter();
        player.events().addMediaPlayerEventListener(infoListener);
        */
        videoSurfaceComponent.addHierarchyListener(new HierarchyListener() {
			// This makes sure that when the player window becomes displayable
			// (which is a subset of visible) the player shows some frame in it. 
			// This can't be done before that time.
            @Override
			public void hierarchyChanged(HierarchyEvent e) {
            	long flags = e.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED);
                if ((flags != 0) &&	e.getComponent().isDisplayable()) {
                	long curTime = player.status().time();
                	player.audio().setVolume(2);
                	player.controls().play();
                	try {
                		Thread.sleep(1000);
                	} catch(InterruptedException ie) {}
                	player.controls().pause();
                	player.controls().setTime(curTime);
                	player.audio().setVolume(100);
                	player.video().setAdjustVideo(true);
                	//checkMediaInfo(player);
                	if (isVisual && videoSize == null) {
                		// try again with actual player
                		videoSize = player.video().videoDimension();
                	}
            	}             
            }
        });

        // configure the player with the given media file
        media.prepare(mediaDescriptor.mediaURL, prepareOptions);
        media.parsing().parse();
        //checkMediaInfo(player);
        player.audio().setMute(false);// initially  not mute
        player.audio().setVolume(100);
        setOffset(mediaDescriptor.timeOrigin);
        timeOffset = mediaDescriptor.timeOrigin;
    	player.controls().setRepeat(false);
        
    	if (isVisual) {
    		((EmbeddedMediaPlayer) player).input().enableMouseInputHandling(false);
    		((EmbeddedMediaPlayer) player).input().enableKeyInputHandling(false);
    	}
    	
	}
	
	private void internalCreateAudioPlayer(String[] prepareOptions) {
		audioPlayerComponent = new AudioPlayerComponent();
		
        factory = audioPlayerComponent.mediaPlayerFactory();
        player = audioPlayerComponent.mediaPlayer();
        media = player.media();
        media.prepare(mediaDescriptor.mediaURL, prepareOptions);
        media.parsing().parse();
        
        long startTime = System.currentTimeMillis();
        final long MAX = 2000;
        while (System.currentTimeMillis() - startTime < MAX) {
        	try {
        		Thread.sleep(50);
        	} catch (InterruptedException ie) {}
        	if (media.info().duration() > 0) {
        		break;
        	}
        }
        msPerFrame = 40d;
        checkMediaInfo(player);
        player.audio().setMute(false);
        player.audio().setVolume(100);
        setOffset(mediaDescriptor.timeOrigin);
        timeOffset = mediaDescriptor.timeOrigin;
    	player.controls().setRepeat(false);
	}

	private void internalCreateCallbackComponent(String [] prepareOptions) {
		callbackPlayerComponent = new CallbackMediaPlayerComponent();
		factory = callbackPlayerComponent.mediaPlayerFactory();
		player = callbackPlayerComponent.mediaPlayer();
//		callbackPlayerComponent.addHierarchyListener(new HierarchyListener() {
//			
//			@Override
//			public void hierarchyChanged(HierarchyEvent e) {
//            	long flags = e.getChangeFlags() & (HierarchyEvent.PARENT_CHANGED);
//                if ((flags != 0) &&	e.getComponent().isDisplayable()) {               	
//                	player.audio().setVolume(2);
//                	player.controls().play();
//                	try {
//                		Thread.sleep(1000);
//                	} catch(InterruptedException ie) {}
//                	player.controls().pause();
//                	player.controls().setTime(0);
//                	player.audio().setVolume(100);
//                	player.video().setAdjustVideo(true);
//                	//checkMediaInfo(player);
//            	}
//			}
//		});
		media = player.media();
        
        // configure the player with the given media file
        media.prepare(mediaDescriptor.mediaURL, prepareOptions);
        media.parsing().parse();
        checkMediaInfo(player);
        player.audio().setMute(false);// initially  not mute
        player.audio().setVolume(100);
        setOffset(mediaDescriptor.timeOrigin);
        timeOffset = mediaDescriptor.timeOrigin;
        videoSurfaceComponent = callbackPlayerComponent.videoSurfaceComponent();
	}
	
	
    /*
     * Play media invisibly, to find out the size.
     */
    private void tryHiddenPlayer(String media) {
    	CallbackMediaPlayerComponent hiddenPlayerComponent = new CallbackMediaPlayerComponent();
    	hiddenPlayerComponent.mediaPlayer().audio().mute();
    	hiddenPlayerComponent.mediaPlayer().audio().setVolume(0);
    	hiddenPlayerComponent.mediaPlayer().media().play(mediaDescriptor.mediaURL, "");

    	Semaphore semaphore = new Semaphore(0);	        

        try {
        	if (logger.isLoggable(Level.FINE)) {
        		logger.log(Level.FINE, "tryHiddenPlayer: waiting for semaphore...");
        	}
        	semaphore.tryAcquire(1000L, TimeUnit.MILLISECONDS);
        } catch(InterruptedException ex) {	        		
        }
        
        hiddenPlayerComponent.mediaPlayer().controls().stop();
        checkMediaInfo(hiddenPlayerComponent.mediaPlayer());// is initialized at this point
        
        hiddenPlayerComponent.release();
        //hiddenPlayerComponent = null;
        //semaphore = null;
    }
    
    @Override // NeedsCreateNewVisualComponent
    public java.awt.Component createNewVisualComponent() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "createNewVisualComponent");
    	}

        if (isVisual && (playerComponent != null || callbackPlayerComponent != null) 
        		&& player != null) {
        	// Preserve the time, volume, play rate, aspect ratio.
        	long time = player.status().time();
        	int volume = player.audio().volume();
        	float rate = player.status().rate();
        	float ar = getAspectRatio();
        	float zoom = getVideoScaleFactor();
   			String opt1 = ":start-time=" + Float.toString(time / 1000f);
   			String[] opts = { opt1 };
   			
        	if (playerComponent != null) {
	   			playerComponent.release();
	   			playerComponent = null;

	            internalCreateNewVisualComponent(opts);
	            videoSurfaceComponent = playerComponent.videoSurfaceComponent();
        		if (mouseAdapter != null) {
        			mouseAdapter.updateVisualComponent(videoSurfaceComponent);
        		}
        	} else if (callbackPlayerComponent != null) {
        		callbackPlayerComponent.release();
        		callbackPlayerComponent = null;
        		internalCreateCallbackComponent(opts);
        		videoSurfaceComponent = callbackPlayerComponent.videoSurfaceComponent();
        		if (mouseAdapter != null) {
        			mouseAdapter.updateVisualComponent(videoSurfaceComponent);
        		}
        	}
			
            player.controls().setRate(rate);
            player.audio().setVolume(volume);
            player.controls().setTime(time);
            setAspectRatio(ar);
            setVideoScaleFactor(zoom);
            
            stopControllers();
            
            if (playerComponent != null) {
            	//return playerComponent;
            	return videoSurfaceComponent;
            } else if (callbackPlayerComponent != null) {
            	return videoSurfaceComponent;
            } else {
            	return null;
            }
        } else {
        	// This method should not have been called in this case.
            return null;
        }        
    }
    
    @Override
    public MediaDescriptor getMediaDescriptor() {
        return mediaDescriptor;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public String getFrameworkDescription() {
    	String suffix = "";
    	if (callbackPlayerComponent != null) {
    		suffix = " (Direct Rendering)";
    	}
        return "VLCJMediaPlayer-" + factory.application().version() + suffix;
    }

    /**
     * Elan controllerUpdate Used to stop at the stop time in cooperation with
     * the playInterval method
     *
     * @param event DOCUMENT ME!
     */
    @Override
	public void controllerUpdate(ControllerEvent event) {
        logger.log(Level.FINER, "controller update {0}", event);
    }

    /**
     * play between two times. This method uses the contollerUpdate method to
     * detect if the stop time is passed.
     *
     * @param startTime DOCUMENT ME!
     * @param stopTime DOCUMENT ME!
     */
    @Override
    public void playInterval(long startTime, long stopTime) {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "play interval {0}-{1}", new Object[]{startTime,
                    stopTime});
    	}
        modeLock.lock();
        try {
            this.startTime = Math.max(0, startTime);
            this.stopTime = Math.min(stopTime + timeOffset, player.media().info().duration());
            internalSetMediaTime(this.startTime);
            /*
            switch (mode) {
                case ToEnd:
                    mode = PlayMode.Interval;
                    player.events().addMediaPlayerEventListener(intervalListener);
            }
            */
    		playSelectionFlag.set(true);
    		new PlaySelectionThread(this, playSelectionFlag, stopTime, 20).start();
            start();
        } finally {
            modeLock.unlock();
        }
    }

    /**
     * Empty implementation for ElanMediaPlayer Interface Only useful for
     * player that correctly supports setting stop time
     */
    @Override
	public void setStopTime(long stopTime) {
    }

    /**
     * Disable all code for interval playing.
     */
    private void stopPlayingInterval() {
        modeLock.lock();
        try {
            //player.events().removeMediaPlayerEventListener(intervalListener);
            mode = PlayMode.ToEnd;
        } finally {
            modeLock.unlock();
        }
    }

    /**
     * Gets the display Component for this Player.
     * Unfortunately, the VLC player insists on its Component to be displayed,
     * even for audio-only media.
     */
    @Override
    public java.awt.Component getVisualComponent() {
    	if (isVisual) {
    		if (playerComponent != null) {
//    			return playerComponent;	
    			return videoSurfaceComponent;
    		} else if (callbackPlayerComponent != null) {
    			return callbackPlayerComponent.videoSurfaceComponent();
    		}   		
    	}
    	
    	return null;
    }

    /**
     * @see mpi.eudico.client.annotator.player.ElanMediaPlayer#getSourceHeight()
     */
    @Override
    public int getSourceHeight() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "getSourceHeight; height is {0}",
        		videoSize == null ? "unknown" : String.valueOf(videoSize.height));
    	}
        if (videoSize != null) {
        	return videoSize.height;
        } else {
        	return fallbackVideoSize.height;
        }
    }

    /**
     * @see mpi.eudico.client.annotator.player.ElanMediaPlayer#getSourceWidth()
     */
    @Override
    public int getSourceWidth() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "getSourceWidth; width is {0}",
        		videoSize == null ? "unknown" : String.valueOf(videoSize.width));
    	}
        if (videoSize != null) {
        	return videoSize.width;
        } else {
        	return fallbackVideoSize.width;
        }
    }

    /**
     * Gets the control Component for this Player. Necessary for CorexViewer.
     * A.K.
     *
     * @return DOCUMENT ME!
     */
    public java.awt.Component getControlPanelComponent() {
        return null;
    }

    /**
     * Gets the ratio between width and height of the video image
     *
     * @return DOCUMENT ME!
     */
    @Override
    public float getAspectRatio() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "getAspectRatio: {0}", aspectRatio);
    	}
    	if (isVisual) {
    		return aspectRatio;
    	} else {
    		return 0; // -1 Hack in ElanLayoutManager2.addMediaPlayer() will move it out of the way.
    	}
    }

    /**
     * Enforces an aspect ratio for the media component.
     * Only these 8 exact strings are understood by VLC.
     * Other strings result in the default aspect ratio.
     *
     * @param aspectRatio the new aspect ratio
     */
    @Override
	public void setAspectRatio(float aspectRatio) {
        String aspect = "";
        switch ((int)(100 * aspectRatio + 0.5)) {
	        case 100: aspect =   "1:1"; break;
	        case 125: aspect =   "5:4"; break;
	        case 133: aspect =   "4:3"; break;
	        case 160: aspect =  "16:10"; break;
	        case 177:
	        case 178: aspect =  "16:9"; break;
	        case 221: aspect = "221:100"; break;
	        case 234: /* bad floating point rounding! */
	        case 235: aspect = "235:100"; break;
	        case 239: aspect = "239:100"; break;
	        default: aspect  = ""; break;
        }
        if (logger.isLoggable(Level.FINE)) {
        	logger.log(Level.FINE, "player.setAspectRatio({0})", aspect);
        }
        player.video().setAspectRatio(aspect);
    	//player.setAspectRatio(aspect);
    	this.aspectRatio = aspectRatio;
    }

    /**
     * Starts the Player as soon as possible.
     * Also set the volume again (try to cause no delay to the starting of all other media).
     */
    @Override
    public void start() {
       	player.controls().play();	// async!
        startControllers();
       	player.audio().setVolume(savedVolume);
       	
       	try {
       		// use tryLock instead of lock() to prevent freezing of the thread 
       		// this method is called on, usually the AWT-EventQueue
			if (playLock.tryLock(300, TimeUnit.MILLISECONDS)) {
				try {
					playingFlag.set(true);
					playCondition.signal();
				} finally {
					playLock.unlock();
				}
			} // returning without setting the flag and signaling the condition
       	} catch (InterruptedException ie) {}
    }

    /**
     * Stop the media player
     */
    @Override
    public void stop() {
        if (player.status().isPlaying()) {
            if (player.status().canPause()) {
                player.controls().pause();
            } else {
                player.controls().stop();
            }
        }
        
        playingFlag.set(false);
        playSelectionFlag.set(false);        
        stopControllers();
        setControllersMediaTime(getMediaTime());
        stopPlayingInterval();
        bTimer.pause();
    }

    /**
     * Tell if this player is playing
     *
     * @return true if the player is playing, false otherwise
     */
    @Override
    public boolean isPlaying() {
        return player.status().isPlaying();
    }

    /**
     * TODO We don't know always how many frames per second the media is...
     * That hopefully becomes known while playing, but even that
     * depends in the format of the media. It seems to work for MPEG4
     * but not for MPEG(2).
     *
     * @return the step size for one frame
     */
    @Override
    public double getMilliSecondsPerSample() {
    	if (msPerFrame > 0) {
    		return msPerFrame;
    	}
    	// Return an arbitrary value: 25 fps.
        return 1000 / 25;
    }

    /**
     * Only supported for audio players. 
     *
     * @param milliSeconds the step size for one frame
     */
    @Override
    public void setMilliSecondsPerSample(long milliSeconds) {
    	if (!frameRateAutoDetected) {
    		msPerFrame = milliSeconds;
        	if (logger.isLoggable(Level.FINE)) {
        		logger.log(Level.FINE, "Setting ms/sample to {0}",
                    milliSeconds);
        	}
    	} else {
        	if (logger.isLoggable(Level.INFO)) {
        		logger.log(Level.INFO, "Setting ms/sample not supported, requested {0}",
                    milliSeconds);
        	}
    	}
    }

    /**
     * Gets the volume as a number between 0 and 1. VLCJ uses values
     * between 0 and 100 internally.
     *
     * @return the volume as a number between 0 and 1
     */
    @Override
    public float getVolume() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "getVolume {0}", (player.audio().volume() / 100f));
    	}
    	
        return player.audio().volume() / 100f;
    }

    /**
     * Sets the volume as a number between 0 and 1.
     * Also remember it for possible later use.
     *
     * @param level a number between 0 and 1
     */
    @Override
    public void setVolume(float level) {
        int value = (int) (level * 100);
        // double check if the player is not muted
        if (value > 0) {
        	if (player.audio().isMute()) {
        		player.audio().setMute(false);
        	}
        }
        player.audio().setVolume(value);
        savedVolume = value;
    }

    /**
     * Set the offset to be used in get and set media time for this player
     *
     * @param offset the offset in milliseconds
     */
    @Override
    public void setOffset(long offset) {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "set offset {0}", offset);
    	}
		long diff = this.timeOffset - offset;
        this.timeOffset = offset;
        mediaDescriptor.timeOrigin = offset;
        if (player != null) {
        	if (origDuration == 0) {
        		origDuration = player.media().info().duration();
        	}
        	duration = origDuration - timeOffset;
        }
        stopTime += diff;
        setStopTime(stopTime);
    }

    /**
     * DOCUMENT ME!
     *
     * @return the offset used by this player
     */
    @Override
    public long getOffset() {
        return timeOffset;
    }

    /**
     * Gets this Clock's current media time in milliseconds.
     *
     * @return the current media time in milliseconds.
     */
    @Override
    public long getMediaTime() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "getMediaTime {0}", (player.status().time() - timeOffset));
    	}

    	long ct = player.status().time();
    	
    	if (ct == lcmt) {// add the difference in clock time, if playing
    		if (playingFlag.get()) {
    			ct += bTimer.getTime();
    		}
    	} else {
    		// first time a new buffer time is encountered, store corresponding clock time
    		lcmt = ct;
    		bTimer.reset();
    	}   	
    	
		return ct - timeOffset;
    }

    /**
     * Sets the media time in milliseconds.
     * This means that the player is set to that time + the time offset.
     * Also sets the time for all controlled media players.
     *
     * @param time in milliseconds
     */
    @Override
    public void setMediaTime(long time) {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "set media time {0} + offset {1}", new Object[] {
    				time, timeOffset });
    	}
        internalSetMediaTime(time);
    }

    private void internalSetMediaTime(long time) {
    	player.controls().setTime(time + timeOffset);
        setControllersMediaTime(time);
        // the following is only for video actually
        if (player.status().time() == lcmt) {
        	bTimer.pause();
        } else {
        	bTimer.pauseReset();
        	lcmt = 0;
        }
    }
    
    /**
     * nextFrame() doesn't work properly on all media files.
     * And even when it works, the media time is very imprecise.
     */
    @Override
    public void nextFrame() {
		if (player.status().isPlaying()) {
			stop();
		}
		//long t = getMediaTime();
        player.controls().nextFrame();
        /*
        if (isMpgvCodec) {
        	setMediaTime((long)(t + msPerFrame));
        	// scrub
        	float curRate = getRate();
        	player.controls().setRate(0.01f);
        	player.controls().play();
        	while (!player.status().isPlaying()) {
        		try {
        			Thread.sleep(5);
        		} catch(InterruptedException ie) {}
        	}
        	player.controls().pause();
        	while (player.status().isPlaying()) {
        		try {
        			Thread.sleep(5);
        		} catch(InterruptedException ie) {}
        	}
        	player.controls().setRate(curRate);
        }
        */
        setControllersMediaTime(getMediaTime());
        if (logger.isLoggable(Level.FINE)) {
        	logger.log(Level.FINE, "time now {0}", player.status().time());
        }
    }

	/**
	 * Since time registration is very imprecise, stepping a frame back is not likely to work properly.
	 * But we can try...
	 */
    @Override
    public void previousFrame() {
    	if (logger.isLoggable(Level.FINE)) {
    		logger.log(Level.FINE, "Previous frame not supported natively.");
    	}
		if (player != null) {
			if (player.status().isPlaying()) {
				stop();
			}
	        
			double msecPerSample = getMilliSecondsPerSample();
			long curTime = getMediaTime();

	        if (frameStepsToFrameBegin) {
	        	long curFrame = (long)(curTime / msecPerSample);
	        	if (curFrame > 0) {
	        		internalSetMediaTime((long) Math.ceil((curFrame - 1) * msecPerSample));
	        	} else {
	        		internalSetMediaTime(0);
	        	}
	        } else {
	        	curTime = (long) Math.ceil(curTime - msecPerSample);
	        	
		        if (curTime < 0) {
		        	curTime = 0;
		        }
		
		        internalSetMediaTime(curTime);
	        }
		}
    }

    @Override
	public void setFrameStepsToFrameBegin(boolean stepsToFrameBegin) {
        frameStepsToFrameBegin = stepsToFrameBegin;
    }

    /**
     * Gets the current temporal scale factor.
     *
     * @return DOCUMENT ME!
     */
    @Override
    public float getRate() {
        return player.status().rate();
    }

    /**
     * Sets the temporal scale factor.
     *
     * @param rate the new play back rate
     */
    @Override
    public void setRate(float rate) {
    	player.controls().setRate(rate);
        setControllersRate(rate);
        bTimer.setRate(rate);
    }

    /**
     * @see
     * mpi.eudico.client.annotator.player.ElanMediaPlayer#isFrameRateAutoDetected()
     */
    @Override
    public boolean isFrameRateAutoDetected() {
        return frameRateAutoDetected;
    }

    /**
     * Get the duration of the media represented by this object in milli
     * seconds.
     *
     * @return the media duration in ms
     */
    @Override
    public long getMediaDuration() {
    	return player.media().info().duration() - timeOffset;
    	/*
    	if (duration <= 0) {
    		if (player != null) {
    			if (origDuration == 0) {
    				origDuration = player.getLength();
    			}
    			duration = origDuration - timeOffset;
    		}
    	}
    	
    	return duration;
    	*/
    }

    /**
     * DOCUMENT ME!
     *
     * @param layoutManager DOCUMENT ME!
     */
    @Override
	public void setLayoutManager(ElanLayoutManager layoutManager) {
		this.layoutManager = layoutManager;
		
		if (isVisual) {
			if (playerComponent != null) {
				mouseAdapter = new VideoMouseAdapter(this, layoutManager, videoSurfaceComponent);
			} else if (callbackPlayerComponent != null) {
				mouseAdapter = new VideoMouseAdapter(this, layoutManager, callbackPlayerComponent.videoSurfaceComponent());
			}
		}
    }

    /**
     * Update labels of menu items etc.
     */
    @Override
	public void updateLocale() {
    	if (mouseAdapter != null) {
    		mouseAdapter.updateLocale();
    	}
    }
   
/*    
	@Override
	public void actionPerformed(ActionEvent e) {
        logger.log(Level.INFO, "action performed {0}", e);
		if (e.getSource().equals(detachItem) && (layoutManager != null)) {
            if (detached) {
                layoutManager.attach(getVisualComponent());
                detachItem.setText(ElanLocale.getString("Detachable.detach"));
                detached = false;
            } else {
                layoutManager.detach(getVisualComponent());
                detachItem.setText(ElanLocale.getString("Detachable.attach"));
                detached = true;
            }
        } else if (e.getSource() == infoItem) {
            new FormattedMessageDlg(this);
        } else if (e.getSource() == saveItem) {
        	ImageExporter export = new ImageExporter();
        	export.exportImage(player.snapshots().get(), mediaDescriptor.mediaURL, 
        			getMediaTime() + getOffset());// or get(width, height)
        	// VLC has a built-in method to save a snapshot, where it chooses its own name.
//        	player.saveSnapshot();
        } else if (e.getActionCommand().startsWith("ratio")) {
	        if (e.getSource() == origRatioItem) {
				aspectRatio = origAspectRatio;
			} else if (e.getSource() == ratio_1_1_Item) {
				aspectRatio = 1.00f;
			} else if (e.getSource() == ratio_5_4_Item) {
				aspectRatio = 1.25f;
			} else if (e.getSource() == ratio_4_3_Item) {
				aspectRatio = 1.33f;
			} else if (e.getSource() == ratio_16_10_Item) {
				aspectRatio = 1.60f;
			} else if (e.getSource() == ratio_16_9_Item) {
				aspectRatio = 1.77f;
			} else if (e.getSource() == ratio_221_1_Item) {
				aspectRatio = 2.21f;
			} else if (e.getSource() == ratio_235_1_Item) {
				aspectRatio = 2.35f;
			} else if (e.getSource() == ratio_239_1_Item) {
				aspectRatio = 2.39f;
			}
	        setAspectRatio(aspectRatio);
			layoutManager.doLayout();
			layoutManager.setPreference(("AspectRatio(" + mediaDescriptor.mediaURL + ")"), 
					Float.valueOf(aspectRatio), layoutManager.getViewerManager().getTranscription());
        } else if (e.getActionCommand().startsWith("zoom")) {
			if (e.getSource() == zoomOriginal) {
				videoScaleFactor = 0f;
			} else if (e.getSource() == zoom100) {
				videoScaleFactor = 1f;
			} else if (e.getSource() == zoom150) {
				videoScaleFactor = 1.5f;
			} else if (e.getSource() == zoom200) {
				videoScaleFactor = 2f;
			} else if (e.getSource() == zoom300) {
				videoScaleFactor = 3f;
			} else if (e.getSource() == zoom400) {
				videoScaleFactor = 4f;
			}
			player.video().setScale(videoScaleFactor);
			layoutManager.setPreference(("VideoZoom(" + mediaDescriptor.mediaURL + ")"), 
					Float.valueOf(videoScaleFactor), layoutManager.getViewerManager().getTranscription());
        } else if (e.getSource() == copyOrigTimeItem) {
			long t = getMediaTime() + timeOffset;
			String timeFormat = Preferences.getString("CurrentTime.Copy.TimeFormat", null);
			String currentTime = null;
			
	        if (timeFormat != null) {
	        	if (timeFormat.equals(Constants.HHMMSSMS_STRING)) {
	            	currentTime = TimeFormatter.toString(t);
	            } else if (timeFormat.equals(Constants.SSMS_STRING)) {
	            	currentTime = TimeFormatter.toSSMSString(t);
	            } else if (timeFormat.equals(Constants.NTSC_STRING)) {
	            	currentTime = TimeFormatter.toTimecodeNTSC(t);
	            } else if (timeFormat.equals(Constants.PAL_STRING)) {
	            	currentTime = TimeFormatter.toTimecodePAL(t);
	            } else if (timeFormat.equals(Constants.PAL_50_STRING)) {
	            	currentTime = TimeFormatter.toTimecodePAL50(t);
	            } else {
	            	currentTime = Long.toString(t);
	            }
	        } else {
	        	currentTime = Long.toString(t);
	        }
	        copyToClipboard(currentTime);
		}
	}
*/    

//    void printControls(Player player) {
//    }

    @Override
    public void cleanUpOnClose() {
    	if (player != null) {
    		player.controls().stop();
    		//player.events().removeMediaPlayerEventListener(listener);// necessary?
    	}
    	if (playerComponent != null) {
    		playerComponent.release();
    		playerComponent = null;
    	}
    	if (callbackPlayerComponent != null) {
    		callbackPlayerComponent.release();
    		callbackPlayerComponent = null;
    	}
    	if (audioPlayerComponent != null) {
    		audioPlayerComponent.release();
    		audioPlayerComponent = null;
    	}
    }


    @Override
	public void setSubVolume(float level) {
		subVolume = level;
	}

	@Override
	public float getSubVolume() {
		return subVolume;
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
	public void preferencesChanged() {
		// stub
		// retrieve framesSetpToFrameBegin value
	}
	
	private void checkMediaInfo(MediaPlayer mediaPlayer) {
		if (mediaPlayer != null) {
			if (origDuration > 0) {// add width and height
				return;// already detected
			}
			
			if (mediaPlayer.media().info().duration() <= 0) {
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("The media player is not initalized yet, duration <= 0");
				}
				return; // not fully initialized player
			}
			
			// extract info from the player
			StringBuffer sb = new StringBuffer("=== Media player info ===\n");
			MediaApi mediaApi = mediaPlayer.media();
			sb.append(String.format("Location: %s\n", mediaApi.info().mrl()));
			sb.append(String.format("Duration: %d ms\n", mediaApi.info().duration()));
			sb.append(String.format("Type: %s\n", mediaApi.info().type()));
			
			origDuration = mediaApi.info().duration();

			List<AudioTrackInfo> atiList = mediaApi.info().audioTracks();
			List<VideoTrackInfo> vtiList = mediaApi.info().videoTracks();
			numAudioTracks = atiList == null ? 0 : atiList.size();
			numVideoTracks = vtiList == null ? 0 : vtiList.size();
			sb.append(String.format("No. audio tracks: %d\n", numAudioTracks));
			sb.append(String.format("No. video tracks: %d\n", numVideoTracks));
			
			AudioApi audioApi = mediaPlayer.audio();
			if (audioApi != null && atiList != null && !atiList.isEmpty()) {
				sb.append("** Audio **\n");
				sb.append(String.format("Volume: %d\n", audioApi.volume()));
				sb.append(String.format("Is mute: %b\n", audioApi.isMute()));
				sb.append(String.format("Audio device: %s\n", audioApi.outputDevice()));
				sb.append(String.format("Current track: %d\n", audioApi.track()));
				if (atiList != null) {
					for (AudioTrackInfo ati : atiList) {
						sb.append(String.format("Audio track: %d\n", ati.id()));
						sb.append(String.format("\tCodec: %s (%s)\n", ati.codecName(), ati.codecDescription()));
						sb.append(String.format("\tBitrate: %d\n", ati.bitRate()));						
						sb.append(String.format("\tNo. channels: %d\n", ati.channels()));
						sb.append(String.format("\tLevel: %d\n", ati.level()));
						sb.append(String.format("\tRate: %d\n", ati.rate()));
					}
				}
			}
			// channels, track descriptions
			
			VideoApi videoApi = mediaPlayer.video();
			if (videoApi != null && vtiList != null && !vtiList.isEmpty()) {
				sb.append("** Video **\n");
				sb.append(String.format("Aspect ratio: %s\n", videoApi.aspectRatio()));// null
				sb.append(String.format("Scale: %f\n", videoApi.scale()));// 0.0
				Dimension vidDim = videoApi.videoDimension();
				if (vidDim != null && vidDim.width > 0) {
					sb.append(String.format("Dimension: %d, %d\n", vidDim.width, vidDim.height));
					videoSize = new Dimension(vidDim);
					origAspectRatio = videoSize.width / (float)videoSize.height;
				}
				sb.append(String.format("Crop geom.: %s\n", videoApi.cropGeometry()));// null
				if (vtiList != null) {
					for (VideoTrackInfo vti : vtiList) {
						if (firstVideoTrackInfo == null) {
							firstVideoTrackInfo = vti;
							if ("mpgv".equals(vti.codecName())) {
								isMpgvCodec = true;
							}
							isVisual = true;
							//hasVideo = true;
						}
						sb.append(String.format("Video track: %d\n", vti.id()));
						sb.append(String.format("\tCodec: %s (%s)\n", vti.codecName(), vti.codecDescription()));
						sb.append(String.format("\tBitrate: %d\n", vti.bitRate()));						
						sb.append(String.format("\tFrame rate: %d\n", vti.frameRate()));
						sb.append(String.format("\tFrame rate base: %d\n", vti.frameRateBase()));
						sb.append(String.format("\tDimension: %d, %d\n", vti.width(), vti.height()));
						sb.append(String.format("\tSpherical: field of view: %f, pitch: %f, roll: %f, yaw: %f\n",
								vti.fov(), vti.pitch(), vti.roll(), vti.yaw()));
						VideoProjection vidProj = vti.projection();
						if (vidProj != null) {
							sb.append(String.format("\tProjection: %s\n", vidProj.toString()));
							// if projection is EQUIRECTANGULAR it seems to be a supported spherical or 360 degrees video
							// otherwise RECTANGULAR is returned
							if (VideoProjection.EQUIRECTANGULAR == vidProj) {
								isSpherical = true;
							}
						}
						
						if (vti.frameRate() > 0 && msPerFrame <= 0) {
							double fr = vti.frameRateBase() / (double) vti.frameRate();
							msPerFrame = 1 / fr;
							frameRateAutoDetected = true;
						}
					}
				}
			}
//			System.out.println(videoApi.brightness());
//			System.out.println(videoApi.contrast());
//			System.out.println(videoApi.gamma());
//			System.out.println(videoApi.hue());
//			System.out.println(videoApi.saturation());

			if (logger.isLoggable(Level.INFO)) {
				logger.info(sb.toString());
			}
		}
	}
/*	
	protected class PlayerEventAdapter implements  MediaPlayerEventListener {
//	MediaPlayerEventListener infoListener = new MediaPlayerEventListener() {
	    @Override
		public void opening(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "opening");
	    	}
	        //checkMediaInfo(mediaPlayer);
	    }
	
	    @Override
		public void buffering(MediaPlayer mediaPlayer,
	            float newCache) {
	    	if (logger.isLoggable(Level.FINER)) {
	    		logger.log(Level.FINER, "buffer");
	    	}
	    }
	
	    @Override
		public void playing(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "playing");
	    	}
	    }
	
	    @Override
		public void paused(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "paused");
	    	}
	    }
	
	    @Override
		public void stopped(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "stopped");
	    	}
	    }
	
	    @Override
		public void forward(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "forward");
	    	}
	    }
	
	    @Override
		public void backward(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "backward");
	    	}
	    }
	
	    @Override
		public void finished(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "finished");
	    	}
	        // Make sure that a StopEvent gets sent (by the PeriodicUpdateController).
	        VLCJ4MediaPlayer.this.stop(); // maybe do somewhere else
	        // It seems that after ending the media, it can't be played anymore.
	        // Re-preparing the media seems rather a sledgehammer approach.
	        //VLCJ4MediaPlayer.this.player.stop();
	        //VLCJ4MediaPlayer.this.player.prepareMedia(VLCJMediaPlayer2.this.getMediaDescriptor().mediaURL);
	    }
	
	    @Override
		public void timeChanged(MediaPlayer mediaPlayer,
	            long newTime) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "time changed {0}", newTime);
	    	}
	    }
	
	    @Override
		public void positionChanged(MediaPlayer mediaPlayer,
	            float newPosition) {
	    	if (logger.isLoggable(Level.FINER)) {
	    		logger.log(Level.FINER, "position changed {0}", newPosition);
	    	}
	    }
	
	    @Override
		public void seekableChanged(MediaPlayer mediaPlayer,
	            int newSeekable) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "seekable changed {0}", newSeekable);
	    	}
	    }
	
	    @Override
		public void pausableChanged(MediaPlayer mediaPlayer,
	            int newSeekable) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "pausable changed {0}", newSeekable);
	    	}
	    }
	
	    @Override
		public void titleChanged(MediaPlayer mediaPlayer,
	            int newTitle) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "title changed {0}", newTitle);
	    	}
	    }
	
	    @Override
		public void snapshotTaken(MediaPlayer mediaPlayer,
	            String filename) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "snapshot taken {0}", filename);
	    	}
	    }
	
	    @Override
		public void lengthChanged(MediaPlayer mediaPlayer,
	            long newLength) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "length changed {0}", newLength);
	    	}
	    }
	
	    @Override
		public void videoOutput(MediaPlayer mediaPlayer,
	            int newCount) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "video output changed {0}", newCount);
	    	}
	    }
	
	    @Override
		public void error(MediaPlayer mediaPlayer) {
	    	if (logger.isLoggable(Level.WARNING)) {
	    		logger.log(Level.WARNING, "error");
	    	}
	    }
	
	    @Override
	    public void scrambledChanged(MediaPlayer mp, int i1) {
	    	if (logger.isLoggable(Level.FINE)) {
	    		logger.log(Level.FINE, "scrambled changed {0}", i1);
	    	}
	    }
	
		@Override
		public void audioDeviceChanged(MediaPlayer arg0, String arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "audio device changed {0}", arg1);
			}
		}
	
		@Override
		public void chapterChanged(MediaPlayer arg0, int arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "chapter changed {0}", arg1);
			}
		}
	
		@Override
		public void corked(MediaPlayer arg0, boolean arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "player corked {0}", arg1);
			}
		}
	
		@Override
		public void elementaryStreamAdded(MediaPlayer arg0, TrackType arg1, int arg2) {
			if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "elementary stream added {0}", arg1);
			}
		}
	
		@Override
		public void elementaryStreamDeleted(MediaPlayer arg0, TrackType arg1, int arg2) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "elementary stream deleted {0}", arg1);
			}
		}
	
		@Override
		public void elementaryStreamSelected(MediaPlayer arg0, TrackType arg1, int arg2) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "elementary stream selected {0}", arg1);
			}
		}
	
		@Override
		public void mediaChanged(MediaPlayer mediaPlayer, MediaRef arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "media changed {0}", arg1);
			}
		}
	
		@Override
		public void mediaPlayerReady(MediaPlayer mediaPlayer) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "media player ready");
			}
		}
	
		@Override
		public void muted(MediaPlayer arg0, boolean arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "media muted {0}", arg1);
			}
		}
	
		@Override
		public void volumeChanged(MediaPlayer arg0, float arg1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "media volume changed {0}", arg1);
			}
		}
	}
*/
	/**
	 * A timer for calculating time progress within a loaded buffer of video 
	 * when the player is playing. The native player seems to return the time of the
	 * first frame in the buffer, regardless of which frame is visible 
	 * (so not the current media time).   
	 */
	private class BufferTimer {
		private long curZeroTime = 0L;
		private long curLapsedTime = 0L;
		private float tickRate = 1.0f; //playback rate, between 0.0 and 2.0
		private boolean started = false;
		
		void reset() {
			curZeroTime = System.currentTimeMillis();
			curLapsedTime = 0L;
			started = true;
		}
		void pause() {
			started = false;
		}
		void pauseReset() {
			curLapsedTime = 0L;
			started = false;
		}
		void setRate(float rate) {
			tickRate = rate;
		}
		
		long getTime() {
			if (!started) {
				return curLapsedTime;
			}
			long d = System.currentTimeMillis() - curZeroTime;
			curLapsedTime = (long) (tickRate * d);
			return curLapsedTime;
		}
	}
// ###  Video Scale and Move  #########################################################################################
	int vdx, vdy, vdx2, vdy2;
	// scaled video width and height, combines the scaling based on the size of the video component
	// and the user selected zoom level
	int svw, svh;
	
	
	/**
	 * @return the current zoom level
	 */
	@Override
	public float getVideoScaleFactor() {
		return videoScaleFactor;
	}

	/**
	 * Instead of using VideoApi.setScale(), this method uses the
	 * VideoApi.setCropGeometry() for specifying which part of the image should
	 * be visible, depending on zoom level and panning (location of the 
	 * "viewport" relative to the image. The geometry values are coordinates
	 * in the "pixel space" of the original video image (0,0 - w,h).
	 * The native part of VLC then take care of projecting that part of the 
	 * image onto the visual component (incorporating its size).
	 * 
	 */
	@Override
	public void setVideoScaleFactor(float scaleFactor) {
		videoScaleFactor = scaleFactor;
		// get current view port
		if (isSpherical) {
			if (videoScaleFactor == 1) {
				// reset
				Viewpoint vp = player.video().newViewpoint();
				vp.setFov(80.0f);
				player.video().updateViewpoint(vp, true);
				vp.release();
			} else {
				Viewpoint vp = player.video().newViewpoint();
				// the following represents zooming in and out with two 
				// different step sizes
				if (videoScaleFactor == 1.5f) {
					vp.setFov(5.0f);
				} else if (videoScaleFactor == 2.0f) {
					vp.setFov(10.0f);
				} else if (videoScaleFactor == 3.0f) {
					vp.setFov(-5.0f);
				} else if (videoScaleFactor == 4.0f) {
					vp.setFov(-10.0f);
				}

				player.video().updateViewpoint(vp, false);
				vp.release();
			}
		} else {
			if (videoScaleFactor == 1) {
				//player.video().setScale(0);// means fill the component
				vdx = vdy = 0;
				if (svw == 0) {
					svw = (int)videoSize.width;
					svh = (int)videoSize.height;
				}
				vdx2 = svw;
				vdy2 = svh;
				player.video().setCropGeometry(String.format("%dx%d+%d+%d", vdx2, vdy2, vdx, vdy));
			} else {
				// the pixel in the center
				int cx = (vdx + vdx2) / 2;
				int cy = (vdy + vdy2) / 2;

				if (svw == 0) {
					svw = (int)videoSize.width;
					svh = (int)videoSize.height;
				}
				// first, center relative to the component
				vdx = (int) ((svw - (svw / videoScaleFactor)) / 2);
				vdy = (int) ((svh - (svh / videoScaleFactor)) / 2);
				
				vdx2 = vdx + (int) (svw / videoScaleFactor);
				vdy2 = vdy + (int) (svh / videoScaleFactor);
				// reposition to keep the pixel that was in the center before,  
				// in the center, if possible
				if (cx != 0) {
					// translation from image center to 'old' center
					int dx = (svw / 2) - cx;
					int dy = (svh / 2) - cy;
					
					if (vdx - dx < 0) {
						vdx = 0;
						vdx2 = (int) (svw / videoScaleFactor);
					} else {
						if (vdx2 - dx > svw) {
							vdx2 = svw;
							vdx = vdx2 - (int) (svw / videoScaleFactor);
						} else {
							vdx -= dx;
							vdx2 -= dx;
						}
					}
					
					if (vdy - dy < 0) {
						vdy = 0;
						vdy2 = (int) (svh / videoScaleFactor);
					} else {
						if (vdy2 - dy > svh) {
							vdy2 = svh;
							vdy = vdy2 - (int) (svh / videoScaleFactor);
						} else {
							vdy -= dy;
							vdy2 -= dy;
						}
					}
				}

				// don't set scale, rely on crop geometry
				// player.video().setScale(rescaleFactor);

				player.video().setCropGeometry(String.format("%dx%d+%d+%d", vdx2, vdy2, vdx, vdy));
			}
		}
	}

	@Override
	public void repaintVideo() {
		// stub
	}

	/**
	 * This method is expected to return the coordinates of the scaled video
	 * image relative to the visual component (the "viewport"). If the zoom 
	 * level is 1 the bounds are identical to the component's bounds (0,0,w,h).
	 * If the zoom level > 1 the video bounds is a larger rectangle, and x and
	 * y or <= 0. 
	 * Since the returned bounds is relative to the (size of) the visual 
	 * component, this method cannot rely on the calculated viewport 
	 * coordinates but has to correct for the size of the visual component.
	 * 
	 *  @return an int array of size 4
	 */
	@Override
	public int[] getVideoBounds() {
		if (videoScaleFactor == 1) {
			if (logger.isLoggable(Level.FINE)) {
				logger.fine(String.format("getVideoBounds: %d, %d, %d, %d", 0, 0, 
						videoSurfaceComponent.getWidth(), 
						videoSurfaceComponent.getHeight()));
			}
			return new int[] {0, 0, videoSurfaceComponent.getWidth(), 
					videoSurfaceComponent.getHeight()};
		} else {
			if (logger.isLoggable(Level.FINE)) {
				logger.fine(String.format("getVideoBounds: %d, %d, %d, %d", vdx, vdy, svw, svh));
			}
			// recalculate the virtual video image bounds based on the visual
			// component's size and the current values of vdx, vdx2, vdy, vdy2, svw and svh
			if (vdx2 - vdx != 0) {
				int vscWidth = playerComponent.videoSurfaceComponent().getWidth();
				int vscHeight = playerComponent.videoSurfaceComponent().getHeight();
				float ratioX = vscWidth / (svw / videoScaleFactor);
				float ratioY = vscHeight / (svh / videoScaleFactor);
//				float ratioX2 = vscWidth / (float)(vdx2 - vdx);
//				float ratioY2 = vscHeight / (float)(vdy2 - vdy);
				
				int x = (int) (ratioX * vdx);
				int y = (int) (ratioY * vdy);
				int w = (int) (ratioX * svw);
				int h = (int) (ratioY * svh);
				
				return new int[] {-x, -y, w, h};
			}
			return new int[] {-vdx, -vdy, svw, svh};
		}
		
	}

	/**
	 * Not implemented. When zoomed in, setting the video bounds is performed 
	 * in {@link #moveVideoPos(int, int)} and {@link #setVideoScaleFactor(float)}.
	 */
	@Override
	public void setVideoBounds(int x, int y, int w, int h) {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine(String.format("setVideoBounds: %d, %d, %d, d", x, y, w, h));
		}
	}

	/**
	 * When zoomed in, the "viewport" to the scaled video image is
	 * updated by changing the "crop geometry" of the VideoApi.
	 * 
	 * In case of "spherical" or 360 degrees videos, the parameters are
	 * used for changing the yaw (rotation around vertical axis, left-right)
	 * and pitch (rotation around the horizontal axis, up-down) of the
	 * viewpoint.
	 * 
	 * @param dx the number of pixels to move the image along the x-axis
	 * @param dy the number of pixels to move the image along the y-axis
	 */
	@Override
	public void moveVideoPos(int dx, int dy) {
		if (videoScaleFactor > 1 && !isSpherical) {
			if (dx == 0 && dy == 0) {
				return;
			}
			if (vdx - dx >= 0 && vdx2 - dx <= svw) {
				vdx -= dx;
				vdx2 -= dx;
			}
			if (vdy - dy >= 0 && vdy2 - dy <= svh) {
				vdy -= dy;
				vdy2 -= dy;
			}
			
			player.video().setCropGeometry(String.format("%dx%d+%d+%d", vdx2, vdy2, vdx, vdy));
			
		} else if (isSpherical){//assume spherical video and modify yaw and pitch
			Viewpoint vp = player.video().newViewpoint();
			vp.setYaw(-dx * 0.3f);// slow down the movement a bit
			vp.setPitch(-dy * 0.3f);
			player.video().updateViewpoint(vp, false);			
		}
	}
//####### VideoFrameGrabber
	/**
	 * Uses the SnapshotApi to get the current image.
	 * The native facility to save the image in a designated location
	 * isn't used (yet).
	 * 
	 * @return the current image in it's original size
	 */
	@Override
	public Image getCurrentFrameImage() {
		if (isVisual) {
			return player.snapshots().get();		
		}
		return null;
	}

	@Override
	public Image getFrameImageForTime(long time) {
		if (isVisual) {
			setMediaTime(time);
			// might need to wait
			return player.snapshots().get();
		}
		return null;
	}

}
