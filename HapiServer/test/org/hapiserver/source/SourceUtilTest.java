
package org.hapiserver.source;

import java.io.File;
import java.util.Iterator;
import org.hapiserver.ExtendedTimeUtil;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for SourceUtil
 * @author jbf
 */
public class SourceUtilTest {
    
    public SourceUtilTest() {
    }

    /**
     * Test of getFileLines method, of class SourceUtil.
     */
    @Test
    public void testGetFileLines() throws Exception {
        System.out.println("getFileLines");
        File f = new File( "/etc/resolv.conf");
        Iterator<String> result = SourceUtil.getFileLines(f);
        int count=0;
        while ( result.hasNext() ) {
            count++;
            result.next();
        }
        assertTrue( count>0 );
    }

    /**
     * Test of getGranuleIterator method, of class SourceUtil.
     */
    @Test
    public void testGetGranuleIterator() {
        System.out.println("getGranuleIterator");
        int[] start = new int[] { 2022, 4, 2, 8, 13, 0, 0, };
        int[] stop = new int[] { 2022, 4, 3, 8, 13, 0, 0, };
        int digit = ExtendedTimeUtil.HOUR;
        
        Iterator result = SourceUtil.getGranuleIterator(start, stop, digit);
        int count= 0;
        while ( result.hasNext() ) {
            count++;
            result.next();
        }
        assertEquals( count, 25 );
        
    }
    
}
