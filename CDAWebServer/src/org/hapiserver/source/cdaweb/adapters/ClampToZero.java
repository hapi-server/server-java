/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.hapiserver.source.cdaweb.adapters;

import org.hapiserver.source.cdaweb.Adapter;

/**
 *
 * @author jbf
 */
public class ClampToZero extends Adapter {

    private final Adapter param;
    private final double amount;
    private double[] work;

    public ClampToZero( Adapter param, double amount ) {
        this.param= param;
        this.amount= amount;    
    }
    
    @Override
    public String getString(int index) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public double adaptDouble(int index) {
        double d= param.adaptDouble(index);
        if ( d<amount ) {
            return 0.;
        } else {
            return amount;
        }
    }

    @Override
    public double[] adaptDoubleArray(int index) {
        double[] dd= param.adaptDoubleArray(index);
        if ( this.work==null ) {
            this.work= new double[dd.length];
        }
        for ( int i=0; i<dd.length; i++ ) {
            double d= dd[i];
            if ( d<amount ) {
                this.work[i]= 0.;
            } else {
                this.work[i]= d;
            }
        }
        return work;
    }
    
}
