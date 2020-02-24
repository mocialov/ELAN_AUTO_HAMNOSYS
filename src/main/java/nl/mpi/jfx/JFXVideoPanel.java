package nl.mpi.jfx;

import java.awt.Dimension;
import java.awt.Rectangle;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.media.MediaView;

/**
 * A JFXPanel for a scene with a media view as first child node. 
 * 
 * @author Han Sloetjes
 */
@SuppressWarnings("serial")
public class JFXVideoPanel extends JFXPanel {
	private MediaView mediaView;

	/**
	 * No-arg constructor
	 */
	public JFXVideoPanel() {
		super();
		setLayout(null);//??
	}

	/**
	 * Retrieves the MediaView node (if part of the Scene).
	 * 
	 * Should be called on the JFX thread
	 * @param the scene that is added to the panel
	 */
	@Override
	public void setScene(Scene scene) {
		super.setScene(scene);
		if (scene != null) {
			if (scene.getRoot().getChildrenUnmodifiable().size() > 0) {
				Node node = scene.getRoot().getChildrenUnmodifiable().get(0);
				if (node instanceof MediaView) {
					mediaView = (MediaView) node;
					setMediaViewBounds();
				}
			}
		} else {
			mediaView = null;
		}
	}

	/**
	 * In the most simple case the media's view bounds coincide with the bounds of
	 * the panel. This assumes that the JFXPanel is sized such that it respects the 
	 * aspect ratio of the media.
	 * Once zooming and panning are implemented setting the viewport becomes relevant as well
	 */
	private void setMediaViewBounds() {
		if (mediaView != null) {
			// perform on the FX Application thread, needed for Windows (at least)
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					mediaView.setX(0d);// this might be superfluous in most cases
					mediaView.setY(0d);
					mediaView.setFitWidth(JFXVideoPanel.this.getWidth());
					mediaView.setFitHeight(JFXVideoPanel.this.getHeight());
				}});
			
//			}
//			mediaView.setX(0d);// this might be superfluous in most cases
//			mediaView.setY(0d);
//			mediaView.setFitWidth(this.getWidth());
//			mediaView.setFitHeight(this.getHeight());
		}
	}

	@Override
	public void setSize(int width, int height) {
		super.setSize(width, height);
		setMediaViewBounds();
	}

	@Override
	public void setSize(Dimension d) {
		super.setSize(d);
		setMediaViewBounds();
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		setMediaViewBounds();
	}

	@Override
	public void setBounds(Rectangle r) {
		super.setBounds(r);
		setMediaViewBounds();
	}
	
	
}
