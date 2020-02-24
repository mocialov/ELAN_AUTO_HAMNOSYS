package mpi.eudico.server.corpora.clomimpl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import mpi.eudico.server.corpora.clom.TimeSlot;
import mpi.eudico.server.corpora.clomimpl.abstr.Parser;
import mpi.eudico.server.corpora.clomimpl.abstr.ParserFactory;
import mpi.eudico.server.corpora.clomimpl.dobes.ACMTranscriptionStore;
import mpi.eudico.server.corpora.clomimpl.dobes.AnnotationRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.CVRecord;
import mpi.eudico.server.corpora.clomimpl.dobes.EAF27;
import mpi.eudico.server.corpora.clomimpl.dobes.EAF28;
import mpi.eudico.server.corpora.clomimpl.dobes.EAF30;
import mpi.eudico.server.corpora.clomimpl.dobes.ECV02;
import mpi.eudico.server.corpora.clomimpl.dobes.LingTypeRecord;
import mpi.eudico.server.corpora.clomimpl.type.Constraint;
import mpi.eudico.server.corpora.util.ProcessReport;
import mpi.eudico.server.corpora.util.SimpleReport;


/**
 * A class to perform sanity checks on an .eaf file.
 * First a parser will check the validity of the XML file and write
 * errors to the report. Then additional checks will be performed
 * on the basis of the intermediate records. // examples//
 * Finally a TranscriptionImpl instance is created and possible
 * additional checks are performed.
 * 
 * 
 * @author Han Sloetjes
 */
public class EAFValidator {
	private SimpleReport report;
	private String filePath;// absolute path or URL
	private File eafFile = null;
	
	/**
	 * Constructor, performs minimal initialization.
	 * 
	 * @param eafFile the local eaf file to validate
	 */
	public EAFValidator(File eafFile) {
		super();
		report = new SimpleReport();
		this.eafFile = eafFile;
	}
	
	/**
	 * Constructor, performs minimal initialization.
	 * @param eafFileName the URI, URL or file path as a string
	 * of the eaf file to validate
	 */
	public EAFValidator(String eafFileName) {
		super();
		report = new SimpleReport();
		filePath = eafFileName;
	}
	
	/**
	 * To be called after the validation process ended. Before that the
	 * report will be empty.
	 * 
	 * @return a report containing the results of the validation process
	 */
	public ProcessReport getReport() {
		return report;
	}
	
	/**
	 * To be called after the validation process ended. Before that the
	 * returned string will be empty.
	 * 
	 * @return a string containing the results of the validation process
	 */
	public String getReportAsString() {
		return report.getReportAsString();
	}
	
	/**
	 * Starts the validation by checking if the file is local or remote,
	 * accessible, readable etc.
	 */
	public void validate() {
		if (eafFile == null && filePath == null) {
			report.append("No eaf file or eaf file path provided, nothing to validate");
			return;
		}
		
		if (eafFile == null) {
			eafFile = new File(filePath);
		} else {
			filePath = eafFile.getAbsolutePath();
		}
		
		if (eafFile.exists() && eafFile.canRead() && !eafFile.isDirectory()) {
			validateLocalFile();
		} else {
			// validate remote
			validateRemoteFile();
		}
	}
	
	/**
	 * Parses a local (network) file.
	 */
	private void validateLocalFile() {
		report.append("Checking file: " + filePath + "\n");

		try {
			FileInputStream fis = new FileInputStream(eafFile);
			InputSource is = new InputSource(fis);
			is.setSystemId(filePath);
			validateXML(is);
			
			// close the stream?
			try {
				fis.close();
			} catch(Throwable thr) {}
			
			int version = ACMTranscriptionStore.eafFileFormatTaster(eafFile.getAbsolutePath());
			Parser eafParser = ParserFactory.getParser(version);
			validateContent(eafParser);
			
		} catch (FileNotFoundException fnfe) {
			report.append("Cannot validate file - NotFound: " + fnfe.getMessage());
		} catch (Throwable t) {
			report.append("Cannot validate file: " + t.getMessage());
		}
	}
	
