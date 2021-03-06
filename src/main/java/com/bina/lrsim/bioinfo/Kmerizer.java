package com.bina.lrsim.bioinfo;

// import org.apache.commons.math3.random.MersenneTwister;

import com.bina.lrsim.util.ThreadLocalResources;

import java.nio.charset.StandardCharsets;

/**
 * Created by bayo on 5/8/15.
 * <p/>
 * kmerize up to 16bp, randomly select a base for N
 */
public class Kmerizer {

  /**
   * kmerize a ascii stream of up to 16 bp
   * 
   * @param ascii ascii stream
   * @param begin begin position
   * @param end ending position
   * @return
   */
  public static int fromASCII(byte[] ascii, int begin, int end) {
    if (end - begin > 16) throw new RuntimeException("stream too big for 16-mer");
    int out = 0;
    for (int ii = begin; ii < end; ++ii) {
      out *= 4;
      int val = EnumBP.ascii2value(ascii[ii]);
      if ( val < 0 || val >=4 ) {
//        throw new RuntimeException("unsupported base call " + ascii[ii]);
        val = ThreadLocalResources.random().nextInt(4);
      }
      out += val;
    }
    return out;
  }


  public static int fromASCII(byte[] ascii) {
    return fromASCII(ascii, 0, ascii.length);
  }

  public static String toString(int kmer, int length) {
    return new String(toByteArray(kmer, length), StandardCharsets.UTF_8);
  }

  public static byte[] toByteArray(int kmer, int length) {
    byte[] tmp = new byte[length];
    for (int ii = length - 1; ii >= 0; --ii) {
      tmp[ii] = EnumBP.value2ascii((byte) (kmer % 4));
      kmer /= 4;
    }
    return tmp;
  }

  public static byte getKmerByte(int kmer, final int length, final int index) {
    for (int i = length - 1; i > index; i--) {
      kmer /= 4;
    }
    return EnumBP.value2ascii((byte) (kmer % 4));
  }
}
