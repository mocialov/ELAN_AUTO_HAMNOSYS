package mpi.eudico.client.annotator.tiersets;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import mpi.eudico.client.annotator.util.ClientLogger;
import mpi.eudico.util.IoUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * A class for reading and writing tier sets in xml format
 *  
 * @author Aarthy Somasundaram
 */
public class TierSetIO {
	
	private final String DESC ="DESCRIPTION";
	private final String NAME ="NAME";	
	private final String TIER = "TIER";
	private final String TIERS = "TIERS";
	private final String TIERSET ="TIERSET";
	private final String TIERSETS = "TIERSETS";
	private final String VISIBLE ="VISIBLE";
	
	public TierSetIO() {
		super();
	}	
   
	/** 
	 * Reads tier set from the file.
	 * 
	 * @param f the parameter file
	 * @return a list of tier set
	 * 
	 * @throws IOException if reading or parsing fails
	 */
	public List<TierSet> read(File file) throws IOException {
		if (file == null) {
			throw new IOException("Cannot read from file: file is null");
		}
		
		try {
			XMLReader reader = XMLReaderFactory.createXMLReader(
	    		"org.apache.xerces.parsers.SAXParser");
			ParamHandler ph = new ParamHandler(); 
			reader.setContentHandler(ph);

			reader.parse(file.getAbsolutePath());
			
			return ph.getTierSetList();
		} catch (SAXException sax) {
			ClientLogger.LOG.warning("Parsing failed: " + sax.getMessage());
			throw new IOException(sax.getMessage());
		}
	}
	
