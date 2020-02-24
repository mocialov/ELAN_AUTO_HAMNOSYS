package mpi.eudico.client.annotator.player;

import java.awt.AWTPermission;
import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

import mpi.eudico.client.annotator.Constants;
import mpi.eudico.client.annotator.ElanLayoutManager;
import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.Preferences;
import mpi.eudico.client.annotator.export.ImageExporter;
import mpi.eudico.client.annotator.gui.FormattedMessageDlg;
import mpi.eudico.util.TimeFormatter;
/**
 * A mouse adapter for visual components of media players.
 * Creates context menu, handles setting the first visual player (displayed larger than other
 * media players in certain layouts), handles copying of coordinates on mouse click,
 * handles panning in case the video is zoomed into.
 * 
 * @author Han Sloetjes
 */
public class VideoMouseAdapter implements MouseListener, MouseMotionListener, ActionListener {
	private ElanMediaPlayer player;
	private VideoScaleAndMove scaledPlayer;
	private ElanLayoutManager layoutManager;
	private Component visualComponent;
	private final DecimalFormat flFormat = new DecimalFormat("#.###");
	
    private JPopupMenu popup;
    private JMenuItem durationItem;
    protected JMenuItem detachItem;
    private JMenuItem infoItem;
	private JMenuItem saveItem;
	private JMenu arMenu;
	private JRadioButtonMenuItem origRatioItem;
	private JRadioButtonMenuItem ratio_1_1_Item;
    private JRadioButtonMenuItem ratio_5_4_Item;
	private JRadioButtonMenuItem ratio_4_3_Item;
	private JRadioButtonMenuItem ratio_3_2_Item;
	private JRadioButtonMenuItem ratio_16_10_Item;
	private JRadioButtonMenuItem ratio_16_9_Item;
	private JRadioButtonMenuItem ratio_185_1_Item;
	private JRadioButtonMenuItem ratio_221_1_Item;
	private JRadioButtonMenuItem ratio_235_1_Item;
	private JRadioButtonMenuItem ratio_239_1_Item;
	private JMenuItem copyOrigTimeItem;
	private boolean detached;
	private JMenu zoomMenu;
	private JRadioButtonMenuItem zoom100;
	private JRadioButtonMenuItem zoom150;
	private JRadioButtonMenuItem zoom200;
	private JRadioButtonMenuItem zoom300;
	private JRadioButtonMenuItem zoom400;
	//private boolean allowVideoScaling = true;
	private float videoScaleFactor = 1f;
	private int dragX = 0, dragY = 0;
	
	/**
	 * Constructor.
	 * @param player the media player controlling the media play back
	 * @param layoutManager the layout manager managing the visual components size and location
	 * in a parent frame
	 * @param visualComponent the visual component of the media player
	 */
	public VideoMouseAdapter(ElanMediaPlayer player, ElanLayoutManager layoutManager, 
			Component visualComponent) {
		this.player = player;
		this.layoutManager = layoutManager;
		this.visualComponent = visualComponent;
		
		this.visualComponent.addMouseListener(this);
		this.visualComponent.addMouseMotionListener(this);
		if (this.player instanceof VideoScaleAndMove) {
			scaledPlayer = (VideoScaleAndMove) this.player;
		}
		detached = !layoutManager.isAttached(this.player);
	}

	/**
	 * Some players need to create a new visual component in the process of 
	 * detaching and attaching. This passes the new component to the listener.  
	 * 
	 * @param nextVisualComponent the new visual component
	 */
	public void updateVisualComponent(Component nextVisualComponent) {
		visualComponent.removeMouseListener(this);
		visualComponent.removeMouseMotionListener(this);
		
		visualComponent = nextVisualComponent;
		visualComponent.addMouseListener(this);
		visualComponent.addMouseMotionListener(this);
	}

	/**
	 * Moves the video image in case the zoom level is > 1 (and the image
	 * is therefore larger than the canvas).
	 * @param e the mouse dragged event
	 */
	@Override
	public void mouseDragged(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
        	return;
        }
		if (scaledPlayer == null /*|| scaledPlayer.getVideoScaleFactor() == 1*/) {
			return;
		}