	/**
	 * Parses a remote file via an input stream.
	 */
	private void validateRemoteFile() {
		try {
			report.append("Checking file: " + filePath + "\n");
			URI fileURI = new URI(filePath);
			InputStream inStream = fileURI.toURL().openStream();
			InputSource is = new InputSource(inStream);
			is.setSystemId(filePath);//??
			
			validateXML(is);
			
			// close inStream//
			try {
				inStream.close();
			} catch (Throwable t) {}
			
			Parser eafParser = ParserFactory.getParser(ParserFactory.EAF30);
			validateContent(eafParser);
		} catch (URISyntaxException use) {
			report.append("Cannot validate file - URISyntax: " + use.getMessage());
		} catch (IOException ioe) {
			report.append("Cannot validate file - IO: " + ioe.getMessage());
		}
	}
	
	/**
	 * Creates a SAXParser and sets a ContentHandler which only records and 
	 * reports the parsing errors and warnings.
	 * 
	 * @param inSource the input source
	 */
	private void validateXML(InputSource inSource) {
		try {
			// implement/override a default handler
			DefaultHandler handler = new ValidationHandler();
			// first get general XML parsing warnings and errors
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			parserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
			parserFactory.setNamespaceAware(true);
			
			// to get a validating parser, set the schema to the proper xsd schema
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			schemaFactory.setErrorHandler(handler);

			Schema eafSchema = schemaFactory.newSchema();
			parserFactory.setSchema(eafSchema);
			//parserFactory.setValidating(false);// has to be false (the default) when a schema is set!
			SAXParser parser = parserFactory.newSAXParser();

			report.append("++ Start of XML validation by SAXParser\n");

			parser.parse(inSource, handler);
			
		} catch (FactoryConfigurationError fce) {
			report.append("Parse problem - FactoryConfiguration: " + fce.getMessage());
		} catch (SAXNotSupportedException snse) {
			report.append("Parse problem - SAXNotSupported: " + snse.getMessage());
		} catch (SAXNotRecognizedException snre) {
			report.append("Parse problem - SaxNotRecognized: " + snre.getMessage());
		} catch (ParserConfigurationException pce) {
			report.append("Parse problem - ParserConfiguration: " + pce.getMessage());
		} catch (SAXException se) {
			report.append("Parse problem - SAXException: " + se.getMessage());
		} catch (IOException ioe) {
			report.append("Parse problem - IO: " + ioe.getMessage());
		}

	}
	
	/**
	 * Checks tier and types based on the intermediate data structures the parser
	 * retrieved from the EAF file.
	 * 
	 * @param eafParser the EFA parser
	 */
	private void validateContent(Parser eafParser) {
		report.append("Checking contents of the EAF file");
		// the first call initiates parsing of the file
		eafParser.getTierNames(filePath);
		
		 //check tier types first
		checkTierTypes(eafParser);
		// then check tiers
		checkTiers(eafParser);
		// check CV's
		checkCVs(eafParser);
		// create tier and annotation hierarchies?
	}
	
