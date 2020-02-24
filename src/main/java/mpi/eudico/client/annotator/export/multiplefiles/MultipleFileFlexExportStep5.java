package mpi.eudico.client.annotator.export.multiplefiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import mpi.eudico.client.annotator.gui.multistep.MultiStepPane;
import mpi.eudico.server.corpora.clom.Transcription;
import mpi.eudico.server.corpora.clomimpl.abstr.TierImpl;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;
import mpi.eudico.server.corpora.clomimpl.flex.FlexConstants;
import mpi.eudico.server.corpora.clomimpl.flex.FlexEncoder;
import mpi.eudico.server.corpora.clomimpl.flex.FlexEncoderInfo;

@SuppressWarnings("serial")
public class MultipleFileFlexExportStep5 extends AbstractMultiFileExportProgessStepPane{
	
	/** a hash map of flexType - list<linguistic types> */
	private Map<String, List<String>> itemTypeMap;
	
	/** a hash map of flex-item Type - list<linguistic types> */
	private Map<String, String> elementTypeMap;	
	
	/** a hash map of flexType - list<String> (linguistic type) */
	private Map<String, List<String>> linTypeMap;
	
	private String morphType;
	
	private FlexEncoderInfo encoderInfo; 	
	
	private boolean getFromTierName;
	/** a list of content languages collected from the tiers in the transcriptions */
	private List<String> tierContentLanguages;
		 
	/**
     * Constructor
     *
     * @param multiPane the container pane
     */
    public MultipleFileFlexExportStep5(MultiStepPane multiPane){
    	super(multiPane);
    }
    
    /**
     * Calls doFinish.
     *
     * @see mpi.eudico.client.annotator.gui.multistep.Step#enterStepForward()
     */
    @SuppressWarnings("unchecked")
	@Override
	public void enterStepForward() {    
    	    	
    	elementTypeMap = (Map<String, String>) multiPane.getStepProperty("ElementTypeMap");	
    	
    	itemTypeMap = (Map<String, List<String>>) multiPane.getStepProperty("ElementItemMap");
		
    	morphType = (String) multiPane.getStepProperty("Morph-Type");
		
    	linTypeMap = (Map<String, List<String>>) multiPane.getStepProperty("TypeLangMap");
    	
    	getFromTierName = (Boolean)multiPane.getStepProperty("GetFromTierName");
    	
		tierContentLanguages = (List<String>) multiPane.getStepProperty(FlexConstants.LANGUAGES);
    	
        super.enterStepForward();  
    }
    
    /**
     * The actual writing.
     *
     * @param fileName path to the file, not null
     * @param orderedTiers tier names, ordered by the user, min size 1	     
     *
     * @return true if all went well, false otherwise
     */
    @Override
	protected boolean doExport(TranscriptionImpl transcription, final String fileName) {	
    	if (transcription != null && fileName != null) {
    		
        	encoderInfo = getFlexEncoderInfo(transcription);   
        	encoderInfo.setFile(fileName);
        	
        	FlexEncoder encoder = new FlexEncoder();
        	encoder.setEncoderInfo(encoderInfo);
            encoder.encode(transcription);
        	
//            try {
//            	FlexEncoder encoder = new FlexEncoder();
//                encoder.encode(transcription);
//            } catch (IOException ioe) {
//                JOptionPane.showMessageDialog(MultipleFileFlexExportStep5.this,
//                        ElanLocale.getString("ExportDialog.Message.Error") + "\n" +
//                        "(" + ioe.getMessage() + ")",
//                        ElanLocale.getString("Message.Error"),
//                        JOptionPane.ERROR_MESSAGE);   
//            }
            return true;
        }

    	// some message describing that y export failed
    	
        return false;
    }   
    
    private FlexEncoderInfo getFlexEncoderInfo(TranscriptionImpl transcription){	    	
    	    	
    	encoderInfo = new FlexEncoderInfo();
    	
    	updateElementMappingTiers(transcription);    	
    	updateElementItemMappingTiers(transcription);
    	updateTypeLangMap(transcription);
      	
      	
      	return encoderInfo;
        
    }
    
