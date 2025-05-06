
package org.hapiserver.source.tilde.geonet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author jbf
 */
public class DartCatalog {
    
    static String data="Station code	Location	Type	Longitude	Latitude	Start	End\n" +
"NZA	40	DART bottom pressure recorder	176.9109	-42.3707	2019-12-22T00:00:00Z	2021-12-17T23:59:59Z\n" +
"NZA	41	DART bottom pressure recorder	176.9093	-42.3717	2021-12-18T00:00:00Z	2023-12-15T00:00:00Z\n" +
"NZA	42	DART bottom pressure recorder	176.9120	-42.3690	2023-12-16T00:15:00Z	\n" +
"NZB	40	DART bottom pressure recorder	179.0996	-40.6003	2020-09-18T21:12:30Z	2022-08-14T22:59:45Z\n" +
"NZB	41	DART bottom pressure recorder	179.0962	-40.5992	2022-08-15T20:15:00Z	2023-12-06T00:00:00Z\n" +
"NZB	42	DART bottom pressure recorder	179.1005	-40.5979	2023-12-07T14:15:00Z	\n" +
"NZC	40	DART bottom pressure recorder	-179.7978	-38.2001	2019-12-13T00:00:00Z	2021-12-10T23:59:59Z\n" +
"NZC	41	DART bottom pressure recorder	-179.7978	-38.2004	2021-12-11T00:00:00Z	2023-12-08T00:00:00Z\n" +
"NZC	42	DART bottom pressure recorder	-179.7968	-38.1969	2023-12-09T01:15:00Z	\n" +
"NZD	40	DART bottom pressure recorder	178.6037	-36.0998	2021-07-23T00:00:00Z	2023-06-11T18:00:00Z\n" +
"NZD	41	DART bottom pressure recorder	178.6009	-36.1000	2023-06-13T21:15:00Z	\n" +
"NZE	40	DART bottom pressure recorder	-177.7080	-36.0493	2019-12-19T00:00:00Z	2021-12-11T23:59:59Z\n" +
"NZE	41	DART bottom pressure recorder	-177.6970	-36.0499	2021-12-14T00:00:00Z	2023-12-09T23:00:00Z\n" +
"NZE	42	DART bottom pressure recorder	-177.6986	-36.0500	2023-12-12T20:15:00Z	\n" +
"NZF	41	DART bottom pressure recorder	-175.0124	-29.6823	2020-08-31T23:10:15Z	2022-08-10T00:59:45Z\n" +
"NZF	42	DART bottom pressure recorder	-175.0125	-29.6826	2022-08-11T00:15:00Z	2024-07-02T22:00:00Z\n" +
"NZF	43	DART bottom pressure recorder	-175.0129	-29.6827	2024-07-04T00:00:00Z	\n" +
"NZG	40	DART bottom pressure recorder	-173.4012	-23.3516	2020-09-10T22:57:30Z	2022-07-29T18:02:45Z\n" +
"NZG	41	DART bottom pressure recorder	-173.4018	-23.3517	2022-07-30T20:15:00Z	2024-06-22T00:00:00Z\n" +
"NZG	42	DART bottom pressure recorder	-173.4034	-23.3509	2024-06-23T05:15:00Z	\n" +
"NZH	40	DART bottom pressure recorder	-171.8599	-20.0896	2020-09-03T22:24:30Z	2022-08-01T00:12:30Z\n" +
"NZH	41	DART bottom pressure recorder	-171.8630	-20.0885	2022-08-01T20:15:00Z	2024-06-24T22:00:00Z\n" +
"NZH	42	DART bottom pressure recorder	-171.8605	-20.0900	2024-06-25T21:37:00Z	\n" +
"NZI	40	DART bottom pressure recorder	-171.1904	-16.8921	2020-09-08T03:15:45Z	2022-08-02T21:59:30Z\n" +
"NZI	41	DART bottom pressure recorder	-171.1905	-16.8890	2022-08-03T21:15:00Z	2024-06-27T22:00:00Z\n" +
"NZI	42	DART bottom pressure recorder	-171.1893	-16.8913	2024-06-28T21:29:00Z	\n" +
"NZJ	40	DART bottom pressure recorder	163.9549	-26.6672	2021-07-09T00:00:00Z	2023-05-28T18:00:00Z\n" +
"NZJ	41	DART bottom pressure recorder	163.9536	-26.6685	2023-05-29T22:15:00Z	\n" +
"NZK	40	DART bottom pressure recorder	169.4988	-24.3093	2021-07-15T00:00:00Z	2023-06-06T00:00:00Z\n" +
"NZK	41	DART bottom pressure recorder	169.5001	-24.3086	2023-06-06T21:00:00Z	\n" +
"NZL	40	DART bottom pressure recorder	166.7820	-19.3096	2021-07-12T00:00:00Z	2023-06-03T00:00:00Z\n" +
"NZL	41	DART bottom pressure recorder	166.8110	-19.2876	2023-06-03T20:15:00Z	";
    
    private static class Station {
        String id;
        String start;
        String stop;
        String lat;
        String lon;
    }
    
    private static final Map<String,Station> stations;
    
    static {
        stations= new LinkedHashMap<>();
        String[] lines= data.split("\n");
        for (String line : lines) {
            if ( line.startsWith("NZ") ) {
                String[] fields= line.split("\\s+");
                Station e= new Station();
                e.id= fields[0] + "_" + fields[1];
                int nstart= fields.length;
                if ( nstart==10 ) {
                    e.start= fields[nstart-2];
                    e.stop= fields[nstart-1];
                    e.lat= fields[nstart-3];
                    e.lon= fields[nstart-4];
                } else {
                    e.start= fields[nstart-1];
                    e.stop= "lasthour";
                    e.lat= fields[nstart-2];
                    e.lon= fields[nstart-3];
                }
                
                stations.put( e.id, e );
            }
        }
    }
    
    static String infoTemplate= "{\n" +
"    \"startDate\": \"%s\",\n" +
"    \"stopDate\": \"%s\",\n" +
"    \"cadence\": \"PT15S\",\n" +
"    \"x_latitude\": %s,\n" +
"    \"x_longitude\": %s,\n" +
"    \"parameters\": [\n" +
"        {\n" +
"            \"name\": \"Time\",\n" +
"            \"type\": \"isotime\",\n" +
"            \"units\": \"UTC\",\n" +
"            \"length\": 20,\n" +
"            \"fill\": null\n" +
"        },\n" +
"        {\n" +
"            \"name\": \"water-height\",\n" +
"            \"type\": \"double\",\n" +
"            \"fill\": null\n" +
"        }\n" +
"    ],\n" +
"    \"x_info_caching\": false\n" +
"}\n" +
"";
    public static String getCatalog() {
        StringBuilder b= new StringBuilder();
        b.append("{\n");
        b.append("   \"catalog\": [\n");
        for ( Station e : stations.values() ) {
            b.append("      {\n");
            b.append("         \"id\":"+"\"").append(e.id).append("\"\n");
            b.append("      },\n");
        }
        // remove the extra comma.
        b.delete( b.length()-3, b.length() );
        b.append("}\n");
        
        b.append("    ]\n");
        b.append("\n}");
        return b.toString();
    }
    
    public static String getInfo(String id) {
        Station e= stations.get(id);
        if ( e==null ) throw new IllegalArgumentException("no such station:" +id);
        return String.format( infoTemplate, e.start, e.stop, e.lat, e.lon );
    }
}