	/**
	 * Checks the tiers and their annotations. The following issues are
	 * detected: consistency of tier type and parent tier reference (but not
	 * yet type consistency within a tier hierarchy), consistency of time-
	 * alignable flag and type of annotations, mixture of alignable and
	 * reference type of annotations, time and time slot related problems
	 * (some of which will be in the XML parsing errors as well, with
	 * line numbers).
	 * 
	 * @param eafParser the parser containing the intermediate data structures
	 * and records
	 */
	private void checkTiers(Parser eafParser) {
		report.append("++ Start of Tiers");
		List<String> tierNameList = eafParser.getTierNames(filePath);
		List<LingTypeRecord> tierTypeRecords = eafParser.getLinguisticTypes(filePath);
		Map<String, String> timeSlotMap = eafParser.getTimeSlots(filePath);
		
		// check per tier, and within the tier, per annotation
		int numTierTypeInconsistencies = 0;
		boolean cveRefChecked = false;
		
		for (String tierName : tierNameList) {
			report.append("Checking tier: " + tierName); 
			
			String tierType = eafParser.getLinguisticTypeIDOf(tierName, filePath);
			LingTypeRecord typeRecord = null;
			
			for (LingTypeRecord ltr : tierTypeRecords) {
				if (ltr.getLingTypeId() != null && ltr.getLingTypeId().equals(tierType)) {
					typeRecord = ltr;
					break;
				}
			}
			String stereoType = null;
			boolean tierTimeAlignable = false;
			
			if (typeRecord == null) {
				report.append("ERROR: the tier type (linguistic type) of the tier is null");
			} else {
				stereoType = typeRecord.getStereoType();
				tierTimeAlignable = Boolean.valueOf(typeRecord.getTimeAlignable());
				String parentTierName = eafParser.getParentNameOf(tierName, filePath);
				// check parent and constraint consistency
				if (parentTierName != null && stereoType == null) {
					numTierTypeInconsistencies++;
					report.append(String.format(
							"ERROR: tier \"%s\" has parent tier \"%s\" but has no stereotype CONSTRAINT defined in its linguistic type \"%s\"", 
							tierName, parentTierName, typeRecord.getLingTypeId()));
				}
				if (parentTierName == null && stereoType != null) {
					numTierTypeInconsistencies++;
					report.append(String.format(
							"ERROR: tier \"%s\" has no parent tier but has stereotype CONSTRAINT \"%s\" defined in its linguistic type \"%s\"", 
							tierName, stereoType, typeRecord.getLingTypeId()));
				}
				// other combinations are valid/consistent
			}
			
			cveRefChecked = false;
			// annotation records of this tier
			// checks performed: mixture of alignable and reference annotations, overlapping annotations,
			// consistency with tier type,  
			List<AnnotationRecord> annRecordList = eafParser.getAnnotationsOf(tierName, filePath);
			
			int numAlignableAnn = 0;
			int numReferenceAnn = 0;
			int numRefAnnOnAlignTier = 0;
			int numAlignAnnOnRefTier = 0;
			// iterate twice, once to add the time slot records, once to check and compare times
			for (AnnotationRecord annRec : annRecordList) {
				// check consistency with tier type
				if (AnnotationRecord.ALIGNABLE.equals(annRec.getAnnotationType())) {
					numAlignableAnn++;
					if (!tierTimeAlignable) {
						numAlignAnnOnRefTier++;
					}
					if (annRec.getAnnotationId() == null || annRec.getAnnotationId().isEmpty()) {
						report.append(String.format("ERROR: annotation at index %d has no ANNOTATION_ID", 
								annRecordList.indexOf(annRec)));
					}
					// check time slot id ref1 and ref2, then check bt and et
					long bt = -2, et = -2;// -1 is reserved for unaligned slots
					if (annRec.getBeginTimeSlotId() == null || annRec.getBeginTimeSlotId().isEmpty()) {
						report.append(String.format("ERROR: annotation with id=\"%s\" has no TIME_SLOT_REF1 (begin time)", 
								annRec.getAnnotationId()));
					} else {
						bt = toTime(timeSlotMap.get(annRec.getBeginTimeSlotId()));
						if (bt == -2) { // the id is not in the map or the returned value is null
							report.append(String.format("ERROR: annotation with id=\"%s\" refers to non-existing time slot with id=\"%s\"", 
									annRec.getAnnotationId(), annRec.getBeginTimeSlotId()));
						} else if (bt == Long.MIN_VALUE) {
							report.append(String.format("ERROR: the value \"%s\" of the time slot with id=\"%s\" is not a valid time in ms", 
									timeSlotMap.get(annRec.getBeginTimeSlotId()), annRec.getBeginTimeSlotId()));
						} else if (bt == TimeSlot.TIME_UNALIGNED && !Constraint.publicStereoTypes[0].equals(stereoType)) {
							// check for -1 (unaligned) and the stereotype not time_subdivision
							report.append(String.format("ERROR: unaligned time slots are only allowed on \"%s\" type of tiers, "
									+ "not on \"%s\" tiers (time slot id=\"%s\")", Constraint.publicStereoTypes[0], 
									stereoType, annRec.getBeginTimeSlotId()));
						}
					}
					
					if (annRec.getEndTimeSlotId() == null || annRec.getEndTimeSlotId().isEmpty()) {
						report.append(String.format("ERROR: annotation with id=\"%s\" has no TIME_SLOT_REF2 (end time)", 
								annRec.getAnnotationId()));
					} else {
						et = toTime(timeSlotMap.get(annRec.getEndTimeSlotId()));
						if (et == -2) { // the id is not in the map or the returned value is null
							report.append(String.format("ERROR: annotation with id=\"%s\" refers to non-existing time slot with id=\"%s\"", 
									annRec.getAnnotationId() + annRec.getEndTimeSlotId()));
						} else if (et == Long.MIN_VALUE) {
							report.append(String.format("ERROR: the value \"%s\" of the time slot with id=\"%s\" is not a valid time in ms", 
									timeSlotMap.get(annRec.getEndTimeSlotId()), annRec.getEndTimeSlotId()));
						} else if (et == TimeSlot.TIME_UNALIGNED && !Constraint.publicStereoTypes[0].equals(stereoType)) {
							// check for -1 (unaligned) and the stereotype not time_subdivision
							report.append(String.format("ERROR: unaligned time slots are only allowed on \"%s\" type of tiers, "
									+ "not on \"%s\" tiers (time slot id=\"%s\")", Constraint.publicStereoTypes[0], 
									stereoType, annRec.getEndTimeSlotId()));
						}
					}
					
					if (annRec.getBeginTimeSlotId() != null && annRec.getBeginTimeSlotId().equals(annRec.getEndTimeSlotId())) {
						report.append(String.format("ERROR: annotation with id=\"%s\" has TIME_SLOT_REF1 equal to TIME_SLOT_REF2", 
								annRec.getAnnotationId()));
					}

					// bt == et
					if (bt > -1 && et > -1 && bt == et) {
						// there is no support (yet) for "point" annotations (bt == et)
						report.append(String.format("ERROR: annotation with id=\"%s\" has begin time equal to end time: %d", 
								annRec.getAnnotationId(), bt));
					}
					// et < bt
					if (et > -1 && et < bt) {
						report.append(String.format("ERROR: annotation with id=\"%s\" has an end time < begin time: %d < %d", 
								annRec.getAnnotationId(), et, bt));
					}
					// overlap with other annotations in second iteration
				} else if (AnnotationRecord.REFERENCE.equals(annRec.getAnnotationType())) {
					numReferenceAnn++;
					if (tierTimeAlignable) {
						numRefAnnOnAlignTier++;
					}
					if (stereoType != null && stereoType.equals("Symbolic_Association") && 
							annRec.getPreviousAnnotId() != null && !annRec.getPreviousAnnotId().isEmpty()) {
						report.append(String.format("ERROR: annotation with id=\"%s\" has a previous annotation reference id=\"%s\" while on a Symbolic Association tier", 
								annRec.getAnnotationId(), annRec.getPreviousAnnotId()));
					}
				}// there can be no other annotation type

				if (!cveRefChecked) {
					if (annRec.getCvEntryId() != null) {
						if (typeRecord != null) {
							if (typeRecord.getControlledVocabulary() == null || typeRecord.getControlledVocabulary().isEmpty()) {
								report.append(String.format("ERROR: annotation id =\"%s\" refers to a CV entry id=\"%s\" while the tier type "
										+ "id=\"%s\" is not linked to a controlled vocabulary", annRec.getAnnotationId(), 
										annRec.getCvEntryId(), typeRecord.getLingTypeId()));
							}
						}
						cveRefChecked = true;
					}
				}
			} // end annotation records iteration
			
			// re-iterate for overlapping annotations within the tier, only considering fully aligned annotations
			if (numAlignableAnn > 0) {
				// even after sorting the order might be undetermined for partially aligned annotations
				//Collections.sort(annRecordList, annotationComparator);
				
				for (int i = 0; i < annRecordList.size(); i++) {
					AnnotationRecord annRec = annRecordList.get(i);
					// get the bt's and et's and compare if all exist and > -1
					long bt = toTime(timeSlotMap.get(annRec.getBeginTimeSlotId()));
					long et = toTime(timeSlotMap.get(annRec.getEndTimeSlotId()));
					
					if (bt > -1 && et > bt) { //fully aligned, bt=et and et<bt already reported before 
						// second loop
						for (int j = i + 1; j < annRecordList.size(); j++) {
							AnnotationRecord annRec2= annRecordList.get(j);
							long bt2 = toTime(timeSlotMap.get(annRec2.getBeginTimeSlotId()));
							long et2 = toTime(timeSlotMap.get(annRec2.getEndTimeSlotId()));
							if (bt2 > -1 && et2 > bt2) {
								if (bt2 < et && et2 > bt) {
									report.append(String.format("ERROR: annotation with id=\"%s\" overlaps with (at least) "
											+ "annotation with id=\"%s\": %d-%d, %d-%d", 
											annRec.getAnnotationId(), annRec2.getAnnotationId(), bt, et, bt2, et2));
									break;
								}
							}
						}
					}
				}
			}		
			
			// report mixture of alignable and reference
			if (numAlignableAnn > 0 && numReferenceAnn > 0) {
				report.append(String.format("ERROR: the tier \"%s\" contains a mixture of annotations, alignable: %d, reference: %d", 
						tierName, numAlignableAnn, numReferenceAnn));
			}
			if (numRefAnnOnAlignTier > 0) {
				report.append(String.format("ERROR: the tier \"%s\" contains %d reference annotations not consistent with tier stereotype \"%s\"", 
						tierName, numRefAnnOnAlignTier, (stereoType == null ? "None" : stereoType)));
			}
			if (numAlignAnnOnRefTier > 0) {
				report.append(String.format("ERROR: the tier \"%s\" contains %d alignable annotations not consistent with tier stereotype \"%s\"", 
						tierName, numAlignAnnOnRefTier, stereoType));
			}
		}// end tier iteration
		
		if (numTierTypeInconsistencies > 0) {
			report.append("There are tier-type/tier-hierarchy inconsistencies. Please refer to the EAF format documentation:");
			report.append("https://www.mpi.nl/tools/elan/EAF_Annotation_Format_3.0_and_ELAN.pdf");
			report.append("for valid combinations of tier constraints and tier dependencies.");
		}
		report.append("-- End of Tiers");
	}
	
