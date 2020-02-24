package mpi.eudico.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;


/**
 * A class for converting time values to and from a String representation 
 * in a specific format and millisecond long values.
 * 
 * Several time string formats are supported, these are listed in the 
 * TimeFormat enumeration.
 *
 */
public class TimeFormatter {
	public enum TIME_FORMAT {
		HHMMSSMS, // hh:mm:ss.msec
		SSMS,     // ss.msec
		MS,       // milliseconds
		HHMMSSFF  // hh:mm:ss:ff
	}
    
    static final int HOUR_MS = 1000 * 60 * 60;
    static final int MIN_MS  = 1000 * 60;
    static final int SEC_MS  = 1000;
    
    /** a two-digit formatter */
    private static final DecimalFormat twoDigits = new DecimalFormat("00");

    /** a three-digit formatter */
    private static final DecimalFormat threeDigits = new DecimalFormat("000");

    // use the US Locale to make sure a '.' is used and not ','

    /** a n-digit.three-digit formatter */
    private static final DecimalFormat secondsMillis = new DecimalFormat("#0.000",
            new DecimalFormatSymbols(Locale.US));
    
    /* in practice the decimal separator is either '.' or ','
    static {
    	for (Locale l : DecimalFormatSymbols.getAvailableLocales()) {
    		System.out.println(String.format("Locale: %s - Decimal char: %s",
    				l.getDisplayName(), DecimalFormatSymbols.getInstance(l).getDecimalSeparator()));
    	}
    }
	*/
    
    /**
     * Converts a time definition in the format hh:mm:ss.sss into a long that
     * contains the time in milliseconds.
     * Jan 2011: now also handles negative time values with minutes and hour values 
     * greater than 0. Negative values with only seconds and milliseconds were 
     * already just parsed, there was no check for a minus sign. 
     *
     * @param timeString the string that contains the time in the format
     *        hh:mm:ss.sss
     *
     * @return the time in milliseconds, -1.0 if the time string has an illegal
     *         format. Since negative values are also handled, the return value of -1 
     *         to indicate an error was and is quite useless.
     *         
     * @see #toMilliSeconds(String, TIME_FORMAT)
     */
    public static long toMilliSeconds(String timeString) {
        try {
            String hourString = new String("0.0");
            String minuteString = new String("0.0");
            String secondString = new String("0.0");
            
            boolean negative = timeString.charAt(0) == '-';
            if (negative) {
            	timeString = timeString.substring(1);
            }
            
            int mark1 = timeString.indexOf(':', 0);

            if (mark1 == -1) { // no ':', so interpret string as sss.ss or ms
            	// HS apr-2006: added millisecond support
            	if (timeString.indexOf('.') < 0) {
            		// no ':' nor '.', so interpret as milliseconds
            		// or first try ',' for comma based seconds formats: 22,345
            		int comma = timeString.indexOf(',');
            		if (comma < 0) {
            			// no ':', '.' or ',', so interpret as milliseconds
	            		if (negative) {
	            			return -Long.parseLong(timeString);
	            		} else {
	            			return Long.parseLong(timeString);
	            		}
            		} else {
            			// replace ',' by '.'
            			secondString = timeString.replace(',', '.');
            		}
            	} else {
            		// no ':', so interpret string as sss.ss
            		secondString = timeString;
            	}

            } else {
                int mark2 = timeString.indexOf(':', mark1 + 1);

                if (mark2 == -1) { // only one :, so interpret string as mm:ss.sss
                    minuteString = timeString.substring(0, mark1);
                    secondString = timeString.substring(mark1 + 1,
                            timeString.length());
                } else { // two :, so interpret string as hh:mm:ss.sss
                    hourString = timeString.substring(0, mark1);
                    minuteString = timeString.substring(mark1 + 1, mark2);
                    secondString = timeString.substring(mark2 + 1,
                            timeString.length());
                }
            }

            double hours = Double.parseDouble(hourString);
            double minutes = Double.parseDouble(minuteString);
            double seconds = Double.parseDouble(secondString);

            if (negative) {
                return (long) -(1000 * ((hours * 3600.0) + (minutes * 60.0) +
                        seconds));
            } else {
	            return (long) (1000 * ((hours * 3600.0) + (minutes * 60.0) +
	            seconds));
            }
        } catch (Exception e) { // the timeString was not parseable

            return -1;
        }
    }
    
