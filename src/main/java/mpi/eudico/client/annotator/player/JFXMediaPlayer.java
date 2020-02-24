package mpi.eudico.client.annotator.player;

import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import nl.mpi.jfx.JFXVideoPanel;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaMarkerEvent;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.media.Track;
import javafx.scene.media.VideoTrack;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.util.Duration;
import mpi.eudico.client.annotator.ElanLayoutManager;
import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.export.ImageExporter;
import mpi.eudico.client.annotator.gui.FormattedMessageDlg;
import mpi.eudico.client.mediacontrol.ControllerManager;
import mpi.eudico.server.corpora.clomimpl.abstr.MediaDescriptor;
import mpi.eudico.util.TimeFormatter;


/**
 * Implementation of an ELAN player based on JavaFX (as included in Java 1.8)
 * Support for media types is very limited (.mp4, .flv, .wav, .mp3). Start/stop
 * behavior and frame forward/backward indicate that JavaFX has not been designed
 * for the kind of precision one would want in an annotation application.
 * 
 * Embedding of a JavaFX Scene/Node in a Swing application depends on the 
 * javafx.embed.swing package. JavaFX has a different threading model than Swing 
 * and some calls have to be made on a JavaFX thread, some operations are asynchronous.  
 * 
 * @author Han Sloetjes 
 */
public class JFXMediaPlayer extends ControllerManager implements ElanMediaPlayer, 
ActionListener, VideoFrameGrabber {
	private MediaDescriptor mediaDescriptor;
	// JFX objects
	private Media media;
	private MediaPlayer mediaPlayer;
	private JFXVideoPanel jfxVideoPanel;
	private MediaView mediaView;
	private ElanLayoutManager layoutManager;
	private long offset = 0L;
	private long intervalStopTime;
	private MarkerHandler intervalMarkerHandler;
	private IntervalStopHandler intervalStopHandler;
	private int intervalSleepTime = 40;
	// maybe use the isInited because of the asynchronous behavior of the JavaFX player?
	private boolean isInited = false;
	private float cachedVolume = 1.0f;
	private float cachedRate = 1.0f;
	private float curSubVolume;
	private boolean mute;
	private long duration;// media duration minus offset
	private long origDuration;// the original media duration
	private float origAspectRatio = 0;
	private float aspectRatio = 0;
	// that is, audio only player
	private boolean isWavPlayer = true;
	
	// gui
    private JPopupMenu popup;
    private JMenuItem durationItem;
    protected JMenuItem detachItem;
    private JMenuItem infoItem;
    private JMenuItem saveItem;
	
	/** if true frame forward and frame backward always jump to the begin
	 * of the next/previous frame, otherwise it jumps with the frame duration */
	private boolean frameStepsToFrameBegin = false;
    private double milliSecondsPerSample;
    private boolean frameRateAutoDetected = false;
    private boolean detached;
    
    private final String SEL_STOP = "SEL_STOP";
    private final static Logger LOG = Logger.getLogger("JFX");
	
    static {
		/* Sets the JFX Implicit Exit flag to false. Otherwise removal of (the last) JFXPanel
		 * from the Swing hierarchy leads to crashes when e.g. setMediaTime is called later (e.g.
		 * after "Detaching" or "Attaching" the visual component
		 * or when a new Player is created after closing the last player (in ELAN's empty frame).
		 *  
		 */
    	try {
    		Platform.setImplicitExit(false);
    	} catch (Throwable t) {
    		LOG.warning("Could not set the JFX Runtime flag for Implicit Exit to false");
    	}
    }
    
    /**
     * 
     * @param mediaDescriptor
     */
	public JFXMediaPlayer(MediaDescriptor mediaDescriptor) throws NoPlayerException {
		super();
		if (mediaDescriptor == null) {
			throw new NoPlayerException("The media descriptor is null");
		}
		this.mediaDescriptor = mediaDescriptor;
		offset = mediaDescriptor.timeOrigin;
		final String mediaURLString;
		try {
			mediaURLString = toSupportedMediaURLString(mediaDescriptor.mediaURL);
		} catch (Exception ex) {// any of the declared exceptions
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not create a valid URL for the media: " + ex.getMessage());
			}
			throw new NoPlayerException(ex.getMessage());
		}
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("JFXMediaPlayer URL: " + mediaURLString);
		}
		// Creating the JFXPanel initializes the JFX runtime and toolkit, so always create
		// the panel even if the player is (will be) audio only
		jfxVideoPanel = new JFXVideoPanel();		
				
		try {
			// Sets the JFX Implicit Exit flag to false. Otherwise removal of (the last) JFXPanel
			// from the Swing hierarchy leads to crashes when e.g. setMediaTime is called later 
			// or when a new Player is created after closing the last player 
			//Platform.setImplicitExit(false);
			media = new Media(mediaURLString);
			if (media.getError() != null) {// synchronous error
				// log
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("JFXMediaPlayer error: " + media.getError().getMessage());
				}
				//??media.getError().printStackTrace();
				throw new NoPlayerException("JFXMediaPlayer cannot handle the file: " + media.getError().getMessage());
			} else {
				// could add a listener for an asynchronous error
				/*
	            media.setOnError(new Runnable() {
	                public void run() {
	                    // Handle asynchronous error in Media object??
	                }
	            });
	            */
			}
		} catch (MediaException me) {
			// log
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("JFXMediaPlayer Media Exception: " + me.getMessage());
			}
			//me.printStackTrace();
			throw new NoPlayerException("JFXMediaPlayer cannot handle the file: " + me.getMessage());
		}
		// create the media player
		try {
			mediaPlayer = new MediaPlayer(media);
			//test show that this doesn't work reliably, repeatedly			
			mediaPlayer.setOnEndOfMedia(() -> {
				handleEndOfMedia();// try to prevent the player to get into a STOPPED status
			});
			
			if (mediaPlayer.getError() != null) {
				// log
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("JFXMediaPlayer error: " + mediaPlayer.getError().getMessage());
				}
				//mediaPlayer.getError().printStackTrace();
				throw new NoPlayerException("JFXMediaPlayer cannot handle the file: " + mediaPlayer.getError().getMessage());
			} else {
				// could add a listener for an asynchronous error
				/*
				mediaPlayer.setOnError(new Runnable() {
	                public void run() {
	                    // Handle asynchronous error in Media object??
	                }
	            });
	            */
			}
