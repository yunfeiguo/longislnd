package com.bina.lrsim.pb;

/**
 * Created by bayo on 5/4/15.
 */


import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class PBReadBuffer {
  private static final int INITIAL_SIZE = 1000;
  private final static Logger log = Logger.getLogger(PBReadBuffer.class.getName());
  // util.ByteBuffer can save a full copy operation everytime a byte[] is extracted
  private final Map<EnumDat, ByteArrayOutputStream> data = new EnumMap<>(EnumDat.class);
  private final Spec spec;

  public PBReadBuffer(Spec spec) {
    this(spec,INITIAL_SIZE);
  }

  public PBReadBuffer(Spec spec, int reserveSize) {
    this.spec = spec;
    for (EnumDat e : spec.getDataSet()) {
      data.put(e, new ByteArrayOutputStream(reserveSize));
    }
    reserve(reserveSize);
  }

  public int size() {
    return data.get(EnumDat.BaseCall).size();
  }

  public void reserve(int size) {
    // ByteArrayOutputStream's ensureCapacity is private, oh well
    /*
     * for (EnumDat e : EnumDat.getBaxSet()) { data.get(e).reserve(size); }
     */
  }


  // should "templatize" when have time

  public void clear() {
    for (EnumDat e : spec.getDataSet()) {
      data.get(e).reset();
    }
  }

  public void addASCIIBases(byte[] asciiSeq, byte[] defaultSeq, byte[] defaultScores) {
    for (EnumDat e : spec.getDataSet()) {
      final byte[] buffer;
      if (e == EnumDat.BaseCall) {
        buffer = asciiSeq;
      } else if (e.isScore) {
        buffer = defaultScores;
      }
      else {
        buffer = defaultSeq;
      }
      data.get(e).write(buffer, 0, asciiSeq.length);
    }
  }

  public void addLast(BaseCalls other) {
    for (EnumDat e : spec.getDataSet()) {
      for (int pp = 0; pp < other.size(); ++pp) {
        data.get(e).write(other.get(pp, e));
      }
    }
  }

  public void addLast(PBReadBuffer other) {
    for (EnumDat e : spec.getDataSet()) {
      final byte[] tmp = other.get(e).toByteArray();
      data.get(e).write(tmp, 0, tmp.length); // the (byte[]) version throws IO exception
    }
  }

  public void addLast(byte[] other, int begin, int end) {
    if ((end - begin) % EnumDat.numBytes != 0) throw new RuntimeException("invalid size");
    for (int itr = begin; itr < end; itr += EnumDat.numBytes) {
      for (EnumDat e : spec.getDataSet()) {
        data.get(e).write(other[itr + e.value]);
      }
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (EnumDat e : spec.getDataSet()) {
      sb.append("\n");
      sb.append(e.path + "\n");
      if (e == EnumDat.BaseCall || e == EnumDat.DeletionTag || e == EnumDat.SubstitutionTag) {
        for (int ii = 0; ii < data.get(e).size(); ++ii) {
          // sb.append((char)data.get(e).get(ii));
        }
      } else {
        for (int ii = 0; ii < data.get(e).size(); ++ii) {
          // sb.append((char)(data.get(e).get(ii)+33));
        }
      }
    }
    return sb.toString();
  }

  public ByteArrayOutputStream get(EnumDat e) {
    return data.get(e);
  }
}
