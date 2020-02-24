package nl.mpi.avf.player;

import java.awt.Image;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts and converts video frame images from a ByteBuffer 
 * containing the bytes for a number of images. Extracting the 
 * actual images depends on color model, the number of bytes per pixel,
 * image width and image height etc.
 *  
 * @author Han Sloetjes
 */
public class JAVFImageProducer {
	private final Logger LOG = Logger.getLogger("AVF");
	
	public enum IMAGE_PRESETS {
		AV_24RGB_DATABUFFER_INT,
		AV_24RGB_DATABUFFER_BYTE,
		AV_32BGRA_DATABUFFER_INT,
		AV_32BGRA_DATABUFFER_BYTE,
		AV_32ARGB_DATABUFFER_INT,
		AV_32ARGB_DATABUFFER_BYTE
		// Bi-Planar and gray scale could be added
	}
	private IMAGE_PRESETS curImagePreset;
	
	private ComponentColorModel comColorModel;
	private ColorModel colorModel;
	private SampleModel sampleModel;
	// a default of 3 or 4 bytes per pixel
	private int bytesPerPixel = 3; // should be in accordance with length of bandOrder array, better use that
	// mapping of AVFoundation PixelFormatType to Java color component band order
	// AVFoundation 32BGRA corresponds to {2,1,0,3} in Java 8 and higher, to {1,2,3,0} in Java 6
	// AVFoundation 32ARGB corresponds to {1,2,3,0} in Java 8 and higher, to {?} in Java 6
	private int[] bandOrder = new int[]{0,1,2}; // {1,2,3,0} // {0,1,2,3}	
	/*
	 * For native pixel format@(kCVPixelFormatType_32ARGB)
	 * ComponentColorModel
	 * possible combinations of 
	 * bytesPerPixel, bandOrder and ColorSpace
	 * 1              {1} (or {2})  ColorSpace.CS_GRAY
	 * 3              {1,2,3}       ColorSpace.CS_LINEAR_RGB
	 * 3              {1,2,3}       ColorSpace.CS_sRGB hasAlpha=false, isAlphaPremultiplied=true/false
	 * 4              {1,2,3,0}     ColorSpace.CS_sRGB hasAlpha=true/false, isAlphaPremultiplied=true/false
	 * For native pixel format @(kCVPixelFormatType_24RGB)
	 * 3              {0,1,2}       ColorSpace.CS_sRGB hasAlpha=false, isAlphaPremultiplied=true/false
	 * For native pixel format @(kCVPixelFormatType_16Gray) (Decoder error, native)
	 * 1              {0}           ColorSpace.CS_GRAY
	 */
	// color masks, based on default ARGB order
	final int AM = 0xff000000;
	final int RM = 0x00ff0000;// 0xff0000
	final int GM = 0x0000ff00;// 0xff00
	final int BM = 0x000000ff;// 0xff
	// the actual order used for the pixel samples
	int[] masks = new int[]{RM, GM, BM};
	
	/**
	 * Constructor initializes a default color model (ComponentColorModel
	 * with sRGB color space).
	 */
	public JAVFImageProducer() {
		setDefaultImagePreset();
	}

	/**
	 * The default is color space sRGB and data type BYTE_BUFFER
	 */
	/*
	private void setDefaultColorModel() {
		comColorModel = new ComponentColorModel(
				ColorSpace.getInstance(ColorSpace.CS_sRGB), 
				false, true, // hasAlpha, isAlphaPremultiplied
				Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
	}
	*/
	private void setDefaultImagePreset() {
		setImagePreset(IMAGE_PRESETS.AV_24RGB_DATABUFFER_INT);
	}
	
