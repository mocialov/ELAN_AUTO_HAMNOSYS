package mpi.eudico.server.corpora.clomimpl.subtitletext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import mpi.eudico.server.corpora.clom.DecoderInfo;
import mpi.eudico.server.corpora.clomimpl.abstr.MediaDescriptor;
import mpi.eudico.server.corpora.clomimpl.abstr.Parser;
import mpi.eudico.server.corpora.clomimpl.dobes.AnnotationRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.AnnotationRecordComparator;
import mpi.eudico.server.corpora.clomimpl.dobes.LingTypeRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.TimeSlotRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.TimeSlotRecordComparator;
import static mpi.eudico.server.corpora.util.ServerLogger.LOG;
import mpi.eudico.util.TimeFormatter;
import mpi.eudico.util.TimeFormatter.TIME_FORMAT;

/**
 * A parser for a few, common subtitle text formats, e.g. SubRip (.srt)
 * and for Audacity label files (not necessarily subtitles).
 * 
 * @author Han Sloetjes
 *
 */
public class SubtitleTextParser extends Parser {
	private SubtitleDecoderInfo decoderInfo;
	private String lastParsedFile;
    private final String TS_ID_PREFIX = "ts";
    private final String ANN_PREFIX = "a";
    private final String TYPE_NAME = "imported-sub";
    private ArrayList<LingTypeRecord> lingTypeRecords;
    private ArrayList<String> tierNames;
    private ArrayList<String> timeOrder; // in the end a list of time slot id's
    private ArrayList<TimeSlotRecord> timeSlots; // of TimeSlotRecords
    private HashMap<String, ArrayList<AnnotationRecord>> recordsPerTier;
    private String baseTierName = "Subtitle-Tier";
    private int annCount = 1;
    private int tsCount = 1;
    		
    /**
     * No-arg constructor, assumes UTF-8 .srt file.
     */
	public SubtitleTextParser() {
		super();
		
		lingTypeRecords = new ArrayList<LingTypeRecord>();
		tierNames = new ArrayList<String>();
		timeOrder = new ArrayList<String>();
		timeSlots = new ArrayList<TimeSlotRecord>();
		recordsPerTier = new HashMap<String, ArrayList<AnnotationRecord>>();
	}

	/**
	 * Constructor.
	 * 
	 * @param decoderInfo the decoder info object containing the input 
	 * file path, file type and other configuration settings
	 */
	public SubtitleTextParser(SubtitleDecoderInfo decoderInfo) {
		this();
		this.decoderInfo = decoderInfo;
		parse(decoderInfo.getSourceFilePath());
	}
	
	/**
	 * Sets the decoder information object, e.g. in case the parser has been
	 * created by the ParserFactory.
	 * 
	 * @param decoderInfo the decoder object
	 */
	public void setDecoderInfo(DecoderInfo decoderInfo) {
		if (decoderInfo instanceof SubtitleDecoderInfo) {
			this.decoderInfo = (SubtitleDecoderInfo) decoderInfo;
		}
	}
	
	/**
	 * Initiates the actual parsing.
	 * 
	 * @param fileName the file to parse
	 */
	protected void parse(String fileName) {
		if (fileName == null || fileName.equals(lastParsedFile)) {
			return;
		}
		
        // reset
        lingTypeRecords.clear();
        tierNames.clear();
        timeOrder.clear();
        timeSlots.clear();
        recordsPerTier.clear();
        parseFile(fileName);
        
        lastParsedFile = fileName;
	}
	
