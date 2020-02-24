package mpi.eudico.client.annotator.player;

import mpi.eudico.client.annotator.Preferences;
import mpi.eudico.client.annotator.util.SystemReporting;
import mpi.eudico.server.corpora.clomimpl.abstr.MediaDescriptor;

import java.io.File;


/**
 * The PlayerFactory creates ElanMediaPlayers for a specific URL
 * 
 * @version Dec 2012 removed the players that depend on the commercial JNIWrapper libraries.
 * @version Mar 2019 removed players that depend on Apple's Java (6) implementation
 * and/or on old QuickTime QTJava or are 32-bit only (the old JMF player)
 */
public class PlayerFactory {
    /** constant for Java Media Framework player */
    public final static String JMF_MEDIA_FRAMEWORK = "JMF";
    /** constant for QuickTime for Java based player */
    public final static String QT_MEDIA_FRAMEWORK = "QT";
    /** constant for Cocoa-QT framework */
    public static final String COCOA_QT = "CocoaQT";
    /** constant for an in-house JNI based Direct Show for Java player */
    public final static String JDS = "JDS";
    /** constant for an in-house JNI based Microsoft Media Foundation for Java player */
    public final static String JMMF = "JMMF";
    /** which types to load with Microsoft Media Foundation? First 3 also on Vista */
    public static final String[] MMF_EXTENSIONS = new String[]{"asf", "wma", "wmv", "m4a", "m4v", "mp4"};
    /** constant for a VLC for Java player */
    public final static String VLCJ = "VLCJ";
    /** constant for a JavaFX based player */
    public final static String JFX = "JFX";
    /** constant for a javax.sound.sampled based audio player */
    public static final String JAVA_SOUND = "JavaSound";
    /** constant for a AVF (AV Foundation) based media player with Java rendering */
    public static final String JAVF = "JAVF";
    /** constant for a AVF (AV Foundation) based media player with native rendering*/
    public static final String AVFN = "AVFN";
    // some players are only available within a certain Java environment
    private static int javaMacroVersion = SystemReporting.getJavaMacroVersion();
    
