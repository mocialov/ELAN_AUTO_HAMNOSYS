package mpi.eudico.util.lock;

import java.io.File;
import java.nio.channels.FileLock;

/**
 * Utility class to store information on a lock file for a file
 * at a location specified as a string.
 * Supports both (global, system wide) Java FileLocks and application specific
 * lock files (only the latter is used at the moment).  
 */
public class FileLockInfo {
	private String   fileString;
	private FileLock globalLock;
	private File     appLock;
	private boolean globalLocked = false;
	private boolean appLocked = false;
	
	/**
	 * Constructor.
	 * Note: could throw an exception if the file is identified by e.g. the
	 * hhtp or https protocol.  
	 * 
	 * @param fileString the location of the file as a string
	 */
	public FileLockInfo(String fileString) {
		super();
		this.fileString = fileString;
	}
	
	/**
	 * 
	 * @return the location of the file to lock
	 */
	public String getFileString() {
		return fileString;
	}

	/**
	 * 
	 * @return the application specific lock file 
	 */
	public File getAppLock() {
		return appLock;
	}

	/**
	 * 
	 * @param appLock the acquired lock file 
	 */
	public void setAppLock(File appLock) {
		this.appLock = appLock;
		appLocked = (appLock != null);
	}

	/**
	 * 
	 * @return the native or system wide file lock
	 */
	public FileLock getGlobalLock() {
		return globalLock;
	}

	/**
	 * 
	 * @param globalLock the acquired FileLock
	 */
	public void setGlobalLock(FileLock globalLock) {
		this.globalLock = globalLock;
		globalLocked = (globalLock != null);
	}

	/**
	 * 
	 * @return whether the source file is globally locked (on behalf of this JVM)
	 */
	public boolean isGlobalLocked() {
		return globalLocked;
	}

	/**
	 * 
	 * @param globalLocked the new value for the global lock flag
	 */
	public void setGlobalLocked(boolean globalLocked) {
		this.globalLocked = globalLocked;
	}

	/**
	 * 
	 * @return whether this application holds the application specific lock
	 * file
	 */
	public boolean isAppLocked() {
		return appLocked;
	}

	/**
	 * 
	 * @param appLocked the new value for the application lock flag
	 */
	public void setAppLocked(boolean appLocked) {
		this.appLocked = appLocked;
	}
	
	
}