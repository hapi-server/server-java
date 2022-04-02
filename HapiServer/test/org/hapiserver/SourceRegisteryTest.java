
package org.hapiserver;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class SourceRegisteryTest {
    
    public SourceRegisteryTest() {
    }

    /**
     * Test of getInstance method, of class SourceRegistery.
     */
    @Test
    public void testGetInstance() {
        System.out.println("getInstance");
        SourceRegistery expResult = null;
        SourceRegistery result = SourceRegistery.getInstance();
        assertNotEquals(expResult, result);
    }

    /**
     * Test of getSource method, of class SourceRegistery.
     */
    @Test
    public void testGetSource() {
        System.out.println("getSource");
        String id = "28.FF6319A21705";
        SourceRegistery instance = new SourceRegistery();
        HapiRecordSource expResult = null;
        HapiRecordSource result = instance.getSource(id);
        assertNotEquals(expResult, result);
        result = instance.getSource("not_a_source");
        assertEquals(expResult, result);
    }
    
}
