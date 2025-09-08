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
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public double adaptDouble(int index) {
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public int adaptInteger(int index) {
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public double[] adaptDoubleArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public int[] adaptIntegerArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public String[] adaptStringArray(int index) {
        throw new IllegalArgumentException("incorrect adapter used");
    }

    public abstract String getString(int index);

}