	/**
	 * Performs some basic checks on the LINGUISTIC_TYPEs in the file,
	 * mainly consistency of the CONSTRAINTS and TIME_ALIGNABLE attributes.
	 * 
	 * @param eafParser the EAF Parser
	 */
	private void checkTierTypes(Parser eafParser) {
		report.append("++ Start of Tier Types (a.k.a. Linguistic Types)");
		for (LingTypeRecord typeRecord : eafParser.getLinguisticTypes(filePath)) {
			String stereoType = typeRecord.getStereoType();
			boolean timeAlignable = Boolean.valueOf(typeRecord.getTimeAlignable());
			
			// check constraint and time alignable consistency of the type
			if (typeRecord != null) {
				if (stereoType == null) {
					if (!timeAlignable) {
						report.append(String.format("ERROR: the tier type \"%s\" is for a top-level tier "
								+ "(no CONSTRAINTS), yet is not TIME_ALIGNABLE", typeRecord.getLingTypeId()));
					}
				} else if (stereoType.equals("Time_Subdivision") || stereoType.equals("Included_In")) {
					if (!timeAlignable) {
						report.append(String.format("ERROR: Stereotype \"%s\" should be TIME_ALIGNABLE", stereoType));
					}
				} else if (stereoType.equals("Symbolic_Subdivision") || stereoType.equals("Symbolic_Association")) {
					if (timeAlignable) {
						report.append(String.format("ERROR: Stereotype \"%s\" should not be TIME_ALIGNABLE", stereoType));
					}
				} else {
					report.append(String.format("ERROR: Stereotype \"%s\" is an unknown stereotype", stereoType));
				}	
			}
		}
		report.append("-- End of Tier Types");
	}
	