    /**
     * Conversion of a time string to milliseconds. Allows to specify how the
     * string needs to be interpreted.
     * The main reason to use this variant instead of {@link #toMilliSeconds(String)}
     * is to be able to treat a value without a dot as seconds instead of 
     * milliseconds (the default). E.g. given a sequence "99.6, 100, 100.4" the first
     * and last value will be recognized as sec.ms while the second value is interpreted
     * as milliseconds.
     * 
     * @param timeString the string containing a time value, e.g. 
     *        "00:02:55.324", "22.110", "6722"
     * @param format determines how the string should be parsed
     * 
     * @return a time value in milliseconds or -1 if the value could not be parsed
     * (although -1 can be a legal value if negative time values are supported too)
     * 
     * @see #toMilliSeconds(String)
     */
    public static long toMilliSeconds(String timeString, TIME_FORMAT format) {
    	if (format == null) {
    		return toMilliSeconds(timeString);
    	}
    	
    	try {
            boolean negative = timeString.charAt(0) == '-';
            if (negative) {
            	timeString = timeString.substring(1);
            }
            long t = -1;
            
    		switch (format) {
    		case MS:
        		t = Long.parseLong(timeString);
        		break;
    		case SSMS:
    			t = (long) (SEC_MS * Double.parseDouble(timeString));
    			
    			break;
    		case HHMMSSMS:
    			String[] hparts = timeString.split(":");
    			// assume last part is seconds and possibly milliseconds
    			t = (long) (SEC_MS * Double.parseDouble(hparts[hparts.length - 1]));
    			// possibly remaining minutes and hours
    			if (hparts.length == 3) {
    				// add minutes
    				t += (MIN_MS * Long.parseLong(hparts[1]));
    				// add hours
    				t += (HOUR_MS * Long.parseLong(hparts[0]));
    			} else if (hparts.length == 2){
    				// add minutes
    				t += (MIN_MS * Long.parseLong(hparts[0]));
    			}// case of hparts.length == 1 already handled above

    			break;
    		case HHMMSSFF:
    			// cannot be supported without an additional parameter specifying
    			// the duration per frame (and maybe NTSC specifics?)
    			default:
    				// fail with return value -1
    		}
    		
    		if (negative) {
    			return -t;
    		} else {
    			return t;
    		}
    	} catch (NumberFormatException nfe){
    		// log failure ?
    	} catch (Throwable t) {// null pointers, others
    		// log failure ?
    	}
    	
    	return -1;
    }

    /**
     * Converts a time in seconds to the following string representation:
     * hh:mm:ss.sss
     *
     * @param time a long containing the time in milliseconds
     *
     * @return the string representation of the time
     */
    public static String toString(long time) {
        long hours = time / HOUR_MS;
        String hourString = twoDigits.format(hours);

        long minutes = (time - (HOUR_MS * hours)) / MIN_MS;
        String minuteString = twoDigits.format(minutes);

        long seconds = (time - (HOUR_MS * hours) - (MIN_MS * minutes)) / SEC_MS;
        String secondString = twoDigits.format(seconds);

        long millis = time - (HOUR_MS * hours) - (MIN_MS * minutes) -
            (SEC_MS * seconds);
        String milliString = threeDigits.format(millis);

        return hourString + ":" + minuteString + ":" + secondString + "." +
        milliString;
    }

    /**
     * Converts a time in ms to a ss.mmm formatted String
     *
     * @param time the time value to convert
     *
     * @return a String in the ss.mmm format
     */
    public static String toSSMSString(long time) {
        double dd = time / 1000.0;

        return secondsMillis.format(dd);
    }