	/**
	 * Switches between different modes of image production. This must be in
	 * accordance with the pixel buffer format settings of the native decoding!
	 * 
	 * @param nextPreset the new image encoding presets
	 */
	public void setImagePreset(IMAGE_PRESETS nextPreset) {
		if (nextPreset != curImagePreset) {
			switch (nextPreset) {
			case AV_24RGB_DATABUFFER_BYTE:
				bandOrder = new int[]{0,1,2};
				masks = new int[]{RM, GM, BM};// ignored in this case
				bytesPerPixel = 3;
				colorModel = new ComponentColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 
						false, true, // hasAlpha, isAlphaPremultiplied
						Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
				
				break;
			case AV_24RGB_DATABUFFER_INT:
				bandOrder = new int[]{0,1,2};// ignored
				masks = new int[]{RM, GM, BM};
				bytesPerPixel = masks.length;
				colorModel = new DirectColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 24, // 32 bits or does 24 suffice in this case?
						masks[0], masks[1], masks[2], 
						0,// 0 for alpha means no transparency
					    true, // isAlphaPremultiplied
						DataBuffer.TYPE_INT);
				
				break;
			case AV_32ARGB_DATABUFFER_BYTE:
				bandOrder = new int[]{1,2,3};// {1,2,3,0} with hasAlpha true, {1,2,3} with hasAlpha false, bytesPerPixel 4
				masks = new int[]{RM, GM, BM};// ignored
				bytesPerPixel = 4;
				colorModel = new ComponentColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 
						false, true, // hasAlpha, isAlphaPremultiplied
						Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
				
				break;
			case AV_32ARGB_DATABUFFER_INT:
				// doesn't work with a masks array of size 3 without changing the code that combines bytes into an int in produceImagesFromBufferInt
				bandOrder = new int[]{1,2,3,0};// ignored
				masks = new int[]{RM, GM, BM, AM};// for 32ARGB this works: {RM, GM, BM, AM} + masks[0], masks[1], masks[2], masks[3]
				bytesPerPixel = masks.length;
				colorModel = new DirectColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 32, 
						masks[0], masks[1], masks[2], 
						masks[3],// 0 for alpha means no transparency
					    true, // isAlphaPremultiplied
						DataBuffer.TYPE_INT);
				
				break;
				
			case AV_32BGRA_DATABUFFER_BYTE:
				bandOrder = new int[]{2,1,0}; // {2,1,0,3} + hasAlpha=true, {2,1,0} + hasAlpha=false
				masks = new int[]{RM, GM, BM};// ignored
				bytesPerPixel = 4;
				colorModel = new ComponentColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 
						false, true, // hasAlpha, isAlphaPremultiplied
						Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
				
				break;
			case AV_32BGRA_DATABUFFER_INT:
				bandOrder = new int[]{2,1,0};// ignored
				masks = new int[]{BM, GM, RM};// {BM, GM, RM} + {2,1,0,3} or {2,1,0} without transparency 
				bytesPerPixel = masks.length;
				colorModel = new DirectColorModel(
						ColorSpace.getInstance(ColorSpace.CS_sRGB), 24, // 32 or 24 without transparency
						masks[0], masks[1], masks[2],
						0,// 0 for alpha means no transparency
					    true, // isAlphaPremultiplied
						DataBuffer.TYPE_INT);
				
				break;
			default:
				break;
			}
			
