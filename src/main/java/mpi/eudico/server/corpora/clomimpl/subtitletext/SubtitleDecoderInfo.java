/**
 * 
 */
package mpi.eudico.server.corpora.clomimpl.subtitletext;

import mpi.eudico.server.corpora.clom.DecoderInfo;

/**
 * An information class for decoding supported subtitle text formats.
 * 
 * @author Han Sloetjes
 */
public class SubtitleDecoderInfo implements DecoderInfo {
	private String filePath;
	private SubtitleFormat format;
	private String fileEncoding;
	private int defaultDuration = 1000;
	private boolean removeHTML = true;
	
	
	/**
	 * @return the source file path
	 */
	@Override
	public String getSourceFilePath() {
		return filePath;
	}
	
	/**
	 * Sets the path to the source file.
	 * 
	 * @param sourceFilePath the path to the source path
	 */
	public void setSourceFilePath(String sourceFilePath) {
		this.filePath = sourceFilePath;
	}

	/**
	 * 
	 * @return the duration of annotations that have a start time but no
	 * end time
	 */
	public int getDefaultDuration() {
		return defaultDuration;
	}

	/**
	 * Sets the duration to apply to annotation without an end time
	 * 
	 * @param defaultDuration the default duration for single point annotations
	 */
	public void setDefaultDuration(int defaultDuration) {
		this.defaultDuration = defaultDuration;
	}

	/**
	 * @return the encoding of file to import
	 */
	public String getFileEncoding() {
		return fileEncoding;
	}

	/**
	 * 
	 * @param fileEncoding the text encoding of the file to import
	 */
	public void setFileEncoding(String fileEncoding) {
		this.fileEncoding = fileEncoding;
	}

	/**
	 * 
	 * @return the (probable) format of the subtitle file 
	 */
	public SubtitleFormat getFormat() {
		return format;
	}

	/**
	 * 
	 * @param format the format the file is in (based on the file extension
	 *  or the information provided by the user)
	 */
	public void setFormat(SubtitleFormat format) {
		this.format = format;
	}

	/**
	 * 
	 * @return true if X/HTML tags should be removed from the subtitle lines
	 */
	public boolean isRemoveHTML() {
		return removeHTML;
	}

	/**
	 * 
	 * @param removeHTML if true, X/HTML tags should be removed from the lines
	 */
	public void setRemoveHTML(boolean removeHTML) {
		this.removeHTML = removeHTML;
	}
	
}
