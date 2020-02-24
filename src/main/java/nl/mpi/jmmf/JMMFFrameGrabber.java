package nl.mpi.jmmf;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for retrieving an image of a video frame or rather retrieving the bytes 
 * of a (decoded) video frame.
 * The native part of it is based on the Microsoft Media Foundation Source Reader 
 * (IMFSourceReader), which seems to only(?) support MFVideoFormat_RGB32 as the type
 * for uncompressed video frames. This corresponds to Direct3D format D3DFMT_X8R8G8B8,
 * so 32 bits per pixel, of which only the R, G and B components are used.
 *
 * @author Han Sloetjes
 */
public class JMMFFrameGrabber {
	private final static Logger LOG = Logger.getLogger("JMMF");
	static {
		try {
			// load libMMFFrameGrabber.dll
			System.loadLibrary("MMFFrameGrabber");
			
			String debug = System.getProperty("JMMFDebug");
			if (debug != null && debug.toLowerCase().equals("true")) {
				JMMFFrameGrabber.setDebugMode(true);
			}
		} catch (Throwable t) {
			//t.printStackTrace();
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Error loading native library: " + t.getMessage());
			}
		}
	}

	private String mediaPath;
	private long id;
	private boolean fieldsInited = false;
	// the following fields are given a value in native code, a change in field name
	// should be combined with a change in the native code
	private int numBytesPerFrame;
	private int numBytesPerRow;
	
	// default values for color video	
	// component meaning R, G, B and/or A
	private int numPixelComponents = 4;
	private int numBitsPerPixelComponent = 8;
	private int numBitsPerPixel = 32;
	
	private int imageWidth;
	private int imageHeight;
	private int videoWidth; // video width and image width are equivalent
	private int videoHeight; // video height and image height are equivalent
	private long videoDuration;

	/** for use with possibly repeated calls to {@link #getVideoFrameImage(long)} */
	private ByteBuffer byteBuffer;
	private ColorModel colorModel;
	private SampleModel sampleModel;