	/**
	 * Checks if a controlled vocabulary doesn't combine an external (.ecv) 
	 * reference with internal CV entries.
	 * 
	 * @param eafParser the EAF Parser
	 */
	private void checkCVs(Parser eafParser) {
		report.append("++ Start of Controlled Vocabularies");
		Map<String, CVRecord> cvRecords = eafParser.getControlledVocabularies(filePath);
		
		for (CVRecord cvr : cvRecords.values()) {
			// the external reference indicates that this is an externally defined CV
			// and should not contain local entries. Check the ext_ref ends with .ecv?
			if (cvr.getExtRefId() != null && !cvr.getExtRefId().isEmpty() &&
					cvr.getExtRefId().trim().toLowerCase().endsWith(".ecv")) {
				if (cvr.getEntries() != null && !cvr.getEntries().isEmpty()) {
					report.append(String.format("The CONTROLLED_VOCABULARY id=\"%s\" seems to be external (EXT_REF=\"%s\"), "
							+ "it should not contain internal entries", cvr.getCv_id(), cvr.getExtRefId()));
				}
			}
			
		}
		report.append("-- End of Controlled Vocabularies");
	}
	
	/**
	 * Converts a milliseconds String to a Long. 
	 * @param timeValue the string to convert
	 * @return the long value, -2 if the parameter is null or Long.MIN_VALUE in case of 
	 * a number format exception  
	 */
	private long toTime(String timeValue) {
		if (timeValue != null) {
			try {
				return Long.valueOf(timeValue);
			} catch (NumberFormatException nfe) {
				return Long.MIN_VALUE;
			}
		}
		
		return -2;
	}
	
