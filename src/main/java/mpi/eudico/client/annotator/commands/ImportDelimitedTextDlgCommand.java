package mpi.eudico.client.annotator.commands;

import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.gui.FileChooser;
import mpi.eudico.client.annotator.gui.ImportCSVDialog;
import mpi.eudico.client.annotator.util.FileExtension;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;
import mpi.eudico.server.corpora.clomimpl.delimitedtext.DelimitedTextDecoderInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * A command that creates an import CSV or tab-delimited text dialog, gets the settings 
 * from the dialog and starts the actual, undoable import process.
 */
public class ImportDelimitedTextDlgCommand implements Command {
    private String commandName;

    /**
     * Creates a Import CSV or delimited text dialog
     *
     * @param name the name of the command
     */
    public ImportDelimitedTextDlgCommand(String name) {
        commandName = name;
    }

    /**
     * Prompts for a file name and creates the import dialog. The dialog might
     * depend on the type of the selected file.
     *
     * @param receiver the Transcription
     * @param arguments null
     *
     * @see mpi.eudico.client.annotator.commands.Command#execute(java.lang.Object,
     *      java.lang.Object[])
     */
    @Override
	public void execute(Object receiver, Object[] arguments) {
        TranscriptionImpl trans = (TranscriptionImpl) receiver;

        // show a dialog, get the decoding object, create a new transcription for it and 
        // merge the results with the receiving transcription        
    	FileChooser chooser = new FileChooser(ELANCommandFactory.getRootFrame(trans));
//    	chooser.createAndShowFileDialog(ElanLocale.getString(
//                "Frame.ElanFrame.OpenDialog.Title"), FileChooser.OPEN_DIALOG,
//                FileExtension.CSV_EXT, "LastUsedCSVDir");
    	// a change to allow the "all" file filter for other separated text value formats
    	List<String[]> extensionList = new ArrayList<String[]>();
    	extensionList.add(FileExtension.CSV_EXT);
    	chooser.createAndShowFileDialog(ElanLocale.getString(
                "Frame.ElanFrame.OpenDialog.Title"), FileChooser.OPEN_DIALOG, null,
    			extensionList, FileExtension.CSV_EXT, true, 
                "LastUsedCSVDir", FileChooser.FILES_ONLY, null);
        File csvFile = chooser.getSelectedFile();
        String fullPath = null;
        if (csvFile != null) {
            fullPath = chooser.getSelectedFile().getAbsolutePath();
            File fileTemp = new File(fullPath);
           
            csvFile = fileTemp;
            // don't check if file is a '.csv' or '.txt' file
            ImportCSVDialog dialog = new ImportCSVDialog(ELANCommandFactory.getRootFrame(trans), fileTemp);
            Object value = dialog.showDialog();
            
            if (value == null || !(value instanceof DelimitedTextDecoderInfo)) {
                return;
            }
            
            DelimitedTextDecoderInfo decInfo = (DelimitedTextDecoderInfo) value;
            
            Command com = ELANCommandFactory.createCommand(trans, ELANCommandFactory.IMPORT_TAB);
            com.execute(trans, new Object[] {decInfo});
        }
    }

    /**
     * Returns the name of the command
     *
     * @return the name of the command
     */
    @Override
	public String getName() {
        return commandName;
    }
}
