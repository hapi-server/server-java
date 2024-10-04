
package org.hapiserver;

/**
 * experiment with a new class for holding time strings. A time string is guaranteed to
 * be ISO8601 and of the format $Y-$m-$dT$H:$M:$S.$NZ.
 * @author jbf
 */
public final class TimeString {
    final String iso8601;
    
    public TimeString( String iso8601 ) {
        this.iso8601= TimeUtil.normalizeTimeString( iso8601 );
    }
    
    public TimeString( int[] components ) {
        this.iso8601= TimeUtil.formatIso8601Time(components);
    }
    
    @Override
    public String toString() {
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
    
    public static void main( String[] args ) {
        TimeString r= new TimeString("2043-04-05T23:13:02.123456789");
        System.err.println(r);
        for ( int i: r.toComponents() ) {
            System.err.println(i);
        }
    }
    
}
