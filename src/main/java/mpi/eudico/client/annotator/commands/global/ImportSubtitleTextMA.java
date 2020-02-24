package mpi.eudico.client.annotator.commands.global;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import mpi.eudico.client.annotator.ElanFrame2;
import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.FrameManager;
import mpi.eudico.client.annotator.gui.FileChooser;
import mpi.eudico.client.annotator.util.ClientLogger;
import mpi.eudico.client.annotator.util.FileExtension;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;
import mpi.eudico.server.corpora.clomimpl.subtitletext.SubtitleDecoderInfo;
import mpi.eudico.server.corpora.clomimpl.subtitletext.SubtitleFormat;

/**
 * A menu action for starting import of subtitle text files (until now 
 * only SubRip .srt) or Audacity Label track files. 
 */
@SuppressWarnings("serial")
public class ImportSubtitleTextMA extends FrameMenuAction {

	/**
	 * 
	 * @param name the name of the command action
	 * @param frame the parent frame of the dialog and the frame
	 * to load the file for
	 */
	public ImportSubtitleTextMA(String name, ElanFrame2 frame) {
		super(name, frame);
	}

	/**
	 * Shows a file selection dialog and starts the import of the 
	 * selected file.
	 * 
	 * @param e the action event
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		FileChooser chooser = new FileChooser(frame);
		List<String[]> extensions = new ArrayList<String[]>();
		extensions.add(FileExtension.SUBRIP_EXT);// for now only add srt as extension
		extensions.add(FileExtension.TEXT_EXT);
		
		chooser.createAndShowFileAndEncodingDialog(
				ElanLocale.getString("Frame.ElanFrame.OpenDialog.Title"), // generic Open title
				FileChooser.OPEN_DIALOG, 
				null,// or Import 
				extensions, 
				FileExtension.SUBRIP_EXT, 
				false,// add the all files filter? 
				"LastUsedSubtitlesDir", 
				FileChooser.encodings,// utf-8, utf-16, CP-1252? 
				FileChooser.UTF_8,// utf-8 
				FileChooser.FILES_ONLY, 
				null);
		
		File subFile = chooser.getSelectedFile();
		String charSet = chooser.getSelectedEncoding();
		
		if (subFile != null) {
			String fullPath = subFile.getAbsolutePath();
			fullPath = fullPath.replace('\\', '/');
			String lowerPath = fullPath.toLowerCase();
			
			SubtitleDecoderInfo decoderInfo = new SubtitleDecoderInfo();
			decoderInfo.setSourceFilePath(fullPath);
			
			if (lowerPath.endsWith("srt")) {
				decoderInfo.setFormat(SubtitleFormat.SUBRIP);
			} else if (lowerPath.endsWith("txt")) {
				decoderInfo.setFormat(SubtitleFormat.AUDACITY_lABELS);
			}
			
			decoderInfo.setFileEncoding(charSet);
			try {
				TranscriptionImpl trans = new TranscriptionImpl(fullPath, decoderInfo);
				trans.setChanged();
				
				FrameManager.getInstance().createFrame(trans);
			} catch (Throwable t) {
				// log or show message
				if (ClientLogger.LOG.isLoggable(Level.WARNING)) {
					ClientLogger.LOG.warning("An error occurred while importing: " + t.getMessage());
				}
			}
		}
	}


}
