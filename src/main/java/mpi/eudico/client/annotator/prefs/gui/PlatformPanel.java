package mpi.eudico.client.annotator.prefs.gui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.Preferences;
import mpi.eudico.client.annotator.player.PlayerFactory;
import mpi.eudico.client.annotator.prefs.PreferenceEditor;
import mpi.eudico.client.annotator.util.SystemReporting;


/**
 * A panel for OS specific preference settings.
 *
 * @author Han Sloetjes
 */
@SuppressWarnings("serial")
public class PlatformPanel extends AbstractEditPrefsPanel implements PreferenceEditor, ChangeListener {
    // Look and Feel options
    public enum LFOption {
    	CROSS_PLATFORM_LF,
    	SYSTEM_LF,
    	NIMBUS_LF
    };
	// macOS
	private JCheckBox macScreenBarCB;
    private boolean origMacUseScreenBar = false;// HS May 2013 changed default to false because if nothing is set the menu is not in the screen menubar
    private JCheckBox macLAndFCB;
    private boolean origMacLF = true;
    private JCheckBox macFileDialogCB;
    private boolean origMacFileDialog = true;
    private JRadioButton jfxRB;
    private JRadioButton javaSoundRB;
    private JRadioButton javfRB;
    private JCheckBox javfJavaCB;
    private String origMacPrefFramework = PlayerFactory.AVFN;

    // windows
    private JRadioButton jdsRB;
    private JCheckBox jmmfCB;
    private String origWinPrefFramework = PlayerFactory.JDS;
    private JCheckBox winLAndFCB;
    private boolean origWinLF = false;
    private boolean origJMMFEnabled = true;
    private JCheckBox correctAtPauseCB;
    private boolean origCorrectAtPause = true;
    private JCheckBox jmmfSynchronousModeCB;
    private boolean origJMMFSynchronousMode = false;// the default is asynchronous behavior
    // Linux
	private String origLinuxPrefFramework  = PlayerFactory.VLCJ;
	private String origLinuxLFPref = LFOption.CROSS_PLATFORM_LF.name();
	private JRadioButton vlcjB;
	private JRadioButton crossPlatformLAndFRB;
	private JRadioButton systemLAndFRB;
	private JRadioButton nimbusLAndFRB;

    /**
     * Creates a new PlatformPanel instance
     */
    public PlatformPanel() {
        super();
        readPrefs();
        initComponents();
    }

    private void readPrefs() {
    	if (SystemReporting.isMacOS()) {
    		Boolean boolPref = Preferences.getBool("OS.Mac.useScreenMenuBar", null);

            if (boolPref != null) {
                origMacUseScreenBar = boolPref.booleanValue();
            }

            boolPref = Preferences.getBool("UseMacLF", null);

            if (boolPref != null) {
                origMacLF = boolPref.booleanValue();
            }
            
            boolPref = Preferences.getBool("UseMacFileDialog", null);

            if (boolPref != null) {
            	origMacFileDialog = boolPref.booleanValue();
            }

            String stringPref = Preferences.getString("Mac.PrefMediaFramework", null);

            if (stringPref != null) {
            	if (!stringPref.equals(PlayerFactory.COCOA_QT) && 
            			!stringPref.equals(PlayerFactory.QT_MEDIA_FRAMEWORK)) {
            		origMacPrefFramework = stringPref;
            	}
            }

    	} else if (SystemReporting.isWindows()) {
            String stringPref = Preferences.getString("Windows.PrefMediaFramework", null);

            if (stringPref != null) {
            	// ignore obsolete frameworks
            	if (!stringPref.equals(PlayerFactory.JMF_MEDIA_FRAMEWORK) &&
            			!stringPref.equals(PlayerFactory.QT_MEDIA_FRAMEWORK)) {
            		origWinPrefFramework = stringPref;
            	}
            }
            
            Boolean boolPref = Preferences.getBool("UseWinLF", null);

            if (boolPref != null) {
                origWinLF = boolPref.booleanValue();
            }
            
            boolPref = Preferences.getBool("Windows.JMMFEnabled", null);
            
            if (boolPref != null) {
            	origJMMFEnabled = boolPref.booleanValue();
            }
            
            boolPref = Preferences.getBool("Windows.JMMFPlayer.CorrectAtPause", null);
            
            if (boolPref != null) {
            	origCorrectAtPause = boolPref.booleanValue();
            }
            
            boolPref = Preferences.getBool("Windows.JMMFPlayer.SynchronousMode", null);
            
            if (boolPref != null) {
            	origJMMFSynchronousMode = boolPref.booleanValue();
            }
            
    	} else if (SystemReporting.isLinux()) {
            String stringPref = Preferences.getString("Linux.PrefMediaFramework", null);

            if (stringPref != null) {
                origLinuxPrefFramework = stringPref;
            }
            stringPref = Preferences.getString("Linux.PrefLookAndFeel", null);
            if (stringPref == null) {
            	origLinuxLFPref = LFOption.CROSS_PLATFORM_LF.name();
            } else {
            	origLinuxLFPref = stringPref;
            }
    	}
    }

