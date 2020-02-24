package mpi.eudico.server.corpora.clomimpl.dobes;

import java.util.Comparator;
import java.util.logging.Level;

import static mpi.eudico.server.corpora.util.ServerLogger.LOG;

/**
 * A comparator that compares AnnotationRecord instances. 
 * First based on begin time value, then on end time value and
 * finally, optionally, based on the id value (assumes id's 
 * in the "a[0-9]+" format, excluding "a0").
 * 
 * <p>Applies no nullsFirst or nullsLast strategy.
 * 
 * @author Han Sloetjes
 */
public class AnnotationRecordComparator implements Comparator<AnnotationRecord> {
    private boolean includeIDs;
    
	/**
     * Creates a new AnnotationRecordComparator instance setting the
     * includeIDs flag to false.
     */
    public AnnotationRecordComparator() {
        super();
        includeIDs = false;
    }

    /**
     * Creates a new AnnotationRecordComparator instance
     * 
     * @param includeIDs if true, the comparator includes the numeric part of
     * the annotation id in the comparison in case the begin and end times 
     * cannot be compared or are equal. Only useful in specific cases, e.g. as
     * part of an import or conversion action where the order of the assigned
     * id's is reliable and meaningful.  
     */
    public AnnotationRecordComparator(boolean includeIDs) {
		super();
		this.includeIDs = includeIDs;
	}


	/**
     * Compares 2 AnnotationRecords. First compares the begin time slot
     * records, next the end time slot records and then the id's.<br>
     * If an annotation record does not have a begin or end time
     * slot record reference, that part is ignored in the comparison.
     *
     * @param ar1 the first annotation record
     * @param ar2 the second annotation record
     *
     * @return -1 if the first is less, 1 if the first is greater, 0
     *         otherwise
     */
    @Override
	public int compare(AnnotationRecord ar1, AnnotationRecord ar2) {
    	if (ar1 == null || ar2 == null) {
    		return 0;
    	}

    	if (ar1.getBeginTimeSlotRecord() != null && ar2.getBeginTimeSlotRecord() != null) {
    		if (ar1.getBeginTimeSlotRecord().getValue() > -1 && // > TimeSlot.TIME_UNALIGNED 
	                ar2.getBeginTimeSlotRecord().getValue() > -1) {
    			
    			if (ar1.getBeginTimeSlotRecord().getValue() < 
    					ar2.getBeginTimeSlotRecord().getValue()) {
    				return -1;
    			}
    			
    			if (ar1.getBeginTimeSlotRecord().getValue() > 
    					ar2.getBeginTimeSlotRecord().getValue()) {
    				return 1;
    			}
    		}
    	}

        // begin time equal or undefined
    	if (ar1.getEndTimeSlotRecord() != null && ar2.getEndTimeSlotRecord() != null) {
	        if (ar1.getEndTimeSlotRecord().getValue() > -1 &&
	                ar2.getEndTimeSlotRecord().getValue() > -1) {
	        	
	        	if (ar1.getEndTimeSlotRecord().getValue() < ar2.getEndTimeSlotRecord()
                        .getValue()) {
	        		return -1;
	        	}
	        	
	        	if (ar1.getEndTimeSlotRecord().getValue() > ar2.getEndTimeSlotRecord()
                        .getValue()) {
	        		return 1;
	        	}
	        }
	        
    	}
        // end time equal or undefined too, optionally compare id's.
        return includeIDs ? compareID(ar1, ar2) : 0;
    }
    
    private int compareID(AnnotationRecord ar1, AnnotationRecord ar2) {
        // Rely on the fact that the id numeric part is not 0 and that 
    	// the length of the id string is > 1.
        try {
            Long l1 = Long.valueOf(ar1.getAnnotationId().substring(1));
            Long l2 = Long.valueOf(ar2.getAnnotationId().substring(1));

            if (l1.longValue() < l2.longValue()) {
                return -1;
            }

            if (l1.longValue() > l2.longValue()) {
                return 1;
            }
        } catch (NumberFormatException nfe) {
        	if (LOG.isLoggable(Level.FINE)) {
        		LOG.fine("Cannot convert the annotation id to a Long value");
        	}
        } catch (Throwable t) {
        	// substring(1) might throw an exception
        	if (LOG.isLoggable(Level.FINE)) {
        		LOG.fine("Cannot convert the annotation id to a Long value: " + t.getMessage());
        	}
        }

        return 0;
    }
}
