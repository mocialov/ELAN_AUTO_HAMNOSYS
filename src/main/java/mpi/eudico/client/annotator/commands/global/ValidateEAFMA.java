package mpi.eudico.client.annotator.commands.global;

import java.awt.event.ActionEvent;
import java.io.File;

import mpi.eudico.client.annotator.ElanFrame2;
import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.gui.FileChooser;
import mpi.eudico.client.annotator.gui.IndeterminateProgressMonitor;
import mpi.eudico.client.annotator.gui.ReportDialog;
import mpi.eudico.client.annotator.util.FileExtension;
import mpi.eudico.server.corpora.clomimpl.util.EAFValidator;
import mpi.eudico.server.corpora.util.ProcessReport;

/**
 * An action to create and show a report of a validation
 * of an EAF file.
 * Errors are reported but not repaired.
 */
@SuppressWarnings("serial")
public class ValidateEAFMA extends FrameMenuAction {

	/**
	 * Constructor.
	 * @param name the name of the action
	 * @param frame the parent frame
	 */
	public ValidateEAFMA(String name, ElanFrame2 frame) {
		super(name, frame);
	}

	/**
	 * Shows a File Chooser, creates and start a validator
	 * and shows the process report. 
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		// create a File chooser for EAF
		FileChooser chooser = new FileChooser(frame);
		
		chooser.createAndShowFileDialog(ElanLocale.getString(
				"Frame.ElanFrame.OpenDialog.Title"), FileChooser.OPEN_DIALOG,
				FileExtension.EAF_EXT, "LastUsedEAFDir");
		
        
		File selEafFile = chooser.getSelectedFile();
        
        if (selEafFile == null) {
    	   	return; 
        }
        // start in separate thread
		// create the validator and start 
        final EAFValidator validator = new EAFValidator(selEafFile);
        
        // perform on separate thread
        new ValidationThread(validator).start();

	}

	/**
	 * A thread to show an indeterminate progress dialog and to start
	 * the validation process.
	 */
	private class ValidationThread extends Thread {
		EAFValidator validator;
		
		/**
		 * Constructor.
		 * @param validator the ready-to-start validator
		 */
		public ValidationThread(EAFValidator validator) {
			this.validator = validator;
		}
		
		/**
		 * Creates a progress monitor, starts the validator and shows the 
		 * process report at the end of the validation process. 
		 */
		@Override
		public void run() {
			if (validator != null) {
				IndeterminateProgressMonitor progMonitor = new IndeterminateProgressMonitor(
						null, false, ElanLocale.getString("Validation.Message.Busy"), false, null);
				progMonitor.setDecorated(true);
				progMonitor.show();
				
				validator.validate();
				
				progMonitor.close();
				
		        ProcessReport report = validator.getReport();
				// show process report
		        ReportDialog reportDlg = new ReportDialog(frame, report);
		        reportDlg.setVisible(true);
			}
		}		
	}
}
