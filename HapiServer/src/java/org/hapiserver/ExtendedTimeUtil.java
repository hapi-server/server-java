
package org.hapiserver;

import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Useful extentions to the TimeUtil class, like support for times like "now-P1D"
 * All of these could potentially go into a future version of the TimeUtil class.
 * @author jbf
 */
public class ExtendedTimeUtil {
    
    /**
     * year component position in seven element decomposed time array
     */
    public static final int YEAR = 0;
    
    /**
     * month component position in seven element decomposed time array
     */
    public static final int MONTH = 1;
    
    /**
     * day component position in seven element decomposed time array
     */
    public static final int DAY = 2;
    
    /**
     * hour component position in seven element decomposed time array
     */
    public static final int HOUR = 3;
    
    /**
     * minute component position in seven element decomposed time array
     */
    public static final int MINUTE = 4;
    
    /**
     * second component position in seven element decomposed time array
     */
    public static final int SECOND = 5;
    
    /**
     * nanosecond component position in seven element decomposed time array
     */
    public static final int NANOSECOND = 6;
    
    /**
     * parse the time which is known to the developer to be valid.  A runtime 
     * error is thrown if it it not valid.
     * @param time
     * @return the decomposed time.
     * @throws RuntimeException if the time is not valid.
     */
    public static int[] parseValidTime( String time ) {
        try {
            return parseTime(time);
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * parse the time, allowing times like "now" and "lastHour"
     * @param time
     * @return
     * @throws ParseException 
     */
    public static int[] parseTime( String time ) throws ParseException {
        if ( time.length()==0 ) {
            throw new IllegalArgumentException("empty time string");
        } else {
            if ( Character.isDigit(time.charAt(0) ) ) {
                //TODO: I didn't realize parseISO8601Time handles the now and last extensions.  This needs review.
                return TimeUtil.parseISO8601Time(time);
            } else {
                return labelledTime( time );
            }
        }
    }
    
    /**
     * allow one of the following:<ul>
     * <li>now
     * <li>lastHour
     * <li>lastDay
     * <li>lastMonth
     * <li>lastYear
     * <li>now-P1D
     * </ul>
     * @param label
     * @return 
     * @throws java.text.ParseException 
     */
    public static int[] labelledTime( String label ) throws ParseException {
        int[] now= TimeUtil.now();
        int[] delta= null;
        int i= label.indexOf("-");
        if ( i==-1 ) i= label.indexOf("+");
        if ( i>-1 ) {
            delta= TimeUtil.parseISO8601Duration(label.substring(i));
            label= label.substring(0,i);
        }
        label= label.toLowerCase();
        if ( label.startsWith("last") ) {
            if ( label.endsWith("minute") ) {
                now[6]=0;
                now[5]=0;
            } else if ( label.endsWith("hour") ) {
                now[6]=0;
                now[5]=0;
                now[4]=0;
            } else if ( label.endsWith("day") ) {
                now[6]=0;
                now[5]=0;                
                now[4]=0;
                now[3]=0;
            } else if ( label.endsWith("month") ) {
                now[6]=0;
                now[5]=0;                
                now[4]=0;
                now[3]=0;
                now[2]=1;
            } else if ( label.endsWith("year") ) {
                now[6]=0;
                now[5]=0;                
                now[4]=0;
                now[3]=0;
                now[2]=1;
                now[1]=1;
            } else {
                throw new IllegalArgumentException("unsupported last component: "+label);
            }
        } else if ( label.equals("now") ) {
            //do nothing
        }
        if ( delta!=null ) {
            return TimeUtil.add( now, delta );
        }  else {
            return now;
        }
        
    }
    
    /**
     * true if t1 is after t2.
     * @param t1
     * @param t2
     * @return 
     */
    public static boolean gt( int[] t1, int[] t2 ) {
        TimeUtil.normalizeTime(t1);
        TimeUtil.normalizeTime(t2);
        for ( int i=0; i<7; i++ ) {
            if ( t1[i]>t2[i] ) {
                return true;
            } else if ( t1[i]<t2[i] ) {
                return false;
            }
        }
        return false; // they are equal
    }
    
    /**
     * given the two times, return a 14 element time range.
     * @param t1 a seven digit time
     * @param t2 a seven digit time after the first time.
     * @return a fourteen digit time range.
     * @throws IllegalArgumentException when the first time is greater than or equal to the second time.
     */
    public static int[] createTimeRange( int[] t1, int[] t2 ) {
        if ( !gt(t2,t1) ) {
            throw new IllegalArgumentException("t1 is greater than t2");
        }
        int[] result= new int[TimeUtil.TIME_DIGITS*2];
        System.arraycopy( t1, 0, result, 0, 7 );
        System.arraycopy( t2, 0, result, 7, 7 );
        return result;
    }
    
    /**
     * return the seven element stop time from the time range.  Note
     * it is fine to use a time range as the start time, because codes
     * will only read the first seven components.
     * @param tr a fourteen-element time range.
     * @return the stop time.
     */
    public static int[] getStopTime( int [] tr ) {
        int[] result= new int[7];
        System.arraycopy( tr, 7, result, 0, 7 );
        return result;
    }
    
    /**
     * format the time as milliseconds since 1970-01-01T00:00Z into a string.  The
     * number of milliseconds should not include leap seconds.
     * 
     * @param time the number of milliseconds since 1970-01-01T00:00Z
     * @return the formatted time.
     * @see DateTimeFormatter#parse
     */
    public static String fromMillisecondsSince1970(long time) {
        return DateTimeFormatter.ISO_INSTANT.format( Instant.ofEpochMilli(time) );
    }

    /**
     * format the time, but omit trailing zeros.  $Y-$m-$dT$H:$M is the coursest resolution returned.
     * @param time seven element time range
     * @return formatted time, possibly truncated to minutes, seconds, milliseconds, or microseconds
     */
    public static String formatIso8601TimeBrief(int[] time ) {
        return formatIso8601TimeBrief(time,0);
    }
    
    /**
     * format the time, but omit trailing zeros.  $Y-$m-$dT$H:$M is the coursest resolution returned.
     * @param time seven element time range
     * @param offset the offset into the time array (7 for stop time in 14-element range array).
     * @return formatted time, possibly truncated to minutes, seconds, milliseconds, or microseconds
     */
    public static String formatIso8601TimeBrief(int[] time, int offset ) {
        
        String stime= TimeUtil.formatIso8601Time(time,offset);
        
        int nanos= time[6+offset];
        int micros= nanos % 1000;
        int millis= nanos % 10000000;
        
        if ( nanos==0 ) {
            if ( time[5+offset]==0 ) {
                return stime.substring(0,16) + "Z";
            } else {
                return stime.substring(0,19) + "Z";
            }
        } else {
            if ( millis==0 ) {
                return stime.substring(0,23) + "Z";
            } else if ( micros==0 ) {
                return stime.substring(0,26) + "Z";
            } else {
                return stime;
            }
        }
    }
    
}