    private void updateElementMappingTiers(TranscriptionImpl transcription){
    	List<TierImpl> itTiers = new ArrayList<TierImpl>();
		List<TierImpl> paraList = new ArrayList<TierImpl>();
		List<TierImpl> phraseList = new ArrayList<TierImpl>();
		List<TierImpl> wordList = new ArrayList<TierImpl>();
		List<TierImpl> morphList = new ArrayList<TierImpl>();
				
		encoderInfo.setMappingForElement(FlexConstants.IT, itTiers);
		encoderInfo.setMappingForElement(FlexConstants.PARAGR, paraList);
		encoderInfo.setMappingForElement(FlexConstants.PHRASE, phraseList);
		encoderInfo.setMappingForElement(FlexConstants.WORD, wordList);
		encoderInfo.setMappingForElement(FlexConstants.MORPH, morphList);
    	  
    	// load IT tier
    	String type = elementTypeMap.get(FlexConstants.IT);
    	TierImpl selectTextTier = null;
    	if(type != null){
    		List<TierImpl> tierList = transcription.getTiersWithLinguisticType(type);    		 		
    		for(int i = 0; i < tierList.size(); i++ ){
    			TierImpl t = tierList.get(i);
    			if(t.getLinguisticType().getConstraints() == null){		
    				if(selectTextTier == null){
    					selectTextTier = t;
    				}
    				
    				if(t.getName().contains(FlexConstants.IT)){
    					selectTextTier = t;
    					break;
    				}
    			}
    		}
    	}   
    	
    	if(selectTextTier != null){
			itTiers.add(selectTextTier);
		}
    	
    	//paragraph tiers
    	type = elementTypeMap.get(FlexConstants.PARAGR);
    	if(type != null){
    		List<TierImpl> tierList = transcription.getTiersWithLinguisticType(type);
    		if(tierList.size() > 0){
    			//if paragraph is the toplevel tier
        		if(tierList.get(0).getLinguisticType().getConstraints() == null){
        			paraList.addAll(tierList);	
    			} else {
    				// paragraph should be child tier is itTier
    				for(int i = 0; i < tierList.size(); i++ ){
    					TierImpl t = tierList.get(i);
    	    			if(selectTextTier == null || t.hasParentTier() && t.getParentTier().equals(selectTextTier)){
    						if(hasPhraseTier(t)){
    							paraList.add(t);								
    						}
    					} 
    	    		}
    			
    			}
    		}
    	}
    	
    	// phrase tiers
    	type = elementTypeMap.get(FlexConstants.PHRASE);
    	if(type != null){
    		if(paraList.size() > 0){
    			//if phrase is the child tier of paragraph
    			for(int x =0; x < paraList.size(); x++){
    				List<TierImpl> tiers = paraList.get(x).getChildTiers();					
					for(int i = 0; i < tiers.size(); i++){
						TierImpl t = tiers.get(i);
						if(t.getLinguisticType().getLinguisticTypeName().equals(type)){
							phraseList.add(t);
						}
					}
    			}
    		} else {
    			phraseList.addAll(transcription.getTiersWithLinguisticType(type));
    		}
    	}
    	
    	//word Tiers
    	type = elementTypeMap.get(FlexConstants.WORD);
    	if(type != null){
    		for(int x =0; x < phraseList.size(); x++){
    			List<TierImpl> tiers = phraseList.get(x).getChildTiers();					
				for(int i = 0; i < tiers.size(); i++){
					TierImpl t = tiers.get(i);
					if(t.getLinguisticType().getLinguisticTypeName().equals(type)){
						wordList.add(t);
					}
				}
    		} 
    	}
    	
    	//morph tiers
    	type = elementTypeMap.get(FlexConstants.MORPH);
    	if(type != null){
    		for(int x =0; x < wordList.size(); x++){
    			List<TierImpl> tiers = wordList.get(x).getChildTiers();					
				for(int i = 0; i < tiers.size(); i++){
					TierImpl t = tiers.get(i);
					if(t.getLinguisticType().getLinguisticTypeName().equals(type)){
						morphList.add(t);
					}
				}
    		} 
    	}
    }
    
