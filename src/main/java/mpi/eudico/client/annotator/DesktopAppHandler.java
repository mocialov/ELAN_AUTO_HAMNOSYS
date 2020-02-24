package mpi.eudico.client.annotator;

import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.io.File;

import mpi.eudico.client.annotator.commands.global.AboutMA;
import mpi.eudico.client.annotator.commands.global.EditPreferencesMA;
import mpi.eudico.client.annotator.commands.global.MenuAction;
import mpi.eudico.client.annotator.util.ClientLogger;

/**
 * A class that adds several handlers to the Desktop instance (About, Preferences etc.)
 */
public class DesktopAppHandler {
	private static DesktopAppHandler dappHandler;
	private DesktopAdapter deskAdapter;
	
	/**
	 * Private constructor
	 */
	private DesktopAppHandler() {	
	}

	/**
	 * @return the single instance of this class
	 */
	public static DesktopAppHandler getInstance() {
		if (dappHandler == null) {
			dappHandler = new DesktopAppHandler();
		}
		
		return dappHandler;
	}
	
	/**
	 * Creates and adds handlers to the Desktop instance once.
	 */
	public void setHandlers() {
		if (deskAdapter == null) {
			deskAdapter = new DesktopAdapter();
			try {
				Desktop.getDesktop().setAboutHandler(deskAdapter);
			} catch (UnsupportedOperationException uoe) {
				ClientLogger.LOG.info(uoe.getMessage());
			}
			try {
				Desktop.getDesktop().setQuitHandler(deskAdapter);
			} catch (UnsupportedOperationException uoe) {
				ClientLogger.LOG.info(uoe.getMessage());
			}
			
			try {
				Desktop.getDesktop().setOpenFileHandler(deskAdapter);
			} catch (UnsupportedOperationException uoe) {
				ClientLogger.LOG.info(uoe.getMessage());
			}
			
			try {
				Desktop.getDesktop().setPreferencesHandler(deskAdapter);
			} catch (UnsupportedOperationException uoe) {
				ClientLogger.LOG.info(uoe.getMessage());
			}
//			Desktop.getDesktop().setPrintFileHandler(deskAdapter);
		}
	}
	
	/**
	 * An adapter that implements several system event listeners.
	 */
	class DesktopAdapter implements AboutHandler, QuitHandler, PreferencesHandler, OpenFilesHandler {
		
		/**
		 * Handles an application-external Quit action.
		 * 
		 * @param qe the QuitEvent
		 * @param response a response object which must be called back with either a "cancelled"
		 * or "performed" message
		 */
		@Override
		public void handleQuitRequestWith(QuitEvent qe, QuitResponse response) {
			System.out.println("Quit");
			
			FrameManager.getInstance().exit();
			// System.out.println("After Quit");
			// this will only be called if FrameManager's exit did not call System.exit()
			// response.performQuit() doesn't ever have to be called
			response.cancelQuit();
		}
	
		/**
		 * Handles an About action by showing the About frame.
		 * 
		 * @param e the AboutEvent
		 */
		@Override
		public void handleAbout(AboutEvent e) {
			System.out.println("About");
			
	        MenuAction ma = new AboutMA("Menu.Help.About", (ElanFrame2) FrameManager.getInstance().getActiveFrame());
	        ma.actionPerformed(null);
		}

		
		/**
		 * Opens the preferences dialog.
		 * 
		 * @param e the preferences event
		 */
		@Override
		public void handlePreferences(PreferencesEvent e) {
			System.out.println("Preferences");
	        MenuAction ma2 = new EditPreferencesMA("Menu.Edit.Preferences.Edit",
	        		(ElanFrame2) FrameManager.getInstance().getActiveFrame());
	        ma2.actionPerformed(null);
		}

		/**
		 * A request to open the files that are in the list contained in the 
		 * event object.
		 * @param e the open files event, contains the list of files to open.
		 */
		@Override
		public void openFiles(OpenFilesEvent e) {
			System.out.println("Open");
			for (File f : e.getFiles()) {
	            try {
	                if (!f.exists() || f.isDirectory()) {
	                    ClientLogger.LOG.info("Cannot open file: " + f.getName());

	                    continue;
	                }

	                FrameManager.getInstance().createFrame(f.getAbsolutePath());
	            } catch (Exception ex) {
	                ClientLogger.LOG.info("Cannot open file: " + ex.getMessage());
	            }
			}
		}
	}

}
