/*
 * Created on Jun 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package mpi.eudico.server.corpora.clomimpl.abstr;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import mpi.eudico.server.corpora.clom.DecoderInfo;
import mpi.eudico.server.corpora.clom.Property;
import mpi.eudico.server.corpora.clomimpl.dobes.AnnotationRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.CVRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.LanguageRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.LexiconServiceRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.LingTypeRecord;
import mpi.eudico.server.corpora.clomimpl.reflink.RefLinkSetRecord;

/**
 * @author hennie
 *
 * Note: for historic reasons all getters take the file to be parsed as a 
 * parameter. This could be dropped in the future.
 * @version Dec 2006: added getter for the Annotator of a tier
 */
public abstract class Parser {
	/**
	 * The single media file property is obsolete and superseded by
	 * the use of MediaDescriptors.
	 *
	 * @param fileName the source file
	 *
	 * @return null
	 */
	public String getMediaFile(String fileName) {
		return null;
	}
	/**
	 * The MediaDescriptors contain information on primary linked 
	 * media files (video, audio).
	 *
	 * @param fileName the source file
	 *
	 * @return a list of MediaDescriptors
	 */
	public abstract List<MediaDescriptor> getMediaDescriptors(String fileName);
	/**
	 * Returns the Linked File descriptors; only applicable to eaf.
	 *
	 * @param fileName the file name
	 *
	 * @return a List of linked file descriptors
	 */
	public List<LinkedFileDescriptor> getLinkedFileDescriptors(String fileName) {
	    return null;
	}
	/**
	 * The author or owner property of a transcription document.
	 *
	 * @param fileName the source file
	 *
	 * @return the name or code of the author, or null
	 */
	public String getAuthor(String fileName) {
		return null;
	}
	/**
	 * Returns a list of the document (header) properties.
	 * 
	 * @param fileName the file to be parsed
	 * 
	 * @return a list of the document (header) properties
	 */
	public List<Property> getTranscriptionProperties(String fileName) {
		return null;
	}
	/**
	 * Returns a list of records needed for the creation of the LinguisticTypes
	 * also known as tier types. 
	 *
	 * @param fileName the source file
	 *
	 * @return a list of tier type records
	 */
	public abstract List<LingTypeRecord> getLinguisticTypes(String fileName);
	/**
	 * A list of time order ID strings which serve as keys to look up time slot
	 * objects.
	 *
	 * @param fileName the source file
	 *
	 * @return a list of time order ID's
	 */
	public abstract List<String> getTimeOrder(String fileName);
	/**
	 * Returns a map containing time slot ID's as keys and time strings as 
	 * values.
	 *
	 * @param fileName the source file
	 *
	 * @return a id to time value mapping
	 */
	public abstract Map<String, String> getTimeSlots(String fileName);
	
	/**
	 * Returns a map of CV id's (names) to CVRecords.
	 * 
	 * @param fileName the source file
	 * 
	 * @return a mapping of CV id's to CVRecords, or null 
	 */
	public Map<String, CVRecord> getControlledVocabularies(String fileName) {
		return null;
	}

	/**
	 * Returns a map of id - external reference objects pairs. 
	 * 
	 * @param fileName the transcription file
	 * @return a map with id - object mappings, null by default
	 */
	public Map<String, ExternalReferenceImpl> getExternalReferences (String fileName) {
		return null;
	}
	
	/**
	 * Returns a map of id - services pairs
	 * 
	 * @param fileName the transcription file
	 * @return a map with id - services mappings
	 */
	public Map<String, LexiconServiceRecord> getLexiconServices(String fileName) {
		return null;
	}
	
