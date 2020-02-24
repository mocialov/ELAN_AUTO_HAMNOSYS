package mpi.eudico.client.annotator;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mpi.eudico.server.corpora.clom.Tier;
import mpi.eudico.server.corpora.clom.Transcription;
import mpi.eudico.server.corpora.clom.TranscriptionStore;
import mpi.eudico.server.corpora.clomimpl.abstr.ParserFactory;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;
import mpi.eudico.server.corpora.clomimpl.dobes.ACMTranscriptionStore;
import mpi.eudico.util.CVEntry;
import mpi.eudico.util.ControlledVocabulary;
import mpi.eudico.util.multilangcv.LangInfo;

/**
 * This class contains various static methods that are helping
 * with the preferences about whether the user wants to save 
 * EAF files and Preferences in EAF 2.7 format.
 * 
 * It also contains methods to convert the CVE preferences.
 * 
 * When the format has finally be obsoleted, this can be removed again.
 * 
 * @author olasei
 */

public class SaveAs27Preferences {
	
	/**
	 * Look in the preferences whether this Transcription should be saved as the current
	 * EAF format, or the old one (2.7).
	 * 
	 * @param t Transcription (may be null) Can optionally be inspected to 
	 *        check if the preferred format is suitable.
	 * @return a TranscriptionStore.EAF* value indicating the file type.
	 */
	
	public static int saveAsType(TranscriptionImpl t) {
        boolean saveAsOld = false;
		Boolean pref = Preferences.getBool("SaveAsOldEAF2_7", null);
		if (pref != null) {
			saveAsOld = pref;
		}
		int saveAsType = saveAsOld ? TranscriptionStore.EAF_2_7
                                   : TranscriptionStore.EAF;
		
		return saveAsType;
	}

	public static int saveAsType(Transcription t) {
		TranscriptionImpl ti;
		if (t instanceof TranscriptionImpl) {
			ti = (TranscriptionImpl)t;
		} else {
			ti = null;
		}
		return saveAsType(ti);
	}
	
	/**
	 * Looks in the preferences whether the older 2.7 format is preferred,
	 * if so, checks whether data will be lost and, if so, asks for confirmation
	 * (unless a preference prevents this). If data loss is accepted, format 2.7
	 * is returned, otherwise the current, latest format is returned.
	 * 
	 * @param t the Transcription. If t is null the preference setting will determine
	 * the format type, otherwise a check will be performed on the contents of the 
	 * transcription
	 * @return the format type value, either 'old' TranscriptionStore.EAF_2_7 or 
	 * 'current' TranscriptionStore.EAF
	 */
	public static int saveAsTypeWithCheck(TranscriptionImpl t) {
		int saveAsType = saveAsType(t);
		
		if (saveAsType == TranscriptionStore.EAF_2_7 && t != null) {
			boolean saveWillLose = savingWillLoseInformation(t);
			
			if (saveWillLose) {
				boolean lossIsOk = askIfLosingInformationIsOk();
				if (!lossIsOk) {
					return TranscriptionStore.EAF;
				}// else saveAsType is returned unchanged
			}
		}
		
		return saveAsType;
	}
	
	/**
	 * 
	 * @param t the transcription to test
	 * @return the format type
	 * @see #saveAsTypeWithCheck(TranscriptionImpl)
	 */
	public static int saveAsTypeWithCheck(Transcription t) {		
		if (t instanceof TranscriptionImpl) {
			return saveAsTypeWithCheck((TranscriptionImpl)t);
		} else {
			return saveAsType(t);
		}
	}
	
