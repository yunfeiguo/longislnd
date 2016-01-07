package com.bina.lrsim.interfaces;

import java.util.Iterator;

import com.bina.lrsim.h5.pb.EnumDat;
import com.bina.lrsim.h5.pb.PBSpec;
import com.bina.lrsim.simulator.Event;

/**
 * Created by bayo on 5/8/15.
 */
public interface EventGroup {
  /**
   * Return an iterator over sequencing events
   * 
   * @param left_flank associate an event by this number of bp preceeding a position of interest
   * @param right_flank associate an event by this number of bp after a position of interest
   * @param left_mask the iterator range omits this number of events to begin with
   * @param right_mask the iterator range omits this number of events at the end
   * @param hp_anchor instead of left/right flank, use this number of bp on bothends of homopolymer event
   * @return an iterator over Events
   */
  Iterator<Event> iterator(int left_flank, int right_flank, int left_mask, int right_mask, int hp_anchor);

  int seq_length();

  int ref_length();

  byte getData(EnumDat ed, int seq_idx);

  PBSpec getSpec();

  byte getRef(int index);

  byte getSeq(int index);

  int getSeqDataIndex(int index);

  int size();
}