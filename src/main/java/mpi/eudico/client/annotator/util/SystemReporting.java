package mpi.eudico.client.annotator.util;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SystemReporting {
	public static final String OS_NAME;
	public static final String USER_HOME;
	public static boolean antiAliasedText = false;
	public static boolean useBufferedPainting = false;
	public static boolean isBufferedPaintingPropertySet = false;
	private static boolean isMacOS;
	private static boolean isMacSierraOrHigher = false;
	private static boolean isWindows;
	private static boolean isVista = false;
	private static boolean isWin7 = false;	
	private static boolean isLinux;
	
	static {
		OS_NAME = System.getProperty("os.name");
		USER_HOME = System.getProperty("user.home");
		
		String lowerOS = OS_NAME.toLowerCase();
		
		if (lowerOS.indexOf("win") > -1) {
			isWindows = true;
		} else if (lowerOS.indexOf("mac") > -1) {
			isMacOS = true;
		} else if (lowerOS.indexOf("lin") > -1) {
			isLinux = true;
		}
		
		// check Windows versions and macOS
		String version = System.getProperty("os.version");// 6.0 = Vista, 6.1 = Win 7

		try {
			if (isWindows) {
				if (version.indexOf('.') > -1) {
					String[] verTokens = version.split("\\.");
					int major = Integer.parseInt(verTokens[0]);
					if (verTokens.length > 1) {
						int minor = Integer.parseInt(verTokens[1]);
						if (major > 6) {
							// treat as win 7 for now
							isWin7 = true;
						} else if (major == 6) {
							if (minor > 0) {
								isWin7 = true;
							} else {
								isVista = true;
							}
						}
					}
				} else {
					int major = Integer.parseInt(version);
					if (major > 6) {
						isWin7 = true;
					} else if (major == 6){
						isVista = true;// arbitrary assumption
					}
				}
			} else if (isMacOS) {
				String[] verTokens = version.split("\\.");
				if (verTokens.length >= 2) {
					int major = Integer.parseInt(verTokens[0]);
					if (major == 10) {
						int minor = Integer.parseInt(verTokens[1]);
						if (minor >= 11) {
							isMacSierraOrHigher = true;
						}
					} else if (major > 10) {
						isMacSierraOrHigher = true;
					}
				}
			}
		} catch (NumberFormatException nfe) {
			ClientLogger.LOG.warning("Unable to parse the Windows version.");
		}
		
		String atp = System.getProperty("swing.aatext");
		if ("true".equals(atp)) {
			antiAliasedText = true;
		}
		// for now under J 1.6 only apply the text anti aliasing property
		@SuppressWarnings("rawtypes")
		Map map = (Map)(Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints"));

		if (map != null) {
			Object aaHint = map.get(RenderingHints.KEY_TEXT_ANTIALIASING);

			if (RenderingHints.VALUE_TEXT_ANTIALIAS_OFF != aaHint /*|| 
					RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT.equals(aaHint)*/) {
				// treat default as anti-aliasing on??
				antiAliasedText = true;
			}
			//Iterator mapIt = map.keySet().iterator();
		}
		
		String awtRH = System.getProperty("awt.useSystemAAFontSettings");
		if ("on".equals(awtRH)) {
			antiAliasedText = true;
		} else if (map != null) {
			// a desktop setting is overridden by a -D argument
			// should do more specialized testing on the value of awtRH
			if ("off".equals(awtRH) || "false".equals(awtRH) || "default".equals(awtRH)) {
			    antiAliasedText = false;
			}
		}
		
        String bufImg = System.getProperty("useBufferedImage");
        if (bufImg != null) {
        	isBufferedPaintingPropertySet = true;
        	if (bufImg.toLowerCase().equals("true")) {
        		useBufferedPainting = true;
        	}
        }
	}

	public static boolean isMacOS() {
		return isMacOS;
	}
	
	public static boolean isMacOSSierraOrHigher() {
		return isMacSierraOrHigher;
	}
	
	public static boolean isWindows() {
		return isWindows;
	}
	
	public static boolean isWindowsVista() {
		return isVista;
	}

	public static boolean isWindows7OrHigher() {
		return isWin7;
	}
	
	public static boolean isLinux() {
		return isLinux;
	}
	
	public static void printProperty(String prop) {
		System.out.println(prop + " = " + System.getProperty(prop));
	}

	/**
	 *  @return lib/ext directory.
	 */
	public static File getLibExtDir() {
		if (OS_NAME.startsWith("Mac OS X")) {
			return verifyMacUserLibExt();
		} else {
			return new File (System.getProperty("java.home")
								 + File.separator
								 + "lib"
								 + File.separator
								 + "ext");
		}
	}

	/**
	   @return files from lib/ext. May be null.
	*/
	public static File[] getLibExt() {
		File ext = SystemReporting.getLibExtDir();
		if (ext != null && ext.exists()) {
			return SystemReporting.getLibExtDir().listFiles();
		} else {
			return null;
		}
	}
	
    private static File verifyMacUserLibExt() {
		// im jars will be stored in the user home library ext dir
		String userLibJavaExt = USER_HOME + "/Library/Java/Extensions";

		File userLibExt = new File(userLibJavaExt);
		//System.out.println("Home lib ext: " + userLibJavaExt);
		if (!userLibExt.exists()) {
			try {
				boolean success = userLibExt.mkdirs();
				if (!success) {
					ClientLogger.LOG.warning("Unable to create folder: " + userLibExt);
					return null;
				}
			} catch (SecurityException se) {
				ClientLogger.LOG.warning("Unable to create folder: " + userLibExt);
				ClientLogger.LOG.warning("Cause: " + se.getMessage());
				return null;
			}
		}
		return userLibExt;
	}

	/**
	   report files from lib/ext
	*/
	public static void printLibExt() {
		File potext[] = getLibExt();
		int NOFfiles = potext==null?0:potext.length;
		System.out.println("Found " + NOFfiles+ " potential extension(s)");
		for (int i=0; i<NOFfiles; i++) {
			System.out.println("\t" + potext[i]);
		}
	}

	/**
	 * 
	 * @return a list containing one line per active, physical screen and
	 * one line with the main screen resolution
	 */
	public static List<String> getScreenInfo() {
		List<String> infoList = new ArrayList<String>(4);
		try {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice[] screens = ge.getScreenDevices();
			int count = 0;
			for (GraphicsDevice gd : screens) {
				count++;
				DisplayMode dMode = gd.getDisplayMode();
				infoList.add(String.format("Screen %d - isDefault:%b, %s", 
						count, (gd == ge.getDefaultScreenDevice()), 
						String.format("w:%d, h:%d, bitDepth:%d", dMode.getWidth(), dMode.getHeight(), 
								dMode.getBitDepth())));
			}

			Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
			infoList.add(String.format("Main screen resolution:%d (w:%d, h:%d)", 
					Toolkit.getDefaultToolkit().getScreenResolution(), dim.width, dim.height));
		} catch (Throwable t) {
		}
		return infoList;
	}
	
	/**
	 * 
	 * @return the resolution in dots-per-inch of the primary screen (which may not be actually 
	 * used for display)
	 */
	public static int getScreenResolution() {
		try {
			return Toolkit.getDefaultToolkit().getScreenResolution();
		} catch (Throwable t) {
			return 0;
		}
	}
	
	/**
	 * 
	 * @return the macro version of the current Java Runtime Environment. 
	 * In case of a 1.x version, x will be returned (so 6 for 1.6, 7 for 1.7 etc.).
	 */
	public static int getJavaMacroVersion() {
		String versionStr = System.getProperty("java.version");
		if (versionStr != null) {
			String[] verSplit = versionStr.split("\\.");
			if (verSplit.length >= 2) {// should be
				if (verSplit[0].equals("1")) {
					try {
						return Integer.parseInt(verSplit[1]);
					} catch (NumberFormatException nfe) {
						if (ClientLogger.LOG.isLoggable(Level.INFO)) {
							ClientLogger.LOG.info("Unable to parse the main Java version from: " + verSplit[1]);
						}
					}
				} else if (verSplit[0].length() > 1){
					try {
						return Integer.parseInt(verSplit[0]);
					} catch (NumberFormatException nfe) {
						if (ClientLogger.LOG.isLoggable(Level.INFO)) {
							ClientLogger.LOG.info("Unable to parse the main Java version from: " + verSplit[0]);
						}
					}
				}
			}
		}
		// error condition
		return -1;
	}
	
	/**
	   testing
	 */
    public static void main(String args[]) throws Exception {
		printProperty("java.home");
		printLibExt();
    }
}
