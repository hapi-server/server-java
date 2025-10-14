package org.hapiserver.source.cdaweb;

/**
 * Generic way to access heterogeneous data as HAPI data, and also
 * to provide some virtual variables.
 * @author jbf
 */
public abstract class Adapter {

    /**
     * one of these methods will be implemented by the adapter.
     */

    public String adaptString(int index) {
        throw new IllegalArgumentException("incorrect adapter used for string");
    }

    public double adaptDouble(int index) {
        throw new IllegalArgumentException("incorrect adapter used for double");
    }

    public int adaptInteger(int index) {
        throw new IllegalArgumentException("incorrect adapter used for integer");
    }

    public double[] adaptDoubleArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used for double array");
    }

    public int[] adaptIntegerArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used for integer array");
    }

    public String[] adaptStringArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used for string array");
    }

    public abstract String getString(int index);

}
