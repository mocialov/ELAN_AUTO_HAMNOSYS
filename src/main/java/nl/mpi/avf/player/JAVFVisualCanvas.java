package nl.mpi.avf.player;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.PaintEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;

/**
 * An AWT Canvas based implementation of a JAVFComponent
 * 
 * @author Han Sloetjes
 */
@SuppressWarnings("serial")
public class JAVFVisualCanvas extends Canvas implements JAVFComponent {
	private ComponentListener compListener;
	private Dimension naturalVideoSize;
	// a transform object to scale from encoded image pixels to encoded 'natural' video width and height
	private AffineTransform pixelsToVideoSizeTransform = null;
	private boolean pixelToImageTransformChecked = false;
	private boolean paintBackground = false;
	
	//private TimedImage currentTimedImage;
	private Image currentImage;
	private double currentTimeSeconds = -1d;
	private long currentTime = -1;
	private final double MS_SEC = 1000d;

	private float forcedAspectRatio = -1;
	// affine transform object for scaling to the component size
	private AffineTransform displayTransform = null;
	// painting with AWT BufferStrategy is too slow
//	private BufferStrategy bufferStrat;
	
	// for performance testing
//	private long imageCount;
//	private long renderCount;
//	private int droppedFrames;
	
	/**
	 * Constructor.
	 */
	public JAVFVisualCanvas() {
		initCanvas();
	}

	/**
	 * Constructor.
	 * @param gc graphics configuration, ignored
	 */
	public JAVFVisualCanvas(GraphicsConfiguration gc) {
		//super(arg0);
		initCanvas();
	}

	private void initCanvas() {
		setBackground(Color.CYAN);
		compListener = new SelfListener();
		addComponentListener(compListener);
		//setIgnoreRepaint(true);
		//System.out.println("Buffer strategy: " + getBufferStrategy());
	}
	
	
	/**
	 * Sets the (current) time and image to be displayed.
	 * (Re)calculates the transform for the image and triggers a repaint.
	 * 
	 * @param time the media time in milliseconds 
	 * @param image the corresponding image
	 */
	@Override
	public void setImage(long time, Image image) {
		currentTime = time;
		currentTimeSeconds = time / MS_SEC;
		currentImage = image;
		if (!pixelToImageTransformChecked) {
			calculatePixelToVideoImageTransform();
		}
		if (displayTransform == null) {
			calculateTransform();
		}
		repaint();
	}
	
	/**
	 * Informs the video component of the natural, encoded size of the video,
	 * Resets the transform.
	 *  
	 * @param naturalSize the natural width and height of the video
	 */
	@Override
	public void setNaturalVideoSize(Dimension naturalSize) {
		if (naturalSize != null) {
			naturalVideoSize = new Dimension(naturalSize);
			if (!pixelToImageTransformChecked) {
				calculatePixelToVideoImageTransform();
			}
			displayTransform = null;//reset
		}
	}
	
	/**
	 * Sets the (current) time and image to be displayed.
	 * (Re)calculates the transform for the image and triggers a repaint.
	 * 
	 * @param timeInSeconds the media time in seconds
	 * @param image the corresponding image
	 */
	@Override
	public void setImage(double timeInSeconds, Image image) {
		//System.out.println("Set image, T:  " + timeInSeconds + " - " + image.hashCode());
		currentTimeSeconds = timeInSeconds;
		currentTime = (long) (timeInSeconds * MS_SEC);
		currentImage = image;
//		imageCount++;
		//
		if (!pixelToImageTransformChecked) {
			calculatePixelToVideoImageTransform();
		}
		if (displayTransform == null) {
			calculateTransform();
		}

		repaint();
	}
	
	/**
	 * Sets (current) time and the image to display.
	 * 
	 * @param timedImage the TimedImage object containing the time and the image
	 */
	@Override
	public void setImage(TimedImage timedImage) {
		if (timedImage != null) {
			setImage(timedImage.t, timedImage.img);
		}
	}
	
	/**
	 * @return the time in milliseconds of the current image
	 */
	@Override
	public long getCurrentImageTime() {
		return currentTime;
	}
	
	/**
	 * @return the time in seconds of the current image
	 */
	@Override
	public double getCurrentImageTimeSeconds() {
		return currentTimeSeconds;
	}
	
	/**
	 * @return the current image or null if no image has been set yet
	 */
	@Override
	public Image getCurrentImage() {
		return currentImage;
	}