    private void initComponents() {        
        GridBagConstraints gbc = new GridBagConstraints();
        Font plainFont = null;
        int gy = 0;
        
        if (SystemReporting.isMacOS()) {
        	super.setTitle(ElanLocale.getString("PreferencesDialog.OS.Mac"));    
        	
	        macScreenBarCB = new JCheckBox(ElanLocale.getString(
	                    "PreferencesDialog.OS.Mac.ScreenMenuBar"));
	        macScreenBarCB.setSelected(origMacUseScreenBar);
	
	        plainFont = macScreenBarCB.getFont().deriveFont(Font.PLAIN);
	        macScreenBarCB.setFont(plainFont);
	        
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.weightx = 1.0;
	        gbc.gridy = gy++;
	        gbc.gridwidth = 1;
	        gbc.insets = globalInset;	       
	        outerPanel.add(macScreenBarCB, gbc);
	
	        JLabel relaunchLabel = new JLabel();
	        ImageIcon relaunchIcon = null;
	
	        // add relaunch icon
	        try {
	            relaunchIcon = new ImageIcon(this.getClass()
	                                             .getResource("/toolbarButtonGraphics/general/Refresh16.gif"));
	            relaunchLabel.setIcon(relaunchIcon);
	        } catch (Exception ex) {
	            relaunchLabel.setText(ElanLocale.getString(
	                    "PreferencesDialog.Relaunch"));
	        }
	
	        relaunchLabel.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	        macScreenBarCB.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	
	        gbc.gridx = 1;
	        gbc.gridwidth = 1;
	        gbc.fill = GridBagConstraints.NONE;
	        gbc.anchor = GridBagConstraints.EAST;
	        gbc.weightx = 0.0;
	        outerPanel.add(relaunchLabel, gbc);
	
	        macLAndFCB = new JCheckBox(ElanLocale.getString(
	                    "PreferencesDialog.OS.Mac.LF"));
	        macLAndFCB.setSelected(origMacLF);
	        macLAndFCB.setFont(plainFont);
	        gbc.gridy = gy++;
	        gbc.gridx = 0;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.weightx = 1.0;
	        outerPanel.add(macLAndFCB, gbc);
	
	        JLabel relaunchLabel2 = new JLabel();
	
	        if (relaunchIcon != null) {
	            relaunchLabel2.setIcon(relaunchIcon);
	        } else {
	            relaunchLabel2.setText(ElanLocale.getString(
	                    "PreferencesDialog.Relaunch"));
	        }
	
	        relaunchLabel2.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	        macLAndFCB.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	
	        gbc.gridx = 1;
	        gbc.gridwidth = 1;
	        gbc.fill = GridBagConstraints.NONE;
	        gbc.anchor = GridBagConstraints.EAST;
	        gbc.weightx = 0.0;
	        outerPanel.add(relaunchLabel2, gbc);
	        
	        macFileDialogCB = new JCheckBox(ElanLocale.getString(
                    "PreferencesDialog.OS.Mac.FileDialog"));
	        macFileDialogCB.setSelected(origMacFileDialog);
	        macFileDialogCB.setFont(plainFont);
	        gbc.gridy = gy++;
	        gbc.gridx = 0;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.weightx = 1.0;
	        outerPanel.add(macFileDialogCB, gbc);     
	
	        JLabel frameworkLabel = new JLabel(ElanLocale.getString(
	                    "Player.Framework"));
	        frameworkLabel.setFont(plainFont);
	        jfxRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.JFX"));
	        jfxRB.setFont(plainFont);
	        javaSoundRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.JavaSound"));
	        javaSoundRB.setFont(plainFont);
	        javfRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.JAVF"), true);
	        javfRB.setFont(plainFont);
	        javfJavaCB = new JCheckBox(ElanLocale.getString("PreferencesDialog.Media.JAVF") + " (Java Rendering)");
	        javfJavaCB.setFont(plainFont);
	        vlcjB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.VLCJ"));
	        vlcjB.setFont(plainFont);
	
	        ButtonGroup gr = new ButtonGroup();
	        gr.add(javfRB);
	        gr.add(jfxRB);
	        gr.add(vlcjB);
	        gr.add(javaSoundRB);
	        
	        javfRB.addChangeListener(this);
	        
	        if (origMacPrefFramework.equals(PlayerFactory.COCOA_QT)) {
	            // leave the AVFN button selected
	        } else if (origMacPrefFramework.equals(PlayerFactory.QT_MEDIA_FRAMEWORK)){
	            // leave the AVFN button selected
	        } else if (origMacPrefFramework.equals(PlayerFactory.JAVF)) {
	        	javfRB.setSelected(true);
	        	javfJavaCB.setSelected(true);
	        } else if (origMacPrefFramework.equals(PlayerFactory.JFX)) {
	        	jfxRB.setSelected(true);
	        } else if (origMacPrefFramework.equals(PlayerFactory.JAVA_SOUND)) {
	        	javaSoundRB.setSelected(true);
	        } else if (origMacPrefFramework.equals(PlayerFactory.VLCJ)) {
	        	vlcjB.setSelected(true);
	        }
	
	        gbc.gridx = 0;
	        gbc.gridy = gy++;
	        gbc.gridwidth = 2;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.weightx = 1.0;
	        gbc.insets = catInset;
	        outerPanel.add(frameworkLabel, gbc);
	
	        gbc.gridy = gy++;
	        gbc.insets = globalInset;
		    outerPanel.add(javfRB, gbc);
	        gbc.gridy = gy++;
	        gbc.insets = singleTabInset;
	        outerPanel.add(javfJavaCB, gbc);
	        
	        gbc.gridy = gy++;
	        gbc.insets = globalInset;
	        outerPanel.add(jfxRB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(vlcjB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(javaSoundRB, gbc);
	        
	        gbc.gridy = gy++;
	        gbc.gridx = 0;
	        gbc.fill = GridBagConstraints.BOTH;
	        gbc.weighty = 1.0;
	        outerPanel.add(new JPanel(), gbc); // filler
        } else if (SystemReporting.isWindows()) {
        	 // add Windows stuff
        	
        	super.setTitle(ElanLocale.getString("PreferencesDialog.OS.Windows"));   
	        
	        // look and feel
	        // add relaunch icon
	        JLabel relaunchLabel = new JLabel();
	        ImageIcon relaunchIcon = null;
	        try {
	            relaunchIcon = new ImageIcon(this.getClass()
	                                             .getResource("/toolbarButtonGraphics/general/Refresh16.gif"));
	            relaunchLabel.setIcon(relaunchIcon);
	        } catch (Exception ex) {
	            relaunchLabel.setText(ElanLocale.getString(
	                    "PreferencesDialog.Relaunch"));
	        }
	
	        relaunchLabel.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	        
	        winLAndFCB = new JCheckBox(ElanLocale.getString("PreferencesDialog.OS.Windows.LF"), origWinLF);
	        winLAndFCB.setFont(plainFont);
	        
	        gbc = new GridBagConstraints();
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.weightx = 1.0;
	        gbc.gridy = gy++;    
	        gbc.gridwidth = 1;
	        gbc.insets = globalInset;	       	       
	        outerPanel.add(winLAndFCB, gbc);
	        
	        gbc.gridx = 1;
	        gbc.fill = GridBagConstraints.NONE;
	        gbc.anchor = GridBagConstraints.EAST;
	        gbc.weightx = 0.0;
	        outerPanel.add(relaunchLabel, gbc);
	        
	       //media framework	
	        ButtonGroup winBG = new ButtonGroup();
	        jdsRB = new JRadioButton(ElanLocale.getString(
	        		"PreferencesDialog.Media.JDS"), true);
	        jmmfCB = new JCheckBox(ElanLocale.getString(
    				"PreferencesDialog.Media.JMMF"), origJMMFEnabled);
	        correctAtPauseCB = new JCheckBox(ElanLocale.getString("PreferencesDialog.Media.JMMF.CorrectAtPause"), 
	        		origCorrectAtPause);
	        jmmfSynchronousModeCB = new JCheckBox(ElanLocale.getString("PreferencesDialog.Media.JMMF.SynchronousMode"),
	        		origJMMFSynchronousMode);
        	jfxRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.JFX"));
	        javaSoundRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.JavaSound"));
	        vlcjB = new JRadioButton(ElanLocale.getString("PreferencesDialog.Media.VLCJ"));
	        
	        winBG.add(jdsRB);
	        winBG.add(javaSoundRB);
	        winBG.add(jfxRB);
	        winBG.add(vlcjB);
	        
	        plainFont = jdsRB.getFont().deriveFont(Font.PLAIN);
	       
	        JLabel winMedia = new JLabel(ElanLocale.getString("Player.Framework"));	        	       
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.weightx = 1.0;
	        gbc.gridwidth = 2;
	        gbc.gridx = 0;
	        gbc.gridy = gy++;	        
	        gbc.insets = catInset;
	        outerPanel.add(winMedia, gbc);
	
	        if (origWinPrefFramework.equals(PlayerFactory.QT_MEDIA_FRAMEWORK)) {
	            // leave the jds radio button selected
	        } else if (origWinPrefFramework.equals(PlayerFactory.JMF_MEDIA_FRAMEWORK)) {
	            // leave the jds radio button selected
	        } else if (origWinPrefFramework.equals(PlayerFactory.JFX)) {
	            jfxRB.setSelected(true);
	        } else if (origWinPrefFramework.equals(PlayerFactory.JAVA_SOUND)) {
	            javaSoundRB.setSelected(true);
	        } else if (origWinPrefFramework.equals(PlayerFactory.VLCJ)) {
	            vlcjB.setSelected(true);
	        }
	
	        jdsRB.setFont(plainFont);
	        jmmfCB.setFont(plainFont);
	        jfxRB.setFont(plainFont);
	        javaSoundRB.setFont(plainFont);
	        vlcjB.setFont(plainFont);
	        correctAtPauseCB.setFont(plainFont);
	
	        gbc.insets = globalInset;
	        gbc.gridy = gy++;
	        outerPanel.add(jdsRB, gbc);
	       
	        gbc.gridy = gy++;
	        gbc.insets = singleTabInset;
	        outerPanel.add(jmmfCB, gbc);
	        
	        gbc.gridy = gy++;
	        gbc.insets = doubleTabInset;
	        outerPanel.add(correctAtPauseCB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(jmmfSynchronousModeCB, gbc);
	       
	        gbc.insets = globalInset;
	        gbc.gridy = gy++;
	        outerPanel.add(jfxRB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(vlcjB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(javaSoundRB, gbc);
	        
	        gbc.gridy = gy++;
	        gbc.gridx = 0;
	        gbc.fill = GridBagConstraints.BOTH;
	        gbc.weighty = 1.0;
	        outerPanel.add(new JPanel(), gbc); // filler
	        
	        if (SystemReporting.isWindows7OrHigher() || SystemReporting.isWindowsVista()) {
	        	jdsRB.addChangeListener(this);
	        	jmmfCB.setEnabled(jdsRB.isSelected());
	        	correctAtPauseCB.setEnabled(jdsRB.isSelected());
	        } else {
	        	jmmfCB.setEnabled(false);//??
	        	jmmfCB.setVisible(false);
	        	correctAtPauseCB.setVisible(false);
	        }
        } else if (SystemReporting.isLinux()) {
        	super.setTitle(ElanLocale.getString("PreferencesDialog.OS.Linux"));
	        // look and feel
	        // add relaunch icon
	        JLabel relaunchLabel = new JLabel();
	        ImageIcon relaunchIcon = null;
	        try {
	            relaunchIcon = new ImageIcon(this.getClass()
	                                             .getResource("/toolbarButtonGraphics/general/Refresh16.gif"));
	            relaunchLabel.setIcon(relaunchIcon);
	        } catch (Exception ex) {
	            relaunchLabel.setText(ElanLocale.getString(
	                    "PreferencesDialog.Relaunch"));
	        }
	
	        relaunchLabel.setToolTipText(ElanLocale.getString(
	                "PreferencesDialog.Relaunch.Tooltip"));
	        
	        JLabel lAndFLabel = new JLabel(ElanLocale.getString("PreferencesDialog.OS.Linux.LFLabel"));
        	crossPlatformLAndFRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.OS.Linux.LF.CrossPlatform"), true);
        	systemLAndFRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.OS.Linux.LF.System"));
        	nimbusLAndFRB = new JRadioButton(ElanLocale.getString("PreferencesDialog.OS.Linux.LF.Nimbus"));
        	
        	ButtonGroup lAndFGroup = new ButtonGroup();
        	lAndFGroup.add(crossPlatformLAndFRB);
        	lAndFGroup.add(systemLAndFRB);
        	lAndFGroup.add(nimbusLAndFRB);
        	
        	if (LFOption.SYSTEM_LF.name().equals(origLinuxLFPref)) {
        		systemLAndFRB.setSelected(true);
        	} else if (LFOption.NIMBUS_LF.name().equals(origLinuxLFPref)) {
        		nimbusLAndFRB.setSelected(true);
        	}
        	// font 
        	plainFont = crossPlatformLAndFRB.getFont().deriveFont(Font.PLAIN);
        	crossPlatformLAndFRB.setFont(plainFont);
        	systemLAndFRB.setFont(plainFont);
        	nimbusLAndFRB.setFont(plainFont);
	        
	        JLabel frameworkLabel = new JLabel(ElanLocale.getString(
	                    "Player.Framework"));
	        //frameworkLabel.setFont(plainFont);//??
	        vlcjB = new JRadioButton(ElanLocale.getString(
	                    "PreferencesDialog.Media.VLCJ"), true);
	        vlcjB.setFont(plainFont);
	        
	        jfxRB = new JRadioButton(ElanLocale.getString(
	        		"PreferencesDialog.Media.JFX"));
	        jfxRB.setFont(plainFont);
	        javaSoundRB = new JRadioButton(ElanLocale.getString(
	        		"PreferencesDialog.Media.JavaSound"));
	        javaSoundRB.setFont(plainFont);
	        ButtonGroup gr = new ButtonGroup();
	        gr.add(vlcjB);
	        gr.add(jfxRB);
	        gr.add(javaSoundRB);
	        
	        if (origLinuxPrefFramework.equals(PlayerFactory.JMF_MEDIA_FRAMEWORK)) {
	            // leave the VLCJ radio button selected 
	        } else if (origLinuxPrefFramework.equals(PlayerFactory.JFX)) {
	        		jfxRB.setSelected(true);
	        } else if (origLinuxPrefFramework.equals(PlayerFactory.JAVA_SOUND)) {
	        	javaSoundRB.setSelected(true);
	        } else {
	            vlcjB.setSelected(true);
	        }
	        
	        // add L&F items first
	        gbc.gridx = 0;
	        gbc.gridy = gy++;
	        gbc.weightx = 1.0;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.insets = catInset;
	        outerPanel.add(lAndFLabel, gbc);
	        gbc.gridx = 1;
	        gbc.fill = GridBagConstraints.NONE;
	        gbc.weightx = 0.0;
	        outerPanel.add(relaunchLabel, gbc);
	        
	        gbc.gridx = 0;
	        gbc.gridy = gy++;
	        gbc.gridwidth = 2;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.weightx = 1.0;
	        gbc.insets = globalInset;
	        outerPanel.add(crossPlatformLAndFRB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(systemLAndFRB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(nimbusLAndFRB, gbc);
	        
	        gbc.gridx = 0;
	        gbc.gridy = gy++;
	        gbc.gridwidth = 2;
	        gbc.fill = GridBagConstraints.HORIZONTAL;
	        gbc.anchor = GridBagConstraints.NORTHWEST;
	        gbc.weightx = 1.0;
	        gbc.insets = catInset;
	        outerPanel.add(frameworkLabel, gbc);
	
	        gbc.gridy = gy++;
	        gbc.insets = globalInset;
	        outerPanel.add(vlcjB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(jfxRB, gbc);
	        
	        gbc.gridy = gy++;
	        outerPanel.add(javaSoundRB, gbc);
	
	        gbc.gridy = gy++;
	        gbc.gridx = 0;
	        gbc.fill = GridBagConstraints.BOTH;
	        gbc.weighty = 1.0;
	        outerPanel.add(new JPanel(), gbc); // filler

        }
    }

    /**
     * @see mpi.eudico.client.annotator.prefs.PreferenceEditor#getChangedPreferences()
     */
    @Override
	public Map<String, Object> getChangedPreferences() {
        if (isChanged()) {
            Map<String, Object> chMap = new HashMap<String, Object>(4);

        	if (SystemReporting.isMacOS()) {
        		if (macScreenBarCB.isSelected() != origMacUseScreenBar) {
                    chMap.put("OS.Mac.useScreenMenuBar",
                    	Boolean.valueOf(macScreenBarCB.isSelected()));
                }

                if (macLAndFCB.isSelected() != origMacLF) {
                    chMap.put("UseMacLF", Boolean.valueOf(macLAndFCB.isSelected()));
                }
                
                if (macFileDialogCB.isSelected() != origMacFileDialog) {
                    chMap.put("UseMacFileDialog", Boolean.valueOf(macFileDialogCB.isSelected()));
                }

                String tmp = PlayerFactory.AVFN;
                if (javfRB.isSelected()) {
                	if (javfJavaCB.isSelected()) {
                		tmp = PlayerFactory.JAVF;
                	}
                } else if (jfxRB.isSelected()) {
            		tmp = PlayerFactory.JFX;
            	} else if (javaSoundRB.isSelected()) {
                	tmp = PlayerFactory.JAVA_SOUND;
                } else if (vlcjB.isSelected()) {
                	tmp = PlayerFactory.VLCJ;
                }

                if (!origMacPrefFramework.equals(tmp)) {
                    chMap.put("Mac.PrefMediaFramework", tmp);
                    //apply immediately
                    System.setProperty("PreferredMediaFramework", tmp);
                }
        	} else if (SystemReporting.isWindows()) {               
                String winTmp = PlayerFactory.JDS;

                if (jfxRB.isSelected()) {
                	winTmp = PlayerFactory.JFX;
                } else if (javaSoundRB.isSelected()) {
                	winTmp = PlayerFactory.JAVA_SOUND;
                } else if (vlcjB.isSelected()) {
                	winTmp = PlayerFactory.VLCJ;
                }

                if (!origWinPrefFramework.equals(winTmp)) {
                    chMap.put("Windows.PrefMediaFramework", winTmp);
                    //apply immediately
                    System.setProperty("PreferredMediaFramework", winTmp);
                }
                
                if (origWinLF != winLAndFCB.isSelected()) {
                	chMap.put("UseWinLF", winLAndFCB.isSelected());
                }
                
                if (origJMMFEnabled != jmmfCB.isSelected()) {
                	chMap.put("Windows.JMMFEnabled", jmmfCB.isSelected());
                }
                
                if (origCorrectAtPause != correctAtPauseCB.isSelected()) {
                	chMap.put("Windows.JMMFPlayer.CorrectAtPause", correctAtPauseCB.isSelected());
                }
                
                if (origJMMFSynchronousMode != jmmfSynchronousModeCB.isSelected()) {
                	chMap.put("Windows.JMMFPlayer.SynchronousMode", jmmfSynchronousModeCB.isSelected());
                }
        	} else if (SystemReporting.isLinux()) {
        		String tmpLF = LFOption.CROSS_PLATFORM_LF.name();
        		if (systemLAndFRB.isSelected()) {
        			tmpLF = LFOption.SYSTEM_LF.name();
        		} else if (nimbusLAndFRB.isSelected()) {
        			tmpLF = LFOption.NIMBUS_LF.name();
        		}
        		if (!tmpLF.equals(origLinuxLFPref)) {
        			chMap.put("Linux.PrefLookAndFeel", tmpLF);
        		}
        		
                String tmp = PlayerFactory.VLCJ;

                if (jfxRB.isSelected()) {
                	tmp = PlayerFactory.JFX;
                } else if (javaSoundRB.isSelected()) {
                	tmp = PlayerFactory.JAVA_SOUND;
                }

                if (!origLinuxPrefFramework.equals(tmp)) {
                    chMap.put("Linux.PrefMediaFramework", tmp);
                    //apply immediately
                    System.setProperty("PreferredMediaFramework", tmp);
                }
        	}
        	
            return chMap;
        }

        return null;
    }

    /**
     * @see mpi.eudico.client.annotator.prefs.PreferenceEditor#isChanged()
     */
    @Override
	public boolean isChanged() {
    	if (SystemReporting.isMacOS()) {
    		if ((macScreenBarCB.isSelected() != origMacUseScreenBar) ||
                    (macLAndFCB.isSelected() != origMacLF) ||
                    (macFileDialogCB.isSelected() != origMacFileDialog)) {
                return true;
            }

            String tmp = PlayerFactory.AVFN;
            if (javfRB.isSelected()) {
            	if (javfJavaCB.isSelected()) {
            		tmp = PlayerFactory.JAVF;
            	}
            } else if (jfxRB.isSelected()) {
	           	tmp = PlayerFactory.JFX;
	        } else if (javaSoundRB.isSelected()) {
	           	tmp = PlayerFactory.JAVA_SOUND;
	        } else if (vlcjB.isSelected()) {
	           	tmp = PlayerFactory.VLCJ;
	        }
            
            if (!origMacPrefFramework.equals(tmp)) {
                return true;
            }
    	} else if (SystemReporting.isWindows()) {
    		String winTmp = PlayerFactory.JDS;
            
            if (jfxRB.isSelected()) {
            	winTmp = PlayerFactory.JFX;
            } else if (javaSoundRB.isSelected()) {
            	winTmp = PlayerFactory.JAVA_SOUND;
            } else if (vlcjB.isSelected()) {
            	winTmp = PlayerFactory.VLCJ;
            }

            if (!origWinPrefFramework.equals(winTmp)) {
                return true;
            }
            if (origWinLF != winLAndFCB.isSelected()) {
            	return true;
            }
            if (origJMMFEnabled != jmmfCB.isSelected()) {
            	return true;
            }
            if (origCorrectAtPause != correctAtPauseCB.isSelected()) {
            	return true;
            }
            if (origJMMFSynchronousMode != jmmfSynchronousModeCB.isSelected()) {
            	return true;
            }
    	}  else if (SystemReporting.isLinux()) {
    		String tmpLF = LFOption.CROSS_PLATFORM_LF.name();
    		if (systemLAndFRB.isSelected()) {
    			tmpLF = LFOption.SYSTEM_LF.name();
    		} else if (nimbusLAndFRB.isSelected()) {
    			tmpLF = LFOption.NIMBUS_LF.name();
    		}
    		if (!tmpLF.equals(origLinuxLFPref)) {
    			return true;
    		}
    		
            String tmp = PlayerFactory.VLCJ;

            if (jfxRB.isSelected()) {
            	tmp = PlayerFactory.JFX;
            } else if (javaSoundRB.isSelected()) {
            	tmp = PlayerFactory.JAVA_SOUND;
            }

            if (!origLinuxPrefFramework.equals(tmp)) {
                return true;
            }    		
    	}
        
        return false;
    }

	@Override
	public void stateChanged(ChangeEvent ce) {
		if (SystemReporting.isWindows()) {
			jmmfCB.setEnabled(jdsRB.isSelected());
			correctAtPauseCB.setEnabled(jdsRB.isSelected());
			jmmfSynchronousModeCB.setEnabled(jdsRB.isSelected());
		} else if (SystemReporting.isMacOS()) {
			javfJavaCB.setEnabled(javfRB.isSelected());
		}
	}
}