	public static boolean askIfLosingInformationIsOk() {
		Boolean pref = Preferences.getBool("SaveAsOldEAF2_7.DontAskAgain", null);
		if (pref != null && pref) {
			return true;
		}
		
    	// Make sure we display the dialog only when we're on the EDT.
    	if (SwingUtilities.isEventDispatchThread()) {
	    	return askIfOk();
    	} else {
    		// Otherwise, ask Swing to execute it on the EDT.
    		class MutableBoolean {
    			boolean reply;
    		};
    		final MutableBoolean helper = new MutableBoolean();
    		helper.reply = false;
    		try {
				SwingUtilities.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						helper.reply = askIfOk();
					}});
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
    		return helper.reply;
    	}	
	}

	private static boolean askIfOk() {
    	JCheckBox dontShowAgainCB = new JCheckBox(ElanLocale.getString("Message.DontShow"));
    	String message = ElanLocale.getString("SaveAs2_7.LosesInformation");
    	String title = ElanLocale.getString("SaveAs2_7.AreYouSure");
    	Object params[] = { message, dontShowAgainCB };
    	int answer = JOptionPane.showConfirmDialog(null,
    			params,
    			title,
    			JOptionPane.YES_NO_OPTION);
    	
    	boolean dont = dontShowAgainCB.isSelected();
    	Preferences.set("SaveAsOldEAF2_7.DontAskAgain", Boolean.valueOf(dont), null, false, false);
    			
    	return answer == JOptionPane.YES_OPTION;
    }
    

	/**
	 * When saving in an old format, augment the preferences temporarily with
	 * old-format ones.
	 * Use {@link #restoreAdjustedPreferences(Transcription, Object)} to restore.
	 * 
	 * We mix, because saving in the old format is not really known to be certain;
	 * the EAF may eventually be saved as the new format.
	 */
	public static Object adjustPreferencesForSavingFormat(Transcription t, int format) {
		switch (format) {
			case TranscriptionStore.EAF_2_7:
				return convert2_7CVEntryPreferences((TranscriptionImpl)t, false, true);
			default:
				return null;
		}
	}

	/**
	 * When loading an older format, convert the older preferences to newer ones.
	 * At the moment, this is only relevant for CV preferences.
	 * <p>
	 * The old ones are stored in &lt;prefGroup key="CV.Prefs">,
	 * the new ones in &lt;prefGroup key="CV.ML.Prefs">.
	 * This has the advantage that the old ones can be removed cleanly when desired.
	 * It can also easily be seen if there was a conversion already.
	 * <p>
	 * <p>
	 * When reading an .eaf file in new format, the old prefs can be removed.
	 */
	public static Object adjustPreferencesAfterLoadingFormat(Transcription t, int format) {
		switch (format) {
		case TranscriptionStore.EAF_2_7:
			return convert2_7CVEntryPreferences((TranscriptionImpl)t, true, false);
		case TranscriptionStore.EAF:
			// remove 2.7 prefs from a 2.8 (or higher) file.
			if (Preferences.get(Preferences.CV_PREFS_OLD_2_7, t) != null) {
				Preferences.set(Preferences.CV_PREFS_OLD_2_7, null, t, false, false);
			}
			return null;
		default:
			// Some other version; we don't know what to do, if anything.
			return null;
		}
	}
	
	/**
	 * When an .eaf file is loaded in ELAN, check if preferences need to be converted. 
	 * When loading an older format, convert the older preferences to newer ones.
	 * At the moment, this is only relevant for CV preferences.
	 * <p>
	 * The old ones are stored in &lt;prefGroup key="CV.Prefs">,
	 * the new ones in &lt;prefGroup key="CV.ML.Prefs">.
	 * This has the advantage that the old ones can be removed cleanly when desired.
	 * It can also easily be seen if there was a conversion already.
	 * <p>
	 * <p>
	 * When reading an .eaf file in new format, the old prefs can be removed.
	 */
	public static Object adjustPreferencesAfterLoadingFormat(TranscriptionImpl t) {
		String trPathName = t.getPathName();
		int format = TranscriptionStore.EAF;
		try {
			int version = ACMTranscriptionStore.eafFileFormatTaster(trPathName);
			
			if (version <= ParserFactory.EAF27) {
				format = TranscriptionStore.EAF_2_7;
			} else {
				format = TranscriptionStore.EAF;
			}
		} catch (Throwable thr) {}
		
		return adjustPreferencesAfterLoadingFormat(t, format);
	}
	
	/**
	 * See {@link #adjustPreferencesAfterLoadingFormat(Transcription, int)}.
	 */
    
	public static void restoreAdjustedPreferences(Transcription t, Object undoprefs) {
		if (undoprefs instanceof Map) {
			Map<String, Object> p = (Map<String, Object>)undoprefs;
			for (Map.Entry<String, Object> entry : p.entrySet()) {
				Preferences.set(entry.getKey(), entry.getValue(), t, false, false);
			}
		}
	}

	/**
	 * Convert 2.8+ style color (etc) preferences to 2.7 style or vice versa.
	 * @param oldToNew If true, convert from old to new. If false, from new to old.
	 * @param mix If true, add the converted values to the preferences rather than
	 *        replacing them.
	 * 
	 * @return some object containing information to undo this change.
	 * To be restored using
	 * {@link #restoreAdjustedPreferences(Transcription t, Object undoprefs)}.
	 * 
	 */
	private static Object convert2_7CVEntryPreferences(TranscriptionImpl t,
			boolean oldToNew, boolean mix) {

		if (oldToNew) {
			// Check if these preferences were converted before.
			// Prefer the new format if it is already present.
			Object prefs = Preferences.get(Preferences.CV_PREFS, t);
			if (prefs != null) {
				return null;
			}
		}

		String fromPrefKey, toPrefKey;
		if (oldToNew) {
			fromPrefKey = Preferences.CV_PREFS_OLD_2_7;
			toPrefKey = Preferences.CV_PREFS;
		} else {
			fromPrefKey = Preferences.CV_PREFS;
			toPrefKey = Preferences.CV_PREFS_OLD_2_7;
		}

		// Create some data to reverse the changes we'll make.
		Map<String, Object> oldPrefs = new HashMap<String, Object>();

		Map fromPrefs = Preferences.getMap(fromPrefKey, t);
		if (fromPrefs != null) {
			Map<String, Map<String, Object>> frommap =
					(Map<String, Map<String, Object>>)fromPrefs;
			Map<String, Map<String, Object>> tomap = new HashMap<String, Map<String, Object>>();

			// Loop over the CV names and rebuild frommap into tomap
			Set<String> cvNames = frommap.keySet();
			for (String cvName : cvNames) {
				ControlledVocabulary cv = t.getControlledVocabulary(cvName);
				if (cv == null) {
					continue;
				}
				Map<String, Object> fromCvEntries = frommap.get(cvName);
				Map<String, Object> toCvEntries = new HashMap<String, Object>();
				// Loop over the CV entry keys (ids or values)
				Set<String> entryKeys = fromCvEntries.keySet();
				for (String fromKey : entryKeys) {
					Object entryPrefs = fromCvEntries.get(fromKey);
					String toKey = null;

					if (oldToNew) {
						CVEntry cve = cv.getEntryWithValue(0, fromKey);
						if (cve != null)
							toKey = cve.getId();
					} else {
						CVEntry cve = cv.getEntrybyId(fromKey);
						if (cve != null)
							toKey = cve.getValue(0);    					
					}
					if (toKey != null) {
						toCvEntries.put(toKey, entryPrefs);
					} else if (!mix) {
						toCvEntries.put(fromKey, entryPrefs);
					}
				}

				// Create a new map for new entries
				tomap.put(cvName, toCvEntries);
			}

			// Make the changes, and record the undo information.
			Preferences.set(toPrefKey, tomap, t, false, false);   
			oldPrefs.put(toPrefKey, null);

			if (!mix) {
				Preferences.set(fromPrefKey, null, t, false, false);   
				oldPrefs.put(fromPrefKey, fromPrefs);
			}
		}

		return oldPrefs;
	}
	
    /**
     * Moved from EAF27Encoder to here, the "client" side.
     * 
     * Saving in the older 2.7 format will lose information if at least one of
     * the following is true:
     * <ol> 
     * <li>there are multiple languages in a CV
     * <li>the selected language for annotations that are linked to a CVE via
     *   CvEntryId is a non-first language. (The annotations would need to be
     *   switched to language #0).
     * <p>  
     * Since point (2) can only happen if (1) is already true, just check for (1).
     * However if the user is prepared to accept data loss case (1), then
     * case (2) still needs to be fixed by setting all comments to the remaining
     * (first) language.
     * 
     * <li>the one language in a CV has been renamed from the default
     * <li>there is at least one License associated with the transcription
     * <li>there is a language or external reference associated with a tier
     * </ol>
     * @param theTranscription
     * @return true if some information will be lost
     */
    public static boolean savingWillLoseInformation(Transcription theTranscription) {
    	TranscriptionImpl ti = (TranscriptionImpl)theTranscription;
    	
    	// Check if here is at least one License associated with the transcription.
    	if (ti.getLicenses() != null && !ti.getLicenses().isEmpty()) {
    		return true;
    	}
    	
    	for (ControlledVocabulary cv : ti.getControlledVocabularies()) {
    		// Check if there are multiple languages in a CV
    		if (cv.getNumberOfLanguages() > 1) {
    			return true;
    		}
    		// Check if the one language in a CV has been renamed from the default
    		LangInfo li = cv.getLangInfo(0);
    		if (!li.getId().equals(ControlledVocabulary.DEFAULT_LANGUAGE_ID)) {
    			return true; // maybe this is a bit strict...
    		}
    		if (!li.getLongId().equals(ControlledVocabulary.DEFAULT_LANGUAGE_DEF)) {
    			return true;
    		}
    		if (!li.getLabel().equals(ControlledVocabulary.DEFAULT_LANGUAGE_LABEL)) {
    			return true;
    		}
    	}
    	
    	for (Tier tier : ti.getTiers()) {
    		// Check if there is a language or external reference associated with a tier
    		if (isNotEmpty(tier.getLangRef())) {
    			return true;
    		}
    		if (isNotEmpty(tier.getExtRef())) {
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    private static boolean isNotEmpty(String s) {
    	return s != null && !s.isEmpty();
    }
}
