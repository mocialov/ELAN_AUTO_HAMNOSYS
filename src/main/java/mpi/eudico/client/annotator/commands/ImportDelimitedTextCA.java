package mpi.eudico.client.annotator.commands;

import mpi.eudico.client.annotator.ViewerManager2;


/**
 * A command action that creates an import (Tab) delimited text dialog.
 */
@SuppressWarnings("serial")
public class ImportDelimitedTextCA extends CommandAction {
    /**
     * Constructor.
     *
     * @param viewerManager the viewer manager
     */
    public ImportDelimitedTextCA(ViewerManager2 viewerManager) {
        super(viewerManager, ELANCommandFactory.IMPORT_TAB);
    }

    /**
     * @see mpi.eudico.client.annotator.commands.CommandAction#newCommand()
     */
    @Override
	protected void newCommand() {
        command = ELANCommandFactory.createCommand(vm.getTranscription(),
                ELANCommandFactory.IMPORT_TAB_DLG);
    }

    /**
     * Returns the receiver of the command
     *
     * @return the receiver
     */
    @Override
	protected Object getReceiver() {
        return vm.getTranscription();
    }
}