	// the actual parsing
	private void parseFile(String filePath) {
        if (filePath == null) {
            throw new NullPointerException("The file name is null");
        }
        if (filePath.startsWith("file:")) {
        	filePath = filePath.substring(5);
        }
		
        BufferedReader bufRead = null;
        try {
			File sourceFile = new File(filePath);
			String charSet = "UTF-8";
			if (decoderInfo != null && decoderInfo.getFileEncoding() != null) {
				charSet= decoderInfo.getFileEncoding();
			}
			InputStreamReader isr = new InputStreamReader(new FileInputStream(sourceFile), charSet);
			bufRead = new BufferedReader(isr);
			
			if (decoderInfo != null) {
				// check the format, call the corresponding sub routine
				SubtitleFormat format = decoderInfo.getFormat();
				switch(format) {
				case SUBRIP:
					parseSubRip(bufRead);
					break;
				case AUDACITY_lABELS:
					parseAudacity(bufRead);
					break;
					default:
						// throw exception
						break;
				}
			} else {
				// assume SubRip, .srt
				parseSubRip(bufRead);
			}
			
			// sort the records and correct overlaps?
			sortRecords();
			// resolve overlaps
	        Iterator<ArrayList<AnnotationRecord>> valIt = recordsPerTier.values().iterator();
	        while (valIt.hasNext()) {
	            correctOverlaps(valIt.next());
	        }
	        // sort time slots
	        sortTimeSlots();
		} catch (FileNotFoundException fnfe) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Import failed: " + fnfe.getMessage());
			}
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Import failed: " + ioe.getMessage());
			}
		} catch (Throwable t) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Import failed: " + t.getMessage());
			}
		} finally {
			if (bufRead != null)
			try {
				bufRead.close();
			} catch (Throwable tt) {/*ignore*/}
		}
	}
	
	/**
	 * Parsing of SubRip .srt files.
	 * The format looks something like this
	 * <blockquote><pre>
	 * 168
	 * 00:20:41,150 --> 00:20:45,109 (X1:Position left X2:Position right Y1:Position up Y2:Position)
     * - How did he do that?
     * - &lt;b&gt;Made him an offer he couldn't refuse&lt;/b&gt;
	 * </pre></blockquote>
	 * Instead of HTML tags, it is also possible that {b}...{/b} is used.
	 * There can be lines for multiple speakers in a single subtitle unit,
	 * these will be merged into one annotation.
	 * Some basic HTML formatting is allowed but will be removed on import 
	 * (could be optional). Encoding of the file can be CP-1252, UTF-8
	 * or UTF-16.
	 * 
	 * @param reader the BufferedReader that reads the file
	 * @throws IOException any IO exception
	 */
	private void parseSubRip(BufferedReader reader) throws IOException {
		final String FROM_TO = "-->";
		// the regular expression for HTML tags and the {b}-style variant
		// thereof. Use of ".+?" (reluctant quantifier) to prevent removal
		// of everything between the first "<" and the ">" of the closing
		// tag.
		final String TAG_PAT = "[<{].+?[>}]";// 
		String line = null;
		
		long curBT = 0, curET = 0;
		boolean inUnit = false;
		StringBuilder sb = new StringBuilder();
		// assume one, single tier
		String tierName = baseTierName;
		tierNames.add(tierName);
		recordsPerTier.put(tierName, new ArrayList<AnnotationRecord>());
		
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty()) {
				if (inUnit) {
					// store current annotation
					if (curET > curBT) {
						addAnnotationRecord(tierName, curBT, curET, sb.toString());
					}
				}
				inUnit = false;
				sb.delete(0, sb.length());
			} else if (line.indexOf(FROM_TO) > 0) {
				// extract times
				int index1 = line.indexOf(FROM_TO);
				String btStr = line.substring(0, index1).trim();//strip();
				String etStr = line.substring(index1 + FROM_TO.length()).trim();//strip();
				int index2 = etStr.indexOf(' ');
				if (index2 > -1) {
					etStr = etStr.substring(0, index2);
				}
				btStr = btStr.replace(',', '.');
				etStr = etStr.replace(',', '.');
				curBT = TimeFormatter.toMilliSeconds(btStr);
				curET = TimeFormatter.toMilliSeconds(etStr);
				//test et > bt
				inUnit = true;
			} else if (inUnit) {
				if (sb.length() > 0) {
					// concatenate multiple-line subtitles, insert a space
					sb.append(' ');
				}
				if (decoderInfo.isRemoveHTML()) {
					String[] noTags = line.split(TAG_PAT);
					for (int i = 0; i < noTags.length; i++) {
						sb.append(noTags[i]);
					}
				} else {
					sb.append(line);
				}
 			}
		}
		// check contents after reading last line
		if (inUnit && curET > curBT && sb.length() > 0) {
			addAnnotationRecord(tierName, curBT, curET, sb.toString());
		}
	}
	
	/**
	 * Audacity label files are basic tab delimited text files without column 
	 * headers. The encoding is UTF-8.
	 * Segments can overlap, also within a track, and start time and end time
	 * can be equal.
	 *  
	 * <blockquote><pre>
	 * 4.556244	16.946029	Start here
	 * </pre></blockquote>
	 * 
	 * Segments are sorted based on their start time. If multiple tracks are 
	 * stored in the same file, first all sorted segments of one track are 
	 * listed, then all segments of the next etc. Therefore if a start time
	 * is encountered which is smaller than the previous one, assume a new 
	 * track starts.
	 *  
	 * @param reader the BufferedReader which produces the lines
	 * @throws IOException any io exception
	 */
	private void parseAudacity(BufferedReader reader) throws IOException {
		final String TAB = "\t";
		
		int tierCount = 1;
		baseTierName = "Label-Track-";
		String tierName = baseTierName + String.valueOf(tierCount++);
		// initialize for the first tier
		tierNames.add(tierName);
		recordsPerTier.put(tierName, new ArrayList<AnnotationRecord>());
		
		int defDur = 1000;
		if (decoderInfo != null) {
			defDur = decoderInfo.getDefaultDuration();
		}
		long lastBT = -2;
		String line = null;
		
		while ((line = reader.readLine()) != null) {
			String[] parts = line.split(TAB);
			if (parts.length >= 3) {
				long bt = TimeFormatter.toMilliSeconds(parts[0], TIME_FORMAT.SSMS);
				long et = TimeFormatter.toMilliSeconds(parts[1], TIME_FORMAT.SSMS);
				
				if (bt < lastBT) {
					// start of a new track, increment the tier count
					tierName = baseTierName + String.valueOf(tierCount++);
					tierNames.add(tierName);
					recordsPerTier.put(tierName, new ArrayList<AnnotationRecord>());
				}
				
				if (bt > -1) {
					if (et <= bt) {
						et = bt + defDur;
					}
					addAnnotationRecord(tierName, bt, et, parts[2]);
					lastBT = bt;
				}
			}
		}
	}
	
	private void addAnnotationRecord(String tierName, long bt, long et, String annValue) {
        AnnotationRecord annRec = new AnnotationRecord();
        annRec.setAnnotationType(AnnotationRecord.ALIGNABLE);
        annRec.setValue(annValue);
        annRec.setAnnotationId(ANN_PREFIX + annCount++);
        TimeSlotRecord tsRec = new TimeSlotRecord(tsCount++, bt);
        annRec.setBeginTimeSlotRecord(tsRec);
        timeSlots.add(tsRec);
        tsRec = new TimeSlotRecord(tsCount++, et);
        annRec.setEndTimeSlotRecord(tsRec);
        timeSlots.add(tsRec);

        recordsPerTier.get(tierName).add(annRec);		
	}
	
	/**
	 * Sorts the annotation records of all tiers.
	 * 
	 * Internal note: this method could be moved to a utility class.
	 */
	private void sortRecords() {
		if (!recordsPerTier.isEmpty()) {
			AnnotationRecordComparator comp = new AnnotationRecordComparator(true);
	        Iterator<ArrayList<AnnotationRecord>> valIt = recordsPerTier.values().iterator();

	        while (valIt.hasNext()) {
	            Collections.sort(valIt.next(), comp);
	        }
		}
	}
	
	/**
	 * Iterate over all records and fix overlaps by adjusting time slot values.
	 * It is assumed the records are sorted.
	 * 
     * Internal note: this method could be moved to a utility class.
     * 
	 * @param annRecords the annotation records of one tier
	 */
	private void correctOverlaps(List<AnnotationRecord> annRecords) {
		List<AnnotationRecord> forRemoval = new ArrayList<AnnotationRecord>();
		
        for (int i = 0; i < (annRecords.size() - 1); i++) {
        	AnnotationRecord annRec = annRecords.get(i);
        	AnnotationRecord nextRec = annRecords.get(i + 1);
        	
        	TimeSlotRecord tsBRec1 = annRec.getBeginTimeSlotRecord();
        	TimeSlotRecord tsERec1 = annRec.getEndTimeSlotRecord();
        	TimeSlotRecord tsBRec2 = nextRec.getBeginTimeSlotRecord();
        	TimeSlotRecord tsERec2 = nextRec.getEndTimeSlotRecord();
        	
        	if (tsBRec1.getValue() == tsBRec2.getValue() 
        			/*&& tsERec1.getValue() == tsERec2.getValue()*/) {
        		// remove the first one, even if only the start times are equal
        		forRemoval.add(annRec);
        		continue;
        	}
            // if there is an overlap, modify end time of the first
        	if (tsERec1.getValue() > tsBRec2.getValue()) {
        		tsERec1.setValue(tsBRec2.getValue());    		
            }
        }
        
        if (!forRemoval.isEmpty()) {
        	for(AnnotationRecord ar : forRemoval) {
        		annRecords.remove(ar);
        		timeSlots.remove(ar.getBeginTimeSlotRecord());
        		timeSlots.remove(ar.getEndTimeSlotRecord());
        	}
        }
	}
	
    /**
     * Sorts the time slot records using a TimeSlotRecordComparator and then
     * regenerates the id's.
     * 
     * Internal note: this method could be moved to a utility class.
     */
    private void sortTimeSlots() {
        if ((timeSlots != null) && (timeSlots.size() > 1)) {
            Collections.sort(timeSlots, new TimeSlotRecordComparator());

            for (int i = 0; i < timeSlots.size(); i++) {
            	TimeSlotRecord tsr = timeSlots.get(i);
                tsr.setId(i + 1);
            }
        }
    }
	
	/*
	private void addAnnotationRecordWithChecks(String tierName, long bt, long et, String value) {
		if (!tierNames.contains(tierName)) {
			tierNames.add(tierName);
		}
		if (!recordsPerTier.containsKey(tierName)) {
			recordsPerTier.put(tierName, new ArrayList<AnnotationRecord>());
		}
		addAnnotationRecord(tierName, bt, et, value);
	}
	*/
	
    /**
     * Returns an empty List
     *
     * @param fileName the file to parse (for historic reasons)
     *
     * @return an empty list
     */
	@Override
	public List<MediaDescriptor> getMediaDescriptors(String fileName) {
        parse(fileName);

        return new ArrayList<MediaDescriptor>();
	}

    /**
     * Returns a list of Linguistic Type Records
     *
     * @param fileName the file to parse (for historic reasons)
     *
     * @return a list of ling. type records, by default only one lin. type
     */
	@Override
	public List<LingTypeRecord> getLinguisticTypes(String fileName) {
        parse(fileName);

        if (lingTypeRecords.size() == 0) {
            LingTypeRecord lt = new LingTypeRecord();
            lt.setLingTypeId(TYPE_NAME);
            lt.setTimeAlignable("true");

            lingTypeRecords.add(lt);
        }

        return lingTypeRecords;
	}

    /**
     * Returns an ordered list of time slot id's (Strings)
     *
     * @param fileName the file to parse (for historic reasons)
     *
     * @return a list of time slot id's
     */
	@Override
	public List<String> getTimeOrder(String fileName) {
        parse(fileName);
        timeOrder.clear();

        for (int i = 0; i < timeSlots.size(); i++) {
        	TimeSlotRecord tsr = timeSlots.get(i);
            timeOrder.add(TS_ID_PREFIX + tsr.getId());
        }

        return timeOrder;
	}

    /**
     * Returns a map containing id - time key value pairs
     *
     * @param fileName the file to parse (for historic reasons)
     *
     * @return a map containing id - time key value pairs
     */
	@Override
	public Map<String, String> getTimeSlots(String fileName) {
        parse(fileName);

        HashMap<String, String> tsMap = new HashMap<String, String>(timeSlots.size());

        for (int i = 0; i < timeSlots.size(); i++) {
        	TimeSlotRecord tsr = timeSlots.get(i);
            // note: could use the timeOrder objects (if we are sure of the order in
            // which the methods are called)?
            tsMap.put(TS_ID_PREFIX + tsr.getId(), Long.toString(tsr.getValue()));
        }

        return tsMap;
	}

    /**
     * Returns a list of tier names.
     *
     * @param fileName the file to parse (for historic reasons)
     *
     * @return a list of tier names
     */
	@Override
	public List<String> getTierNames(String fileName) {
        parse(fileName);

        return tierNames;
	}

    /**
     * Returns null
     *
     * @param tierName the tier
     * @param fileName the file to parse (for historic reasons)
     *
     * @return null
     */
	@Override
	public String getParticipantOf(String tierName, String fileName) {
        parse(fileName);

        return null;
	}

    /**
     * Returns the linguistic type name for a tier.
     *
     * @param tierName the tier
     * @param fileName the file to parse (for historic reasons)
     *
     * @return the linguistic type name, a default type name
     */
	@Override
	public String getLinguisticTypeIDOf(String tierName, String fileName) {
        parse(fileName);

        return TYPE_NAME;
	}

    /**
     * Returns the parent tier name for a tier.
     *
     * @param tierName the tier
     * @param fileName the file to parse (for historic reasons)
     *
     * @return the parent tier name, null by default
     */
	@Override
	public String getParentNameOf(String tierName, String fileName) {
        parse(fileName);

        // parent - child relations not in a subtitle text file.
        return null;
	}

	@Override
	public List<AnnotationRecord> getAnnotationsOf(String tierName, String fileName) {
		parse(fileName);
		
        ArrayList<AnnotationRecord> records = recordsPerTier.get(tierName);

        if (records != null) {
            return records;
        }

        return new ArrayList<AnnotationRecord>();
	}


}
