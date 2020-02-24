package mpi.eudico.util.lock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.Logger.Level;

/**
 * Utility class for creating and deleting file lock for (transcription)
 * files. 
 * The current implementation supports handling of application specific
 * lock files.
 * The methods for handling native or system dependent "global" FileLocks
 * are not used yet (after acquiring a FileLock reading of and writing to
 * the underlying file needs to be performed via the lock's FileChannel).
 * 
 * @see {@link FileLock}
 */
public class FileLockUtil {
	private static System.Logger logger = System.getLogger("util");
	private final static String PREFIX = "~";
	private final static String SUFFIX = ".lock";
	
	/**
	 * Private constructor.
	 */
	private FileLockUtil() {
		super();
	}

	/**
	 * Tries to acquire a native (system) file lock. 
	 * Note: only useful if the application uses the channel associated with
	 * the lock is used for reading from and writing to the file.
	 * 
	 * @param fileString the location of a file as a string
	 * 
	 * @return the acquired system FileLock or null if the file is already 
	 * locked
	 * 
	 * @throws IOException if it is impossible to either acquire the lock or
	 * to detect that the file is already locked by an application.
	 * Errors that can occur when creating a Path or a FileChannel or in
	 * situations where file locking is not supported etc. are wrapped in
	 * an IOException
	 */
	public static synchronized FileLock acquireNativeLock(String fileString) throws IOException {
		try {
			Path path  = Paths.get(fileString);
			FileChannel fjChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
			
			try {
				return fjChannel.tryLock();// either null or the lock
			} catch (IOException ioe) {// maybe not supported
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, String.format("Cannot acquire system lock (io): %s", ioe.getMessage()));
				}
				throw ioe;
			} catch (Throwable tt) {
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, String.format("Cannot acquire system lock (t1): %s", tt.getMessage()));
				}
				throw new IOException(tt);
			}
			
		} catch (Throwable t) {
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, String.format("Cannot acquire system lock (t2): %s", t.getMessage()));
			}
			throw new IOException(t);
		}
	}
	
	/**
	 * Releases the specified FileLock.
	 * 
	 * @param fileLock the native lock to release
	 * @return true if the lock existed and was successfully released, false 
	 * otherwise (e.g. when the lock is null or the channel already closed etc.
	 */
	public static synchronized boolean releaseNativeLock(FileLock fileLock) {
		if (fileLock == null) {
			return false;
		}
		try {
			fileLock.release();
			return true;
		} catch (Exception e) {
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, e.getMessage());
			}
			return false;
		}
	}
	
	/**
	 * Checks whether a file is already locked by an(other) application. 
	 * 
	 * @param fileString the location of the file as a string
	 * @return true if the file is locked, false otherwise
	 * @throws IOException if any error or exception occurs and it cannot
	 * be established whether the file is locked or not
	 */
	public static boolean isNativeLocked(String fileString) throws IOException {
		FileLock fl = acquireNativeLock(fileString);
		if (fl == null) {
			return true;
		} else {
			fl.release();
			return false;
		}
	}
	
	/**
	 * Creates an application specific lock file if one doesn't exist already.
	 * 
	 * @param fileString the location of the file as a string
	 * 
	 * @return the lock file if it is successfully created or null if a lock 
	 * file already exists 
	 * 
	 * @throws IOException wrapper for any error that can occur while creating
	 * a lock file and it is unknown whether the file is already locked or not
	 */
	public static synchronized File acquireAppLockFile(String fileString) throws IOException {
		try {
			Path path  = Paths.get(fileString);
			File f = path.toFile();
			if (!f.exists()) {
				throw new FileNotFoundException(String.format(
						"The file \"%s\" does not exist: ", fileString));
			}
			
			String lockFileName = f.getParent() + File.separator + PREFIX + f.getName() + SUFFIX;
		    File lockFile = new File(lockFileName);
		    
		    if (lockFile.exists()) {
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, String.format("A .lock file exists for: \"%s\"", fileString));
				}
		    	return null;
		    }
		    if (logger.isLoggable(Level.DEBUG)) {
		    	logger.log(Level.DEBUG, String.format("Creating lock file with filename %s", lockFileName));
		    }

			Path lockFilePath = lockFile.toPath();//Paths.get(lockFileName)
			java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
			List<String> lockInfo = new ArrayList<String>();
			//lockInfo.add(Long.toString(Instant.now().getEpochSecond()));
			lockInfo.add(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
			lockInfo.add(System.getProperty("user.name"));
			lockInfo.add(localMachine.getHostName());
			Files.write(lockFilePath, lockInfo, Charset.forName("UTF-8"), 
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, 
					StandardOpenOption.TRUNCATE_EXISTING);
		    lockFile.deleteOnExit();
			return lockFile;
		} catch (Throwable t) {
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, String.format("An error occured when acquiring a lock for file \"%s\": ", 
						fileString, t.getMessage()));
			}
			throw new IOException(t);
		}
	}
	
	/**
	 * Deletes the application specific lock file.
	 * 
	 * @param appLockFile the application specific lock file
	 * @return true if the file existed and was deleted, false in all other cases
	 */
	public static synchronized boolean releaseAppLockFile(File appLockFile) {
		try {
			//return appLockFile.delete();
			Files.delete(appLockFile.toPath());
			return true;
		} catch (Throwable t) {
			// null pointer, file not found, io etc
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, String.format("An error occured when deleting a lock file: \"%s\" ", 
						t.getMessage()));
			}
			return false;
		}
	}
	
	/**
	 * A method to check whether an application specific lock file exists, 
	 * without creating one if there is no lock file yet
	 * 
	 * @param fileString the location of the file as a string
	 * @return true if an application specific lock file exists, false otherwise
	 */
	public static boolean isAppLocked(String fileString) throws IOException {
		try {
			Path path  = Paths.get(fileString);
			File f = path.toFile();
			if (!f.exists()) {
				throw new FileNotFoundException(String.format(
						"The file \"%s\" does not exist: ", fileString));
			}
			
			String lockFileName = f.getParent() + File.separator + PREFIX + f.getName() + SUFFIX;
		    File lockFile = new File(lockFileName);
		    if (lockFile.exists()) {
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, String.format("A .lock file exists for: \"%s\"", fileString));
				}
		    	return true;
		    }
		} catch (Throwable t) {
			if (logger.isLoggable(Level.WARNING)) {
				logger.log(Level.WARNING, String.format("Cannot determine if a .lock file exists for: \"%s\"", fileString));
			}
			throw new IOException(t);
		}
		
		return false;
	}
	
	/**
	 * 
	 * @param fileString the location of the source file for which to find a
	 * lock file
	 * 
	 * @return the contents of the lock file or null 
	 */
	public static List<String> getAppLockContent(String fileString) {
		try {
			Path path  = Paths.get(fileString);
			File f = path.toFile();
			
			String lockFileName = f.getParent() + File.separator + PREFIX + f.getName() + SUFFIX;
		    File lockFile = new File(lockFileName);
		    
		    return getAppLockContent(lockFile);
		} catch(Throwable t) {
			if (logger.isLoggable(Level.WARNING)) {
				logger.log(Level.WARNING, String.format("Cannot determine if a .lock file exists for: \"%s\"", fileString));
			}
			return null;
		}
	}
	
	/**
	 * Retrieves the contents of the lock file.
	 *  
	 * @param appLockFile the lock file to retrieve the contents from
	 * @return a list of strings (expected to be of size 3) 
	 */
	public static List<String> getAppLockContent(File appLockFile) {
		try {
			if (appLockFile.exists()) {
				List<String> lines = new ArrayList<String>();
				BufferedReader br = new BufferedReader(new FileReader(appLockFile));
				String line = null;
				while ((line = br.readLine()) != null) {
					lines.add(line);
				}
				try {
					br.close();
				} catch (IOException ie) {}
				
				return lines;
			} else {
				if (logger.isLoggable(Level.INFO)) {
					logger.log(Level.INFO, String.format("The .lock file does not exist: \"%s\"", appLockFile.getAbsolutePath()));
				}
			}
		} catch (Throwable t) {
			if (logger.isLoggable(Level.INFO)) {
				logger.log(Level.INFO, String.format("Cannot get the contents of the .lock file: \"%s\"", t.getMessage()));
			}
		}
		return null;
	}

}