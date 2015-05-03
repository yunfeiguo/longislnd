package com.bina.hdf5;

/**
 * Created by bayo on 5/2/15.
 */
import ncsa.hdf.object.h5.H5File;
public class CmpH5AlnData {

    public CmpH5AlnData(H5File h5, String path){ load(h5,path); }

    //these might not be byte for other fields
    // still trying to figure out how to do associate class type with enum then cast and generic
    public byte[] get(EnumDat f) throws Exception{
        if( null == data_[f.value()] ){
            data_[f.value()] = H5ScalarDSReader.<byte[]>Read(h5_,path_+f.path());
        }
        return (byte[])data_[f.value()];
    }

    public void load(H5File h5, String path){
        h5_ = h5;
        path_ = path;
        data_ = new Object[EnumDat.num_fields.value()];
    }

    private H5File h5_ = null;
    private String path_ = null;
    private Object[] data_ = null; // they don't have to be in byte, there are int and short
}