// tests show that using an event marker for the end of play selection doesn't work very well either
//			intervalMarkerHandler = new MarkerHandler();
//			mediaPlayer.setOnMarker(intervalMarkerHandler);
		} catch (MediaException me) {
			// log
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("JFXMediaPlayer Media Exception: " + me.getMessage());
			}
			me.printStackTrace();
			throw new NoPlayerException("JFXMediaPlayer cannot handle the file: " + me.getMessage());
		}
		
		// if media is ok and media player too, create a view
		// Media metadata and tracks are only available once the player has reached the READY status
		// could register listeners to the Metadata map and to the Tracks list of the Media object
		// or add a listener to the READY property of the player, e.g.
		mediaPlayer.setOnReady(() -> {
			// seems to be called only once
			 for (Track tr : mediaPlayer.getMedia().getTracks()) {
				 if (tr instanceof VideoTrack) {
					 isWavPlayer = false;
				 }
			 }
			 // maybe extract metadata from the Media, in case of flv video?
			 printMediaInfo(mediaPlayer.getMedia());
			 
			 if (!isWavPlayer) {
				MouseHandler mh = new MouseHandler();
				jfxVideoPanel.addMouseListener(mh);
				jfxVideoPanel.addMouseMotionListener(mh);
				
				// create and add a Scene on the JFX thread
			    Platform.runLater(new Runnable() {
			    	@Override
				    public void run() {
			    		try {
			    			LOG.info("Setting up the Scene.");
			    			addScene(mediaPlayer);
			    		} catch (MediaException me) {
				    		LOG.warning("Add Scene Media Exception: " + me.getMessage());
				    	}
				    }
				 });
			 } else {
				 isInited = true;
			 }
			 // try to prevent reaching the end of the media
			 mediaPlayer.setStopTime(new Duration(mediaPlayer.getMedia().getDuration().toMillis() - 220));
		});
	}
	
	/**
	 * Create a MediaView and a Scene and add it to the JFXPanel.
	 * 
	 * @param player the video player to create the scene for
	 */
	private void addScene(MediaPlayer player) {
		mediaView = new MediaView(mediaPlayer);
        Group root = new Group();
        Scene scene = new Scene(root/*, javafx.scene.paint.Color.ALICEBLUE*/);
        root.getChildren().add(mediaView);
        jfxVideoPanel.setScene(scene);
        isInited = true;
	}
	
	/**
	 * Performs a check on the protocol of the url. Default is "file".
	 * 
	 * @param mediaURL the provided url string
	 * @return a url as string as supported by JFX
	 * @throws URISyntaxException 
	 * @throws MalformedURLException 
	 * @throws ProtocolException 
	 */
	private String toSupportedMediaURLString(String mediaURLString) throws URISyntaxException, 
		MalformedURLException, ProtocolException {
		
		URI mediaURI = new URI(mediaURLString);
		URL mediaURL = mediaURI.toURL();
		// the protocol should be file or http (HLS is http Live Streaming)
		if (!mediaURL.getProtocol().equalsIgnoreCase("file") && 
				!mediaURL.getProtocol().equalsIgnoreCase("http")) {
			throw new ProtocolException("JFX Media only supports the FILE and HTTP protocols, not: " 
				+ mediaURL.getProtocol());
		}
		return mediaURL.toString();
	}
	
	/**
	 * Prints some information on the media and extracts the fps if available.
	 * 
	 * @param media the media
	 */
	private void printMediaInfo(Media media) {
		StringBuilder sb = new StringBuilder("Media Information:\n");
		if (media.getMetadata().size() > 0) {
			for(Map.Entry<String, Object> entry : media.getMetadata().entrySet()) {
				sb.append(entry.getKey() + ": " + entry.getValue() + "\n");
				if (entry.getKey().equals("framerate")) {
					if (entry.getValue() instanceof Double) {
						milliSecondsPerSample = 1 / (Double) entry.getValue();
						frameRateAutoDetected = true;
					}
				}
			}
		}
		for (Track track : media.getTracks()) {
			sb.append("Track: " + track.getTrackID() + " " + track.getName() + "\n");
			for(Map.Entry<String, Object> entry : track.getMetadata().entrySet()) {
				sb.append(entry.getKey() + ": " + entry.getValue() + "\n");
			}
		}
		LOG.info(sb.toString());
	}

	@Override
	public void preferencesChanged() {
		

	}

	@Override
	public MediaDescriptor getMediaDescriptor() {
		return mediaDescriptor;
	}

	@Override
	public void start() {
		if (mediaPlayer != null) {
			if (mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED || 
					mediaPlayer.getStatus() == MediaPlayer.Status.READY) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Play Request Time: " + getMediaTime());
				}
				startControllers();
				mediaPlayer.play();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Started At: " + getMediaTime());
				}
