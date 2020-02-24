package mpi.eudico.client.annotator.commands;

import java.util.Map;
import java.util.logging.Level;

import mpi.eudico.client.annotator.imports.MergeUtil;
import mpi.eudico.client.annotator.imports.UndoableTranscriptionMerger;
import mpi.eudico.client.annotator.util.ClientLogger;
import mpi.eudico.server.corpora.clom.DecoderInfo;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;

/**
 * A class for importing contents of a CSV or tab-delimited text file,
 * or of other similar text files, e.g. subtitle text files.
 * It first create a new transcription for the contents of the text file,
 * using existing functionality, and then adds the contents of the created 
 * transcription to the receiving transcription. 
 */
public class ImportDelimitedTextCommand implements UndoableCommand {
	protected String name;
	
    // receiver, destination
    protected TranscriptionImpl transcription;
    
    // merger utility with undo/redo
    protected UndoableTranscriptionMerger transMerger = null;

    /**
     * Constructor
     * @param commandName name of the command
     */
	public ImportDelimitedTextCommand(String commandName) {
		name = commandName;
	}
	
	/**
	 * @param receiver the transcription to which the imported contents should 
	 * be added (TranscriptionImpl)
	 * @param arguments the arguments: arguments[0] = a decoder info object
	 * which includes the source file path (DecoderInfo)
	 */
	@Override
	public void execute(Object receiver, Object[] arguments) {
		transcription = (TranscriptionImpl) receiver;
		DecoderInfo decInfo = null;
		
		if (arguments.length > 0 && arguments[0] instanceof DecoderInfo) {
			decInfo = (DecoderInfo) arguments[0];
		}
		
		if (decInfo == null) {
			if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
				ClientLogger.LOG.warning("No source file and decoder information provided");
			}
			return;
		}
		try {
			TranscriptionImpl impTrans = new TranscriptionImpl(decInfo.getSourceFilePath(), decInfo);
			
			// start merging into the target transcription
			if (!impTrans.getTiers().isEmpty()) {
				MergeUtil mergeUtil = new MergeUtil();
				// the following call renames tiers if needed and returns a map of old_name->new_name
				Map<String, String> renamedTiers = mergeUtil.getRenamingTierMap(impTrans, transcription, null);
				mergeUtil.renameTiersWithTierMap(impTrans, renamedTiers);
				
				// use UndoableTranscriptionMerger here, or is it overkill?
				transMerger = new UndoableTranscriptionMerger();
				transMerger.mergeWith(transcription, impTrans, true);
			}
		} catch (Throwable t) {
			if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
				ClientLogger.LOG.warning("Error while importing text: " + t.getMessage());
			}
		}
	}

	/**
	 * Delegates undo to the merger utility.
	 */
	@Override
	public void undo() {
		if (transMerger != null) {
			transMerger.undo();
		}
	}

	/**
	 * Delegates redo to the merger utility
	 */
	@Override
	public void redo() {
		if (transMerger != null) {
			transMerger.redo();
		}
	}
	
	/**
	 * @return the name of the command
	 */
	@Override
	public String getName() {
		return name;
	}
}