			bytesPerPixel = bandOrder.length;
			sampleModel = null;//reset
			curImagePreset = nextPreset;
		}
	}

	
	/**
	 * Sets the color model. It creates a {@link ComponentColorModel}.
	 * 
	 * @param colorSpaceType the color space for the model
	 * @param dataType the data type for the model, one of the {@link DataBuffer} type constants
	 * @param hasAlpha whether or not the ColorModel has data for transparency
	 */
	public void setColorModel(int colorSpaceType, int dataType, boolean hasAlpha) {
		try {
			colorModel = new ComponentColorModel(
				ColorSpace.getInstance(colorSpaceType), 
				hasAlpha, true, // hasAlpha, isAlphaPremultiplied
				Transparency.OPAQUE, dataType);
		} catch (IllegalArgumentException e) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Could not change the color model: " + e.getMessage());
			}
		}
	}
	
	/**
	 * Sets the number of bytes needed to encode one pixel
	 * @param numBytesPerPixel the number of bytes that encode one pixel
	 */
	/*
	public void setNumBytesPerPixel(int numBytesPerPixel) {
		bytesPerPixel = numBytesPerPixel;
	}
	*/
	/**
	 * Sets the band order of the color component, indicating in which order 
	 * e.g. the r, g, b, and alpha bands are encoded in the buffer
	 * @param order the band order
	 */
	/*
	public void setColorComponentBandOrder(int[] order) {
		bandOrder = order;
	}
	*/
	// Color model, bytes per pixel, band order should be in accordance with each other,
	// with the AV Foundation decoding settings and with the DataBuffer.TYPE and Raster type
	// Have one method to change the configuration to one of the known and supported configurations,
	// trusting that the provided ByteBuffer to produce the images from is in accordance with the settings here.
	
	/**
	 * Produces an array of images extracted from the specified buffer of bytes and
	 * based on bytes per image, and width and height of the images etc.
	 * 
	 * @param byteBuffer the buffer containing the bytes of multiple video images
	 * @param bytesPerImage the number of bytes that encode one image
	 * @param numImages the number of images to extract from the buffer
	 * @param width the width of each image
	 * @param height the height of each image
	 * @return an array of images
	 */
	public Image[] produceImagesFromBuffer(ByteBuffer byteBuffer, int bytesPerImage, 
			int numImages, int width, int height) {
		switch(curImagePreset) {
		case AV_24RGB_DATABUFFER_INT:
			// fall through
		case AV_32ARGB_DATABUFFER_INT:
			// fall through
		case AV_32BGRA_DATABUFFER_INT:
			if (bytesPerImage == width * height * bytesPerPixel) {
				return produceImagesFromBufferInt(byteBuffer, bytesPerImage, 
					numImages, width, height);
			} else {
				return produceImagesFromBufferIntCorrectForStride(byteBuffer, bytesPerImage, 
						numImages, width, height);
			}
		case AV_24RGB_DATABUFFER_BYTE:
			// fall through
		case AV_32ARGB_DATABUFFER_BYTE:
			// fall through
		case AV_32BGRA_DATABUFFER_BYTE:
			return produceImagesFromBufferByte(byteBuffer, bytesPerImage, 
					numImages, width, height);
			default:
				return null;
		}
	}
	
	/*
	 * Variant based on native 24RGB/32BGRA/32ARGB input and TYPE_BYTE 
	 * PixelInterleavedSampleModel (implicitly)
	 * image encoding for the buffered images.
	 */
	private Image[] produceImagesFromBufferByte(ByteBuffer byteBuffer, int bytesPerImage, 
			int numImages, int width, int height) {
//		byte[] copyBytes = new byte[bytesPerImage];
		BufferedImage[] images = new BufferedImage[numImages];
		int imgIndex = 0;
		int position = 0;
		
		while (imgIndex < numImages && position <= byteBuffer.capacity() - bytesPerImage) {
			byte[] copyBytes = new byte[bytesPerImage];
			byteBuffer.get(copyBytes, 0, bytesPerImage);
			try {
				// for gray scale inverted WhiteIsZero
//				for (int i = 0 ; i < copyBytes.length; i++) {
//					//copyBytes[i] = (byte) -copyBytes[i];
//					copyBytes[i] = (byte) ~copyBytes[i];
//				}
				// create a databuffer
				DataBufferByte dataBufferByte = new DataBufferByte(copyBytes, bytesPerImage);
				int scanlineStride = bytesPerPixel * width;
				if (scanlineStride != (bytesPerImage / height)) {
					scanlineStride = (bytesPerImage / height);
				}
				// create a raster, can this be reused? Not with byte buffers
				WritableRaster raster = Raster.createInterleavedRaster(dataBufferByte, 
						width, height, scanlineStride, bytesPerPixel, 
						bandOrder, null);
				
				// test scaling, could scale on the Raster? No. Scaling makes the process too slow
//				BufferedImage largeBI = new BufferedImage(colorModel, raster, true, null);
//				BufferedImage smallBI = scaleDownOp.createCompatibleDestImage(largeBI, colorModel);
//				images[imgIndex] = scaleDownOp.filter(largeBI, null);
				//
				images[imgIndex] = new BufferedImage(colorModel, raster, true, null);
				
				imgIndex++;
				position += bytesPerImage;
				byteBuffer.position(position);
			} catch (RasterFormatException rfe) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images, invalid raster format: " + rfe.getMessage());
				}
				break;
			} catch (IllegalArgumentException iae) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images: " + iae.getMessage());
				}
				break;
			}
		}
		// rewind buffer if needed