    private void updateElementItemMappingTiers(Transcription transcription){				
		encoderInfo.setMappingForItem(FlexConstants.IT, getTiersForItem(FlexConstants.IT));
		encoderInfo.setMappingForItem(FlexConstants.PHRASE, getTiersForItem(FlexConstants.PHRASE));
		encoderInfo.setMappingForItem(FlexConstants.WORD, getTiersForItem(FlexConstants.WORD));
		encoderInfo.setMappingForItem(FlexConstants.MORPH, getTiersForItem(FlexConstants.MORPH));
		
		//morph Type tiers
		List<TierImpl> morphTypeTierList = new ArrayList<TierImpl>();
		encoderInfo.setMorphTypeTiers(morphTypeTierList);
		if(morphType != null){
			List<TierImpl> tierList = encoderInfo.getMappingForElement(FlexConstants.MORPH);
			
			for(int i= 0; i< tierList.size(); i++){
				TierImpl t = tierList.get(i);
				List<TierImpl> childTiers = t.getChildTiers();
				for(int j= 0; j< childTiers.size(); j++){
					t = childTiers.get(j);
					if(morphType.equals(t.getLinguisticType().getLinguisticTypeName())){
						morphTypeTierList.add(t);
					}
				}
			}
		}
    }
    
    private List<TierImpl> getTiersForItem(String itemType){
		List<TierImpl> tierList = encoderInfo.getMappingForElement(itemType);;
		List<String> typeList = itemTypeMap.get(itemType);
				
		List<TierImpl> itemTierList = new ArrayList<TierImpl>();
		for(int i= 0; i< tierList.size(); i++){
			TierImpl t = tierList.get(i);
			List<TierImpl> childTiers = t.getChildTiers();
			for(int j= 0; j< childTiers.size(); j++){
				t = childTiers.get(j);
				if(typeList.contains(t.getLinguisticType().getLinguisticTypeName())){
					itemTierList.add(t);
				}
			}
		}		
		return itemTierList;
	}
    
    /**
	 * Checks if the given tier has any phrase type tiers
	 * 
	 * @return boolean
	 */
	private boolean hasPhraseTier(TierImpl tier){
		if(tier != null){
			List<TierImpl> childTiers = tier.getChildTiers();
			for(int i=0; i < childTiers.size(); i++){
				tier = childTiers.get(i);				
				if(tier.getLinguisticType().getLinguisticTypeName().equals(elementTypeMap.get(FlexConstants.PHRASE))){
					return true;
				}
			}
		}
		
		return false;
	}
	
	private void updateTypeLangMap(TranscriptionImpl transcription){
		
		List<TierImpl> tierList = new ArrayList<TierImpl>();
		tierList.addAll(encoderInfo.getMappingForElement(FlexConstants.IT));
		tierList.addAll(encoderInfo.getMappingForElement(FlexConstants.PHRASE));
		tierList.addAll(encoderInfo.getMappingForElement(FlexConstants.WORD));
		tierList.addAll(encoderInfo.getMappingForElement(FlexConstants.MORPH));
		
		tierList.addAll(encoderInfo.getMappingForItem(FlexConstants.IT));
		tierList.addAll(encoderInfo.getMappingForItem(FlexConstants.PHRASE));
		tierList.addAll(encoderInfo.getMappingForItem(FlexConstants.WORD));
		tierList.addAll(encoderInfo.getMappingForItem(FlexConstants.MORPH));
		
		HashMap<String, List<String>> map = new HashMap<String, List<String>>();
		
		for (Entry<String, List<String>> entry : linTypeMap.entrySet()) {
			String lingType = entry.getKey();
			List<String> valueList = entry.getValue();
			
			List<TierImpl> tiers = transcription.getTiersWithLinguisticType(lingType);
			
			for (TierImpl t : tiers) {
				if(tierList.contains(t)){
					
					if(getFromTierName){
						String type = getTypeName(t.getName());
						String lang = getLanguage(t.getName());
						
						if (type != null || lang != null) {
							List<String> tvList = new ArrayList<String>(valueList);
							
							if(type != null){
								tvList.set(0, type);
							}
							
							if(lang != null){
								tvList.set(1, lang);
							}
							
							map.put(t.getName(), tvList);
						} else {
							// no own type or language detected, use the tier-type's type and 
							// language settings for the tier
							map.put(t.getName(), valueList);
						}
					} else {
						// use the tier-type's type and language settings for the tier
						map.put(t.getName(), valueList);
					}
				}
			}			
		}
		
		encoderInfo.setTypeLangMap(map);
	}
	