    /**
     * The preferred call for client code to ask for an ElanMediaPlayer. Only
     * if the client code has a special reason to do so it should ask for a
     * specific media framework like JAVF or JFX. When there is more than one
     * media framework available the returned framework will be chosen as
     * follows: If the property "PreferredMediaFramework" is defined and if the preferred
     * framework is available it will be chosen to construct a player. The
     * property is set by using java -DPreferredMediaFramework="JFX" etc.
     * Of course if there is only one media framework available it
     * is chosen to construct the player. If the creation of the media players
     * fails a NoPlayerException is thrown
     *
     * @param mediaDescriptor the media descriptor object containing the URL or file path
     *
     * @return an appropriate player
     *
     * @throws NoPlayerException if no player could be found for the media file
     */
    public static ElanMediaPlayer createElanMediaPlayer(MediaDescriptor mediaDescriptor) throws NoPlayerException { 
    	// check if the media descriptor contains a valid media file reference
        String mediaURL = mediaDescriptor.mediaURL;

        if (mediaURL.startsWith("file")) {
            if (!new File(mediaURL.substring(5)).exists()) { // remove file: part of url string
                throw new NoPlayerException("Media File not found: " +
                    mediaURL);
            }
        } else if (mediaURL.startsWith("rtsp") || mediaURL.startsWith("http")) {
        	// for http/https or rtsp media files or streams nothing special
        	// is done; the URL string is passed to the players which then can
        	// try to play it and report errors 
        }
        
        // if it is an image, create an image viewer here
        // TODO have a constant for image mime type
        if (mediaDescriptor.mimeType != null && mediaDescriptor.mimeType.startsWith("image")) {
        	return new ImagePlayer(mediaDescriptor);
        }
        
        String preferredMF = System.getProperty("PreferredMediaFramework");
        
        // windows specific flags and variables
        boolean jmmfTried = false;
        //boolean jdsTried = false;
        String tempPreferredMF = preferredMF;
        StringBuilder sb = new StringBuilder();
        
        try {
	        // simplified version of the above
	        if (preferredMF != null) {
	            
	            if (preferredMF.equals(JMF_MEDIA_FRAMEWORK)) {
	            	// no longer there, do nothing so that preferred player is created
	            } else if (preferredMF.equals(QT_MEDIA_FRAMEWORK)) {
	                // no longer there, do nothing
	            } else if (preferredMF.equals(COCOA_QT)) {
	            	// no longer there, do nothing
	            } else if (preferredMF.equals(JDS) || preferredMF.equals(JMMF)) {
	            	String playerType = checkLoadJdsOrJmmf(mediaDescriptor);
	            	if (playerType == JMMF) {
	            		preferredMF = JMMF;// set the preferred fm for the message in case of an exception
	            		jmmfTried = true;
	            		return createJMMFPlayer(mediaDescriptor);
	            	}
	            	//jdsTried = true;
	            	return createJDSPlayer(mediaDescriptor);
	            } else if (preferredMF.equals(VLCJ)) {
	            	return createVLCJPlayer(mediaDescriptor);
	            } else if (preferredMF.equals(JFX)) {
	            	return createJavaFXPlayer(mediaDescriptor);
	            } else if (preferredMF.equals(JAVA_SOUND)) {
	            	return createJavaSoundPlayer(mediaDescriptor);
	            } else if (preferredMF.equals(JAVF)) {
	            	return createJAVFPlayer(mediaDescriptor);
	            } else if (preferredMF.equals(AVFN)) {
	            	return createAVFNPlayer(mediaDescriptor);
	            }
	        }
        } catch (NoPlayerException npe) {
        	System.out.println("Preferred media framework \'" + preferredMF + 
				"\' can not handle the file: " + npe.getMessage());
        	sb.append("Preferred media framework \'" + preferredMF + 
    				"\' can not handle the file: " + npe.getMessage() + "\n");
        	if (jmmfTried) {// reset preferred mf
        		preferredMF = tempPreferredMF;
        	}
        }

        if (SystemReporting.isWindows()) {
        	// at this point the preferred framework (if any) has already been tried
        	ElanMediaPlayer player = null;
        	// jds / jmmf section
	        if (!JDS.equals(preferredMF)) {
	        	String playerType = checkLoadJdsOrJmmf(mediaDescriptor);
	        	if (playerType == JMMF && !jmmfTried) {
	        		try {
	        			player = createJMMFPlayer(mediaDescriptor);
	        		} catch (NoPlayerException npe) {
		        		System.out.println("Could not create a JMMF based player: " + npe.getMessage());
		        		sb.append(npe.getMessage() + "\n");
		        		// make sure that jds is tested separately as well
			        	try {
				        	player = createJDSPlayer(mediaDescriptor);
				        } catch (NoPlayerException nnpe) {
				        	System.out.println("Could not create a JDS based player: " + nnpe.getMessage());
				        	sb.append(nnpe.getMessage() + "\n");
				        }
	        		}
	        	} else {
		        	try {
			        	player = createJDSPlayer(mediaDescriptor);
			        } catch (NoPlayerException npe) {
			        	System.out.println("Could not create a JDS based player: " + npe.getMessage());
			        	sb.append(npe.getMessage() + "\n");
			        }
	        	}
	        }
	        
        	if (player == null && !JFX.equals(preferredMF)) {
        		try {
        			player = createJavaFXPlayer(mediaDescriptor);
        		} catch (NoPlayerException npe) {
        			System.out.println("Could not create a JavaFX based player: " + npe.getMessage());
        			sb.append(npe.getMessage() + "\n");
        		}
        	}
        	if (player == null && !VLCJ.equals(preferredMF)) {
        		try {
        			player = createVLCJPlayer(mediaDescriptor);
        		} catch (NoPlayerException npe) {
        			System.out.println("Could not create a VLCJ based player: " + npe.getMessage());
        			sb.append(npe.getMessage() + "\n");
        		}
        	}
	        if (player == null && !JAVA_SOUND.equals(preferredMF) /*&& 
	        		mediaDescriptor.mimeType.indexOf("audio") > -1*/) {
	        	try {
	        		player = createJavaSoundPlayer(mediaDescriptor);
	        	} catch(NoPlayerException npe) {
					System.out.println("JavaSound cannot play the file: " + npe.getMessage());
					sb.append(npe.getMessage() + "\n");
	        	}
	        }
			
			if (player != null) {
				return player;
			} else {
				throw new NoPlayerException(/*"Could not create any media player for: " + 
				    mediaDescriptor.mediaURL + "\n" + */sb.toString());
			}
        	
        } else if (SystemReporting.isMacOS()) {
        	ElanMediaPlayer player = null;
        	
	        if (player == null && !AVFN.equals(preferredMF)) {
	        	try {
	        		player = createAVFNPlayer(mediaDescriptor);
	        	} catch (NoPlayerException npe) {
	        		System.out.println("Could not create a AVFN based player: " + npe.getMessage());
	        		sb.append(npe.getMessage() + "\n");
	        	}
	        }
	        
	        if (player == null && !JAVF.equals(preferredMF)) {
	        	try {
	        		player = createJAVFPlayer(mediaDescriptor);
	        	} catch (NoPlayerException npe) {
	        		System.out.println("Could not create a JAVF based player: " + npe.getMessage());
	        		sb.append(npe.getMessage() + "\n");
	        	}
	        }
        	
        	if (player == null && !JFX.equals(preferredMF)) {
        		try {
        			player = createJavaFXPlayer(mediaDescriptor);
        		} catch (NoPlayerException npe) {
        			System.out.println("Could not create a JavaFX based player... " + npe.getMessage());
        			sb.append(npe.getMessage() + "\n");
        		}
        	}
        	
        	if (player == null && !VLCJ.equals(preferredMF)) {
        		try {
        			player = createVLCJPlayer(mediaDescriptor);
        		} catch (NoPlayerException npe) {
        			System.out.println("Could not create a VLCJ based player: " + npe.getMessage());
        			sb.append(npe.getMessage() + "\n");
        		}
        	}
        	
	        if (player == null && !JAVA_SOUND.equals(preferredMF) /*&& 
	        		mediaDescriptor.mimeType.indexOf("audio") > -1*/) {
	        	try {
	        		player = createJavaSoundPlayer(mediaDescriptor);
	        	} catch(NoPlayerException npe) {
					System.out.println("JavaSound cannot play the file: " + npe.getMessage());
					sb.append(npe.getMessage() + "\n");
	        	}
	        }
	        
        	if (player != null) {
        		return player;
        	} else {
        		throw new NoPlayerException(/*"Could not create any media player for: " + 
    				    mediaDescriptor.mediaURL + "\n" + */sb.toString());
        	}
        } else if (SystemReporting.isLinux()) {
        	ElanMediaPlayer player = null;
        	if (!VLCJ.equals(preferredMF)) {
	        	try {
	        		player = createVLCJPlayer(mediaDescriptor);
	        	} catch (NoPlayerException npe) {
					System.out.println("Could not create a VLCJ player for the file: " + npe.getMessage());
					sb.append(npe.getMessage() + "\n");
	        	}
        	}
        	
        	if (player == null && !JFX.equals(preferredMF)) {
        		try {
        			player = createJavaFXPlayer(mediaDescriptor);
        		} catch (NoPlayerException npe) {
        			System.out.println("Could not create a JavaFX based player... " + npe.getMessage());
        			sb.append(npe.getMessage() + "\n");
        		}
        	}
        	 
	        if (player == null && !JAVA_SOUND.equals(preferredMF) && 
	        		mediaDescriptor.mimeType.indexOf("audio") > -1) {
	        	try {
	        		player = createJavaSoundPlayer(mediaDescriptor);
	        	} catch(NoPlayerException npe) {
					System.out.println("JavaSound cannot play the file: " + npe.getMessage());
					sb.append(npe.getMessage() + "\n");
	        	}
	        }
	        
        	if (player != null) {
        		return player;
        	} else {
        		throw new NoPlayerException(/*"Could not create any media player for: " + 
    				    mediaDescriptor.mediaURL + "\n" + */sb.toString());
        	}
        }
        
        return null;
    }
    