	/**
	 * Returns the names of the Tiers that are present in the Transcription
	 * file
	 *
	 * @param fileName DOCUMENT ME!
	 *
	 * @return DOCUMENT ME!
	 */
	public abstract List<String> getTierNames(String fileName);
	/**
	 * Returns the participant property of a tier.
	 *
	 * @param tierName the tier to get the participant for
	 * @param fileName the source file
	 *
	 * @return the participant's name or code, or null
	 */
	public abstract String getParticipantOf(String tierName, String fileName);
	/**
	 * Returns the annotator of a tier, will not be present in a lot of 
	 * file types.
	 *
	 * @param tierName the tier id
	 * @param fileName the file to parse
	 *
	 * @return the annotator property or null
	 */
	public String getAnnotatorOf(String tierName, String fileName) {
		return null;
	}
	/**
	 * Returns the LinguisticType IDREF for the specified tier.
	 *
	 * @param tierName the tier id 
	 * @param fileName the source file
	 *
	 * @return the ID (name) of the LinguisticType (tier type), should not be null
	 */
	public abstract String getLinguisticTypeIDOf(
		String tierName,
		String fileName);
	/**
	 * Returns the Locale for an input method to be used for a tier.
	 *
	 * @param tierName the tier id
	 * @param fileName the source file
	 *
	 * @return a Locale object or null
	 */
	public Locale getDefaultLanguageOf(
		String tierName,
		String fileName) {
			return null;
		}
	/**
	 * Returns the name of the parent tier of the specified tier.
	 *
	 * @param tierName the tier id 
	 * @param fileName the source file
	 *
	 * @return the name (id) of the parent tier, or null
	 */
	public abstract String getParentNameOf(String tierName, String fileName);
	/**
	 * Returns a List with the Annotations for this Tier. Each
	 * AnnotationRecord contains begin time, end time and text values
	 * 
	 *
	 * @param tierName the tier id
	 * @param fileName the source file
	 *
	 * @return a List of Annotation Records representing the annotations of the tier
	 */
	public abstract List<AnnotationRecord> getAnnotationsOf(String tierName, String fileName);
	
	/**
	 * Sets the decoder info object for the parser. Empty implementation.
	 * Some file types need additional information on how the contents should be parsed 
	 * and converted to ACM structures. This information is oftem provided by the user
	 * in a dedicated user interface.
	 * 
	 * @param decoderInfo the decoder information object
	 */
	public void setDecoderInfo(DecoderInfo decoderInfo) {
	    // stub
	}

	/**
	 * Returns a list of content languages present in the file.
	 * 
	 * @param fileName the source file
	 * 
	 * @return a list of language records
	 */
	public List<LanguageRecord> getLanguages(String fileName) {
		// stub
		return null;
	}
	
	/**
	 * Get the file format version, converted from a string to a single integer:
	 * (major * 1000 * 1000) +
	 * (minor * 1000) (+ micro).
	 * 
	 * Or 0 if not known (for older formats where it isn't implemented).
	 * @return the file format version or 0
	 */
	public int getFileFormat() {
		return 0;
	}
	
	/**
	 * Return the license(s) that apply to the transcription.
	 * The list may be empty but not null.
	 * 
	 * @param fileName the source file
	 * @return a list of LicenseRecords, not null
	 */
	public List<LicenseRecord> getLicenses(String fileName) {
		return Collections.emptyList();
	}

	/**
	 * Return the LANG_REF attribute of a tier.
	 * May be null if there is none.
	 * 
	 * @param tierName the tier id
	 * @param fileName the source file
	 * 
	 * @return the IDREF of the content language of the tier, or null
	 */
	public String getLangRefOf(String tierName, String fileName) {
		return null;
	}

	/**
	 * Return the EXT_REF attribute of a tier.
	 * May be null if there is none.
	 * 
	 * @param tierName the tier id
	 * @param fileName the source file
	 * 
	 * @return the IDREF of an external reference of a tier, or null
	 */
	public String getExtRefOf(String tierName, String fileName) {
		return null;
	}
	
	/**
	 * Return the contents of the REF_LINK_SET elements of a ANNOTATION_DOCUMENT.
	 * 
	 * @param fileName the source file
	 * 
	 * @return a list of RefLinkSetRecord from which a RefLinkSet can be built, or null
	 */
	public List<RefLinkSetRecord> getRefLinkSetList(String fileName) {
		return null;
	}
}