//		if (byteBuffer.remaining() > 0) {
			byteBuffer.rewind();
//		}
		
		return images;
	}
	
	/*
	 * Variant based on native 24RGB (32BGRA/32ARGB?) input and TYPE_INT 
	 * SinglePixelPackedSampleModel image encoding for the buffered images.
	 */
	private Image[] produceImagesFromBufferInt(ByteBuffer byteBuffer, int bytesPerImage, 
			int numImages, int width, int height) {
		BufferedImage[] images = new BufferedImage[numImages];
		int imgIndex = 0;
		int position = 0;

		if (sampleModel == null) {
			sampleModel = new SinglePixelPackedSampleModel(
				DataBuffer.TYPE_INT, width, height, masks);
		}
		
		while (imgIndex < numImages && position <= byteBuffer.capacity() - bytesPerImage) {
			byte[] copyBytes = new byte[bytesPerImage];
			byteBuffer.get(copyBytes, 0, bytesPerImage);
			try {
				// create int array
				//int[] copyInts = new int[copyBytes.length / bytesPerPixel];
				int[] copyInts = new int[width * height];
				int j = 0;
				// combine 3 RGB bytes into a single int value, converting the bytes to unsigned int first
				for (int i = 0; i < copyBytes.length; i += bytesPerPixel) { 
//					int p = 0 | (copyBytes[i] & 0xff); // or Byte.toUnsignedInt(copyBytes[i]);
//					p = p << 8;
//					p = p | (copyBytes[i + 1] & 0xff);
//					p = p << 8;
//					p = p | (copyBytes[i + 2] & 0xff);
					/*
					int p = (0 | (copyBytes[i] & 0xff)) << 8;
					p = (p | (copyBytes[i + 1] & 0xff)) << 8;
					p = p | (copyBytes[i + 2] & 0xff);
					*/
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

				WritableRaster raster = Raster.createWritableRaster(sampleModel, 
						dataBufferInt, null);
				
				images[imgIndex] = new BufferedImage(colorModel, raster, true, null);
				
				imgIndex++;
				position += bytesPerImage;
				byteBuffer.position(position);
			} catch (RasterFormatException rfe) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images, invalid raster format: " + rfe.getMessage());
				}
				break;
			} catch (IllegalArgumentException iae) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images: " + iae.getMessage());
				}
				break;
			}
		}
		// rewind buffer
		//if (byteBuffer.remaining() > 0) {
			byteBuffer.rewind();
		//}
		return images;
	}
	
	/*
	 * Variant based on native 24RGB (32BGRA/32ARGB?) input and TYPE_INT 
	 * SinglePixelPackedSampleModel image encoding for the buffered images.
	 * This variant has to be called if the scanline stride is greater than the width of
	 * the image * the byte per pixel. It is assumed that in such case the scanline
	 * is padded at the end (to the right) with zero's. 
	 */
	private Image[] produceImagesFromBufferIntCorrectForStride(ByteBuffer byteBuffer, int bytesPerImage, 
			int numImages, int width, int height) {
		BufferedImage[] images = new BufferedImage[numImages];
		int imgIndex = 0;
		int position = 0;

		if (sampleModel == null) {
			sampleModel = new SinglePixelPackedSampleModel(
				DataBuffer.TYPE_INT, width, height, masks);
		}
		// check scanline stride
		int scline = bytesPerPixel * width;
		int scline2 = bytesPerImage / height;
		int scplus = 0;
		if (scline != scline2) {
			scplus = scline2 - scline;
		}
		
		while (imgIndex < numImages && position <= byteBuffer.capacity() - bytesPerImage) {
			byte[] copyBytes = new byte[bytesPerImage];
			byteBuffer.get(copyBytes, 0, bytesPerImage);
			try {
				// create int array
				//int[] copyInts = new int[copyBytes.length / bytesPerPixel];
				int[] copyInts = new int[width * height];
				int j = 0;
				// combine 3 RGB bytes into a single int value, converting the bytes to unsigned int first
				for (int i = 0; i < copyBytes.length; i += bytesPerPixel) { 
//					int p = 0 | (copyBytes[i] & 0xff); // or Byte.toUnsignedInt(copyBytes[i]);
//					p = p << 8;
//					p = p | (copyBytes[i + 1] & 0xff);
//					p = p << 8;
//					p = p | (copyBytes[i + 2] & 0xff);
					/*
					int p = (0 | (copyBytes[i] & 0xff)) << 8;
					p = (p | (copyBytes[i + 1] & 0xff)) << 8;
					p = p | (copyBytes[i + 2] & 0xff);
					*/
					int p = 0;
					for (int m = 0; m < masks.length; m++) {
						p = p | ((copyBytes[i + m] & 0xff));
						if (m < masks.length - 1) {
							p = p << 8;
						}
					}
					copyInts[j++] = p;
					if (scplus > 0 && (i + bytesPerPixel + scplus) % scline2 == 0) {
						i += scplus;
					}
				}
				
				// create a databuffer
				DataBufferInt dataBufferInt = new DataBufferInt(copyInts, copyInts.length);

				WritableRaster raster = Raster.createWritableRaster(sampleModel, 
						dataBufferInt, null);
				
				images[imgIndex] = new BufferedImage(colorModel, raster, true, null);
				
				imgIndex++;
				position += bytesPerImage;
				byteBuffer.position(position);
			} catch (RasterFormatException rfe) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images, invalid raster format: " + rfe.getMessage());
				}
				break;
			} catch (IllegalArgumentException iae) {
				// if this fails once it will fail for all images in the buffer
				if (LOG.isLoggable(Level.WARNING)) {
					LOG.warning("Cannot convert to images: " + iae.getMessage());
				}
				break;
			}
		}
		// rewind buffer
