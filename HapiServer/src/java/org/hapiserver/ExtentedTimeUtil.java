
package org.hapiserver;

import java.text.ParseException;

/**
 * Useful extentions to the TimeUtil class, like support for times like "now-P1D"
 * @author jbf
 */
public class ExtentedTimeUtil {
    
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
}
