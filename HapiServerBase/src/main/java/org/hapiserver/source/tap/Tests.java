/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.hapiserver.source.tap;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import org.hapiserver.HapiRecord;
import org.hapiserver.TimeString;
import org.hapiserver.TimeUtil;
import org.hapiserver.source.SourceUtil;

/**
 *
 * @author jbf
 */
public class Tests {

    public static void mainCase1( String[] args ) {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id= "CL_SP_WHI";
        TimeString start= new TimeString( new int[] { 2012, 12, 25, 0, 0, 0, 0 } );
        TimeString stop= new TimeString( new int[] { 2012, 12, 26, 0, 0, 0, 0 } );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(start, stop);
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
    
    public static void mainCase2( String[] args ) {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id= "D1_CG_STA-DWP_COMBI_PNG";
        TimeString  start= new TimeString( new int[] { 2012, 12, 25, 0, 0, 0, 0 } );
        TimeString  stop= new TimeString( new int[] { 2012, 12, 26, 0, 0, 0, 0 } );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(start, stop);
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
    
    public static void mainCase3( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="CM_CG_WBD_OVERVIEW_500_19_PNG";
        String tr= "2023-01-18T17:00/18:00";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        TimeString  start= new TimeString( Arrays.copyOfRange( timerange, 0, 7 ) );
        TimeString  stop= new TimeString( Arrays.copyOfRange( timerange, 7, 14 ) );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(start, stop);
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
    
    public static void mainCase4( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="C4_CP_CIS-CODIF_HS_O1_PEF";
        String tr= "2021-12-01T00:00/00:02";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        TimeString start= new TimeString( Arrays.copyOfRange( timerange, 0, 7 ) );
        TimeString stop= new TimeString( Arrays.copyOfRange( timerange, 7, 14 ) );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(start, stop);
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
    
    public static void mainCase5( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="C1_CP_PEA_3DRH_PSD";
        String tr= "2019-08-01T00:00/0:10";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        int[] start= Arrays.copyOfRange( timerange, 0, 7 );
        int[] stop= Arrays.copyOfRange( timerange, 7, 14 );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(new TimeString( start), new TimeString( stop));
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }

    /**
     * This returns 769 fields while the info thinks it should be 897 (128 more).
     * @see https://github.com/hapi-server/server-java/issues/21
     * @param args
     * @throws ParseException 
     */
    public static void mainCase6( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="C4_CP_STA_CS_NBR";
        String tr= "2022-07-31T11:00Z/2022-08-01T00:00Z";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        int[] start= Arrays.copyOfRange( timerange, 0, 7 );
        int[] stop= Arrays.copyOfRange( timerange, 7, 14 );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator( new TimeString(start), new TimeString( stop ));
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }
        
/**
     * This returns 769 fields while the info thinks it should be 897 (128 more).
     * @see https://github.com/hapi-server/server-java/issues/21
     * @param args
     * @throws ParseException 
     */
    public static void mainCase7( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="C1_PP_EDI";
        String tr= "2018-10-24T18:59:56Z/2018-10-25T00:00:04Z";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        int[] start= Arrays.copyOfRange( timerange, 0, 7 );
        int[] stop= Arrays.copyOfRange( timerange, 7, 14 );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(new TimeString( start), new TimeString( stop));
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }    
        
/**
     * This returns 769 fields while the info thinks it should be 897 (128 more).
     * @see https://github.com/hapi-server/server-java/issues/21
     * @param args
     * @throws ParseException 
     */
    public static void mainCase8( String[] args ) throws ParseException {
        String tapServerURL="https://csa.esac.esa.int/csa-sl-tap/";
        String id="C1_PP_WHI";
        String tr= "2012-12-15T20:00Z/2012-12-15T20:07Z";
        int[] timerange = TimeUtil.parseISO8601TimeRange(tr);
        int[] start= Arrays.copyOfRange( timerange, 0, 7 );
        int[] stop= Arrays.copyOfRange( timerange, 7, 14 );
        Iterator<HapiRecord> iter= new TAPDataSource(tapServerURL, id).getIterator(new TimeString( start), new TimeString( stop));
        while ( iter.hasNext() ) {
            HapiRecord r= iter.next();
            System.err.println( String.format( "%s %d fields", r.getIsoTime(0), r.length() ) );
        }
    }    
    
    public static void main(String[] args ) throws Exception {
        //mainCase1(args);
        //mainCase2(args);
        //mainCase3(args);
        //mainCase4(args);
        //mainCase5(args);
        //mainCase6(args);
        //mainCase7(args);
        mainCase8(args);
    }    
}
