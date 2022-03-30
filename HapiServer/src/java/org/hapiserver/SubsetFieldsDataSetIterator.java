
package org.hapiserver;

import java.util.Iterator;

/**
 *
 * @author jbf
 */
public class SubsetFieldsDataSetIterator implements Iterator<HapiRecord> {

    Iterator<HapiRecord> iter;
    int[] fieldMap;
    
    public SubsetFieldsDataSetIterator( Iterator<HapiRecord> iter, int[] fieldMap ) {
        this.iter= iter;
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public HapiRecord next() {
        return new SubsetFieldsHapiRecord( iter.next() );
    }

    private class SubsetFieldsHapiRecord implements HapiRecord {
        
        HapiRecord next;
        
        public SubsetFieldsHapiRecord( HapiRecord next ) {
            this.next= next;
        }

        @Override
        public String getIsoTime(int i) {
            return next.getIsoTime( fieldMap[i] );
        }

        @Override
        public String[] getIsoTimeArray(int i) {
            return next.getIsoTimeArray(fieldMap[i] );
        }

        @Override
        public String getString(int i) {
            return next.getString(fieldMap[i] );
        }

        @Override
        public String[] getStringArray(int i) {
            return next.getStringArray( fieldMap[i] );
        }

        @Override
        public double getDouble(int i) {
            return next.getDouble( fieldMap[i] );
        }

        @Override
        public double[] getDoubleArray(int i) {
            return next.getDoubleArray( fieldMap[i] );
        }

        @Override
        public int getInteger(int i) {
            return next.getInteger( fieldMap[i] );
        }

        @Override
        public int[] getIntegerArray(int i) {
            return next.getIntegerArray( fieldMap[i] );
        }

        @Override
        public String getAsString(int i) {
            return next.getAsString( fieldMap[i] );
        }

        @Override
        public int length() {
            return fieldMap.length;
        }
    }
}
