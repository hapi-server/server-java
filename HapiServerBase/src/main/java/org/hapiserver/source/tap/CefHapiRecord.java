package org.hapiserver.source.tap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hapiserver.AbstractHapiRecord;

/**
 *
 * @author jbf
 */
public class CefHapiRecord extends AbstractHapiRecord {

    List<List<Integer>> columnIndices = new ArrayList<>();
    List<String> ffields = new ArrayList<>();
    Map<Integer, String[]> vfields = new HashMap<>();
    String[] fields;

    public CefHapiRecord(List<List<Integer>> columnIndices, List<String> ffields, Map<Integer, String[]> vfields, String[] fields) {
        this.columnIndices = columnIndices;
        this.ffields = ffields;
        this.vfields = vfields;
        this.fields = fields;
    }

    @Override
    public int length() {
        return columnIndices.size();
    }

    @Override
    public String getIsoTime(int i) {
        int idx = columnIndices.get(i).get(0);
        String field = fields[idx].trim();
        if (field.length() > 45) { //TODO: kludge for time ranges.  See https://github.com/hapi-server/server-java/issues/22
            int is1 = field.indexOf("/");
            if (is1 > 0) {
                switch (i) {
                    case 0:
                        field = field.substring(0, is1);
                        if (!field.endsWith("Z")) {
                            field = field + "Z";
                        }   break;
                    case 1:
                        field = field.substring(is1 + 1).substring(0, 24);
                        if (!field.endsWith("Z")) {
                            field = field + "Z";
                        }   break;
                    default:
                        throw new IllegalArgumentException("time ranges only supported for first two fields");
                }
            }
        }
        return field;
    }

    @Override
    public String getString(int i) {
        return getAsString(i);
    }

    @Override
    public int getInteger(int i) {
        return Integer.parseInt(getAsString(i));
    }

    @Override
    public int[] getIntegerArray(int i) {
        String[] stringArray = getStringArray(i);
        int[] intArray = new int[stringArray.length];
        for (int iField = 0; iField < stringArray.length; iField++) {
            intArray[iField] = Integer.parseInt(stringArray[iField]);
        }
        return intArray;
    }

    @Override
    public double[] getDoubleArray(int i) {
        String[] stringArray = getStringArray(i);
        double[] doubleArray = new double[stringArray.length];
        for (int iField = 0; iField < stringArray.length; iField++) {
            doubleArray[iField] = Double.parseDouble(stringArray[iField]);
        }
        return doubleArray;

    }

    @Override
    public String[] getStringArray(int i) {
        List<Integer> indices = columnIndices.get(i);
        int firstIndex = indices.get(0);
        if (firstIndex < 0) {
            return vfields.get(firstIndex);
        } else {
            String[] vector = new String[indices.size()];
            int lastIndex = firstIndex + indices.size();
            for (int iField = firstIndex; iField < lastIndex; iField++) {
                vector[iField - firstIndex] = fields[iField].trim();
            }
            return vector;
        }
    }

    @Override
    public double getDouble(int i) {
        return Double.parseDouble(getAsString(i));
    }

    @Override
    public String getAsString(int i) {
        int idx = columnIndices.get(i).get(0);
        if (idx < 0) {
            return vfields.get(idx)[0];
        } else {
            String s = fields[idx].trim();
            if (s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1);
            }
            return s;
        }
    }

    @Override
    public String toString() {
        return getAsString(0) + " " + length() + " fields";
    }

}
