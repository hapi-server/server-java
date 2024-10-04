
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
    
    public int[] toComponents() {
        char[] cc= iso8601.toCharArray();
        return new int[] { 
            cc[0]*1000 + cc[1]*100 + cc[2]*10 + cc[3],
            cc[5]*10 + cc[6],
            cc[8]*10 + cc[9],
            cc[11]*10 + cc[12],
            cc[14]*10 + cc[15],
            cc[17]*10 + cc[18],
            cc[20]*100000000 + cc[21]*10000000 + cc[22]*1000000 
            + cc[23]*100000 + cc[24]*10000 + cc[25]*1000
            + cc[26]*100 + cc[27]*10 + cc[28] 
        };
    }
    
}
