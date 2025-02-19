/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit4TestClass.java to edit this template
 */
package org.hapiserver.source;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.stream.IntStream;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.HapiRecord;
import org.junit.Test;
import static org.junit.Assert.*;
import org.w3c.dom.Document;

/**
 *
 * @author jbf
 */
public class SourceUtilTest {
    
    public SourceUtilTest() {
    }
    

    /**
     * Test of getFileLines method, of class SourceUtil.
     */
    @Test
    public void testGetFileLines_File() throws Exception {
        System.out.println("getFileLines");
         
        File f = new File( SourceUtilTest.class.getResource( "/org/hapiserver/source/testjson.json").getFile() );
        Iterator<String> result = SourceUtil.getFileLines(f);
        int count=0;
        while ( result.hasNext() ) {
            count++;
            result.next();
        }
        assertEquals( 7, count );
        
    }

    /**
     * Test of getFileLines method, of class SourceUtil.
     */
    @Test
    public void testGetFileLines_URL() throws Exception {
        System.out.println("getFileLines");
        URL url = SourceUtilTest.class.getResource( "/org/hapiserver/source/testjson.json");
        Iterator<String> result = SourceUtil.getFileLines(url);
        int count=0;
        while ( result.hasNext() ) {
            count++;
            result.next();
        }
        assertEquals( 7, count );
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of getAllFileLines method, of class SourceUtil.
     */
    @Test
    public void testGetAllFileLines() throws Exception {
        System.out.println("getAllFileLines");
        URL url =  SourceUtilTest.class.getResource( "/org/hapiserver/source/testjson.json");
        String expResult = "{\n" +
"    \"name\": \"Zach\",\n" +
"    \"age\": 150,\n" +
"    \"address\": {\n" +
"        \"city\":\"Iowa City\",\n" +
"        \"state\":\"Iowa\",\n" +
"        \"country\":\"USA\"\n" +
"    }\n" +
"}\n" +
"";
        String result = SourceUtil.getAllFileLines(url);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
    }

    /**
     * Test of getEmptyHapiRecordIterator method, of class SourceUtil.
     */
    @Test
    public void testGetEmptyHapiRecordIterator() {
        System.out.println("getEmptyHapiRecordIterator");
        Iterator<HapiRecord> result = SourceUtil.getEmptyHapiRecordIterator();
        assertEquals( false, result.hasNext() );
        
    }

    /**
     * Test of getGranuleIterator method, of class SourceUtil.
     */
    @Test
    public void testGetGranuleIterator() {
        System.out.println("getGranuleIterator");
        int[] start = new int[] { 2024,2,1,0,0,0,0 };
        int[] stop = new int[] { 2024,2,10,0,0,0,0 };
        int digit = 2; // count by days
        int count = 0;
        Iterator result = SourceUtil.getGranuleIterator(start, stop, digit);
        while ( result.hasNext() ) {
            count++;
            result.next();
        }
        assertEquals( 10, count );

    }

    /**
     * Test of guardedSplit method, of class SourceUtil.
     */
    @Test
    public void testGuardedSplit() {
        System.out.println("guardedSplit");
        String s = "2022-02-02T02:02:02,\"thruster,mode2,on\",2";
        char delim = ',';
        char exclude1 = '\"';
        String[] expResult = new String[] { "2022-02-02T02:02:02", "\"thruster,mode2,on\"", "2" };
        String[] result = SourceUtil.guardedSplit(s, delim, exclude1);
        assertArrayEquals(expResult, result);
    }

    /**
     * Test of stringSplit method, of class SourceUtil.
     */
    @Test
    public void testStringSplit() {
        System.out.println("stringSplit");
        String s = "C3_PP_CIS,\"Proton and ion densities, bulk velocities and temperatures, spin resolution\"";
        String[] expResult = new String[] { "C3_PP_CIS","Proton and ion densities, bulk velocities and temperatures, spin resolution" };
        String[] result = SourceUtil.stringSplit(s);
        assertArrayEquals(expResult, result);
        
    }

    /**
     * Test of readDocument method, of class SourceUtil.
     */
    @Test
    public void testReadDocument_URL() throws Exception {
        System.out.println("readDocument");
        URL url = new URL( "https://raw.githubusercontent.com/hapi-server/server-java/refs/heads/main/HapiServerBase/nbproject/project.xml" );
        Document result = SourceUtil.readDocument(url);
        assertEquals( 1, result.getChildNodes().getLength() );
    }

    /**
     * Test of getInputStream method, of class SourceUtil.
     */
    @Test
    public void testGetInputStream() throws Exception {
        System.out.println("getInputStream");
        URL url = new URL( "https://cottagesystems.com/server/ct/hapi/about");
        int ageSeconds = 0;
        InputStream result = SourceUtil.getInputStream(url, ageSeconds);
        assertNotNull(result);
        result.close();
        
    }

    /**
     * Test of readDocument method, of class SourceUtil.
     */
    @Test
    public void testReadDocument_URL_int() throws Exception {
        System.out.println("readDocument");
        URL url =  new URL( "https://raw.githubusercontent.com/hapi-server/server-java/refs/heads/main/HapiServerBase/nbproject/project.xml" );
        int ageSeconds = 0;
        Document result = SourceUtil.readDocument(url, ageSeconds);
        assertNotNull(result);

    }

    /**
     * Test of readDocument method, of class SourceUtil.
     */
    @Test
    public void testReadDocument_String() throws Exception {
        System.out.println("readDocument");
        String src = "<note>\n" +
"<to>Tove</to>\n" +
"<from>Jani</from>\n" +
"<heading>Reminder</heading>\n" +
"<body>Don't forget me this weekend!</body>\n" +
"</note>";
        Document result = SourceUtil.readDocument(src);
        assertNotNull(result);
        
    }

    /**
     * Test of readJSONObject method, of class SourceUtil.
     */
    @Test
    public void testReadJSONObject() throws Exception {
        System.out.println("readJSONObject");
        URL url = SourceUtilTest.class.getResource( "/org/hapiserver/source/testjson.json");
        //new URL( "https://cottagesystems.com/server/ct/hapi/about");
        JSONObject result = SourceUtil.readJSONObject(url);
        assertEquals(3, result.length());
        
    }

    /**
     * Test of downloadFile method, of class SourceUtil.
     */
    @Test
    public void testDownloadFile() throws Exception {
        System.out.println("downloadFile");
        URL url = new URL( "https://cottagesystems.com/server/ct/hapi/about");
        File file = File.createTempFile( "SourceUtilTest", ".json");
        File result = SourceUtil.downloadFile(url, file);
        assertEquals( 436, result.length() );
        assertEquals( result, file );
        
    }
    
}
