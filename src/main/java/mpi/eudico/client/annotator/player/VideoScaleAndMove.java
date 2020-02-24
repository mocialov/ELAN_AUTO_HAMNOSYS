package mpi.eudico.client.annotator.player;

/**
 * Interface for video zoom and pan behavior, to be implemented by media players
 * that support this functionality. 
 */
public interface VideoScaleAndMove {
	/**
	 * The scale is (or will most likely be) 1.0 or higher, so that the video
	 * image will not be smaller than the canvas it is displayed on.
	 * A value of 2.0 corresponds to a zoom level of 200% etc.
	 * 
	 * @return the current scale factor
	 */
	public float getVideoScaleFactor();
	
	/**
	 * @see #getVideoScaleFactor()
	 * @param scaleFactor the new scale factor for the video display
	 */
	public void setVideoScaleFactor(float scaleFactor);
	
	/**
	 * A request to the player to update the video display.
	 */
	public void repaintVideo();
	
	/**
	 * The video bounds contain the location and size of the video images.
	 * The location consists of the coordinates of the left top corner and the values
	 * are relative to the left top corner of the video canvas (with coordinates [0,0]).
	 * The x and y values of the location will usually be <= 0. The width and height
	 * of the video bounds will usually be => canvas width and height.
	 * 
	 * @return the current video bounds, an array of size 4 (x, y, w, h)
	 */
	public int[] getVideoBounds();
	
	/**
	 * Repositions the video relative to the canvas it is displayed on.
	 * 
	 * @param x the x coordinate of the video image
	 * @param y the y coordinate of the video image
	 * @param w the width of the video image
	 * @param h the height of the video image
	 */
	public void setVideoBounds(int x, int y, int w, int h);
	
	/**
	 * Moves, translates the video relative to its current location.
	 * 
	 * @param dx the number of pixels to move the video image horizontally
	 * @param dy the number of pixels to move the video image vertically
	 */
	public void moveVideoPos(int dx, int dy);
}
