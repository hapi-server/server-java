/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cottagesystems.hapiext;

import java.util.Iterator;
import org.hapiserver.HapiRecord;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class SimpleRecordSourceTest {
    
    public SimpleRecordSourceTest() {
    }

    /**
     * Test of hasGranuleIterator method, of class SimpleRecordSource.
     */
    @Test
    public void testHasGranuleIterator() {
        System.out.println("hasGranuleIterator");
        SimpleRecordSource instance = new SimpleRecordSource();
        boolean expResult = false;
        boolean result = instance.hasGranuleIterator();
        assertEquals(expResult, result);
    }

    /**
     * Test of getGranuleIterator method, of class SimpleRecordSource.
     */
    @Test
    public void testGetGranuleIterator() {
        System.out.println("getGranuleIterator");
    }

    /**
     * Test of hasParamSubsetIterator method, of class SimpleRecordSource.
     */
    @Test
    public void testHasParamSubsetIterator() {
        System.out.println("hasParamSubsetIterator");
    }

    /**
     * Test of getIterator method, of class SimpleRecordSource.
     */
    @Test
    public void testGetIterator_3args() {
        System.out.println("getIterator");
    }

    /**
     * Test of getIterator method, of class SimpleRecordSource.
     */
    @Test
    public void testGetIterator_intArr_intArr() {
        System.out.println("getIterator");
        int[] start = new int[] { 2022, 1, 1, 0, 0, 45, 0 };
        int[] stop = new int[] { 2022, 1, 1, 1, 0, 0, 0 };
        SimpleRecordSource instance = new SimpleRecordSource();
        Iterator<HapiRecord> result = instance.getIterator(start, stop);
        int count= 240;
        HapiRecord rec;
        while ( result.hasNext() ) {
            rec= result.next();
            System.err.println( rec );
            count--;
        }
        assertEquals(count,0);
    }

    /**
     * Test of getTimeStamp method, of class SimpleRecordSource.
     */
    @Test
    public void testGetTimeStamp() {
        System.out.println("getTimeStamp");
        int[] start = new int[] { 2022, 1, 1, 0, 0, 0, 0 };
        int[] stop = new int[] { 2022, 1, 1, 1, 0, 0, 0 };
        SimpleRecordSource instance = new SimpleRecordSource();
        String expResult = null;
        String result = instance.getTimeStamp(start, stop);
        assertEquals(expResult, result);
    }
    
}