//	private WritableRaster raster;
	private byte[] byteArray;
	private boolean useByteArray = false;// use of ByteBuffer is the default
	
	// pixel components masks for destination image
	final int RM = 0x00ff0000;// 0xff0000
	final int GM = 0x0000ff00;// 0xff00
	final int BM = 0x000000ff;// 0xff
	// the actual order used for the pixel samples
	//int[] masks = new int[]{RM, GM, BM};
	int[] masks = new int[]{BM, GM, RM};
	
	/**
	 * Constructor, initializes a native  Media Foundation Source Reader for the specified
	 * media (video) file. The source reader can be used for repeated
	 * frame grabbing without requiring initialization each time.
	 *
	 * @param mediaPath the path or url of the video file
	 */
	public JMMFFrameGrabber(String mediaPath) {
		this.mediaPath = mediaPath;

		id = initNative(mediaPath);

		// initialize all fields
		if (id > 0) { 
			videoWidth = getVideoImageWidth(id);
			videoHeight = getVideoImageHeight(id);
			numBytesPerRow = getBytesPerRow(id);// returns 0, could hard code this natively
			if (numBytesPerRow < videoWidth) {
				numBytesPerRow = numPixelComponents * videoWidth;
			}
			numBytesPerFrame = numBytesPerRow * videoHeight;

			if (getBytesPerPixel(id) > 1) {
				numBitsPerPixel = getBytesPerPixel(id) * 8;
			}
			numBitsPerPixelComponent = numBitsPerPixel / numPixelComponents;
			// add tests?
			if (numBytesPerRow > 0 || videoWidth > 0 || videoHeight > 0) {
				fieldsInited = true;
			}
			
		} else {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("The MMFoundation based FrameGrabber could not be initialized");
			}
		}
	}

	/**
	 * Creates and returns an image object for the specified time.
	 *
	 * @param sampleTime the time to get the image for
	 * @return a BufferedImage of the natural size of the video frame, or null if
	 * an error occurred
	 */
	public synchronized BufferedImage getVideoFrameImage(long sampleTime) {
		if (!fieldsInited) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("No Image: the FrameGrabber was not correctly initialized");
			}
			return null;
		}
		
		//long time = System.currentTimeMillis();
		if(numBytesPerFrame == 0) {
			numBytesPerFrame = numBytesPerRow * imageHeight;
		}
		int numBytes = 0;

		if (useByteArray) {
			if (byteArray == null) {
				byteArray = new byte[numBytesPerFrame];
			}
			numBytes = grabVideoFrameBA(id, sampleTime, byteArray);
		} else {
			if (byteBuffer == null) {
				byteBuffer = ByteBuffer.allocateDirect(numBytesPerFrame);
			} else {
				//reset position to 0
				byteBuffer.position(0);
			}
			numBytes = grabVideoFrame(id, sampleTime * 10000, byteBuffer);
		}

		if (numBytes > 0) {
			try {
				byte[] dataBytes = null;
				if (useByteArray) {
					dataBytes = byteArray;
				} else {
					if (byteBuffer.hasArray()) {// returns false
						dataBytes = byteBuffer.array();
					} else {
						// this repeated creation of a new byte array seems to be non-optimal
						dataBytes = new byte[numBytes];
						byteBuffer.get(dataBytes, 0, numBytes);
					}
				}

				if (imageWidth == 0) {
					imageWidth = videoWidth;
				}
				if (imageHeight == 0) {
					imageHeight = videoHeight;
				}

				return createImageDataBufferInt(dataBytes, imageWidth, imageHeight, 
						numBitsPerPixel / numBitsPerPixelComponent);// 4
			} catch (Throwable t) {
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Error converting frame bytes to Java image: " + t.getMessage());
				}
				//t.printStackTrace();
			}
		} else {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Error retrieving frame bytes from the AVFoundation image generator ");
			}
		}

		return null;
	}

	/**
	 * 
	 * @return a compatible, DataBuffer.TYPE_INT based color model 
	 */
	private ColorModel createCorrespondingColorModel() {
		// especially the color space, the hasAlpha and isAlphaPremultiplied parameters should
		// depend on the properties of the native image
		/*
		ComponentColorModel comModel = new ComponentColorModel(
				ColorSpace.getInstance(ColorSpace.CS_sRGB),
				true, true, // hasAlpha, isAlphaPremultiplied
				Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
		return comModel;
		*/
		return new DirectColorModel(
				ColorSpace.getInstance(ColorSpace.CS_sRGB), 24, // 32 bits or does 24 suffice in this case?
				masks[0], masks[1], masks[2], 
				0,// 0 for alpha means no transparency
			    true, // isAlphaPremultiplied
				DataBuffer.TYPE_INT);
	}
	
	/**
	 * @return a compatible, DataBuffer.TYPE_INT based SinglePixelPackedSampleModel.
	 */
	private SampleModel createCorrespondingSampleModel() {
		return new SinglePixelPackedSampleModel(
				DataBuffer.TYPE_INT, imageWidth, imageHeight, masks);
	}

	/**
	 * The Media Foundation Source Reader decodes into MFVideoFormat_RGB32 format 
	 * uncompressed video frames. This corresponds to Direct3D format D3DFMT_X8R8G8B8, 
	 * meaning 32 bits per pixel, of which only the R, G and B components are used. 
	 * 
	 * @param copyBytes the array of bytes of one frame produced by the decoder
	 * @param imageWidth the width of the image, i.e. the number of pixels per row
	 * @param imageHeight the height of the image, i.e. the number of rows of pixels
	 * @param bytesPerPixel the number of bytes used to encode a single pixel
	 * 
	 * @return a BufferedImage based on a DataBufferInt type of raster
	 */
	private BufferedImage createImageDataBufferInt(byte[] copyBytes, int imageWidth, 
			int imageHeight, int bytesPerPixel) {

		int[] copyInts = new int[imageWidth * imageHeight];
		int j = 0;
		
		for (int i = 0; i < copyBytes.length; i += bytesPerPixel) {
			
			int p = 0;
			for (int m = 0; m < masks.length; m++) {
				p = p | ((copyBytes[i + m] & 0xff));
				if (m < masks.length - 1) {
					p = p << 8;
				}
			}
			copyInts[j++] = p;
		}
		
		// create a databuffer
		DataBufferInt dataBufferInt = new DataBufferInt(copyInts, copyInts.length);
		
		if (sampleModel == null) {
			sampleModel = createCorrespondingSampleModel();
		}
		WritableRaster raster = Raster.createWritableRaster(sampleModel, 
				dataBufferInt, null);
		
		if (colorModel == null) {
			colorModel = createCorrespondingColorModel();
		}

		return new BufferedImage(colorModel, raster, true, null);
	}
	
	/**
	 * Grabs the bytes of a video frame and stores them in the ByteBuffer, shared between
	 * Java and the native interface.
	 *
	 * @param sampleTime the time to get the frame for
	 * @param buffer the buffer to copy the bytes into, the buffer must be of sufficient size
	 * @return the actual number of copied bytes
	 */
	/*
	public int grabVideoFrame(long sampleTime, ByteBuffer buffer) {
		if (!fieldsInited) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("No pixel bytes: the FrameGrabber was not correctly initialized");
			}
			return 0;
		}

		return grabVideoFrame(id, sampleTime, buffer);
	}
	*/
	/**
	 * Grabs the bytes of a video frame and stores them in the byte[].
	 *
	 * @param sampleTime the time to get the frame for
	 * @param byteArray the byte array to copy the bytes into, the array must be of sufficient size
	 * @return the actual number of copied bytes
	 */
	/* currently not supported
	public int grabVideoFrameByteArray(long sampleTime, byte[] byteArray) {
		if (!fieldsInited) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("No pixel bytes: the FrameGrabber was not correctly initialized");
			}
			return 0;
		}

		return grabVideoFrameBA(id, sampleTime, byteArray);
	}
	*/
	/**
	 * Saves the video frame in the specified location using native code. If the file
	 * already exists it will be overwritten without warning.
	 *
	 * @param imageURL the location to store the image. The type of the image is based on
	 * the file extension, .png in case of unknown or unsupported image format. Other
	 * supported formats are .jpg and .bmp.
	 * @param sampleTime the time for which to save the frame image
	 *
	 * @return true if the file was saved successfully, false otherwise
	 */
	/* currently not supported
	public boolean saveFrameNative(String imageURL, long sampleTime) {
		if (!fieldsInited) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not save the image: the FrameGrabber was not correctly initialized");
			}
			return false;
		}

		return saveFrameNative(id, imageURL, sampleTime);
	}
	*/
	
	/**
	 * Releases the native resources from memory when done with this class.
	 */
	public void release() {
		if (id > 0) {
			release(id);
		}
	}

	// getters for details of the image if the caller loads the bytes into a ByteBuffer
	/**
	 * @return the number of bytes necessary for storing the pixels of a single video frame
	 */
	public int getNumBytesPerFrame() {
		return numBytesPerFrame;
	}

	/**
	 * @return the number of bytes per row
	 */
	public int getNumBytesPerRow() {
		return numBytesPerRow;
	}

	/**
	 * 
	 * @return the number of bits necessary for storing a single pixel
	 */
	public int getNumBitsPerPixel() {
		return numBitsPerPixel;
	}

	/**
	 * Returns 8.
	 * @return the number of bits per pixel component, e.g. the R (or G or B etc.) component
	 */
	public int getNumBitsPerPixelComponent() {
		return numBitsPerPixelComponent;
	}

	/**
	 * @return the width in pixels of a frame image, this can be different from the videoWidth
	 */
	public int getImageWidth() {
		return imageWidth;
	}

	/**
	 * @return the height in pixels of a frame image, this can be different from the videoHeight
	 */
	public int getImageHeight() {
		return imageHeight;
	}

	/**
	 * @return the width of the video, can be different from the decoded frame image width
	 */
	public int getVideoWidth() {
		return videoWidth;
	}

	/**
	 * @return the height of the video, can be different from the decoded frame image height
	 */
	public int getVideoHeight() {
		return videoHeight;
	}

	/**
	 * @return the duration of the video in milliseconds
	 */
	public long getVideoDuration() {
		return videoDuration;
	}

	/**
	 * @return whether or not a byte array should be used when grabbing an image,
	 * instead of a nio.ByteBuffer
	 *
	 * @see #getVideoFrameImage(long)
	 */
	/* currently not supported
	public boolean isUseByteArray() {
		return useByteArray;
	}
	*/
	
	/**
	 * Sets the flag that determines whether a nio.ByteBuffer is used to receive the pixels
	 * from the native AVFoundation or a byte array
	 *
	 * @param useByteArray the new value of this flag
	 * @see #getVideoFrameImage(long)
	 */
	/* currently not supported
	public void setUseByteArray(boolean useByteArray) {
		this.useByteArray = useByteArray;
	}
	*/

	/**
	 * Initializes the native Media Foundation Source Reader and generates an id for it.
	 * @param mediaPath the path or url of the video
	 * @return an id which serves as a key for storing and retrieving the generator
	 * in and from a map or dictionary.
	 */
	private native long initNative(String mediaPath);

	private native int grabVideoFrame(long id, long sampleTime, ByteBuffer buffer);
	/* This is not supported (yet). */
	private native int grabVideoFrameBA(long id, long sampleTime, byte[] byteArray);
	/* This is not supported (yet). */
	private native boolean saveFrameNative(long id, String imageURL, long sampleTime);

	private native void release(long id);

	private native int getBytesPerRow(long id);
	private native int getBytesPerPixel(long id);
	private native int getVideoImageWidth(long id); //the width in pixels of the video image buffer
	private native int getVideoImageHeight(long id);//the height in pixels of the video image buffer	
	// global native debug setting
	public static native void setDebugMode(boolean debugMode);
	public static native boolean isDebugMode();
}