    /**
     * A request for a particular type of player.
     * 
     * @param mediaDescriptor the media descriptor
     * @param framework the requested framework or type of player, if null 
     * {@link #createElanMediaPlayer(MediaDescriptor)} will be called
     * @return the player or null if there is no such framework (or not for this platform)
     * @throws NoPlayerException if creation of the player failed for whatever reason (file
     * not supported, missing libraries etc.)
     */
    public static ElanMediaPlayer createElanMediaPlayer(MediaDescriptor mediaDescriptor, String framework) throws NoPlayerException {
    	if (framework == null) {
    		return createElanMediaPlayer(mediaDescriptor);
    	}
    	
    	if (framework.equals(JDS)) {
    		if (!SystemReporting.isWindows()) {
    			return null;
    		}
    		return createJDSPlayer(mediaDescriptor);
    	} else if (framework.equals(JMMF)) {
    		if (!SystemReporting.isWindows()) {
    			return null;
    		}
    		return createJMMFPlayer(mediaDescriptor);
    	} else if (framework.equals(JFX)) {
    		return createJavaFXPlayer(mediaDescriptor);
    	} else if (framework.equals(AVFN)) {
    		if (!SystemReporting.isMacOS()) {
    			return null;
    		}
    		return createAVFNPlayer(mediaDescriptor);
    	} else if (framework.equals(JAVF)) {
    		if (!SystemReporting.isMacOS()) {
    			return null;
    		}
    		return createJAVFPlayer(mediaDescriptor);
    	} else if (framework.equals(JAVA_SOUND)) {
    		return createJavaSoundPlayer(mediaDescriptor);
    	} else if (framework.equals(VLCJ)) {
    		return createVLCJPlayer(mediaDescriptor);
    	}
    	
    	return null;
    }
    