	/**
	 * A handler which simply reports and counts XML validation errors and
	 * warnings.
	 */
	private class ValidationHandler extends DefaultHandler {
		int numWarnings = 0;
		int numErrors = 0;

		@Override
		public void endDocument() throws SAXException {
			report.append(String.format("Received %d warnings and %d errors", numWarnings, numErrors));
			report.append("-- End of XML validation by SAXParser\n");
		}

		@Override
		public void warning(SAXParseException e) throws SAXException {
			if (numWarnings < 5) {
				report.append("Warning: " + e.getMessage());
			}
			numWarnings++;
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			if (numErrors < 10) {
				report.append("ERROR:   " + e.getMessage());
				// system id is the file path, is already in the report
				//report.append("System id: " + e.getSystemId());
				//report.append("Public id: " + e.getPublicId());
				report.append("Line:    " + e.getLineNumber());
				report.append("Column:  " + e.getColumnNumber());
			}
			numErrors++;
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			report.append("Fatal parsing error: " + e.getMessage());
		}

        /**
         * Tries to load the local schema file (.xsd) corresponding to the file
         * that is being parsed.
         * Version 2.7 is used for EAF files with an older schema. The latest known
         * version will be used for apparent newer files.  
         * Caller must close() the returned InputStreamReader which will close() the stream.
         * Copied from EAF30Parser$EAFResolver
         */
    	@SuppressWarnings("resource")
		@Override
		public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
    		InputStream stream = null;
    		String resource = null;
    		
    		if (systemId.equals(ACMTranscriptionStore.getCurrentEAFSchemaRemote())) {
    			resource = ACMTranscriptionStore.getCurrentEAFSchemaLocal();
    		} else if (systemId.equals(EAF30.EAF30_SCHEMA_LOCATION)) {
    			resource = EAF30.EAF30_SCHEMA_RESOURCE;
    		} else if (systemId.equals(EAF28.EAF28_SCHEMA_LOCATION)) {
    			resource = EAF28.EAF28_SCHEMA_RESOURCE;
    		} else if (systemId.equals(ECV02.ECV_SCHEMA_LOCATION)) {
        		resource = ECV02.ECV_SCHEMA_RESOURCE;
    		} else if (systemId.equals(EAF27.EAF27_SCHEMA_LOCATION)) {
        		resource = EAF27.EAF27_SCHEMA_RESOURCE;
        	} else {
        		// Use a fallback - try to detect newer version based on 
        		// expected schema file name format "EAFv{n}.{m}.xsd
        		if (systemId.matches(".*v[3-9].[1-9]\\.xsd\\s*")) {        			
        			resource = ACMTranscriptionStore.getCurrentEAFSchemaLocal();
        			report.append("The schema version of the file seems to be newer than expected: " + 
        			systemId.substring(systemId.lastIndexOf('/') + 1));
        		} else {
	        		// EAF 2.7 should be compatible with older versions.
	        		resource = EAF27.EAF27_SCHEMA_RESOURCE; 
        		}
        	}
    		if (resource != null) {
    			// return a special input source
    			try {
    				stream = this.getClass().getResource(resource).openStream();
        			Reader reader = new InputStreamReader(stream);
        			report.append("Resolved schema to local resource: " + resource + "\n");
        			return new InputSource(reader);
    			} catch (IOException e) {
    				//e.printStackTrace();
    				report.append("Cannot create a local Input Source for the EAF schema");
    			}
    		}
    		// use the default behaviour
    		return null;			
		}
		
		
	}
	
	//####################################################################
	// test main
	/*
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("No input EAF file");
			return;
		}
		EAFValidator validator = new EAFValidator(args[0]);
		validator.validate();
		System.out.println(validator.getReport());
	}
	*/
}
