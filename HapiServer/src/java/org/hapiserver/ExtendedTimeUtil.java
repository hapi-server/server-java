
package org.hapiserver;

import java.text.ParseException;

/**
 * Useful extentions to the TimeUtil class, like support for times like "now-P1D"
 * All of these could potentially go into a future version of the TimeUtil class.
 * @author jbf
 */
public class ExtendedTimeUtil {
    
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
        if ( label.startsWith("last") ) {
            if ( label.endsWith("Minute") ) {
                now[6]=0;
                now[5]=0;
            } else if ( label.endsWith("Hour") ) {
                now[6]=0;
                now[5]=0;
                now[4]=0;
            } else if ( label.endsWith("Day") ) {
                now[6]=0;
                now[5]=0;                
                now[4]=0;
                now[3]=0;
            } else if ( label.endsWith("Month") ) {
                now[6]=0;
                now[5]=0;                
                now[4]=0;
                now[3]=0;
                now[2]=1;
            } else if ( label.endsWith("Year") ) {
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
}