		// implement with zooming/scaling
		int dx = dragX - e.getX();
		int dy = dragY - e.getY();
		scaledPlayer.moveVideoPos(-dx, -dy);
		dragX = e.getX();
		dragY = e.getY();
		
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// stub
	}

	/**
	 * A double click makes the video the first player in the layout if there are multiple videos
	 * connected. 
	 * A single click copies the location of the mouse click to the clip board. The format of the 
	 * copied coordinates depends on modifier keys. Examples are "908,437 [1280,720]" or "0.925, 0.658".
	 * @param e the mouse clicked event
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() >= 2) {
            if (layoutManager != null) {
                layoutManager.setFirstPlayer(player);
            }

            return;
        }
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
        	return;
        }
        // copy coordinates
		if (scaledPlayer != null) {
	        try {
	        	// the scaled video image bounds, equal to the canvas bounds if scale is 1
	        	// incorporates possible changes to the aspect ratio
	        	int[] videoBounds = scaledPlayer.getVideoBounds();
	        	float videoScale = scaledPlayer.getVideoScaleFactor();
	        	int canvasW = e.getComponent().getWidth();
	        	int canvasH = e.getComponent().getHeight();
	        	int x = e.getX();// the click's x coordinate in canvas space
	        	int y = e.getY();// the click's y coordinate
	        	int nx = x; // the x coordinate adjusted for translation of the scaled video
	        	int ny = y; // the y coordinate adjusted for translation of the scaled video
	        	float relX = x / (float) canvasW;
	        	float relY = y / (float) canvasH;
	        	
	        	// if scale factor is 1, video bounds w,h are equal to canvas w,h
	        	if (videoScale > 1) {
	        		nx -= videoBounds[0];
	        		ny -= videoBounds[1];
	        		relX = nx / (float) videoBounds[2];
	        		relY = ny / (float) videoBounds[3];
	        	}
	        	int sw = player.getSourceWidth();// the encoded width
	        	int sh = player.getSourceHeight();// the encoded height
	        	
	        	if (e.isAltDown() && e.isShiftDown()) {
	        		copyToClipboard(String.format("%d,%d [%d,%d]", x, y, canvasW, canvasH));
	        	} else if (e.isAltDown()) {
	        		copyToClipboard(String.format("%s,%s", flFormat.format(relX), flFormat.format(relY)));
	        	} else if (e.isShiftDown()) {
	        		// x,y adjusted for scaling, so x,y in the original video image size
	        		copyToClipboard(String.format("%d,%d", (int)((sw / (float)videoBounds[2]) * nx), 
	        				(int)((sh / (float)videoBounds[3]) * ny)));
	        	} else {
	        		// x,y adjusted for scaling, so x,y in the original video image size followed by the encoded video size
	        		copyToClipboard(String.format("%d,%d [%d,%d]", (int)((sw / (float)videoBounds[2]) * nx), 
	        				(int)((sh / (float)videoBounds[3]) * ny), sw, sh));
	        	}
	        	
	        } catch (Throwable t) {}
		}

	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// stub
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// stub
	}

	/**
	 * Creates/shows the context menu or sets the start coordinates of a drag/pan
	 * mouse movement.
	 * @param e the mouse pressed event
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		Point cl = e.getPoint();
    	
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
        	if (popup == null) {
        		createPopup();
        	}
        	// check the detached state, attaching can be done independently of the menu
        	if (layoutManager.isAttached(player)) {
        		if (detached) {
        			detached = false;
        			detachItem.setText(ElanLocale.getString("Detachable.detach"));
        		}
        	}
        	durationItem.setText(ElanLocale.getString("Player.duration") +
                    ":  " + TimeFormatter.toString(player.getMediaDuration()));
        	//System.out.println("S: " + e.getSource() + " X: " + e.getX() +  " Y: " + e.getY());
            popup.show(visualComponent, (int) cl.getX(), (int) cl.getY());
            return;
        }

		dragX = (int) cl.getX();
		dragY = (int) cl.getY();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// stub
//        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
//        	return;
//        }
	}
	/*
	    
	    	private JRadioButtonMenuItem origRatioItem;
	private JRadioButtonMenuItem ratio_1_1_Item;
    private JRadioButtonMenuItem ratio_5_4_Item;
	private JRadioButtonMenuItem ratio_4_3_Item;
	private JRadioButtonMenuItem ratio_16_10_Item;
	private JRadioButtonMenuItem ratio_16_9_Item;
	private JRadioButtonMenuItem ratio_235_1_Item;
	private JRadioButtonMenuItem ratio_221_1_Item;
	private JRadioButtonMenuItem ratio_239_1_Item;
	    
	    
	    origRatioItem = new JRadioButtonMenuItem(ElanLocale.getString("Player.ResetAspectRatio"), true);
        origRatioItem.setActionCommand("ratio_orig");
        origRatioItem.addActionListener(this);
		ratio_1_1_Item = new JRadioButtonMenuItem("1:1");
		ratio_1_1_Item.setActionCommand("ratio_1_1");
		ratio_1_1_Item.addActionListener(this);
		ratio_5_4_Item = new JRadioButtonMenuItem("5:4");
		ratio_5_4_Item.setActionCommand("ratio_5_4");
		ratio_5_4_Item.addActionListener(this);
		ratio_4_3_Item = new JRadioButtonMenuItem("4:3");
		ratio_4_3_Item.setActionCommand("ratio_4_3");
		ratio_4_3_Item.addActionListener(this);
		ratio_16_10_Item = new JRadioButtonMenuItem("16:10");
		ratio_16_10_Item.setActionCommand("ratio_16_10");
		ratio_16_10_Item.addActionListener(this);
		ratio_16_9_Item = new JRadioButtonMenuItem("16:9");
		ratio_16_9_Item.setActionCommand("ratio_16_9");
		ratio_16_9_Item.addActionListener(this);
		ratio_221_1_Item = new JRadioButtonMenuItem("2.21:1");
		ratio_221_1_Item.setActionCommand("ratio_221_1");
		ratio_221_1_Item.addActionListener(this);
		ratio_235_1_Item = new JRadioButtonMenuItem("2.35:1");
		ratio_235_1_Item.setActionCommand("ratio_235_1");
		ratio_235_1_Item.addActionListener(this);
		ratio_239_1_Item = new JRadioButtonMenuItem("2.39:1");
		ratio_239_1_Item.setActionCommand("ratio_239_1");
		ratio_239_1_Item.addActionListener(this);
	 */

	/**
	 * Creates and initializes the context menu and sub-menus.
	 */
	private void createPopup() {
		popup = new JPopupMenu();
        detachItem = new JMenuItem(ElanLocale.getString("Detachable.detach"));
        detachItem.addActionListener(this);
		infoItem = new JMenuItem(ElanLocale.getString("Player.Info"));
        infoItem.addActionListener(this);
        durationItem = new JMenuItem(ElanLocale.getString("Player.duration") +
                ":  " + TimeFormatter.toString(0));
        durationItem.setEnabled(false);
        saveItem = new JMenuItem(ElanLocale.getString("Player.SaveFrame"));
        saveItem.addActionListener(this);
        saveItem.setEnabled(player instanceof VideoFrameGrabber);
        
        origRatioItem = new JRadioButtonMenuItem(ElanLocale.getString("Player.ResetAspectRatio"), true);
        origRatioItem.setActionCommand("ratio_orig");
        origRatioItem.addActionListener(this);
		ratio_1_1_Item = new JRadioButtonMenuItem("1:1");
		ratio_1_1_Item.setActionCommand("ratio_1_1");
		ratio_1_1_Item.addActionListener(this);
		ratio_5_4_Item = new JRadioButtonMenuItem("5:4");
		ratio_5_4_Item.setActionCommand("ratio_5_4");
		ratio_5_4_Item.addActionListener(this);
		ratio_4_3_Item = new JRadioButtonMenuItem("4:3");
		ratio_4_3_Item.setActionCommand("ratio_4_3");
		ratio_4_3_Item.addActionListener(this);
		ratio_3_2_Item = new JRadioButtonMenuItem("3:2");
		ratio_3_2_Item.setActionCommand("ratio_3_2");
		ratio_3_2_Item.addActionListener(this);
		ratio_16_10_Item = new JRadioButtonMenuItem("16:10");
		ratio_16_10_Item.setActionCommand("ratio_16_10");
		ratio_16_10_Item.addActionListener(this);
		ratio_16_9_Item = new JRadioButtonMenuItem("16:9");
		ratio_16_9_Item.setActionCommand("ratio_16_9");
		ratio_16_9_Item.addActionListener(this);
		ratio_185_1_Item = new JRadioButtonMenuItem("1.85:1");
		ratio_185_1_Item.setActionCommand("ratio_185_1");
		ratio_185_1_Item.addActionListener(this);
		ratio_221_1_Item = new JRadioButtonMenuItem("2.21:1");
		ratio_221_1_Item.setActionCommand("ratio_221_1");
		ratio_221_1_Item.addActionListener(this);
		ratio_235_1_Item = new JRadioButtonMenuItem("2.35:1");
		ratio_235_1_Item.setActionCommand("ratio_235_1");
		ratio_235_1_Item.addActionListener(this);
		ratio_239_1_Item = new JRadioButtonMenuItem("2.39:1");
		ratio_239_1_Item.setActionCommand("ratio_239_1");
		ratio_239_1_Item.addActionListener(this);
		arMenu = new JMenu(ElanLocale.getString("Player.ForceAspectRatio"));
		ButtonGroup arbg = new ButtonGroup();
		arbg.add(origRatioItem);
		arbg.add(ratio_1_1_Item);
		arbg.add(ratio_5_4_Item);
		arbg.add(ratio_4_3_Item);
		arbg.add(ratio_3_2_Item);
		arbg.add(ratio_16_10_Item);
		arbg.add(ratio_16_9_Item);
		arbg.add(ratio_185_1_Item);
		arbg.add(ratio_221_1_Item);
		arbg.add(ratio_235_1_Item);
		arbg.add(ratio_239_1_Item);
		arMenu.add(origRatioItem);
		arMenu.addSeparator();
		arMenu.add(ratio_1_1_Item);
		arMenu.add(ratio_5_4_Item);
		arMenu.add(ratio_4_3_Item);
		arMenu.add(ratio_3_2_Item);
		arMenu.add(ratio_16_10_Item);
		arMenu.add(ratio_16_9_Item);
		arMenu.add(ratio_185_1_Item);
		arMenu.add(ratio_221_1_Item);
		arMenu.add(ratio_235_1_Item);	
		arMenu.add(ratio_239_1_Item);
		//arMenu.setEnabled(false);
		
		copyOrigTimeItem = new JMenuItem(ElanLocale.getString("Player.CopyTimeIgnoringOffset"));
		copyOrigTimeItem.addActionListener(this);
		zoomMenu = new JMenu(ElanLocale.getString("Menu.Zoom"));
		zoom100 = new JRadioButtonMenuItem("100%", (videoScaleFactor == 1));
		zoom100.setActionCommand("zoom100");
		zoom100.addActionListener(this);
		zoom150 = new JRadioButtonMenuItem("150%", (videoScaleFactor == 1.5));
		zoom150.setActionCommand("zoom150");
		zoom150.addActionListener(this);
		zoom200 = new JRadioButtonMenuItem("200%", (videoScaleFactor == 2));
		zoom200.setActionCommand("zoom200");
		zoom200.addActionListener(this);
		zoom300 = new JRadioButtonMenuItem("300%", (videoScaleFactor == 3));
		zoom300.setActionCommand("zoom300");
		zoom300.addActionListener(this);
		zoom400 = new JRadioButtonMenuItem("400%", (videoScaleFactor == 4));
		zoom400.setActionCommand("zoom400");
		zoom400.addActionListener(this);
		ButtonGroup zbg = new ButtonGroup();
		zbg.add(zoom100);
		zbg.add(zoom150);
		zbg.add(zoom200);
		zbg.add(zoom300);
		zbg.add(zoom400);
		zoomMenu.add(zoom100);
		zoomMenu.add(zoom150);
		zoomMenu.add(zoom200);
		zoomMenu.add(zoom300);
		zoomMenu.add(zoom400);
		zoomMenu.setEnabled(scaledPlayer != null);
		
        popup.add(detachItem);
        popup.addSeparator();
        popup.add(saveItem);
        popup.add(infoItem);
        popup.add(arMenu);
        popup.add(zoomMenu);
        popup.add(durationItem);
        popup.add(copyOrigTimeItem);
	}
	
	/**
	 * Updates the menu labels with localized texts.
	 */
	public void updateLocale() {
		if (popup != null) {
			if (!detached) {
				detachItem.setText(ElanLocale.getString("Detachable.detach"));
			} else {
				detachItem.setText(ElanLocale.getString("Detachable.attach"));
			}
			infoItem.setText(ElanLocale.getString("Player.Info"));
	        durationItem.setText(ElanLocale.getString("Player.duration") +
	                ":  " + TimeFormatter.toString(player.getMediaDuration()));
	        saveItem.setText(ElanLocale.getString("Player.SaveFrame"));
	        arMenu.setText(ElanLocale.getString("Player.ForceAspectRatio"));
	        origRatioItem.setText(ElanLocale.getString("Player.ResetAspectRatio"));
	        copyOrigTimeItem.setText(ElanLocale.getString("Player.CopyTimeIgnoringOffset"));
	        zoomMenu.setText(ElanLocale.getString("Menu.Zoom"));
		}
	}

	/**
	 * The action handling for the menu items.
	 * @param e action event
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(detachItem) && (layoutManager != null)) {
            if (detached) {
                layoutManager.attach(visualComponent);
                detachItem.setText(ElanLocale.getString("Detachable.detach"));
                detached = false;
            } else {
                layoutManager.detach(visualComponent);
                detachItem.setText(ElanLocale.getString("Detachable.attach"));
                detached = true;
            }
        } else if (e.getSource() == infoItem) {
            new FormattedMessageDlg(player);
        } else if (e.getSource() == saveItem) {
            Image frameImg = ((VideoFrameGrabber) player).getCurrentFrameImage();
            ImageExporter imgExporter = new ImageExporter();
            imgExporter.exportImage(frameImg, player.getMediaDescriptor().mediaURL, 
            		player.getMediaTime() + player.getOffset());
        } else if (e.getActionCommand().startsWith("ratio")) {
        	float aspectRatio = 0;
	        if (e.getSource() == origRatioItem) {
	        	if (player.getSourceHeight() > 0) {
	        		aspectRatio = player.getSourceWidth() / (float) player.getSourceHeight();
	        	}
			} else if (e.getSource() == ratio_4_3_Item) {
				aspectRatio = 1.33f;
			} else if (e.getSource() == ratio_3_2_Item) {
				aspectRatio = 1.66f;
			} else if (e.getSource() == ratio_16_9_Item) {
				aspectRatio = 1.78f;
			} else if (e.getSource() == ratio_185_1_Item) {
				aspectRatio = 1.85f;			
			} else if (e.getSource() == ratio_235_1_Item) {
				aspectRatio = 2.35f;
			} 
	        player.setAspectRatio(aspectRatio);
			layoutManager.doLayout(); // this does not work in detached mode
			if (e.getSource() != origRatioItem) {
				layoutManager.setPreference(("AspectRatio(" + player.getMediaDescriptor().mediaURL + ")"), 
					Float.valueOf(aspectRatio), layoutManager.getViewerManager().getTranscription());
			} else {// reset, remove
				layoutManager.setPreference(("AspectRatio(" + player.getMediaDescriptor().mediaURL + ")"), 
						null, layoutManager.getViewerManager().getTranscription());
			}
        } else if (e.getActionCommand().startsWith("zoom")) {
			if (e.getSource() == zoom100) {
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
			scaledPlayer.setVideoScaleFactor(videoScaleFactor);
			layoutManager.setPreference(("VideoZoom(" + player.getMediaDescriptor().mediaURL + ")"), 
					Float.valueOf(videoScaleFactor), layoutManager.getViewerManager().getTranscription());
        } else if (e.getSource() == copyOrigTimeItem) {
			long t = player.getMediaTime() + player.getOffset();
			String timeFormat = Preferences.getString("CurrentTime.Copy.TimeFormat", null);
			String currentTime = null;
			
	        if (timeFormat != null) {
	        	if (timeFormat.equals(Constants.HHMMSSMS_STRING)){
	            	currentTime = TimeFormatter.toString(t);
	            } else if(timeFormat.equals(Constants.SSMS_STRING)){
	            	currentTime = TimeFormatter.toSSMSString(t);
	            } else if(timeFormat.equals(Constants.NTSC_STRING)){
	            	currentTime = TimeFormatter.toTimecodeNTSC(t);
	            } else if(timeFormat.equals(Constants.PAL_STRING)){
	            	currentTime = TimeFormatter.toTimecodePAL(t);
	            } else if(timeFormat.equals(Constants.PAL_50_STRING)){
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
	
	/**
     * Puts the specified text on the clipboard (if accessible).
     * 
     * @param text the text to copy
     */
    private void copyToClipboard(String text) {
    	if (text == null) {
    	    return;
    	}
    	//System.out.println(text);
    	if (System.getSecurityManager() != null) {
            try {
                System.getSecurityManager().checkPermission(new AWTPermission("accessClipboard"));
                StringSelection ssVal = new StringSelection(text);
                
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ssVal, null);
            } catch (SecurityException se) {
                //LOG.warning("Cannot copy, cannot access the clipboard.");
            } catch (IllegalStateException ise) {
            	   // LOG.warning("");
            }
        } else {
            try {
                StringSelection ssVal = new StringSelection(text);
                
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ssVal, null);
            } catch (IllegalStateException ise) {
            	   // LOG.warning("");
            }
        }
    }
}
