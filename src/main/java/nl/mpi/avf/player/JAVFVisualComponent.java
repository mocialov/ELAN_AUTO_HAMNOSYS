package nl.mpi.avf.player;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;

//import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The visual component for a video player.
 * This implementation is based on JPanel (i.e. lightweight, a variant based on Canvas 
 * is there as well).
 * 
 * @see JAVFVisualCanvas
 * @author Han Sloetjes
 */
@SuppressWarnings("serial")
public class JAVFVisualComponent extends JPanel implements ComponentListener, JAVFComponent {// extend JPanel?
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

	// to be used for rendering performance tests
//	private long imageCount;
//	private long renderCount;
//	private int droppedFrames;

	// members for zooming into the video, zoom level and translation x and y
	// coordinates of the enlarged video, relative to the host canvas
	float videoScaleFactor = 1f;
	int vx = 0, vy = 0, vw = 0, vh = 0;
	double vxToTlcPerc = 0.0d;// the current x position (between 0 and 1) in the video that is located at the top left corner of the canvas
	double vyToTlcPerc = 0.0d;
	// members for a preview sequence; 3, 5 or 7 images with the current image as the center
	//...
	
	/**
	 * Constructor.
	 */
	public JAVFVisualComponent() {
		setBackground(new Color(0.0f, 0.5f, 1.0f));
		addComponentListener(this);
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
		//System.out.println("Set image, T:  " + timeInSeconds + " - " + image.getWidth(null));
		
		currentTimeSeconds = timeInSeconds;
		currentTime = (long) (timeInSeconds * MS_SEC);
		currentImage = image;
		//imageCount++;
		//
		if (!pixelToImageTransformChecked) {
			calculatePixelToVideoImageTransform();
		}
		if (displayTransform == null) {
			calculateTransform();
		}
		//
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
	 * Sets/forces the aspect ratio to a particular value and recalculates the 
	 * image transform.
	 * 
	 *  @param the new aspect ratio
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
	 * Resets the aspect ratio to its original value and recalculates the image transform.
	 */
	@Override
	public void resetAspectRatio() {
		forcedAspectRatio = -1;
		calculatePixelToVideoImageTransform();
	}
	
	/**
	 * Paints the current image while applying the transform. 
	 */
	@Override
	protected void paintComponent(Graphics g) {
		//super.paintComponent(g);// makes painting slow
		// paint background conditionally
		if (paintBackground) {
			g.setColor(getBackground());// makes painting slow
			g.fillRect(0, 0, getWidth(), getHeight());// makes painting slow
		}	
		//System.out.println("Paint: " + currentTimeSeconds + " - " + currentImage.hashCode());
		Graphics2D g2d = (Graphics2D) g;
		// testing purposes, no scaling
//		if (currentImage != null) {// 
//			g2d.drawImage(currentImage, 0, 0, null);
//		}
//
		if (currentImage != null) {
			if (displayTransform == null) {
				calculateTransform();
			}
			if (displayTransform == null) {
				g2d.drawImage(currentImage, 0, 0, null);
//				g2d .setColor(Color.BLUE);
//				g2d.drawString(String.valueOf(currentTimeSeconds), 10, 50);
			} else {
				g2d.drawImage(currentImage, displayTransform, null);
//				g2d .setColor(Color.BLUE);
//				g2d.drawString(String.valueOf(currentTimeSeconds), (int)(displayTransform.getTranslateX() + 10), 
//						(int) (displayTransform.getTranslateY() + 50));
			}
		} else {
			//System.out.println("Image is null");
		}
//
		//System.out.println("FT: " + currentTimeSeconds);
		
		//renderCount++;// for performance testing
	}
	
	/**
	 * Overridden to do nothing.
	 */
	@Override
	protected void paintChildren(Graphics g) {
		// do nothing
		//super.paintChildren(g);
	}

	/**
	 * Overridden to do nothing.
	 */
	@Override
	protected void paintBorder(Graphics g) {
		// do nothing
		//super.paintBorder(g);
	}

	/**
	 * Calculates the transform based on the difference between original video size and 
	 * the video images, these sizes are not always the same.
	 * This method does not take a preferred or forced aspect ratio into account. 
	 * That comes into play when the display transform is calculated. 
	 */
	private void calculatePixelToVideoImageTransform() {
		if (naturalVideoSize != null && currentImage != null) {
			if (naturalVideoSize.width != currentImage.getWidth(null) ||
					naturalVideoSize.height != currentImage.getHeight(null)) {				
				double videoImageSW =  naturalVideoSize.getWidth() / currentImage.getWidth(null);
				double videoImageSH = naturalVideoSize.getHeight() / currentImage.getHeight(null);
				pixelsToVideoSizeTransform = AffineTransform.getScaleInstance(videoImageSW, videoImageSH);
			}
			pixelToImageTransformChecked = true;
		} else {
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
		
		double w = getWidth() * videoScaleFactor;
		double h = getHeight() * videoScaleFactor;
		if (w > 0 && h > 0 && srcWidth > 0 && srcHeight > 0) {
			double hs = w / srcWidth;
			double vs = h / srcHeight;

			if (forcedAspectRatio == -1) {
				// use the smallest scale
				if (vs > hs) {
					vs = hs;
				} else if (hs > vs) {
					hs = vs;
				}
			}
			// compensate for the pixel to display size transform, if not identical
			if (pixelsToVideoSizeTransform != null) {
				hs = hs * pixelsToVideoSizeTransform.getScaleX();
				vs = vs * pixelsToVideoSizeTransform.getScaleY();
			}
			// check image translation, in principle one of xImg or yImg should be 0
			double xImg = (w - (hs * srcWidth)) / 2;
			double yImg = (h - (vs * srcHeight)) / 2;
			
			// create a transform incorporating translation and scaling
			displayTransform = new AffineTransform(hs, 0, 0, vs, xImg, yImg);
		} else {
			displayTransform = null;
		}
	}
	
	/**
	 * Set the video scale (zoom) factor.
	 * 
	 * @param scaleFactor a value greater than or equal to 1
	 */
	public void setVideoScaleFactor(float scaleFactor) {
		if (scaleFactor < 1 || scaleFactor == videoScaleFactor) {
			return;
		}
		
		this.videoScaleFactor = scaleFactor;
		componentResizedOrVideoScaled();
		repaint();
	}
	
	/**
	 * Not really used, the location of the video is changed by calling 
	 * {@link #moveVideoPosition(int, int)}. This method calls that method by
	 * calculating the difference between the current and the specified 
	 * (x,y) coordinates.  
	 * 
	 * @param x the target x coordinate of the video image
	 * @param y the target y coordinate of the video image
	 * @param w ignored
	 * @param h ignored
	 */
	public void setVideoBounds(int x, int y, int w, int h) {
		// compare with current translation and call move
		if (videoScaleFactor > 1) {
			if (x <= 0 && y <= 0) {
				moveVideoPosition(x - vx, y - vy);
			}
		}
	}
	
	/**
	 * If the video is zoomed into, this returns the location and size of the video
	 * image relative to the canvas' coordinate system.
	 * If the video is not scaled (i.e. its dimensions are equal to the dimensions of the
	 * canvas, which are likely not the same as the encoded video dimensions), this returns
	 * [0, 0, canvas width, canvas height].
	 * 
	 * @return the current rectangle of the scaled video image relative to the canvas
	 */
	public int[] getVideoBounds() {
		if (vw != 0 && vh != 0) {
			return new int[] {vx, vy, vw, vh};
		} else {
			return new int[] {0, 0, getWidth(), getHeight()};
		}
	}
	
	/**
	 * If the video is zoomed into (scale factor > 1) it is possible to drag or pan the image
	 * and thus change the viewport (the part of the image that is visible).
	 *  
	 * @param dx the amount (in pixels) to move the image along the x axis
	 * @param dy the amount (in pixels) to move the image along the y axis
	 */
	public void moveVideoPosition(int dx, int dy) {
		if (displayTransform != null && videoScaleFactor > 1) {
			double dvw = getWidth() * videoScaleFactor;
			double dvh = getHeight() * videoScaleFactor;
			if (vw == 0) {
				vw = (int) dvw;
			}
			if (vh == 0) {
				vh = (int) dvh;
			}
			int nx = vx + dx;
			int ny = vy + dy;
			// force translation coordinates to be such that the entire canvas is covered
			// by part of the video, no "blank" areas
			// so vx <= 0 && vx >= scaledVideoWidth - canvasWidth
			nx = nx > 0 ? 0 : nx;
			nx = nx + vw < getWidth() ? getWidth() - vw : nx;
			
			ny = ny > 0 ? 0 : ny;
			ny = ny + vh < getHeight() ? getHeight() - vh : ny;
			
			vxToTlcPerc = nx / dvw;
			vyToTlcPerc = ny / dvh;
			
			if (nx != vx && ny != vy) {
				vx = nx;
				vy = ny;
				displayTransform = new AffineTransform(displayTransform.getScaleX(), 0, 0, 
						displayTransform.getScaleY(), vx, vy);
				// or use displayTransform.translate(ndx, ndy) with recalculated values for the x,y translation?
			}	
			
			repaint();
		}
	}
	
	/**
	 * If the component's size or the scale factor of the video changes
	 * first a new basic AffineTransform is created with the new scale
	 * set and then, optionally, a new translation is calculated. 
	 */
	private void componentResizedOrVideoScaled() {
		//System.out.println("Canvas resized");
		if (videoScaleFactor > 1) {
			calculateTransform();
			
			int nw = this.getWidth();
			int nh = this.getHeight();
			float bw = nw * videoScaleFactor;
			float bh = nh * videoScaleFactor;
			vw = (int) bw;
			vh = (int) bh;
			// try to maintain the point in the video that is in the top left corner of the canvas
			double bx = vxToTlcPerc * bw;
			double by = vyToTlcPerc * bh;
			// after a change in the zoom or scale factor, it might happen that not the entire canvas
			// is covered by a part of the video, correct it here
			bx = bx + bw < nw ? (nw - bw) : bx;
			by = by + bh < nh ? (nh - bh) : by;
			
			vx = (int) bx;
			vy = (int) by;
			
			if (displayTransform != null) {
				//displayTransform.translate(bx, by);
				displayTransform = new AffineTransform(displayTransform.getScaleX(), 0, 0,  
						displayTransform.getScaleY(), bx, by);
			}
			
			vxToTlcPerc = bx / bw;
			vyToTlcPerc = by / bh;
		} else {
			//setVideoBounds(0, 0, visualComponent.getWidth(), visualComponent.getHeight());
			vx = 0;
			vy = 0;
			vw = getWidth();
			vh = getHeight();
			vxToTlcPerc = 0.0d;
			vyToTlcPerc = 0.0d;
			calculateTransform();
		}
	}

	@Override
	public void componentHidden(ComponentEvent e) {
		// stub
	}

	@Override
	public void componentMoved(ComponentEvent e) {
		// stub
	}

	/**
	 * (Re)calculates the transformation and repaints.
	 */
	@Override
	public void componentResized(ComponentEvent e) {
		// reset transform
		//calculateTransform();
		componentResizedOrVideoScaled();
		repaint();
	}

	/**
	 * (Re)calculates the transformation and repaints.
	 */
	@Override
	public void componentShown(ComponentEvent e) {
		calculateTransform();
		repaint();
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
	}

	/* Not applicable to swing components, it seems
	@Override
	protected AWTEvent coalesceEvents(AWTEvent existingEvent, AWTEvent newEvent) {
		if (existingEvent instanceof PaintEvent) {
			System.out.println("coalesce: " + existingEvent.paramString() + " new: " + newEvent.paramString());
			droppedFrames++;
		}
		
		return super.coalesceEvents(existingEvent, newEvent);
	}
	*/

	/* used for rendering tests
	@Override
	public String toString() {
		return String.format("Offered: %s, Rendered: %s, Dropped: %s", imageCount, renderCount, droppedFrames);
	}
	*/
}
