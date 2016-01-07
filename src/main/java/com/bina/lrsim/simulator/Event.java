package com.bina.lrsim.simulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.bina.lrsim.bioinfo.Context;
import com.bina.lrsim.h5.pb.BaseCalls;
import com.bina.lrsim.h5.pb.EnumDat;
import com.bina.lrsim.h5.pb.PBSpec;

/**
 * Created by bayo on 5/8/15.
 */

public final class Event {
  private Context context_;
  private EnumEvent event_;
  private final BaseCalls bc_;

  public Event(PBSpec spec) {
    context_ = null;
    event_ = null;
    bc_ = new BaseCalls(spec);
  }

  public Event(Context c, EnumEvent e, BaseCalls b) {
    context_ = c;
    event_ = e;
    bc_ = b;
  }

  public int size() {
    return bc_.size();
  }

  public void resize(int s) {
    bc_.resize(s);
  }

  public EnumEvent event() {
    return event_;
  }

  public int kmer() {
    return context_.kmer();
  }

  public int hp_len() {
    return context_.hp_len();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(context_.toString() + " " + event_.toString() + " " + bc_.size() + "\n");
    if (null != bc_) sb.append(bc_.toString());
    return sb.toString();
  }

  public final byte get(int pos, EnumDat e) {
    return bc_.get(pos, e);
  }

  public final void set(int pos, EnumDat e, byte b) {
    bc_.set(pos, e, b);
  }

  public byte[] data_cpy() {
    return bc_.toByteArray();
  }

  // there are 4-byte per 12-byte match event here, which is huge overhead
  // we can save 4 byte by storing 2byte hp-length and 2byte base length if needed
  // we can also save all 4 bytes by writing homopolymer events to a different stream
  // this can be done down the line if we have time
  public void write(DataOutputStream dos) throws IOException {
    if (event_.value >= EnumEvent.values().length) throw new RuntimeException("invalid i/o format");
    dos.writeInt(context_.kmer());
    dos.writeInt(EnumEvent.values().length * context_.hp_len() + event_.value);
    bc_.write(dos);
  }

  public void read(DataInputStream dis) throws IOException {
    final int kmer = dis.readInt();
    int tmp = dis.readInt();
    context_ = new Context(kmer, tmp / EnumEvent.values().length);
    event_ = EnumEvent.value2enum(tmp % EnumEvent.values().length);
    bc_.read(dis);
    for (int ii = 0; ii < size(); ++ii) {
      byte base = get(ii, EnumDat.BaseCall);
      if (base != 'A' && base != 'C' && base != 'T' && base != 'G') {
        throw new RuntimeException("bad event at " + ii + ":" + toString());
      }

    }
  }
}