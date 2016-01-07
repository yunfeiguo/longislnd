package com.bina.lrsim.bioinfo;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.log4j.Logger;

import java.util.Arrays;

/**
 * Created by laub2 on 9/2/15.
 */
public class FragmentSequenceDrawer extends ReferenceSequenceDrawer {
  private final static Logger log = Logger.getLogger(FragmentSequenceDrawer.class.getName());

  public FragmentSequenceDrawer(String filename) {
    super(filename);
  }

  @Override
  protected Fragment getSequenceImpl(int length, RandomGenerator gen) {
    final boolean rc = gen.nextBoolean();

    // select a fragment with equal probability
    final Fragment fragment = get(gen.nextInt(name_.size()));
    final byte[] ref_seq = fragment.getSeq(); // this is a reference don't modify it

    // if fragment is shorter than indicated length, return the whole fragment
    int begin, end;
    if (ref_seq.length <= length) {
      begin = 0;
      end = ref_seq.length;
    } else {
      // otherwise, assume that long fragment is sheared into such length
      begin = gen.nextInt(ref_seq.length - length + 1);
      end = begin + length;
    }

    final byte[] sequence;
    if (rc) {
      sequence = new byte[end - begin];
      for (int ss = 0, cc = ref_seq.length - 1 - begin; ss < sequence.length; ++ss, --cc) {
        sequence[ss] = EnumBP.ascii_rc(ref_seq[cc]);
      }
      // shift begin/end back to forward strain's coordinate to describe locus
      end = ref_seq.length - begin;
      begin = end - sequence.length;
    } else {
      sequence = Arrays.copyOfRange(ref_seq, begin, end);
    }
    return new Fragment(sequence, new Locus(fragment.getLocus().getChrom(), begin, end, rc));
  }
}