    /**
     * Timecode has the format hh:mm:ss:ff. PAL has 25 frames per second.
     *
     * @param time the time to convert
     * @return a PAL time code string
     */
    public static String toTimecodePAL(long time) {
        long hours = time / HOUR_MS;
        String hourString = twoDigits.format(hours);

        long minutes = (time - (HOUR_MS * hours)) / MIN_MS;
        String minuteString = twoDigits.format(minutes);

        long seconds = (time - (HOUR_MS * hours) - (MIN_MS * minutes)) / SEC_MS;
        String secondString = twoDigits.format(seconds);

        long frames = (time - (HOUR_MS * hours) - (MIN_MS * minutes) -
            (SEC_MS * seconds)) / 40;
        String milliString = twoDigits.format(frames);

        return hourString + ":" + minuteString + ":" + secondString + ":" +
        milliString;
    }
    
    /**
     * Time code has the format hh:mm:ss:ff. This PAL variant has 50 frames per second.
     *
     * @param time the time to convert
     * @return a PAL-50 time code string
     */
    public static String toTimecodePAL50(long time) {
        long hours = time / HOUR_MS;
        String hourString = twoDigits.format(hours);

        long minutes = (time - (HOUR_MS * hours)) / MIN_MS;
        String minuteString = twoDigits.format(minutes);

        long seconds = (time - (HOUR_MS * hours) - (MIN_MS * minutes)) / SEC_MS;
        String secondString = twoDigits.format(seconds);

        long frames = (time - (HOUR_MS * hours) - (MIN_MS * minutes) -
            (SEC_MS * seconds)) / 20;
        String milliString = twoDigits.format(frames);

        return hourString + ":" + minuteString + ":" + secondString + ":" +
        milliString;
    }

    /**
     * Returns the frame number for a given time in a PAL encoded media file.
     * First frame is index 0.
     *
     * @param time the time in milliseconds
     * @return a string containing the frame number in PAL
     */
    public static String toFrameNumberPAL(long time) {
    	return String.valueOf((long) (time / 40));
    }
    
    /**
     * Returns the frame number for a given time in a PAL-50 encoded media file.
     * First frame is index 0.
     *
     * @param time the time in milliseconds
     * @return a string containing the frame number in PAL - 50fps
     */
    public static String toFrameNumberPAL50(long time) {
    	return String.valueOf((long) (time / 20));
    }

    /**
     * Time code has the format hh:mm:ss:ff. NTSC has approx. 29.97 frames per second.
     * The 'standard' SMPTE drop frames mechanism is used for frame number calculation,
     * i.e. drop the first two frames from every minute except every tenth minute.
     *
     * @param time the time to convert
     * @return a NTSC time code string
     */
    public static String toTimecodeNTSC(long time) {
        // this is already off by a frame in the Premiere test movie
        int frameNumber = (int)((time / 1000f) * 29.97);
        //int frameNumber = (int)time;
        // every block of ten minutes has an exact number of frames, 17982. Calculate the
        // number of 10-minute-blocks, can also be used for calculation of hours + minutes
        int numTenMin = frameNumber / 17982;
        int hours = numTenMin / 6;
        numTenMin = numTenMin - (6 * hours);
        // the rest is used to calculate minutes (less than 10), seconds and frames
        // calculate number of complete minutes from remaining frames
        int numMin = frameNumber % 17982;
        // complete minutes
        int min = numMin / 1800;
        // remainder for calculation of seconds
        int rest = numMin - (min * 1800);
        int sec = rest / 30;
        // remaining frames
        int fr = rest - (sec * 30);
        // adjust, add 2 frames for each minute
        fr += (min * 2);
        // if frames > 29 add extra second and eventually minute
        if (fr > 29) {
            fr -= 30;
            sec += 1;
            if (sec > 59) {
                sec = 0;
                min += 1;
                fr += 2;
            }
        }
        // convert to string
        return twoDigits.format(hours) + ":" + twoDigits.format((numTenMin * 10) + min) + ":"
        + twoDigits.format(sec) + ":" + twoDigits.format(fr);
    }

