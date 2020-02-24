package mpi.eudico.client.annotator.gui;

import mpi.eudico.client.annotator.ElanLocale;
import mpi.eudico.client.annotator.Preferences;
import mpi.eudico.client.annotator.util.ClientLogger;
import mpi.eudico.client.annotator.util.WindowLocationAndSizeManager;
import mpi.eudico.client.util.CheckBoxTableCellRenderer;
import mpi.eudico.client.util.ComboBoxTableCellRenderer;
import mpi.eudico.server.corpora.clomimpl.delimitedtext.DelimitedTextDecoderInfo;
import mpi.eudico.server.corpora.clomimpl.delimitedtext.DelimitedTextReader;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;


/**
 * A dialog for import of annotations from a .csv or tab delimited text file.
 * Provides customization options for the import process.
 *
 * @author Han Sloetjes
 * @version 1.0
 */
@SuppressWarnings("serial")
public class ImportCSVDialog extends ClosableDialog implements ClientLogger,
    ActionListener, ChangeListener, ItemListener {
    private File csvFile;
    private Object value;
    private String[] colTypes;
    private DefaultTableModel selectModel;
    private String detectedDel;
    private int detectedNumCols;
    private int assumedFirstRow = 0;

    // ui
    private JPanel examplePanel;
    private JPanel optionsPanel;
    private JPanel buttonPanel;
    private JButton okButton;
    private JButton cancelButton;
    private JCheckBox delimiterCB;
    private JCheckBox firstRowCB;
    private JCheckBox defaultDurCB;
    private JCheckBox skipEmptyCB;
    private JComboBox delimiterCombo;
    private JSpinner firstRowSpinner;
    private JLabel selectionLabel;
    private JTable sampleTable;
    private SelectionTable selectionTable;
    private JTextField durationTF;
    // preference key for duration
    private final String INTERVAL_PREF = "ShoeboxChatBlockDuration";
    private String curDelimiter;
	private DelimitedTextReader csvReader;

    /**
     * Creates a new ImportCSVDialog instance
     *
     * @param owner the owner frame
     * @param csvFile the file to import
     *
     * @throws HeadlessException headless exception
     */
    public ImportCSVDialog(Frame owner, File csvFile) throws HeadlessException {
        super(owner, true);
        this.csvFile = csvFile;
        initComponents();
        initTables(); // could be done on a separate thread
    }

    private void initTables() {
        try {
            csvReader = new DelimitedTextReader(csvFile);
            detectedDel = csvReader.detectDelimiter();
            detectedNumCols = csvReader.detectNumColumns();

            List<String[]> rows = csvReader.getSamples(8);
            createAndApplyModels(rows, detectedNumCols);
            
            delimiterCombo.setSelectedItem(detectedDel);
            curDelimiter = detectedDel;
            // if the first rows contain a recognizable header row, 
            // update the selection table's check boxes and types
            updateSelectionTable(detectHeaders(rows));
        } catch (FileNotFoundException fnfe) {
            LOG.warning("File not found: " + csvFile.getName());

            // warning
        } catch (IOException ioe) {
            LOG.warning("Read error: " + ioe.getMessage());

            // warning
        }
    }
    
    /**
     * Reinitializes the table using the specified delimiter. Should only be called after an initial
     * initialization.
     * 
     * @param delimiter the new delimiter
     */
    private void reInitTables(String delimiter) {
    	if (csvReader != null) {
    		try {
	    		csvReader.setDelimiter(delimiter);
	    		int numColumns = csvReader.detectNumColumns();
	    		
	            List<String[]> rows = csvReader.getSamples(8);
	            // if the number of columns is the same only update the table contents
	            if (numColumns == detectedNumCols) {
	            	 
	            	for (int i = 0; i < rows.size(); i++) {
	            		String[] row = rows.get(i);
		                
		                sampleTable.setValueAt(Integer.valueOf(i + 1), i, 0);
		                for (int j = 0; j < row.length && j < numColumns; j++) {
		                	sampleTable.setValueAt(row[j], i, j + 1);
		                }

		                if (row.length < numColumns) {
		                    for (int j = 0; j < (numColumns - row.length); j++) {
		                    	sampleTable.setValueAt("", i, row.length + j + 1);
		                    }
		                }
	            	}
	            	
	            	updateSelectionTable(detectHeaders(rows));
	            } else {
	            	// reinitialize the two tables
	            	createAndApplyModels(rows, numColumns);
	            	detectedNumCols = numColumns;
	            	updateSelectionTable(detectHeaders(rows));
	            }
    		} catch (IOException ioe) {
    			LOG.warning("Re-read error: " + ioe.getMessage());
    		}
    	}
    }
    
    private void createAndApplyModels(List<String[]> rows, int numberOfColumns) {
    	if (rows == null) {
    		ClientLogger.LOG.severe("The list of rows for the table model is null, cannot update the table");
    		return;
    	}
    	DefaultTableModel csvModel = new DefaultTableModel(rows.size(),
    			numberOfColumns + 1);

        for (int i = 0; i < rows.size(); i++) {
        	String[] row = rows.get(i);
            csvModel.setValueAt(Integer.valueOf(i + 1), i, 0);
            // TO DO check j < detectedNumCols or <= 
            for (int j = 0; j < row.length && j < numberOfColumns; j++) {
                csvModel.setValueAt(row[j], i, j + 1);
            }

            if (row.length < numberOfColumns) {
                for (int j = 0; j < (numberOfColumns - row.length); j++) {
                    csvModel.setValueAt("", i, row.length + j + 1);
                }
            }
        }

        String[] headers = new String[numberOfColumns + 1];
        headers[0] = ElanLocale.getString("Frame.GridFrame.ColumnCount");

        for (int i = 0; i < numberOfColumns; i++) {
            headers[i + 1] = String.valueOf(i + 1);
        }

        csvModel.setColumnIdentifiers(headers);
        sampleTable.setModel(csvModel);
        sampleTable.getTableHeader().setReorderingAllowed(false);
        sampleTable.getColumnModel().getColumn(0).sizeWidthToFit();

        selectModel = new SelectionTableModel(2, numberOfColumns + 1);
        headers = new String[numberOfColumns + 1];
        headers[0] = "-";

        for (int i = 1; i < numberOfColumns + 1; i++) {
            headers[i] = String.valueOf(i);
        }

        selectModel.setColumnIdentifiers(headers);

        for (int i = 1; i < numberOfColumns + 1; i++) {
            selectModel.setValueAt(Boolean.TRUE, 0, i);
            selectModel.setValueAt(colTypes[0], 1, i);
        }

        selectionTable.setModel(selectModel);
        selectionTable.getTableHeader().setReorderingAllowed(false);
        selectionTable.setRowSelectionAllowed(false);
    }
    
    /**
     * Tries to detect contents of columns based on "known" or recognized column headers.
     * When ELAN exports tab-delimited, there is an option to have column headers like
     * these "Begin Time - msec". If these are encountered, the selection table can
     * already be (partially) filled in. 
     * Note: the column headers are localized so this approach will/might fail if
     * the user interface language is different from the exported one.
     * 
     * @param rows the first 10 rows as read from the file
     * @return a map containing column header indices as keys and a column type strings
     * as values. Can contain null values and the map will be null if no known column
     * headers were found. 
     */
    private Map<Integer, String> detectHeaders(List<String[]> rows) {
    	Map<Integer, String> headers = new HashMap<Integer, String>();
    	boolean headersFound = false;
    	
    	for (String[] row : rows) {
			if (row[0].startsWith("#") || row[0].startsWith("\"#")) {
				continue;// consider as comment
			}
			
    		for (int i = 0; i < row.length; i++) {
    			for (int j = 1; j < colTypes.length; j++) {
    				if (row[i].indexOf(colTypes[j]) > -1) {
    					headersFound = true;
    					break;
    				}
    			}
    			if (headersFound) {
    				assumedFirstRow = rows.indexOf(row) + 1;
    				break;
    			}
    		}
    		
    		// loop again, store mappings and break the "rows" loop by returning
    		if (headersFound) {
        		for (int i = 0; i < row.length; i++) {
        			int curColType = 1; // Annotation
        			
        			for (int j = 1; j < colTypes.length; j++) {	
        				if (row[i].indexOf(colTypes[j]) > -1) {
        					curColType = j;
        					break;
        				}
        			}
        			
        			if (curColType == 1) {
        				headers.put(i, colTypes[curColType]);// Annotation
        			} else {
        				// a time or tier name column, include only once
        				if (!headers.containsValue(colTypes[curColType])) {
        					headers.put(i, colTypes[curColType]);
        				} else {
        					headers.put(i, null);
        				}
					}
        		}

    			return headers;
    		}
    	}
    	
    	return null;
    }
    
    /**
     * Selects check boxes and combo box items based on the retrieved header
     * information.
     * 
     * @param detectedHeaders a map of column index to column data type string
     */
    private void updateSelectionTable(Map<Integer, String> detectedHeaders) {
    	if (detectedHeaders == null || detectedHeaders.isEmpty()) {
    		return;
    	}
    	// column 0 in the headers map corresponds to index 1 in the selection table
    	Iterator<Map.Entry<Integer, String>> entryIt = detectedHeaders.entrySet().iterator();
    	while (entryIt.hasNext()) {
    		Map.Entry<Integer, String> entry = entryIt.next(); 
    		int col = entry.getKey().intValue();
    		String cType = entry.getValue();
    		if (cType != null) {
    			selectModel.setValueAt(Boolean.TRUE, 0, col + 1);
    			selectModel.setValueAt(cType, 1, col + 1);
    		} else {
    			selectModel.setValueAt(Boolean.FALSE, 0, col + 1);
    			//selectModel.setValueAt(colTypes[0], 1, col + 1);//
    		}
    	}
    	
    	if (assumedFirstRow > 0) {
    		firstRowCB.setSelected(true);
    		firstRowSpinner.setValue(assumedFirstRow + 1);
    	} else {
    		firstRowCB.setSelected(false);
    		firstRowSpinner.setValue(1);
		}
    }

    private void initComponents() {
        // use localized values for column data types
        colTypes = new String[6];
        colTypes[0] = ElanLocale.getString("Button.Select");
        colTypes[1] = ElanLocale.getString("Frame.GridFrame.ColumnAnnotation");
        colTypes[2] = ElanLocale.getString("Frame.GridFrame.ColumnTierName");
        colTypes[3] = ElanLocale.getString("Frame.GridFrame.ColumnBeginTime");
        colTypes[4] = ElanLocale.getString("Frame.GridFrame.ColumnEndTime");
        colTypes[5] = ElanLocale.getString("Frame.GridFrame.ColumnDuration");
        //colTypes[6] = ElanLocale.getString("Frame.GridFrame.ColumnFileName");
        // ??
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
                @Override
				public void windowClosing(WindowEvent evt) {
                    closeDialog(evt);
                }
            });

        getContentPane().setLayout(new GridBagLayout());

        Insets insets = new Insets(2, 6, 2, 6);
        examplePanel = new JPanel(new GridBagLayout());
        sampleTable = new JTable();

        JScrollPane tableScrollPane = new JScrollPane(sampleTable);
        Dimension size = new Dimension(500, 120);
        tableScrollPane.setMinimumSize(size);
        tableScrollPane.setPreferredSize(size);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = insets;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        examplePanel.add(tableScrollPane, gbc);

        getContentPane().add(examplePanel, gbc);

        optionsPanel = new JPanel(new GridBagLayout());
        selectionLabel = new JLabel();
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = insets;
        gbc.gridwidth = 2;
        optionsPanel.add(selectionLabel, gbc);

        selectionTable = new SelectionTable();

        JScrollPane tableScrollPane2 = new JScrollPane(selectionTable);
        size = new Dimension(500, 80);
        tableScrollPane2.setMinimumSize(size);
        tableScrollPane2.setPreferredSize(size);

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        optionsPanel.add(tableScrollPane2, gbc);

        firstRowCB = new JCheckBox();
        firstRowCB.addChangeListener(this);
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;
        optionsPanel.add(firstRowCB, gbc);

        firstRowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        firstRowSpinner.setEnabled(false);
        gbc.gridx = 1;
        optionsPanel.add(firstRowSpinner, gbc);

        delimiterCB = new JCheckBox();
        delimiterCB.addChangeListener(this);
        gbc.gridx = 0;
        gbc.gridy = 3;
        optionsPanel.add(delimiterCB, gbc);

        delimiterCombo = new JComboBox(new String[] {
                    ElanLocale.getString("ImportDialog.CSV.Label.Delimiter.Tab"),
                    ";", ":", ",", "|"
                });
        delimiterCombo.setEnabled(false);
        gbc.gridx = 1;
        optionsPanel.add(delimiterCombo, gbc);

        defaultDurCB = new JCheckBox();
        defaultDurCB.addChangeListener(this);
        gbc.gridx = 0;
        gbc.gridy = 4;
        optionsPanel.add(defaultDurCB, gbc);

        durationTF = new JTextField(6);
		if (Preferences.getInt(INTERVAL_PREF, null) != null) {
			Integer val = Preferences.getInt(INTERVAL_PREF, null);
			durationTF.setText("" + val.intValue());	
		} else {
			durationTF.setText("1000");
		}       
        durationTF.setEnabled(false);
        gbc.gridx = 1;
        optionsPanel.add(durationTF, gbc);
        
        skipEmptyCB = new JCheckBox();
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        optionsPanel.add(skipEmptyCB, gbc);

        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridy = 1;
        gbc.insets = insets;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        getContentPane().add(optionsPanel, gbc);

        buttonPanel = new JPanel(new GridLayout(1, 2, 6, 0));

        okButton = new JButton();
        okButton.addActionListener(this);
        buttonPanel.add(okButton);

        cancelButton = new JButton();
        cancelButton.addActionListener(this);
        buttonPanel.add(cancelButton);
        gbc = new GridBagConstraints();
        gbc.gridy = 2;
        gbc.insets = insets;
        getContentPane().add(buttonPanel, gbc);

        updateLocale();
    }

    private void updateLocale() {
        setTitle(ElanLocale.getString("Button.Import") + ": " +
            csvFile.getName());
        examplePanel.setBorder(new TitledBorder(ElanLocale.getString(
                    "ImportDialog.CSV.Label.Sample")));
        optionsPanel.setBorder(new TitledBorder(ElanLocale.getString(
                    "ImportDialog.Label.Options")));
        selectionLabel.setText(ElanLocale.getString(
                "ImportDialog.CSV.Label.Select"));
        firstRowCB.setText(ElanLocale.getString(
                "ImportDialog.CSV.Label.FirstRow"));
        delimiterCB.setText(ElanLocale.getString(
                "ImportDialog.CSV.Label.Delimiter"));
        defaultDurCB.setText(ElanLocale.getString(
                "ImportDialog.CSV.Label.Duration"));
        skipEmptyCB.setText(ElanLocale.getString("ImportDialog.CSV.Label.SkipEmpty"));

        okButton.setText(ElanLocale.getString("Button.OK"));
        cancelButton.setText(ElanLocale.getString("Button.Cancel"));
    }

    /**
     * Closes the dialog
     *
     * @param evt the window closing event
     */
    protected void closeDialog(WindowEvent evt) {
    	WindowLocationAndSizeManager.storeLocationAndSizePreferences(this, "ImportCSVDialog");
    	
        setVisible(false);
        dispose();
    }

    /**
     * Sets the dialog visible and blocks untill "Ok" or "Cancel" has been
     * clicked (or untill the dialog  is closed through the window close
     * button). The created value is returned; it is either an DecoderInfo
     * (DelimitedTextDecoderInfo) object or null.
     *
     * @return a DelimitedTextDecoderInfo object or null
     */
    public Object showDialog() {
        pack();
        
        WindowLocationAndSizeManager.postInit(this, "ImportCSVDialog", 600, 400);
        /*
        int w = 600;
        int h = 400;
        setSize((getSize().width < w) ? w : getSize().width,
            (getSize().height < h) ? h : getSize().height);
        setLocationRelativeTo(getParent());
        */
        setVisible(true); //blocks

        return value;
    }

    /**
     * Action listener implementation
     *
     * @param e the event
     */
    @Override
	public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            createValueAndClose();
        } else if (e.getSource() == cancelButton) {
            value = null;
           closeDialog(null);

            //dispose();
        }
    }

    /**
     * The state changed event handling
     *
     * @param ce the event
     */
    @Override
	public void stateChanged(ChangeEvent ce) {
        if (ce.getSource() == firstRowCB) {
            firstRowSpinner.setEnabled(firstRowCB.isSelected());
        } else if (ce.getSource() == delimiterCB) {
            boolean selected = delimiterCB.isSelected();
        	delimiterCombo.setEnabled(selected);
        	if (selected) {
        		delimiterCombo.addItemListener(this);
        	} else {
        		delimiterCombo.removeItemListener(this);
        	}
        } else if (ce.getSource() == defaultDurCB) {
            durationTF.setEnabled(defaultDurCB.isSelected());
        }
    }
    
	@Override
	public void itemStateChanged(ItemEvent ie) {
		if (ie.getSource() == delimiterCombo && ie.getStateChange() == ItemEvent.SELECTED) {
			//System.out.println("Del: " + delimiterCombo.getSelectedItem());
			String selDelimiter = (String) delimiterCombo.getSelectedItem();
			if (curDelimiter == null || !curDelimiter.equals(selDelimiter)) {
				if (selDelimiter.equals(
						ElanLocale.getString("ImportDialog.CSV.Label.Delimiter.Tab"))) {// or equals delimiterCombo.delimiterCombo.getItemAt(0)
					selDelimiter = "\t";
				}
				curDelimiter = selDelimiter;
				reInitTables(curDelimiter);
			}
		}
		
	}

    private void createValueAndClose() {
        for (int i = 1; i < selectionTable.getColumnCount(); i++) {
        	Object val1 = selectionTable.getValueAt(0, i);
        	Object val2 = selectionTable.getValueAt(1, i);

            if (val1 instanceof Boolean) {
                if (((Boolean) val1).booleanValue()) {
                    if (colTypes[0].equals(val2)) {
                        // no type has been selected
                        JOptionPane.showMessageDialog(this,
                            (ElanLocale.getString(
                                "ImportDialog.CSV.Warning.Select") + " " +
                            (i + 1)), ElanLocale.getString("Message.Error"),
                            JOptionPane.ERROR_MESSAGE);

                        return;
                    }
                }
            }
        }

        // check default duration
        int duration = 1000;

        if (defaultDurCB.isSelected()) {
            String durValue = durationTF.getText();

            if (durValue != null) {
                try {
                    duration = Integer.parseInt(durValue);
                } catch (NumberFormatException nfe) {
                    JOptionPane.showMessageDialog(this,
                        ElanLocale.getString(
                            "RegularAnnotationDialog.Message.InvalidSize"),
                        ElanLocale.getString("Message.Error"),
                        JOptionPane.ERROR_MESSAGE);
                    durationTF.requestFocus();

                    return;
                }
            } else {
                JOptionPane.showMessageDialog(this,
                    ElanLocale.getString(
                        "RegularAnnotationDialog.Message.InvalidSize"),
                    ElanLocale.getString("Message.Error"),
                    JOptionPane.ERROR_MESSAGE);
                durationTF.requestFocus();

                return;
            }
        }

        HashMap<Integer, Object> colMap = new HashMap<Integer, Object>(selectionTable.getColumnCount());
        int numAnnColumns = 0;

        for (int i = 1; i < selectionTable.getColumnCount(); i++) {
        	Object val1 = selectionTable.getValueAt(0, i);
        	Object val2 = selectionTable.getValueAt(1, i);

            if (val1 instanceof Boolean) {
                if (((Boolean) val1).booleanValue()) {
                	// recalculate table column index to source column index
                    colMap.put(Integer.valueOf(i - 1), val2);

                    if (colTypes[1].equals(val2)) {
                        numAnnColumns++;
                    }
                }
            }
        }

        if (numAnnColumns == 0) {
            JOptionPane.showMessageDialog(this,
                ElanLocale.getString("ImportDialog.CSV.Warning.NoAnnotation"),
                ElanLocale.getString("Message.Error"), JOptionPane.ERROR_MESSAGE);

            return;
        }

        DelimitedTextDecoderInfo decoInfo = new DelimitedTextDecoderInfo(csvFile.getAbsolutePath());
        decoInfo.setDefaultDuration(duration);
        Preferences.set(INTERVAL_PREF, duration, null);
        decoInfo.setSingleAnnotationPerRow(numAnnColumns == 1);
        // apply the detected delimiter first
        if (detectedDel != null) {
        	decoInfo.setDelimiter(detectedDel);
        }
        // delimiter, check if another one has been selected
        if (delimiterCB.isSelected()) {
            Object delVal = delimiterCombo.getSelectedItem();

            if (ElanLocale.getString("ImportDialog.CSV.Label.Delimiter.Tab")
                              .equals(delVal)) {
                decoInfo.setDelimiter("\t");
            } else if (delVal != null) {
                decoInfo.setDelimiter((String) delVal);
            }
        }

        // set the number of the included columns
        List<Integer> tempList = new ArrayList<Integer>();

        for (int i = 1; i < selectionTable.getColumnCount(); i++) {
        	Object val1 = selectionTable.getValueAt(0, i);

            if (val1 instanceof Boolean) {
                if (((Boolean) val1).booleanValue()) {
                    tempList.add(Integer.valueOf(i - 1));
                }
            }
        }

        int[] inclCols = new int[tempList.size()];

        for (int i = 0; i < tempList.size(); i++) {
            inclCols[i] = tempList.get(i).intValue();
        }

        decoInfo.setIncludedColumns(inclCols);

        int firstRow = 0;

        if (firstRowCB.isSelected()) {
            firstRow = ((Integer) firstRowSpinner.getValue()).intValue() - 1;
        }

        decoInfo.setFirstRowIndex(firstRow); // set the first row

        String[] inclColumnNames = null;

        if (firstRow > 0) {
            // extract column names from header row etc.
            inclColumnNames = new String[tempList.size()];
            int headerRow = 0;
            
            if (firstRow > 1) {
            	for (int i = 0; i < firstRow; i++) {// could iterate from first row - 1 to 0
            		Object val = sampleTable.getValueAt(i, 1);// column 1 is first column with content
            		if (val instanceof String) {
            			String l = (String) val;
            			if (l.isEmpty() || l.startsWith("#") || l.startsWith("\"#")) {
            				continue;
            			} else {
            				headerRow = i;
            				break;
            			}
            		}
            	}
            }
			
            for (int i = 0; i < inclCols.length; i++) {
            	Object val = sampleTable.getValueAt(headerRow, inclCols[i] + 1);

                if (val instanceof String && !((String) val).isEmpty()) {
                    inclColumnNames[i] = (String) val;
                } else {
                    inclColumnNames[i] = String.valueOf(i);// ensure unique tier names
                }
            }

            decoInfo.setIncludedColumnsNames(inclColumnNames);
        }
        // default is false
        decoInfo.setSkipEmptyCells(skipEmptyCB.isSelected());

        Iterator<Integer> colIt = colMap.keySet().iterator();
        Integer keyInt;
        Object val;

        // begintime
        while (colIt.hasNext()) {
            keyInt = colIt.next();
            val = colMap.get(keyInt);

            if (colTypes[3].equals(val)) {
                decoInfo.setBeginTimeColumn(keyInt.intValue());
                try {
                	decoInfo.putTimeFormat(keyInt.intValue(), 
                		csvReader.detectTimeFormat(keyInt.intValue()));
                } catch (Throwable t) {// ignore
                }
                break;
            }
        }

        colIt = colMap.keySet().iterator();

        // endTime
        while (colIt.hasNext()) {
            keyInt = colIt.next();
            val = colMap.get(keyInt);

            if (colTypes[4].equals(val)) {
                decoInfo.setEndTimeColumn(keyInt.intValue());

                try {
                	decoInfo.putTimeFormat(keyInt.intValue(), 
                		csvReader.detectTimeFormat(keyInt.intValue()));
                } catch (Throwable t) {// ignore
                }
                break;
            }
        }

        colIt = colMap.keySet().iterator();

        // duration
        while (colIt.hasNext()) {
            keyInt = colIt.next();
            val = colMap.get(keyInt);

            if (colTypes[5].equals(val)) {
                decoInfo.setDurationColumn(keyInt.intValue());

                try {
                	decoInfo.putTimeFormat(keyInt.intValue(), 
                		csvReader.detectTimeFormat(keyInt.intValue()));
                } catch (Throwable t) {// ignore
                }
                
                break;
            }
        }

        colIt = colMap.keySet().iterator();

        // column with tier names
        while (colIt.hasNext()) {
            keyInt = colIt.next();
            val = colMap.get(keyInt);

            if (colTypes[2].equals(val)) {
                decoInfo.setTierColumnIndex(keyInt.intValue());

                break;
            }
        }

        List<Integer> annColumns = new ArrayList<Integer>();
        colIt = colMap.keySet().iterator();

        // multiple annotations?
        while (colIt.hasNext()) {
            keyInt = colIt.next();
            val = colMap.get(keyInt);

            if (colTypes[1].equals(val)) {
                annColumns.add(keyInt);
            }
        }

        int[] annArray = new int[annColumns.size()];

        for (int j = 0; j < annArray.length; j++) {
            annArray[j] = annColumns.get(j).intValue();
        }

        decoInfo.setAnnotationColumns(annArray);

        Map<Integer, String> tierColMap = new HashMap<Integer, String>(annArray.length);

        if (inclColumnNames != null) {
            for (int i = 0; i < annArray.length; i++) {
                for (int j = 0; j < inclCols.length; j++) {
                    if ((annArray[i] == inclCols[j]) &&
                            (j < inclColumnNames.length)) {
                        tierColMap.put(Integer.valueOf(annArray[i]),
                            inclColumnNames[j]);
                    }
                }
            }
        } else if (decoInfo.getTierColumnIndex() < 0){
        	for (int i = 0; i < annArray.length; i++) {
                tierColMap.put(Integer.valueOf(annArray[i]),
                        "Tier-" + i);
        	}
        }

        if (!tierColMap.isEmpty()) {
        	decoInfo.setColumnsWithTierNames(tierColMap);
        }
        
        value = decoInfo;

        setVisible(false);
    }
    
    /**
     * A model where the first column is not editable.  
     *
     * Jan 30, 2019
     */
    private class SelectionTableModel extends DefaultTableModel {

		public SelectionTableModel(int rowCount, int columnCount) {
			super(rowCount, columnCount);
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			if (column == 0) {
				return false;
			}
			return super.isCellEditable(row, column);
		}
    }

    /**
     * A table with editors and renderers for selectionof th tiers to include
     * in the import  and to specify their data type (begin time, duration,
     * annotation etc.)
     *
     * @author Han Sloetjes
     * @version 1.0
     */
    private class SelectionTable extends JTable {
        private DefaultCellEditor cbEditor;
        private DefaultCellEditor comboEditor;
        private CheckBoxTableCellRenderer cbRenderer;
        private ComboBoxTableCellRenderer comboRenderer;

        /**
         * Creates a new SelectionTable instance
         */
        public SelectionTable() {
            super();

            JCheckBox cb = new JCheckBox();
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cbEditor = new DefaultCellEditor(cb);
            comboEditor = new DefaultCellEditor(new JComboBox(colTypes));
            cbRenderer = new CheckBoxTableCellRenderer();
            cbRenderer.setHorizontalAlignment(SwingConstants.CENTER);
            comboRenderer = new ComboBoxTableCellRenderer(colTypes);

            setRowMargin(3);
            setRowHeight(getRowHeight() + 10);
        }

        /**
         * Returns the editor for the row: a checkbox for row 0, a combobox for
         * row 1.
         *
         * @param row the table row
         * @param column the table column
         *
         * @return the editor component
         */
        @Override
		public TableCellEditor getCellEditor(int row, int column) {
        	if (column != 0) {
	            if (row == 0) {
	                return cbEditor;
	            } else if (row == 1) {
	                return comboEditor;
	            }
        	}

            return super.getCellEditor(row, column);
        }

        /**
         * Returns the renderer for the row: a checkbox for row 0, a combobox
         * for row 1.
         *
         * @param row the table row
         * @param column the table column
         *
         * @return the renderer component
         */
        @Override
		public TableCellRenderer getCellRenderer(int row, int column) {
        	if (column != 0) {
	            if (row == 0) {
	                return cbRenderer;
	            } else if (row == 1) {
	                return comboRenderer;
	            }
        	}

            return super.getCellRenderer(row, column);
        }
    }

}
