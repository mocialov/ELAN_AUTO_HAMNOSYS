package nl.mpi.avf.player;

import java.awt.Image;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JAVFVideoLoadController {
	private final Logger LOG = Logger.getLogger("AVF");
	
	private final JAVFMediaPlayer javfMediaPlayer;
	// could consider a buffer based on a number of frames?
	private final static int BUFFER_LENGTH_MS = 500;// default of 0.5 or 1 second per buffer
	private int bufferLengthMs = BUFFER_LENGTH_MS;// default buffer size
	private double bufferLengthSeconds = bufferLengthMs / 1000d;
	private int numBytesPerFrame = 0;
	int numFramesPerBuffer = 0;
	
	private ByteBuffer loadByteBuffer;
	private final ReentrantLock byteBufferLock = new ReentrantLock();
	
	private TimedImageSequence headSequence;
	private TimedImageSequence bodySequence;
	private TimedImageSequence tailSequence;
	private final ReentrantLock sequencesLock = new ReentrantLock();
	
	private JAVFImageProducer imageProducer;
	private BackgroundLoader sequenceLoader;
	
	/**
	 * Creates a controller with a default buffer length
	 * @param javfMediaPlayer the media player to load and control the video images for
	 */
	public JAVFVideoLoadController(JAVFMediaPlayer javfMediaPlayer) {
		this(javfMediaPlayer, BUFFER_LENGTH_MS);			
	}
	
	/**
	 * Creates three buffers, three TimedImageSequence instances which will rotate 
	 * their contents while play back is progressing.
	 * 
	 * @param javfMediaPlayer the media player to load and control the video images for
	 * @param bufferLengthMs the length of the buffer in milliseconds
	 */
	public JAVFVideoLoadController(JAVFMediaPlayer javfMediaPlayer, int bufferLengthMs) {
		super();
		this.javfMediaPlayer = javfMediaPlayer;
		this.bufferLengthMs = bufferLengthMs;
		bufferLengthSeconds = this.bufferLengthMs / 1000d;
		headSequence = new TimedImageSequence();
		bodySequence = new TimedImageSequence();
		tailSequence = new TimedImageSequence();
		imageProducer = new JAVFImageProducer();
		init();
	}
	
	private void init() {			
		if (javfMediaPlayer != null) {
			headSequence.setMediaDuration(javfMediaPlayer.getDurationSeconds());
			bodySequence.setMediaDuration(javfMediaPlayer.getDurationSeconds());
			tailSequence.setMediaDuration(javfMediaPlayer.getDurationSeconds());
			// init fields
			numBytesPerFrame = javfMediaPlayer.getBytesPerFrame();
			numFramesPerBuffer = (int) (bufferLengthMs / javfMediaPlayer.getFrameDuration()); // +1?
			
			loadByteBuffer = ByteBuffer.allocateDirect(numBytesPerFrame * numFramesPerBuffer);
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info(String.format("Byte Buffer Size (Mb): %f", (numBytesPerFrame * numFramesPerBuffer) / (float) (1024 *1024) ));
			}
			preloadBuffer(0.010, bodySequence);//??
			// simply assume the visualComponent is not null at this point
			javfMediaPlayer.getVideoComponent().setImage(bodySequence.getFirstTime(), bodySequence.getImageForTime(
					bodySequence.getFirstTime()));
			// preload next buffer too
			double lt = bodySequence.getLastTime();
			new BackgroundLoader(lt, headSequence).start();
		}
	}
	
	/**
	 * 
	 * @return the image producer object
	 */
	public JAVFImageProducer getImageProducer() {
		return imageProducer;
	}
	
	/**
	 * 
	 * @param time the time in milliseconds 
	 * @return the corresponding image if it is in one of the buffers, null otherwise
	 */
	public Image getImageForTime(long time) {
		double tt = time / 1000d;
		if (bodySequence.hasTime(tt)) {
			return bodySequence.getImageForTime(tt);
		}
		if (headSequence.hasTime(tt)) {
			return headSequence.getImageForTime(tt);
		}
		if (tailSequence.hasTime(tt)) {
			return tailSequence.getImageForTime(tt);
		}
		return null;
	}

	/**
	 * Tells the controller to prepare the buffer(s) for play back
	 * from the specified time. If that media time is already in the
	 * main buffer this method just returns.
	 * 
	 * @param time the media time where play back will start 
	 */
	public void prepareForPlay(double time) {
		if (bodySequence.hasTime(time)) {
			return;
		}
		if (headSequence.hasTime(time)) {
			swapSequences(true);
		}
		if (tailSequence.hasTime(time)) {
			swapSequences(false);
		}
		preloadBuffer(time, bodySequence);
	}
	
	/**
	 * Conditionally loads in advance new frame images into the 'head' sequence.
	 * This loading is not required (not possible) if the main sequence, the 'body'
	 * sequence contains the last video frame of the file.
	 */
	public void preloadHeadBufferCond() {
		if (headSequence.getFirstTime() - bodySequence.getLastTime() <= javfMediaPlayer.getFrameDuration()) {
			if (sequenceLoader == null || !sequenceLoader.isAlive()) {
				sequenceLoader = new BackgroundLoader(bodySequence.getLastTime(), headSequence);
				sequenceLoader.start();
			}
		}
	}
	
	/**
	 * Called by the media time watch thread; the controller checks the time and informs the
	 * video component if a new image needs to be displayed.
	 * 
	 * @param timeInSeconds the current media time in seconds
	 */
	public void timeUpdateSeconds(double timeInSeconds) {
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Time update: " + timeInSeconds);
		}
		//update the render component
		double curBT = javfMediaPlayer.getVideoComponent().getCurrentImageTimeSeconds();
		if (timeInSeconds >= curBT && timeInSeconds < curBT + (this.javfMediaPlayer.getFrameDuration() / 1000)) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(String.format("No frame update needed, current image: %d, new media time %d", 
						curBT, javfMediaPlayer.getFrameDuration()));
			}
			// the right image is already rendered
			return;
		}
		// possible 'rotation' directions: 0 = not need to rotate, 1 = rotate forward, -1 = rotate backward
		int rotateDir = 0;
		sequencesLock.lock();
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("Acquired sequences lock");
		}
		
		try {
			TimedImage ti = bodySequence.getTimedImageForTime(timeInSeconds);
			
			if (ti != null) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Time update 3: " + ti.t);
				}
				this.javfMediaPlayer.getVideoComponent().setImage(ti);
				return;
			} else {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Time update 4: next image is from head sequence");
				}
				ti = headSequence.getTimedImageForTime(timeInSeconds);
				if (ti != null) {
					javfMediaPlayer.getVideoComponent().setImage(ti);
					rotateDir = 1;
				} else {
					ti = tailSequence.getTimedImageForTime(timeInSeconds);
					if (ti != null) {
						javfMediaPlayer.getVideoComponent().setImage(ti);
						rotateDir = -1;
					}
				}
			}
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Update 5: next image is " + ti + "  Rotate: " + rotateDir);
			}
		} finally {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Releasing sequences lock");
			}
			sequencesLock.unlock();
		}
		
		if (rotateDir == 1) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Rotating sequence forward");
			}
			// rotate sequences
			double lastTime = headSequence.getLastTime();
			swapSequences(true);
			new BackgroundLoader(lastTime, headSequence).start();
		} else if (rotateDir == -1) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Rotating sequence backward");
			}
			// rotate sequences	
			swapSequences(false);
			double firstTime = bodySequence.getFirstTime();
			new BackgroundLoader(firstTime - bufferLengthSeconds, tailSequence).start();
		} else {// rotateDir == 0
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Required image in none of the sequences, loading new sequence");
			}
			// position the requested frame in the center of a new sequence
			double seqStartTime = timeInSeconds - (bufferLengthMs / 2000.0d);
			seqStartTime = seqStartTime >= 0 ? seqStartTime : 0.0d;
			// synchronously load
			preloadBuffer(seqStartTime, bodySequence);
			// try to set the image
			//timeUpdateSeconds(timeInSeconds);
			TimedImage ti = bodySequence.getTimedImageForTime(timeInSeconds);
			
			if (ti != null) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("Update 6: next image is " + ti.t);
				}
				javfMediaPlayer.getVideoComponent().setImage(ti);
			}
			
			double lastTime =  bodySequence.getLastTime();
			new BackgroundLoader(lastTime, headSequence).start();
		}
	}
	
	/**
	 * Calls {@link #timeUpdateSeconds(double)}.
	 * 
	 * @param timeInMs media time in milliseconds
	 */
	public void timeUpdateMs(long timeInMs) {
		timeUpdateSeconds(timeInMs / 1000d);
	}
	
	/**
	 * Loads from the specified time and stores images in the specified sequence without checking
	 * if the sequence already contains the image for that time.
	 * Returns immediately if the time to load from is &lt; 0.
	 * 
	 * @param fromTime load a sequence starting at this time
	 * @param targetSequence the sequence object to store the images in
	 */
	private void preloadBuffer(double fromTime, TimedImageSequence targetSequence) {
		if (fromTime < 0) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Illegal start time to load images from: " + fromTime);
			}
			return;
		}
		if (loadByteBuffer == null) {
			init();
		}
		if (javfMediaPlayer != null) {// superfluous?
			byteBufferLock.lock();
			try {
				//long bt = (long)(fromTime * 1000);// or use the seconds variant?
				double[] frameSequence = javfMediaPlayer.getVideoFrameSequenceSeconds(
						fromTime, fromTime + bufferLengthSeconds, loadByteBuffer);
				
				if (frameSequence != null) {
					// convert to BufferedImages
					Image[] images = imageProducer.produceImagesFromBuffer(loadByteBuffer, numBytesPerFrame, 
							numFramesPerBuffer, javfMediaPlayer.getVideoImageWidth(), 
							javfMediaPlayer.getVideoImageHeight()); 
					
					if (images != null) {
//						if (images.length < frameSequence.length) {
//							double[] timeIndices = Arrays.copyOf(frameSequence, images.length);
//							targetSequence.setSequence(timeIndices, images);
//						} else {
							targetSequence.setSequence(frameSequence, images);
//						}
					}
				}
				
			} finally {
				byteBufferLock.unlock();
			}
		}
	}
	
	/**
	 * Copies the image sequence from one buffer to another.
	 * When moving forward the contents of the middle buffer, the body sequence,
	 * is copied to the last buffer, the tail sequence. The original contents of the 
	 * tail sequence is discarded. The sequence of the first buffer, the head sequence,
	 * is then copied to the body sequence. The head sequence is then available to 
	 * be filled with a new buffer.
	 * When moving backward the copying is performed in the reverse direction.
	 * 
	 * @param forward if true swaps the sequences forward, backward otherwise
	 */
	private void swapSequences(boolean forward) {
		if (LOG.isLoggable(Level.FINE)) {
			String s = forward ? "forward" : "backward";
			LOG.fine(String.format("Swapping image sequences in %s direction", s));
		}
		
		sequencesLock.lock();
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("Swap sequence acquired lock");
		}
		
		try {
			if (forward) {
				bodySequence.copySequenceTo(tailSequence);
				headSequence.copySequenceTo(bodySequence);
				// headSequence ready to be filled with a new buffer
			} else {
				bodySequence.copySequenceTo(headSequence);
				tailSequence.copySequenceTo(bodySequence);
				// tailSequence ready to be filled with a new buffer
			}
		} finally {
			sequencesLock.unlock();
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("Swap sequence released lock");
			}
		}
	}
	
	public void close() {
		if (sequenceLoader != null && sequenceLoader.isAlive()) {
			sequenceLoader.interrupt();
			sequenceLoader = null;
		}
		//imageProducer = null;
		bodySequence.clear();
		headSequence.clear();
		tailSequence.clear();
		bodySequence = null;
		headSequence = null;
		tailSequence = null;
	}
	
	
	/**
	 * A thread to perform loading of an image sequence in the background,
	 * with low priority.
	 * 
	 */
	private class BackgroundLoader extends Thread {
		private double fromTime;
		private TimedImageSequence tiSequence;
		
		/**
		 * Constructor.
		 * 
		 * @param fromTime the time of the first frame to load
		 * @param tiSequence the sequence to update with new images
		 */
		public BackgroundLoader(double fromTime, TimedImageSequence tiSequence) {
			super();
			setPriority(Thread.MIN_PRIORITY);
			this.fromTime = fromTime;
			this.tiSequence = tiSequence;
		}

		/**
		 * Calls {@link JAVFVideoLoadController#preloadBuffer(double, TimedImageSequence)}
		 */
		@Override
		public void run() {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Background loading from time: " + fromTime);
			}
			JAVFVideoLoadController.this.preloadBuffer(fromTime, tiSequence);
		}			
		
	}
}// JAVFVideoLoadController