//				startControllers();
			} else {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot start player, status is: " + mediaPlayer.getStatus());
				}
			}
			// prevent from reaching the end of the media file
			//mediaPlayer.setStopTime(new Duration(mediaPlayer.getMedia().getDuration().toMillis() - 200));
		}
	}

	/**
	 * This corresponds to JFX pause()
	 */
	@Override
	public void stop() {
		if (mediaPlayer != null) {
			if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Pause Request Time: " + getMediaTime());
				}
				if (intervalStopHandler != null) {
					intervalStopHandler.setStopped();
					intervalStopHandler = null;
				}
				long curTime = getMediaTime();
				stopControllers();
				mediaPlayer.pause();
				//LOG.info("Paused At: " + getMediaTime());
//				stopControllers();
				long st = System.currentTimeMillis();
				while (mediaPlayer.getStatus() != MediaPlayer.Status.PAUSED) {
//					LOG.info("Waiting for paused state");
					try {
						Thread.sleep(10);
					} catch (InterruptedException ie) {
					}
					if (System.currentTimeMillis() - st > 2000) {
						break;// log
					}
				}
				if (LOG.isLoggable(Level.FINE)) {
					LOG.info("Paused At: " + getMediaTime());
				}
//				setControllersMediaTime(getMediaTime());
				setMediaTime(curTime);
			} else {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot pause player, status is: " + mediaPlayer.getStatus());
				}
			}
		}
	}

	@Override
	public boolean isPlaying() {
		if (mediaPlayer != null) {
			// when the media player reaches the end-of-media the status remains PLAYING
			// unless this event is handled
			return mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
		}
		return false;
	}

	@Override
	public void playInterval(long startTime, long stopTime) {
		if (mediaPlayer != null) {
			if (isPlaying()) {
				mediaPlayer.pause();
			}

			setStopTime(stopTime);
			
			if (getMediaTime() != startTime) {// already corrected for offset?
				mediaPlayer.seek(new Duration(startTime + offset));
				// wait//??
			}
			startInterval();
		}
	}

	/**
	 * Assumes everything is setup all right; start time marker, stop time marker
	 */
	void startInterval() {
		if (mediaPlayer != null) {
			if (intervalStopHandler != null && intervalStopHandler.isAlive()) {
				intervalStopHandler.setStopped();
			}
			intervalStopHandler = new IntervalStopHandler(intervalStopTime, intervalSleepTime);
			mediaPlayer.play();
			startControllers();
			intervalStopHandler.start();
		}
	}
	
	@Override
	public void setStopTime(long stopTime) {
		this.intervalStopTime = stopTime;
		if (mediaPlayer != null) {

			double msps = getMilliSecondsPerSample();
			if (msps != 0.0) {
		        long nFrames = (long) ((intervalStopTime + offset) / msps);
		
		        if ((long) Math.ceil(nFrames * msps) == (intervalStopTime + offset)) { // on a frame boundary
		            intervalStopTime += 1;
		        }
			}
			// this requires a marker listener
			//media.getMarkers().put(SEL_STOP, new Duration(intervalStopTime + offset));			
			// or this: but this is tricky if it is not reset in time to duration 
			//mediaPlayer.setStopTime(new Duration(intervalStopTime + offset));
		}
		
		setControllersStopTime(this.intervalStopTime);
	}
	
	/**
	 * Handles End-of-Media (which at the end of the file is problematic, it doesn't
	 * pause the player and seems to leave the player in a unstable state) and currently
	 * the play selection is also based on this, the stop time.
	 * For some reason either play or pause seems to reset the stop time; if play selection
	 * is stopped before reaching the stop time, it is possible to play beyond the stop time
	 * after that (don't know why).
	 * An alternative for play selection would be to set a marker at the stop time (selection
	 * end time), but then one can observe some 200 ms overshoot (like when clicking the pause
	 * button. 
	 * 
	 * The code below almost works, only the end-of-media event is only received every other
	 * time. No idea why.
	 */
	private void handleEndOfMedia() {
//		System.out.println("End of media");
		LOG.warning("Player status is: " + mediaPlayer.getStatus());

		mediaPlayer.pause();
		stopControllers();
		// perform a seek otherwise the player will start (continue) playing after
		// a manual seek (setMediaTime).
		mediaPlayer.seek(mediaPlayer.getCurrentTime());
		mediaPlayer.pause();

//		mediaPlayer.setStopTime(new Duration(origDuration));
//		setControllersStopTime(duration);
	}

	@Override
	public void setOffset(long offset) {
		long diff = this.offset - offset;
        this.offset = offset;
        mediaDescriptor.timeOrigin = offset;

        if (mediaPlayer != null) {
        	if (origDuration == 0) {
        		origDuration = (long) mediaPlayer.getTotalDuration().toMillis();
        	}
        	duration = origDuration - offset;
        }
		// update the player given the current media time
        // update the play selection stop time??
		setStopTime(intervalStopTime + diff);
	}

	@Override
	public long getOffset() {
		return offset;
	}

	@Override
	public void nextFrame() {
		// the JFX Media Player doesn't have a "next frame" method
		// and only for FLash video the sample rate is in the metadata
		if (mediaPlayer != null) {
			if (isPlaying()) {
				stop();
			}
			// wait...?
			double currentMediaTime = mediaPlayer.getCurrentTime().toMillis();
			double nextMediaTime;
			if (frameStepsToFrameBegin) {
				long curFrame = (long) (currentMediaTime / getMilliSecondsPerSample());
				nextMediaTime = (curFrame + 1) * getMilliSecondsPerSample() + 1;// add a millisecond
			} else {
				nextMediaTime = currentMediaTime + getMilliSecondsPerSample();
			}
			
			if (nextMediaTime < getMediaDuration()) {
				mediaPlayer.seek(new Duration(nextMediaTime));
				setControllersMediaTime((long) nextMediaTime);
			} else {
				mediaPlayer.seek(new Duration(getMediaDuration()));
				setControllersMediaTime((long) getMediaDuration());
			}
		}
	}

	@Override
	public void previousFrame() {
		// the JFX Media Player doesn't seem to have a "previous frame" method
		if (mediaPlayer != null) {
			if (isPlaying()) {
				stop();
			}
			
			double currentMediaTime = mediaPlayer.getCurrentTime().toMillis();
			double nextMediaTime;
			
			if (frameStepsToFrameBegin) {
				long curFrame = (long) (currentMediaTime / getMilliSecondsPerSample());
				nextMediaTime = (curFrame - 1) * getMilliSecondsPerSample() + 1;// add a millisecond
			} else {
				nextMediaTime = currentMediaTime - getMilliSecondsPerSample();
			}
			
			if (nextMediaTime >= 0) {
				mediaPlayer.seek(new Duration(nextMediaTime));
				setControllersMediaTime((long) nextMediaTime);
			} else {
				mediaPlayer.seek(new Duration(0));
				setControllersMediaTime(0);
			}
		}
	}

	@Override
	public void setFrameStepsToFrameBegin(boolean stepsToFrameBegin) {
		frameStepsToFrameBegin = stepsToFrameBegin;
	}

	@Override
	public void setMediaTime(long time) {
		if (mediaPlayer != null) {
			if (isPlaying()) {
				mediaPlayer.pause();
				// wait?
			}
			
			mediaPlayer.seek(new Duration(time + offset));
			
			setControllersMediaTime(time);
		}
	}

	@Override
	public long getMediaTime() {
		if (mediaPlayer != null) {
			return (long) mediaPlayer.getCurrentTime().toMillis() - offset;
		}
		return 0;
	}

	@Override
	public void setRate(float rate) {
		if (!isInited) {
			cachedRate = rate;
		}
		if (mediaPlayer != null) {
			mediaPlayer.setRate(rate);
		}
		setControllersRate(rate);
	}

	@Override
	public float getRate() {
		if (mediaPlayer != null) {
			return (long) mediaPlayer.getRate();
		}
		return 0;
	}

	/**
	 * Java FX only seems to detect the frame-rate of .flv files automatically
	 */
	@Override
	public boolean isFrameRateAutoDetected() {
		return frameRateAutoDetected;
	}

	@Override
	public long getMediaDuration() {
		if (duration <= 0) {
			if (media != null) {
				if (origDuration == 0) {
					origDuration = (long) media.getDuration().toMillis();				
				}
				duration = origDuration - offset;
			}
		}
		return duration;
	}

	@Override
	public float getVolume() {
		if (mediaPlayer != null) {
			return (float) mediaPlayer.getVolume();
		}
		return 0f;
	}

	@Override
	public void setVolume(float level) {
		if (!isInited) {
			cachedVolume = level;
		}
		if (mediaPlayer != null) {
			mediaPlayer.setVolume(level);
		}

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
		if (this.layoutManager != null) {
			detached = !(this.layoutManager.isAttached(this));
		}
	}

	/**
	 * Returns the video JFXPanel or null (in case of audio)
	 */
	@Override
	public Component getVisualComponent() {
		if (isInited) {
			if (isWavPlayer) {
				return null;
			} else {
				return jfxVideoPanel;
			}
		} else {
			long curTime = System.currentTimeMillis();
			while (!isInited) {
				try {
					Thread.sleep(20);
				} catch (InterruptedException ie){}
				if (System.currentTimeMillis() > curTime + 4000) {
					break;
				}
			}
			//just return the panel, initialized or not
			return jfxVideoPanel;
		}		
	}

	@Override
	public int getSourceWidth() {
		if (media != null) {// && !isWavPlayer?
			return media.getWidth();
		}
		return 0;
	}

	@Override
	public int getSourceHeight() {
		if (media != null) {// && !isWavPlayer?
			return media.getHeight();
		}
		return 0;
	}

	/**
	 * Allow custom aspect ratio?
	 */
	@Override
	public float getAspectRatio() {
		if (aspectRatio != 0) {
			return aspectRatio;
		}
		// initialization of some fields
		if (media != null) {
			int h = media.getHeight();
			if (h != 0) {
				origAspectRatio = media.getWidth() / (float) h;
				aspectRatio = origAspectRatio;
				return aspectRatio;
			}
		}
		return 0;// or 1 ??
	}

	/**
	 * Allow setting a custom aspect ratio? In that case set the preserve ratio
	 * property to false.
	 */
	@Override
	public void setAspectRatio(float aspectRatio) {
		if (mediaView != null) {
			mediaView.setPreserveRatio(false);
		}
		this.aspectRatio = aspectRatio;
	}

	/**
	 * Java FX only provides frame rate metadata for .flv (VP6 Video) files.
	 * So in the current implementation this will probably return the default
	 * value of 40 ms.
	 */
	@Override
	public double getMilliSecondsPerSample() {
		// find out how to get that information, return 40 for now
		if (milliSecondsPerSample == 0) {
			return 40;
		} else {
			return milliSecondsPerSample;
		}
	}

	@Override
	public void setMilliSecondsPerSample(long milliSeconds) {
//		milliSecondsPerSample = milliSeconds;
		if (!frameRateAutoDetected) {
			this.milliSecondsPerSample = milliSeconds;
		}
	}

	@Override
	public void updateLocale() {
		if (popup != null) {
			if(detached) {
				detachItem.setText(ElanLocale.getString("Detachable.attach"));
			} else {
				detachItem.setText(ElanLocale.getString("Detachable.detach"));
			}			
			infoItem.setText(ElanLocale.getString("Player.Info"));
			saveItem.setText(ElanLocale.getString("Player.SaveFrame"));
			durationItem.setText(ElanLocale.getString("Player.duration") +
	                ":  " + TimeFormatter.toString(getMediaDuration()));
		}
	}

	@Override
	public String getFrameworkDescription() {
		return "Java FX Media Player";
	}

	@Override
	public void cleanUpOnClose() {
		if (mediaPlayer != null) {
			stop();
			//
		    Platform.runLater(new Runnable() {
		    	@Override
		    	public void run() {
			    	mediaPlayer.stop();
					if (jfxVideoPanel != null) {
						jfxVideoPanel.setScene(null);
					}
			    	mediaPlayer.dispose();
					mediaPlayer = null;
					mediaView = null;
			    }
			 });
			 //
//			mediaPlayer.stop();
//			if (jfxVideoPanel != null) {
//				jfxVideoPanel.setScene(null);
//			}
//			mediaPlayer.dispose();
//			mediaPlayer = null;
//			mediaView = null;
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
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

            //getVisualComponent().addNotify();
        } else if (e.getSource() == infoItem) {
            new FormattedMessageDlg(this);
        } else if (e.getSource() == saveItem) {
        	Image snap = getCurrentFrameImage();
        	// check null?
        	ImageExporter export = new ImageExporter();
        	export.exportImage(snap, mediaDescriptor.mediaURL, getMediaTime() + offset);
        }
		
	}
	private void initPopupMenu() {
		if (jfxVideoPanel == null) {
			return;
		}
		popup = new JPopupMenu();
        detachItem = new JMenuItem(ElanLocale.getString("Detachable.detach"));
        detachItem.addActionListener(this);
		infoItem = new JMenuItem(ElanLocale.getString("Player.Info"));
        infoItem.addActionListener(this);
        durationItem = new JMenuItem(ElanLocale.getString("Player.duration") +
                ":  " + TimeFormatter.toString(duration));
        durationItem.setEnabled(false);
        saveItem = new JMenuItem(ElanLocale.getString("Player.SaveFrame"));
        saveItem.addActionListener(this);
        
		popup.add(detachItem);
        popup.addSeparator();
        popup.add(saveItem);
        popup.add(infoItem);
        
        popup.add(durationItem);
	}


	@Override
	public Image getCurrentFrameImage() {
		if (mediaView != null) {
			// for extraction of an image of the original size this works somehow,
			// maybe it should be possible to either save the rendered size or the original size
			double scale = mediaPlayer.getMedia().getWidth() / mediaView.getBoundsInParent().getWidth();
			Transform transform = new Scale(scale, scale);
			
			ImageRetriever imageRetr = new ImageRetriever();
			imageRetr.transform = transform;
			// get the JavaFX image on the JavaFX thread
		    Platform.runLater(imageRetr);
		    
		    // wait with a time out
		    long curTime = System.currentTimeMillis();
		    while (!imageRetr.ready) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {
					LOG.warning("Image wait interrupted.");
				}
				if (System.currentTimeMillis() > curTime + 5000) {
					LOG.warning("Waited more than 5 sec for image, breaking");
					break;
				}
		    }

			return imageRetr.bufImg;
		}
		return null;
	}

	/**
	 * A runnable that retrieves the current image of the video player.
	 * Should be executed on the JavaFX thread.
	 */
	private class ImageRetriever implements Runnable {
		BufferedImage bufImg;
		Transform transform;
		boolean ready = false;
		
    	@Override
	    public void run() {
			SnapshotParameters snapPar = new SnapshotParameters();
			snapPar.setTransform(transform);
			WritableImage snapImg = mediaView.snapshot(snapPar, null);
			if (snapImg != null) {
				bufImg = SwingFXUtils.fromFXImage(snapImg, null);
			}
			ready = true;
	    }
	}
	
	@Override
	public Image getFrameImageForTime(long time) {
		// this doesn't seem to be supported by JavaFX
		return null;
	}
	
	private class MouseHandler implements MouseListener, MouseMotionListener {
		//private final DecimalFormat format = new DecimalFormat("#.###");
		
		@Override
		public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2) {
                if (layoutManager != null) {
                    layoutManager.setFirstPlayer(JFXMediaPlayer.this);
                }

                return;
            }
            if (SwingUtilities.isRightMouseButton(e)) {
            	return;
            }
		}
		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {			
		}
		
		@Override
		public void mousePressed(MouseEvent e) {
			if (popup == null) {
				initPopupMenu();
			}
			
			Point cl = e.getPoint();
        	
            if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
            	// check the detached state, attaching can be done independently of the menu
            	if (layoutManager.isAttached(JFXMediaPlayer.this)) {
            		if (detached) {
            			detached = false;
            			detachItem.setText(ElanLocale.getString("Detachable.detach"));
            		}
            	}
            	durationItem.setText(ElanLocale.getString("Player.duration") +
                        ":  " + TimeFormatter.toString(duration));
            	//System.out.println("S: " + e.getSource() + " X: " + e.getX() +  " Y: " + e.getY());
                popup.show(getVisualComponent(), (int) cl.getX(), (int) cl.getY());
                return;
            }
			//dragX = (int) cl.getX();
			//dragY = (int) cl.getY();

		}

		@Override
		public void mouseReleased(MouseEvent e) {
		}
		@Override
		public void mouseDragged(MouseEvent e) {		
		}
		@Override
		public void mouseMoved(MouseEvent e) {	
		}
	}
	
	public class MarkerHandler implements EventHandler<MediaMarkerEvent> {
		@Override
		public void handle(MediaMarkerEvent mmEvent) {
			//System.out.println("On Marker " + mmEvent.getMarker().getValue().toMillis());
			if (mmEvent.getMarker().getKey() == SEL_STOP) {
				stop();
				System.out.println("Handle marker event: " + mediaPlayer.getCurrentTime());
				/*
				stopControllers();
				if (mediaPlayer.getStatus() != MediaPlayer.Status.PAUSED) {
					System.out.println("Handle marker event: Not Paused");
					//mediaPlayer.pause();
					
					long curTime = System.currentTimeMillis();
					while (mediaPlayer.getStatus() != MediaPlayer.Status.PAUSED) {
						try {
							Thread.sleep(10);
						} catch (InterruptedException ie){}
						if (System.currentTimeMillis() - curTime > 5000) {
							break;
						}
					}
				}*/
//				mediaPlayer.seek(mmEvent.getMarker().getValue());
				//mediaPlayer.setStopTime(new Duration(origDuration));
//				media.getMarkers().put(SEL_STOP, new Duration(origDuration));
//				setControllersStopTime(duration);
//				setControllersMediaTime((long)mmEvent.getMarker().getValue().toMillis());
			}
		}
	}

	private class IntervalStopHandler extends Thread {
		private long curStopTime;
    	private int sleepInterval = 250;
		private boolean stopped = false;
		
		public IntervalStopHandler(long curStopTime, int sleepInterval) {
			super();
			if (intervalStopTime > 0) {
				this.curStopTime = curStopTime;
			}
			if (sleepInterval > 0) {
				this.sleepInterval = sleepInterval;
			}
		}
		
    	public void setStopped() {
    		stopped = true;
    	}

		@Override
		public void run() {
			long curTime;
			MediaPlayer.Status curStatus;
			
			while(!stopped) {
				curTime = getMediaTime();
				curStatus = mediaPlayer.getStatus();
				if (curTime >= curStopTime) {
					if (curStatus == MediaPlayer.Status.PAUSED) {
						break;
					} else {
						stopPlayer();
						break;
					}
				} else if (curTime - curStopTime < sleepInterval) {
					sleepInterval = (int) (curStopTime - curTime);
				}
				
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException ie) {
                    //ie.printStackTrace();
                	return;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
                
                if (stopped) {
                	return;
                }
			}
			
		}
    	
		private void stopPlayer() {
			JFXMediaPlayer.this.stop();
			//mediaPlayer.pause();
		}
    	
	}
}
