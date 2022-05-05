/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hapiserver.exceptions.HapiException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class HapiServerSupportTest {
    
    public HapiServerSupportTest() {
    }

    /**
     * Test of getCatalog method, of class HapiServerSupport.
     */
    @Test
    public void testGetCatalog() throws Exception {
        System.out.println("getCatalog");
        String HAPI_HOME = "/tmp/jbf-hapi-server/";
        JSONObject result = HapiServerSupport.getCatalog(HAPI_HOME);
        assertTrue( result.getJSONArray("catalog").length()>0 );
    }

    /**
     * Test of getInfo method, of class HapiServerSupport.
     * @throws java.lang.Exception
     */
    @Test
    public void testGetInfo() throws Exception, IOException, JSONException, HapiException {
        System.out.println("getInfo");
        String HAPI_HOME = "/tmp/jbf-hapi-server/";
        String id = "icconditions";
        JSONObject result = HapiServerSupport.getInfo(HAPI_HOME, id);
        assertTrue( result!=null );
    }

    /**
     * Test of splitParams method, of class HapiServerSupport.
     */
    @Test
    public void testSplitParams() {
        try {
            System.out.println("splitParams");
            String HAPI_HOME = "/tmp/jbf-hapi-server/";
            String id = "icconditions";
            JSONObject info = HapiServerSupport.getInfo(HAPI_HOME, id);
            String[] expResult = new String[] { "Time", "Temperature", "Windspeed" };
            String[] result = HapiServerSupport.splitParams(info, "Temperature,Windspeed");
            assertArrayEquals(expResult, result);
            // TODO review the generated test code and remove the default call to fail.
        } catch (IOException | JSONException | HapiException ex ) {
            fail(ex.getMessage());
        }            
    }

    /**
     * Test of joinParams method, of class HapiServerSupport.
     */
    @Test
    public void testJoinParams() {
        try {
            System.out.println("joinParams");
            String HAPI_HOME = "/tmp/jbf-hapi-server/";
            String id = "icconditions";
            JSONObject info = HapiServerSupport.getInfo(HAPI_HOME, id);
            String[] params = "Temperature,WindSpeed".split(",");
            String expResult = "Time,Temperature,WindSpeed";
            String result = HapiServerSupport.joinParams(info,params);
            assertEquals(expResult, result);
            // TODO review the generated test code and remove the default call to fail.
        } catch (IOException | JSONException | HapiException ex) {
            fail(ex.getMessage());
        }
    }
    
}
