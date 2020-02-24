package nl.mpi.avf.player;

import java.awt.Component;
import java.util.logging.Level;

/**
 * Implementation of an AudioVideo Foundation based media player which uses a native
 * PlayerLayer which can be embedded in or added to a Java AWT Component (e.g. a Canvas).
 * If this works it has the advantage that both decoding and rendering of video are 
 * performed natively, assuring the best performance, making optimal use of GPU where
 * possible, taking care of synchronization of audio and video on the lowest level etc.
 * 
 * Note: the name of this player can be read as "fully native", or "embedded" or
 * "decode and render natively". 
 * 
 * @author Han Sloetjes
 * 
 * @see JAVFPlayer
 */
public class AVFNativePlayer extends AVFBasePlayer {
	/** the AWT component which acts as the host or parent for the native AVPlayerLayer */
	private Component visualComponent;
	
	/**
	 * Constructor with media path
	 * 
	 * @param mediaPath the path to a media source
	 */
	public AVFNativePlayer(String mediaPath) throws JAVFPlayerException {
		super(mediaPath);
		if (id > 0) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Player type: AVFNativePlayer - rendering in a AVPlayerLayer");
			}
		}
	}
	
	/**
	 * Provides the player with an AWT or Swing component to which the native video layer
	 * will be added.
	 * It is currently assumed that this is done only once, the behavior when replacing the 
	 * host component is undefined, untested.
	 * 
	 * @param comp the host component
	 */
	public void setVisualComponent(Component comp) {
		setVisualComponent(id, comp);
		// check errors
		visualComponent = comp;
	}
	
	/**
	 * 
	 * @return the AWT or Swing component to which the native video panel is attached
	 * or null
	 */
	public Component getVisualComponent() {
		return visualComponent;
	}
	
	public void disconnectVisualComponent(Component comp) {
		disconnectVisualComponent(id, comp);
		// check errors
		//visualComponent = comp;
	}
	
	/**
	 * When deleting this player the visual component needs to be disconnected from 
	 * the native video layer(s).
	 */
	@Override
	public void deletePlayer() {
		if (id <= 0) {
			return;
		}
		if (visualComponent != null) {
			this.disconnectVisualComponent(id, visualComponent);
		}
		this.deletePlayer(id);
		this.id = -id;
	}

	/**
	 * Scaling and positioning is mainly handled on the Java side of the player.
	 * The native player might change video scaling and/or gravity setting depending
	 * on whether the scale is 1 or greater than 1.
	 * 
	 *  @param scale the scale factor
	 */
	@Override
	public void setVideoScaleFactor(float scale) {
		setVideoScaleFactor(id, scale);
	}

	/**
	 * Sets the bounds of a scaled video, has no effect of the scaling is 1.
	 * 
	 * @param x x coordinate of video image bounds
	 * @param y y coordinate of video image bounds
	 * @param w width of video image bounds
	 * @param h height of video image bounds
	 */
	@Override
	public void setVideoBounds(int x, int y, int w, int h) {
		setVideoBounds(id, x, y, w, h);
	}

	/**
	 * @return an (x,y,w,h) array or null if there is no video scaling
	 */
	@Override
	public int[] getVideoBounds() {
		return getVideoBounds(id);
	}

	/**
	 * Tries to force an update of the video.
	 */
	@Override
	public void repaintVideo() {
		repaintVideo(id);
	}

	// native methods, all (package) private
	/**
	 * Initializes the native counterpart player, returns the id for subsequent calls to 
	 * the native player. Overrides the super's initialization method by creating 
	 * an AVPLayerLayer if possible (i.e. if there is a video track).
	 * 
	 * @param mediaURL the url of a media file as a string
	 * @return the id of the new player
	 */
	@Override
	native long initPlayer(String mediaURL);
	
	/**
	 * Deletes all native resources associated with this player.
	 * 
	 * @param id the id of the player
	 */
	@Override
	native void deletePlayer(long id);

	/**
	 * Offers the AWT/Swing component to the native player as the host for the native 
	 * player layer. Based on the JAWT part of JNI.
	 * 
	 * @param id the id of the native player
	 * @param visualComponent the component 
	 * (which should conform to the JAWT_SurfaceLayers protocol)
	 */
	private native void setVisualComponent(long id, Component visualComponent);
	
	/**
	 * Informs the native player to disconnect the native player layer from the 
	 * AWT/Swing component. This can be called when the player is unloaded or 
	 * when the AWT component's hierarchy changed (detached from one window,
	 * added to another window). 
	 * Based on the JAWT part of JNI.
	 * 
	 * @param id the id of the native player
	 * @param visualComponent the component 
	 * (which should conform to the JAWT_SurfaceLayers protocol)
	 */
	private native void disconnectVisualComponent(long id, Component visualComponent);
	
	/**
	 * Inform the native player of a change in the video scaling
	 * 
	 * @param id the id of the native player
	 * @param scale the scale factor, >= 1, 1 means no scaling
	 */
	native void setVideoScaleFactor(long id, float scale);
	
	/**
	 * Set the bounds of the scaled image (player layer).
	 * 
	 * 
	 * @param id the id of the native player
	 * @param x x of top level corner of the bounds
	 * @param y y of top level corner of the bounds
	 * @param w width of the bounds
	 * @param h height of the bounds
	 */
	native void setVideoBounds(long id, int x, int y, int w, int h);
	
	/**
	 * 
	 * @param id the id of the native player
	 * @return the current bounds of the scaled image
	 */
	native int[] getVideoBounds(long id);
	
	/**
	 * Tries to force a repaint or re-display of the video image 
	 * 
	 * @param id the id of the native player
	 */
	native void repaintVideo(long id);

}
