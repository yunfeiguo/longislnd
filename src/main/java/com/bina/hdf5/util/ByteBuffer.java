package com.bina.hdf5.util;

/**
 * Created by bayo on 5/5/15.
 */

import java.util.Arrays;

public class ByteBuffer {
    private byte[] data_;
    private int size_;


    public ByteBuffer() {
        this(64);
    }

    public ByteBuffer(int reserve) {
        data_ = new byte[Math.abs(reserve) + 1];
        size_ = 0;
    }

    public void addLast(byte b) {
        if (size_ >= data_.length) {
            reserve(size_ * 2 + 1000);
        }
        data_[size_] = b;
        ++size_;
    }

    public void addLast(ByteBuffer other) {
        final int newSize = size_ + other.size();
        if (newSize >= data_.length) {
            reserve(newSize * 2 + 1000);
        }
        // there's probably something like std::copy in java?
        for (int ii = 0; ii < other.size(); ++ii, ++size_) {
            data_[size_] = other.data()[ii];
        }
    }

    public void reserve(int new_size) {
        if (new_size > data_.length) {
            byte[] new_data = Arrays.copyOf(data_, new_size);
            data_ = new_data;
        }
    }

    public byte[] data() {
        return data_;
    }

    public int size() {
        return size_;
    }

    public void clear() {
        size_ = 0;
    }
}