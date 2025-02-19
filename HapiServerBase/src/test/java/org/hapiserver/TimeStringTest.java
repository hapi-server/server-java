
package org.hapiserver;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class TimeStringTest {
    
    public TimeStringTest() {
    }


    /**
     * Test of toComponents method, of class TimeString.
     */
    @Test
    public void testToComponents() {
        System.out.println("toComponents");
        TimeString r= new TimeString("2043-04-05T23:13:02.123456789");
        int[] expResult = new int[] { 2043, 4, 5, 23, 13, 2, 123456789 };
        int[] result = r.toComponents();
        assertArrayEquals(expResult, result);
        
    }

    
}