	/**
	 * Forces the display to the specified aspect ratio.
	 * @param aspectRatio the new, forced aspect ratio
	 */
	@Override
	public void setAspectRatio(float aspectRatio) {
		if (aspectRatio == forcedAspectRatio) {
			return;
		}
		forcedAspectRatio = aspectRatio;
		calculatePixelToVideoImageTransform();
	}
	
	/**
	 * Resets the aspect ration to the natural, encoded aspect ratio.
	 */
	@Override
	public void resetAspectRatio() {
		forcedAspectRatio = -1;
		calculatePixelToVideoImageTransform();
	}

	/**
	 * Draws the current image.
	 */
	@Override
	public void paint(Graphics g) {
		//super.paint(g);
		if (paintBackground) {
			g.setColor(getBackground());// makes painting slow
			g.fillRect(0, 0, getWidth(), getHeight());// makes painting slow
		}
		//System.out.println("Paint: " + currentTimeSeconds + " - " + currentImage.hashCode());
		//g.drawImage(currentImage, 0, 0, null);
		//g.drawImage(currentImage, 0, 0, naturalVideoSize.width / 4, naturalVideoSize.height / 4, 
		//		0, 0, naturalVideoSize.width, naturalVideoSize.height, null);
		//
		Graphics2D g2d = (Graphics2D) g;
		if (currentImage != null) {
			if (displayTransform == null) {
				calculateTransform();
			}
			if (displayTransform == null) {
				g2d.drawImage(currentImage, 0, 0, null);
//				g2d .setColor(Color.BLUE);
//				g2d.drawString(String.valueOf(currentTimeSeconds), 10, 50);
			} else {
				//System.out.println("Paint transformed: " + currentTimeSeconds);
				g2d.drawImage(currentImage, displayTransform, null);
//				g2d .setColor(Color.BLUE);
//				g2d.drawString(String.valueOf(currentTimeSeconds), (int)(displayTransform.getTranslateX() + 10), 
//						(int) (displayTransform.getTranslateY() + 50));
			}
		} else {
			//System.out.println("Image is null");
		}
		// for testing purposes
		//renderCount++;
	}

	/**
	 * Calls {@link #paint(Graphics)}
	 */
	@Override
	public void update(Graphics g) {
		//System.out.println("Update: " + currentTimeSeconds);
		paint(g);// call super.update(g) or paint(g) directly
	}
	
	/**
	 * Calculates the transform based on the difference between original video size and 
	 * the video images, these sizes are not always the same.
	 * This method also takes a preferred or forced aspect ratio into account. 
	 */
	private void calculatePixelToVideoImageTransform() {
		if (naturalVideoSize != null && currentImage != null) {
			if (naturalVideoSize.width != currentImage.getWidth(null) ||
					naturalVideoSize.height != currentImage.getHeight(null)) {				
				double videoImageSW =  naturalVideoSize.getWidth() / currentImage.getWidth(null);
				double videoImageSH = naturalVideoSize.getHeight() / currentImage.getHeight(null);
				if (forcedAspectRatio == -1) {
					pixelsToVideoSizeTransform = AffineTransform.getScaleInstance(videoImageSW, videoImageSH);
				} else {
					pixelsToVideoSizeTransform = AffineTransform.getScaleInstance(
							forcedAspectRatio / videoImageSW, videoImageSH);
				}
			} else {
				if (forcedAspectRatio != -1) {
					double imgAR = currentImage.getWidth(null) / (double) currentImage.getHeight(null);
					pixelsToVideoSizeTransform = AffineTransform.getScaleInstance(forcedAspectRatio / imgAR, 1.0);
				}
			}
			pixelToImageTransformChecked = true;
		} else {
			if (forcedAspectRatio != -1) {
				if (currentImage != null) {
					double imgAR = currentImage.getWidth(null) / (double) currentImage.getHeight(null);
					if (imgAR != forcedAspectRatio) {
						pixelsToVideoSizeTransform = AffineTransform.getScaleInstance(forcedAspectRatio / imgAR, 1.0);
					} else {
						pixelsToVideoSizeTransform = null;
					}
				}
			}
			pixelsToVideoSizeTransform = null;
		}
	}

