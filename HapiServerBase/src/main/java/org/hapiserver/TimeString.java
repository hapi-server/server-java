
package org.hapiserver;

/**
 * experiment with a new class for holding time strings. A time string is guaranteed to
 * be ISO8601 and of the format $Y-$m-$dT$H:$M:$S.$NZ.
 * @author jbf
 */
public final class TimeString implements Comparable<TimeString> {
    final String iso8601;
    
    public TimeString( String iso8601 ) {
        this.iso8601= TimeUtil.normalizeTimeString( iso8601 );
    }
    
    public TimeString( int[] components ) {
        this.iso8601= TimeUtil.formatIso8601Time(components);
    }
    
    public TimeString( int year, int month, int day ) {
        int[] components= new int[] { year, month, day, 0, 0, 0, 0 };
        this.iso8601= TimeUtil.formatIso8601Time(components);
    }
    
    /**
     * return the start time from the first seven elements of the 14-element time range array.
     * @param arr
     * @return 
     */
    public static TimeString getStartTime( int[] arr ) {
        int[] components= TimeUtil.getStartTime(arr);
        return new TimeString( TimeUtil.formatIso8601Time(components) );
    }
    
    /**
     * return the stop time from the last seven elements of the 14-element time range array.
     * @param arr
     * @return 
     */
    public static TimeString getStopTime( int[] arr ) {
        int[] components= TimeUtil.getStopTime(arr);
        return new TimeString( TimeUtil.formatIso8601Time(components) );
    }

    @Override
    public String toString() {
        return this.iso8601;
    }
    
    public String toIsoTime() {
        return this.iso8601;
    }
    
    /**
     * This could use TimeUtil.isoTimeToArray(iso8601), but since we 
     * store the times in a prescribed way, we can optimize for performance.
     * @return [ Y, m, d, H, M, S, N ]
     * @see TimeUtil#isoTimeToArray(java.lang.String) 
     */
    public int[] toComponents() {
        char[] cc= iso8601.toCharArray();
        return new int[] { 
            (cc[0]-48)*1000 + (cc[1]-48)*100 + (cc[2]-48)*10 + (cc[3]-48),
            (cc[5]-48)*10 + (cc[6]-48),
            (cc[8]-48)*10 + (cc[9]-48),
            (cc[11]-48)*10 + (cc[12]-48),
            (cc[14]-48)*10 + (cc[15]-48),
            (cc[17]-48)*10 + (cc[18]-48),
            (cc[20]-48)*100000000 + (cc[21]-48)*10000000 + (cc[22]-48)*1000000 
            + (cc[23]-48)*100000 + (cc[24]-48)*10000 + (cc[25]-48)*1000
            + (cc[26]-48)*100 + (cc[27]-48)*10 + (cc[28]-48) 
        };
    }
    
    /**
     * return the year
     * @return the year
     */
    public int getYear() {
        return Integer.parseInt(iso8601.substring(0,4));
    }
    
    /**
     * return the month
     * @return the month
     */
    public int getMonth() {
        return (iso8601.charAt(5)-'0') * 10 + ( iso8601.charAt(6)-'0' );
    }

    /**
     * return the day
     * @return the day
     */
    public int getDay() {
        return (iso8601.charAt(8)-'0') * 10 + ( iso8601.charAt(9)-'0' );
    }
    
    public static void main( String[] args ) {
        TimeString r= new TimeString("2043-04-05T23:13:02.123456789");
        System.err.println(r.getYear());
        System.err.println(r.getMonth());
        System.err.println(r.getDay());
        System.err.println(r);
        for ( int i: r.toComponents() ) {
            System.err.println(i);
        }
        System.err.println(r.gt( new TimeString("2043-04-05T23:13:02.123456780")));
    }

    @Override
    public int compareTo(TimeString t) {
        return this.iso8601.compareTo(t.iso8601);
    }
    
    public boolean gt( TimeString t ) {
        return compareTo(t)>0;
    }
    
}
