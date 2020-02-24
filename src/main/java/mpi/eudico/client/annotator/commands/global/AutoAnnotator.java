package mpi.eudico.client.annotator.commands.global;

import java.awt.event.ActionEvent;
import mpi.eudico.client.annotator.ElanFrame2;
import mpi.eudico.client.annotator.autoannotator.AutoAnnotatorDialog;
import mpi.eudico.client.annotator.update.ElanUpdateDialog;

/**
 * Menu action that checks for new updates of ELAN
 *
 * @author aarsom
 */
public class AutoAnnotator extends FrameMenuAction{

    /**
     * Creates a new UpdateElanMA instance
     *
     * @param name the name of the action
     */
    public AutoAnnotator(String name, ElanFrame2 frame) {
        super(name, frame);
    }

    /**
     * Creates a updater and checks for update.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        AutoAnnotatorDialog updater = new AutoAnnotatorDialog(frame);
        //updater.checkForUpdates();
    }

}