    /**
     * Creates a DirectShow for Java player.
     * @param mediaDescriptor the media descriptor
     * @return a JDSMediaPlayer
     * @throws NoPlayerException thrown when the player could not be created e.g. 
     * when the file is not supported or when there is a problem initializing the
     * native Direct Show framework
     */
    public static ElanMediaPlayer createJDSPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	System.out.println("Using JDS Player for " + mediaDescriptor.mediaURL);
    	return new JDSMediaPlayer(mediaDescriptor);
    }
    
    /**
     * Creates a Microsoft Media Foundation for Java Player.
     * Only available on Vista and Windows 7 and only for certain media types.
     * Is currently an alternative player within the JDS framework.
     * 
     * @param mediaDescriptor the media descriptor
     * @return a JMMFPlayer 
     * @throws NoPlayerException thrown when a player cannot be created
     */
    public static ElanMediaPlayer createJMMFPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	System.out.println("Using JMMF Player for " + mediaDescriptor.mediaURL);
    	return new JMMFMediaPlayer(mediaDescriptor);
    }
    
    /**
     * Checks whether a JDS or a JMMF player should be created.
     * The decision depends on file type, OS version and user preferences.
     * 
     * @param mediaDescriptor contains the media url
     * @return JDS or JMMF constant
     */
    private static String checkLoadJdsOrJmmf(MediaDescriptor mediaDescriptor) {
    	// check OS version and media type (loosely, based on extension)
    	String lower = mediaDescriptor.mediaURL.toLowerCase();
    	int extIndex = lower.lastIndexOf('.');
    	if (extIndex > -1 && extIndex < lower.length() - 1) {
    		String ext = lower.substring(extIndex + 1);
    		int fileTypeIndex = -1;
    		for (int i = 0; i < MMF_EXTENSIONS.length; i++) {
    			if (MMF_EXTENSIONS[i].equals(ext)) {
    				fileTypeIndex = i;
    				break;
    			}
    		}
    		if (fileTypeIndex > -1) {
    			// if JMMF is disabled return a JDS
    			String jmmfPref = System.getProperty("JMMFEnabled");

    			if (jmmfPref != null && "false".equals(jmmfPref.toLowerCase())) {
    		    	//System.out.println("Using JDS Player for " + mediaDescriptor.mediaURL);
    		    	return JDS;
    			}
    			
    			Boolean jmmfUserPref = Preferences.getBool("Windows.JMMFEnabled", null);
    			if (jmmfUserPref != null && !jmmfUserPref) {
    		    	//System.out.println("Using JDS Player for " + mediaDescriptor.mediaURL);
    		    	return JDS;
    			}
    			
    			// check OS version
    			boolean isVista = SystemReporting.isWindowsVista();
    			boolean isWin7 = SystemReporting.isWindows7OrHigher();
    			
    			if (isVista && fileTypeIndex <= 2) {
    				//System.out.println("Using JMMF Player for " + mediaDescriptor.mediaURL);
    				return JMMF;
    			} else if (isWin7) {
    				//System.out.println("Using JMMF Player for " + mediaDescriptor.mediaURL);
    				return JMMF;
    			}
    		}
    	}
    	
    	return JDS;
    }
    
    /**
     * Creates a VLC for Java Player.
     * Mainly relevant for Linux systems
     * 
     * @version 2019 the player now depends on VLCJ 4.1 and is available for 
     * Windows and macOS too 
     * 
     * @param mediaDescriptor the media descriptor
     * @return a VLCJPlayer 
     * @throws NoPlayerException thrown when a player cannot be created
     */
    public static ElanMediaPlayer createVLCJPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	try {
        	System.out.println("Trying VLCJ Player for " + mediaDescriptor.mediaURL);
        	//return new VLCJMediaPlayer(mediaDescriptor);
        	return new VLCJ4MediaPlayer(mediaDescriptor);
    	} catch (UnsatisfiedLinkError le) {
        	System.out.println("Failing to load VLCJ Player.");
        	System.out.println("Is VLC properly installed?");
        	throw new NoPlayerException("Failing to load VLCJ Player.\nIs VLC properly installed?\n"
        								+ le.toString());
    	} catch (Throwable t) {
        	throw new NoPlayerException(t.toString());
    	}
    }
    
    /**
     * Creates a JavaFX Player (built-in in Java 1.8, limited media support).
     * 
     * @param mediaDescriptor the media descriptor
     * @return a JFXMediaPlayer 
     * @throws NoPlayerException thrown when a player of this type cannot be created
     */
    public static ElanMediaPlayer createJavaFXPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	System.out.println("Using JavaFX Player for " + mediaDescriptor.mediaURL);
    	
        if (javaMacroVersion < 8) {
        	throw new NoPlayerException(
        		"The JavaFX based player requires Java 8 or higher");
        }
        
    	return new JFXMediaPlayer(mediaDescriptor);
    }
    
    /**
     * Creates a JavaSound (javax.sound.sampled) based media player. 
     * This player only supports WAV, AU, AIFF and SND files. This player
     * is independent of any non-JRE libraries, so it should only throw an
     * exception when the file is not supported.
     *  
     * @param mediaDescriptor the media descriptor
     * @return a JavaSoundPlayer
     * @throws NoPlayerException if the file format is not supported
     */
    public static ElanMediaPlayer createJavaSoundPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	try {
    		System.out.println("Using Java Sound Player for " + mediaDescriptor.mediaURL);
    		return new JavaSoundPlayer(mediaDescriptor);
    	} catch (Throwable t) {
    		throw new NoPlayerException(t.getMessage());
    	}
    }

    /**
     * Creates a AV Foundation based media player with native rendering, a native player
     * layer is added to a Java canvas (macOS only).
     * 
     * @param mediaDescriptor the media descriptor
     * @return a player with native decoding and rendering
     * @throws NoPlayerException if the media is not supported
     */
    public static ElanMediaPlayer createAVFNPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	System.out.println("Using AVFN Player for " + mediaDescriptor.mediaURL);
    	return new JAVFELANMediaPlayer(mediaDescriptor);
    }
    
    /**
     * Creates a AV Foundation based media player which decodes videos natively
     * but renders the frames in Java (macOS only).
     * 
     * @param mediaDescriptor the media descriptor
     * @return a player with native decoding and Java rendering
     * @throws NoPlayerException if the media is not supported
     */
    public static ElanMediaPlayer createJAVFPlayer(
    		MediaDescriptor mediaDescriptor) throws NoPlayerException {
    	System.out.println("Using JAVF Player for " + mediaDescriptor.mediaURL);
    	return new JAVFELANMediaPlayer(mediaDescriptor, false);
    }
}
