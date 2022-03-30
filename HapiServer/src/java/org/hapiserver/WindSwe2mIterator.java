
package org.hapiserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.nashorn.api.scripting.URLReader;

/**
 * Bootstrap method for getting server going.
 * (i4.4," ", f11.6," ", i4.2,18(" ",f7.1),2(" ",f9.3),14(" ",f7.1),2(" ",f9.3)," ",f6.1,7(" ",f7.1)," ",f9.3,10(" ",f9.3),2(" ", f11.6))
 *
 * @author jbf
 */
public class WindSwe2mIterator implements Iterator<HapiRecord> {

    private static final Logger logger= Util.getLogger();
    
    int currentYear;
    int stopYear;
    
    URL currentUrl;
    BufferedReader readerCurrentYear;
    
    String nextRecord=null;
    
    public WindSwe2mIterator( int[] startTime, int[] stopTime ) {
        if ( stopTime[1]==1 && stopTime[2]==1 && stopTime[3]==0 && stopTime[4]==0 && stopTime[5]==0 && stopTime[6]==0 ) {
            stopYear= stopTime[0]+1;
        } else {
            stopYear= stopTime[0];
        }
        currentYear= startTime[0];
        
        try {
            currentUrl= new URL( String.format( "file:/home/jbf/ct/data.backup/2022/wind_swe_2m/wind_swe_2m_sw%4d.asc",
                currentYear ) );
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
        readerCurrentYear= new BufferedReader( new URLReader( currentUrl ) );
        
        readNextRecord();
        
        if ( nextRecord==null ) {
            try {
                readerCurrentYear.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    private void readNextRecord() {
        try {
            while ( nextRecord==null && currentYear<=stopYear) {
                nextRecord= readerCurrentYear.readLine();
                if ( nextRecord==null ) {
                    readerCurrentYear.close();
                    currentYear++;
                    if ( currentYear<=stopYear ) {
                        currentUrl= new URL( String.format( "file:/home/jbf/ct/data.backup/2022/wind_swe_2m/wind_swe_2m_sw%4d.asc",
                            currentYear ) );
                        readerCurrentYear= new BufferedReader( new URLReader( currentUrl ) );
                        nextRecord= readerCurrentYear.readLine();
                    }
                }
            }
        } catch ( IOException ex ) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public boolean hasNext() {
        return nextRecord!=null;
    }

    @Override
    public HapiRecord next() {
        
        final String localNextRecord= nextRecord;
        
        readNextRecord();
        
        return new HapiRecord() {
            @Override
            public String getIsoTime(int i) {
                if ( i==0 ) {
                    final int year= Integer.parseInt( localNextRecord.substring(0,4) );
                    double fdoy= Double.parseDouble( localNextRecord.substring(5,16) );
                    final int doy= (int)fdoy;
                    double fsec= ( fdoy - doy ) * 86400;
                    final int hr= (int)( fsec / 3600. );
                    fsec= fsec - hr*3600;
                    final int mn= (int)( fsec / 60 );
                    fsec= fsec - mn*60;
                    final double ffsec= fsec;
                    return String.format( "%04d-%03dT%02d:%02d:%9.6fZ", year, doy, hr, mn, ffsec );
                } else {
                    throw new IllegalArgumentException("implementation error");
                }
            }

            @Override
            public String[] getIsoTimeArray(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public String getString(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public String[] getStringArray(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public double getDouble(int i) {
                if ( i==1 ) {
                    // it's field 12
                    return Double.parseDouble( localNextRecord.substring(85,93) );
                } else {
                    throw new IllegalArgumentException("implementation error");
                }
            }

            @Override
            public double[] getDoubleArray(int i) {
                if ( i==2 ) { // it's field 54,55,56
                    double d1= Double.parseDouble(localNextRecord.substring(440,449) );
                    double d2= Double.parseDouble(localNextRecord.substring(450,459) );
                    double d3= Double.parseDouble(localNextRecord.substring(460,469) );
                    return new double[] { d1, d2, d3 };
                } else {
                    throw new IllegalArgumentException("implementation error");
                }
                
            }

            @Override
            public int getInteger(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public int[] getIntegerArray(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public String getAsString(int i) {
                throw new IllegalArgumentException("implementation error");
            }

            @Override
            public int length() {
                return 3;
            }
        };
    }
    
}