	/**
	 * Calculates the transform based on the natural size of the video (or the dimensions of
	 * the current image) and the size of the component. 
	 */
	private void calculateTransform() {
		if (currentImage == null) {
			return;
		}
		double srcWidth = 0d;
		double srcHeight = 0d;
		
		if (naturalVideoSize != null) {
			srcWidth = naturalVideoSize.getWidth();
			srcHeight = naturalVideoSize.getHeight();
		} else {
			srcWidth = (double) currentImage.getWidth(null);
			srcHeight = (double) currentImage.getHeight(null);
		} 
		
		int w = getWidth();
		int h = getHeight();
		if (w > 0 && h > 0 && srcWidth > 0 && srcHeight > 0) {
			double hs = w / srcWidth;
			double vs = h / srcHeight;

			// use the smallest scale
			if (vs > hs) {
				vs = hs;
			} else if (hs > vs) {
				hs = vs;
			}
			// compensate for the pixel to display size transform, if not identical
			if (pixelsToVideoSizeTransform != null) {
				hs = hs * pixelsToVideoSizeTransform.getScaleX();
				vs = vs * pixelsToVideoSizeTransform.getScaleY();
			}
			
			// check image translation, in principle one of xImg or yImg should be 0
			double xImg = (w - (hs * currentImage.getWidth(null))) / 2;
			double yImg = (h - (vs * currentImage.getHeight(null))) / 2;
			// create a transform incorporating translation and scaling
			displayTransform = new AffineTransform(hs, 0, 0, vs, xImg, yImg);
		} else {
			displayTransform = null;
		}
	}
	
	/**
	 * Revise: include the pixelsToVideoSizeTransform?
	 * 
	 * @return the preferred size based on the dimensions of the current image
	 */
	@Override
	public Dimension getPreferredSize() {
		if (naturalVideoSize != null) {
			return naturalVideoSize;
		}
		if (currentImage != null) {
			return new Dimension(currentImage.getWidth(null), currentImage.getHeight(null));
		}
		return super.getPreferredSize();
	}

	/**
	 * Calculates the transform and repaints the image.
	 */
	@Override
	public void addNotify() {
		super.addNotify();
		calculateTransform();
		repaint();
		/*
		if (bufferStrat == null) {
			BufferCapabilities bufCaps = new BufferCapabilities(new ImageCapabilities(true), new ImageCapabilities(true), 
					BufferCapabilities.FlipContents.PRIOR);
			try {
				this.createBufferStrategy(2, bufCaps);
			} catch (AWTException awe) {
				try {
					this.createBufferStrategy(2, new BufferCapabilities(new ImageCapabilities(true), 
							new ImageCapabilities(true), 
							BufferCapabilities.FlipContents.COPIED));
				} catch (AWTException awte) {
					this.createBufferStrategy(2);
				}
			}
			bufferStrat = getBufferStrategy();
			if (bufferStrat != null) {
				System.out.println("BufferStrategy: " + bufferStrat.getCapabilities().getFrontBufferCapabilities().isAccelerated());
			}
			//System.out.println(getGraphicsConfiguration().getBufferCapabilities().getBackBufferCapabilities().isAccelerated());
		}
		*/
	}

	
	/**
	 * A listener for component events.
	 */
	private class SelfListener extends ComponentAdapter {
		/**
		 * (Re)calculates the transformation and repaints.
		 */
		@Override
		public void componentResized(ComponentEvent e) {
			//super.componentResized(e);
			// reset transform
			calculateTransform();
			repaint();
		}

		/**
		 * (Re)calculates the transformation and repaints.
		 */
		@Override
		public void componentShown(ComponentEvent e) {
			//super.componentShown(e);
			// reset transform
			calculateTransform();
			repaint();
		}
		
	}

	@Override
	public void repaint() {
		//System.out.println("repaint");
		super.repaint();
	}

	@Override
	protected AWTEvent coalesceEvents(AWTEvent existingEvent, AWTEvent newEvent) {
		// for performance testing purposes, counts the number of dropped frames
//		if (existingEvent instanceof PaintEvent) {
//			System.out.println("coalesce: " + existingEvent.paramString() + " new: " + newEvent.paramString());
//			droppedFrames++;
//		}
		
		return super.coalesceEvents(existingEvent, newEvent);
	}

	/* for testing purposes
	@Override
	public String toString() {	
		return String.format("Offered: %s, Rendered: %s, Dropped: %s", imageCount, renderCount, droppedFrames);
	}
	 */
	
}