//		if (byteBuffer.remaining() > 0) {
			byteBuffer.rewind();
//		}
		return images;
	}
	/* band orders
	 * {0,1,2,3}
	 * {0,1,3,2}
	 * {0,2,1,3}
	 * {0,2,3,1}
	 * {0,3,1,2}
	 * {0,3,2,1}
	 * 
	 * {1,0,2,3}
	 * {1,0,3,2}
	 * {2,0,1,3}
	 * {2,0,3,1}
	 * {3,0,1,2}
	 * {3,0,2,1}
	 * 
	 * {1,2,0,3}
	 * {1,3,0,2}
	 * {2,1,0,3}
	 * {2,3,0,1}
	 * {3,1,0,2}
	 * {3,2,0,1}
	 * 
	 * {1,2,3,0}
	 * {1,3,2,0}
	 * {2,1,3,0}
	 * {2,3,1,0}
	 * {3,1,2,0}
	 * {3,2,1,0}
	 */
	
//	AffineTransformOp scaleDownOp = new AffineTransformOp(
//	new AffineTransform(0.5, 0.0, 0.0, 0.5, 0.0, 0.0), AffineTransformOp.TYPE_BICUBIC);
// test scaling, could scale on the Raster? No. Scaling makes the process too slow
//	BufferedImage largeBI = new BufferedImage(colorModel, raster, true, null);
//	BufferedImage smallBI = scaleDownOp.createCompatibleDestImage(largeBI, colorModel);
//	images[imgIndex] = scaleDownOp.filter(largeBI, null);
	//
}
