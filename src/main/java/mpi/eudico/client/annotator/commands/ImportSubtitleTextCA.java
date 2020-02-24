package mpi.eudico.client.annotator.commands;

import mpi.eudico.client.annotator.ViewerManager2;

/**
 *  A command action that creates an import subtitle text dialog.
 */
@SuppressWarnings("serial")
public class ImportSubtitleTextCA extends CommandAction {

    /**
     * Constructor.
     *
     * @param viewerManager the viewer manager
     */
	public ImportSubtitleTextCA(ViewerManager2 viewerManager) {
		super(viewerManager, ELANCommandFactory.IMPORT_SUBTITLE);
	}

    /**
     * Creates a command to show a file selection dialog.
     * 
     * @see mpi.eudico.client.annotator.commands.CommandAction#newCommand()
     */
    @Override
	protected void newCommand() {
        command = ELANCommandFactory.createCommand(vm.getTranscription(),
                ELANCommandFactory.IMPORT_SUBTITLE_DLG);
	}

    /**
     * Returns the receiver of the command, the transcription
     *
     * @return the receiver
     */
	@Override
	protected Object getReceiver() {
		return vm.getTranscription();
	}	

}