	/**
	 * Extracts the type information from the tier type name.
	 * Expected type name format: <TierTypeName-FLExTypeName-language>
	 * Since both the FLEx type name and the language part can contain
	 * the hyphen character ('-'), the first check is now if the input
	 * ends with any of the available content languages in the transcription.
	 *
	 * @param typeName from which the type has to be extracted
	 * @return the FLEx type part or null
	 */
	private String getTypeName(String typeName){
		String type = null;
		
		if(typeName.startsWith(FlexConstants.IT)){
			typeName = typeName.substring(FlexConstants.IT.length());
		}
		
		if (tierContentLanguages != null) {
			// first try to detect if it ends with any tier's content language
			for (String cl : tierContentLanguages) {
				if (typeName.endsWith(cl)) {
					// perform some more checks
					int li = typeName.lastIndexOf(cl);
					if (li > 0) {
						if (typeName.charAt(li - 1) == '-') {
							// remove the detected language
							typeName = typeName.substring(0, li - 1);
							int fi = typeName.indexOf("-");
							if (fi > -1 && fi < typeName.length() - 1) {
								return typeName.substring(fi + 1);
							} else {
								return typeName;
							}
						}
					}
					//break;// or continue the loop if the name does not end with "-language"?
				}
			}
		}
		
		String[] compsArray = typeName.split("-");
		if (compsArray.length == 3) {
			// select second component
			type = compsArray[1];
		} else if (compsArray.length > 3) {
			// glue all components except the first and the last together
			// equivalent to
			type = typeName.substring(typeName.indexOf("-") + 1, typeName.lastIndexOf("-"));
		} else if (compsArray.length == 2) {
			type = compsArray[0];//??
		}
		/*
		int index = name.indexOf('-');
		int nextIndex = -1;
		
		if(index > -1){
			if(index+1 < name.length()){
				nextIndex = name.indexOf('-', index+1);		
			}
					
			if(nextIndex > -1 && index+1 < nextIndex){
				type = name.substring(index+1, nextIndex);
			} else {
				type = name.substring(index+1);
			}
		}
		*/
		if(type == null || type.equals(FlexConstants.ITEM)){
			type = null;
		}
		
		return type;		
	}
	
	/**
	 * Extracts the language information from the tier type name
	 * Expected Tier Type name format: <TierTypeName-FLExTypeName-language>
	 * Since both the FLEx type name and the language part can contain
	 * the hyphen character ('-'), the first check is now if the input
	 * ends with any of the available content languages in the transcription. 
	 * 
	 * @param typeName the name of the tier/type name
	 * @return the detected language part or null
	 */
	private String getLanguage(String typeName){		
		String lang = null;		
		
		if(typeName.startsWith(FlexConstants.IT)){
			typeName = typeName.substring(FlexConstants.IT.length());
		}
		// first try to detect if the name ends with any tier's content language
		if (tierContentLanguages != null) {
			
			for (String cl : tierContentLanguages) {
				if (typeName.endsWith(cl)) {
					// perform some more checks
					int li = typeName.lastIndexOf(cl);
					if (li > 0) {
						if (typeName.charAt(li - 1) == '-') {
							// return the detected language
							return typeName.substring(li);
						}
					}
					//break;// or continue the loop the name does not end with "-language"?
				}
			}			
		}
		

		int lindex = typeName.lastIndexOf("-");
		if (lindex > -1 && lindex < typeName.length() - 2) {
			lang = typeName.substring(lindex + 1);
		}
		/*
		int firstIndex = name.indexOf('-');	
		int nextIndex = -1;
		if(firstIndex+1 < name.length()){
			nextIndex = name.indexOf('-', firstIndex+1);		
		}
		
		if(nextIndex > -1 && firstIndex+1 < nextIndex){
			lang = name.substring(nextIndex+1);
		} 
		*/
		return lang;
	}
}