    /**
     * Returns the frame number for a given time in a NTSC encoded media file.
     * First frame is index 0.
     *
     * @param time the time in milliseconds
     * @return a string containing the frame number in NTSC
     */
    public static String toFrameNumberNTSC(long time) {
        // this is already off by a frame in the Premiere test movie
        int frameNumber = (int)((time / 1000f) * 29.97);

    	return String.valueOf(frameNumber);
    }
    
    /**
     * Returns the unformatted (absolute) frame number or frame index at the
     * specified time for the given the duration of one frame.
     * 
     * @param timeMs the media time in milliseconds
     * @param frameDurationMs the duration of one (video) frame
     * 
     * @return the frame number or index at the specified time
     */
    public static String toFrameNumber(long timeMs, double frameDurationMs) {
    	return String.valueOf((int) (timeMs / frameDurationMs)); // rounds down
    }
    
    /**
     * Returns a hh:mm:ss:ff formatted string for the specified media time and the
     * specified duration per frame (both in milliseconds).
     * 
     * @param timeMs the media time in milliseconds. For accurate calculation of 
     * the frame number, a double value which has not been rounded would be required
     * (depending on the frame rate / duration per frame value)
     * @param frameDurationMs the duration per frame in milliseconds
     * 
     * @return a formatted time string
     */
    public static String toTimeCode(long timeMs, double frameDurationMs) {
    	//int frameNumber = (int) (timeMs / frameDurationMs);// round down, first frame is 0
    	// cannot assume any "calibration" points like with NTSC, assume a linear layout of frames
    	double modRemain = timeMs % frameDurationMs;
    	// start time of the frame we are "in"
    	double frameBeginTime = timeMs - modRemain;
    	// use this start time to determine the hour, minute and second "boundary"
    	int numHours = 0;
    	int numMinutes = 0;
    	int numSeconds = 0;
    	int numFrames = 0;
    	// this doesn't work sufficiently, use the time based approach and only calculate remaining frames at the end
    	numHours = (int) (frameBeginTime / HOUR_MS);// rounding down
    	
    	double minuteRemainTimeMs = frameBeginTime - (numHours * HOUR_MS);
    	
    	numMinutes = (int) (minuteRemainTimeMs / MIN_MS);// rounding down
    	
    	if (numMinutes == 60) {// can't be > 60?
    		numMinutes--;
    		numHours++;
    	}
    	
    	double secRemainTimeMs = minuteRemainTimeMs - (numMinutes * MIN_MS);
    	
    	numSeconds = (int) (secRemainTimeMs / SEC_MS);
    	
    	if (numSeconds == 60) {
    		numSeconds--;
    		numMinutes++;
        	if (numMinutes == 60) {
        		numMinutes--;
        		numHours++;
        	}
    	}
    	
    	double framesRemainTimeMs = secRemainTimeMs - (numSeconds * SEC_MS);
    	// have observed strange rounding, e.g. the double / double = 7.0000000
    	// but cast to an int it becomes 6
    	numFrames = (int) (framesRemainTimeMs / frameDurationMs);

    	//double fps = SEC_MS / frameDurationMs;
    	int maxFrameNum = (int) Math.ceil(SEC_MS / frameDurationMs) - 1;
    	// check whether the number of frames > number of frames per second??
    	if (numFrames > maxFrameNum) {
    		numSeconds++;
        	if (numSeconds == 60) {
        		numSeconds--;
        		numMinutes++;
            	if (numMinutes == 60) {
            		numMinutes--;
            		numHours++;
            	}
        	}
    	}
    	
    	return String.format("%s:%s:%s:%s", twoDigits.format(numHours), twoDigits.format(numMinutes),
    			twoDigits.format(numSeconds), twoDigits.format(numFrames));
    }

}
