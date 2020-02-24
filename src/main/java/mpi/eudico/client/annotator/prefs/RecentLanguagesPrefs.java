package mpi.eudico.client.annotator.prefs;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import mpi.eudico.client.annotator.Constants;
import mpi.eudico.client.annotator.FrameManager;
import mpi.eudico.client.annotator.ShutdownListener;
import mpi.eudico.client.annotator.util.ClientLogger;
import mpi.eudico.util.multilangcv.RecentLanguages;

/**
 * This class initializes the languages that were "recently seen" in CVs and 
 * tiers etc. The actual list implementation moved to 
 * {@link RecentLanguages}, this class manages the location of stored 
 * recent languages and triggers loading and saving of the language list.
 */
public class RecentLanguagesPrefs implements ShutdownListener {

	public static final String privatePreferencesFile = "RecentLanguages.xml";
	private boolean recentLanguagesLoaded = false;
	
	private static RecentLanguagesPrefs instance;
	
	/**
	 * Get the singleton RecentLanguagesPrefs instance.
	 * To be called from the GUI thread: this is not thread-safe.
	 * 
	 * @return the single instance
	 */
	public static RecentLanguagesPrefs getInstance() {
		if (instance != null) {
			return instance;
		}
		instance = new RecentLanguagesPrefs();
		return instance;
	}

	/**
	 * Nobody else should be able to call the constructor directly.
	 * The first time this instance is needed, the stored recent languages
	 * are loaded.
	 */
	private RecentLanguagesPrefs() {
		loadPrivatePreferences();
	}

	/**
	 * Passes the location of the stored languages to {@link RecentLanguages} 
	 * where the languages are loaded.
	 * Convert the Preferences values describing the list of recently used languages
	 * to a private form which is easier to process.
	 */	
	private void loadPrivatePreferences() {
		if (recentLanguagesLoaded) {
			// We've already done the work before...
			return;
		}
		recentLanguagesLoaded = true;// regardless of success
		
        try {
            String fileName = Constants.ELAN_DATA_DIR + File.separator + privatePreferencesFile;
            RecentLanguages.getInstance().loadRecentLanguages(fileName);
        } catch (IOException e) {
            if (ClientLogger.LOG.isLoggable(Level.FINE)) {
            	ClientLogger.LOG.fine(e.getMessage());
            }
		} catch (Throwable t) {
            if (ClientLogger.LOG.isLoggable(Level.FINE)) {
            	ClientLogger.LOG.fine(t.getMessage());
            }
		}
        
        FrameManager.getInstance().addWindowCloseListener(this);
	}
	
	/**
	 * Save the recent languages to a private "preferences" file.
	 * Create DOM tree and then save it.
	 */
	private void savePrivatePreferences() {
        try {
        	 String fileName = Constants.ELAN_DATA_DIR + File.separator + privatePreferencesFile;
        	 RecentLanguages.getInstance().saveRecentLanguages(fileName);
        } catch (IOException ioe) {
            if (ClientLogger.LOG.isLoggable(Level.FINE)) {
            	ClientLogger.LOG.fine(ioe.getMessage());
            }
        } catch (Throwable t) {
            if (ClientLogger.LOG.isLoggable(Level.FINE)) {
            	ClientLogger.LOG.fine(t.getMessage());
            }
        }
	}

	/**
	 * Called when a window, or Elan, is closing.
	 */
	@Override
	public void somethingIsClosing(ShutdownListener.Event e) {
		if (e.getType() == Event.ELAN_EXITS_EARLY) {
			savePrivatePreferences();
		}
	}
}