	/**
	 * Writes the tier set to the file
	 * 
	 * @param file , xml tier set file
	 * @param tiersetList, list of tier sets
	 * 
	 * @throws IOException if writing fails
	 * @deprecated substituted by {@link #writeLS(File, List)}
	 */
	public void write(File file, List<TierSet> tierSetList ) throws IOException {
		if (file == null) {
			throw new IOException("Cannot write to file: file is null");
		}
		
		if(tierSetList == null || tierSetList.isEmpty()){
			throw new IOException("Cannot write to file: the tier set list is null or empty");
		}

		PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file))));
		writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		writer.println("<TIERSETS>");
		
		for(TierSet tierSet : tierSetList){
			writer.println("<" + TIERSET + " " + NAME + "=\"" + tierSet.getName() + "\" " +
					DESC + "=\"" + tierSet.getDescription() + "\" " +
					VISIBLE + "=\"" + tierSet.isVisible() +"\">");
			writer.println("<TIERS>");
			
			List<String> visibleTierList = tierSet.getVisibleTierList();
			List<String> tierList = tierSet.getTierList();
			for(String tier: tierList){
				writer.println("<" + TIER + " " + NAME + "=\"" + tier + "\" " +
						VISIBLE + "=\"" + visibleTierList.contains(tier) +"\" />");
			}
			writer.println("</TIERS>");
			writer.println("</TIERSET>");
		}
		
		writer.println("</TIERSETS>");
		
		writer.close();
	}
	
	/**
	 * Alternative implementation of writing the TierSet file using {@link IoUtil},
	 * which uses an {@link LSSerializer}. This takes care of XML escaping of tier 
	 * names etc. as opposed to the raw XML writing in the 
	 * {@link #write(File, List)} method.
	 * 
	 * @since November 2018
	 * @author Han Sloetjes
	 * 
	 * @param file the file to write to
	 * @param tierSetList the list containing the current tier sets
	 * 
	 * @throws IOException any exception (wrapped in an IOException) that can occur 
	 * while constructing a Document and writing to the file
	 */
	public void writeLS(File file, List<TierSet> tierSetList) throws IOException {
		if (file == null) {
			throw new IOException("Cannot write to file: file is null");
		}
		
		if(tierSetList == null || tierSetList.isEmpty()){
			throw new IOException("Cannot write to file: the tier set list is null or empty");
		}
		
		Document doc = createDoc(tierSetList);
		
		try {
			IoUtil.writeEncodedFile("UTF-8", file.getAbsolutePath(), doc.getDocumentElement());
		} catch (Throwable t) {
			if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
				ClientLogger.LOG.warning("Error while writing the tierset file: " +t.getMessage());
			}
			throw new IOException(t);
		}
	}
	
	/**
	 * Creates a TIERSETS DOM Document which is not based on an XML schema.
	 * 
	 * @since November 2018
	 * @author Han Sloetjes
	 * 
	 * @param tierSetList the list of tier sets to serialize
	 * @return a DOM Document 
	 * 
	 * @throw IOException any exception or error that can occur is wrapped in a
	 * (new) IOException
	 */
	private Document createDoc(List<TierSet> tierSetList) throws IOException {
		Document doc = null;
		
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder        db  = dbf.newDocumentBuilder();
			doc = db.newDocument();
			// root element
			Element docEl = doc.createElement(TIERSETS);
			if (docEl != null) {
				doc.appendChild(docEl);
			}
			
			// add each tier set
			for (TierSet tierSet : tierSetList){
				Element tierSetEl = doc.createElement(TIERSET);
				docEl.appendChild(tierSetEl);
				tierSetEl.setAttribute(NAME, tierSet.getName());
				tierSetEl.setAttribute(DESC, tierSet.getDescription());
				tierSetEl.setAttribute(VISIBLE, String.valueOf(tierSet.isVisible()));
				
				// the wrapper TIERS element for the tiers in the set
				Element tiersEl = doc.createElement(TIERS);
				tierSetEl.appendChild(tiersEl);
				
				List<String> visibleTierList = tierSet.getVisibleTierList();
				List<String> tierList = tierSet.getTierList();
				for (String tier: tierList){
					Element tierEl = doc.createElement(TIER);
					tiersEl.appendChild(tierEl);
					// add name and visible attributes
					tierEl.setAttribute(NAME, tier);
					tierEl.setAttribute(VISIBLE, String.valueOf(visibleTierList.contains(tier)));
				}
			}
			
			return doc;
		} catch (ParserConfigurationException pce) {
			if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
				ClientLogger.LOG.warning("Could not create a document: " + pce.getMessage());
			}
			
			throw new IOException(pce);
		} catch (Throwable t) {
			if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
				ClientLogger.LOG.warning("Could not create a document: " + t.getMessage());
			}
			
			throw new IOException(t);
		}
	}
	
	// ###############  Parser content handler  ############################################# */
	class ParamHandler implements ContentHandler {
		//private String curContent = "";
		private String curName = "";
		private String desc = "";
		private boolean isVisible;
		
		private List<String> tierList = null;
		private List<String> visibleTierList = null;
		private List<TierSet> tierSetList = null;
		
		/**
		 * Constructor.
		 */
		public ParamHandler() {
			super();
			tierSetList= new ArrayList<TierSet>();
		}

		/**
		 * Returns the list of tier set objects
		 * 
		 * @return the list of tier set objects
		 */
		public List<TierSet> getTierSetList() {
			return tierSetList;
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			//curContent += new String(ch, start, length);			
		}

		@Override
		public void startElement(String nameSpaceURI, String name,
	            String rawName, Attributes attributes) throws SAXException {
			if (name.equals(TIERSET)) {
				curName = attributes.getValue(NAME);
				desc = attributes.getValue(DESC);
				isVisible = Boolean.valueOf(attributes.getValue(VISIBLE));
			} 
			else if(name.equals(TIERS)){
				tierList = new ArrayList<String>();
				visibleTierList = new ArrayList<String>();
			}
			else if (name.equals(TIER)){
				tierList.add(attributes.getValue(NAME));
				if(Boolean.valueOf(attributes.getValue(VISIBLE))){
					visibleTierList.add(attributes.getValue(NAME));
				}
			}
		}

		@Override
		public void endElement(String nameSpaceURI, String name, String rawName)
				throws SAXException {
			if(name.equals(TIERSET)){
				TierSet tierSet = new TierSet(curName, tierList);
				tierSet.setDescription(desc);
				tierSet.setVisibleTiers(visibleTierList);
				tierSet.setVisible(isVisible);			
				
				tierSetList.add(tierSet);
				
				curName = "";
				tierList = null;
				visibleTierList = null;
				desc = "";
				//curContent = "";
			}
			
		}

		@Override
		public void endDocument() throws SAXException {			
		}
		
		@Override
		public void endPrefixMapping(String arg0) throws SAXException {
		}

		@Override
		public void ignorableWhitespace(char[] arg0, int arg1, int arg2)
				throws SAXException {	
		}

		@Override
		public void processingInstruction(String arg0, String arg1)
				throws SAXException {
		}

		@Override
		public void setDocumentLocator(Locator arg0) {
		}

		@Override
		public void skippedEntity(String arg0) throws SAXException {
		}

		@Override
		public void startDocument() throws SAXException {
		}

		@Override
		public void startPrefixMapping(String arg0, String arg1)
				throws SAXException {
		}
		
	}
}
