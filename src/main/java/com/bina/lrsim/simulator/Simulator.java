package com.bina.lrsim.simulator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import com.bina.lrsim.bioinfo.*;
import com.bina.lrsim.pb.*;
import com.bina.lrsim.simulator.samples.pool.AppendState;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Pair;
import org.apache.log4j.Logger;

import com.bina.lrsim.interfaces.RandomFragmentGenerator;
import com.bina.lrsim.simulator.samples.SamplesDrawer;
import com.bina.lrsim.util.Monitor;

/**
 * Created by bayo on 5/11/15.
 */
public class Simulator {
  private final static Logger log = Logger.getLogger(Simulator.class.getName());
  private final AtomicLongArray baseCounter = new AtomicLongArray(EnumEvent.values.length);
  private final AtomicLongArray eventCounter = new AtomicLongArray(EnumEvent.values.length);
  private final ConcurrentHashMap<String, AtomicLong> nameCounter = new ConcurrentHashMap<>();
  private final RandomFragmentGenerator seqGen;

  /**
   * Constructor
   * 
   * @param seqGen a random sequence generator
   */
  public Simulator(RandomFragmentGenerator seqGen) {
    this.seqGen = seqGen;
  }

  /**
   * Generate a pacbio h5 file containing reads simulated according to the sampler and reference
   * 
   * @param path output path
   * @param movieName movie name
   * @param firsthole first hole producing sequence
   * @param drawer an instance from which samples can be drawn
   * @param totalBases minimum number of bp to generate
   * @param gen random number generator
   */
  public int simulate(final String path, final String movieName, final int firsthole, SamplesDrawer drawer, int totalBases, final Spec spec, RandomGenerator gen) throws IOException {
    try (ReadsWriter writer = ReadsWriterFactory.makeWriter(spec, new File(path, movieName + spec.getSuffix()).getPath(), movieName, firsthole, drawer.getNumRunInfo() > 0 ? drawer.getRunInfo(0) : new RunInfo())) {
      long[] localBaseCounter = new long[baseCounter.length()];
      PBReadBuffer read = new PBReadBuffer(spec);

      for (int numBases = 0; numBases < totalBases;) {
        read.clear();
        if (read.size() != 0) {
          log.error("couldn't clear buffer");
          throw new RuntimeException("different lengths!");
        }

        // draw a list of smrt belts
        Pair<int[], Integer> lenScore = drawer.getRandomLengthScore(gen);
        final int[] insertLengths = lenScore.getFirst();
        MultiPassSpec multiPassSpec = new MultiPassSpec(insertLengths);

        final Fragment fragment = seqGen.getFragment(multiPassSpec.fragmentLength, gen);
        final Locus locus = fragment.getLocus();
        nameCounter.putIfAbsent(locus.getChrom(), new AtomicLong((long) 0));
        nameCounter.get(locus.getChrom()).incrementAndGet();
        final byte[] sequence = fragment.getSeq();
        // in some sequence drawer, such as fragment mode, the sequence can be much longer than read length, in this case we set the passes to fragment length
        if(multiPassSpec.fragmentLength < Heuristics.READLENGTH_RESCUE_FRACTION * sequence.length) {
          for (int ii = 1; ii + 1 < insertLengths.length; ++ii) {
            if (insertLengths[ii] < sequence.length) {
              insertLengths[ii] = sequence.length;
            }
          }
        }
        // correct insert lengths if the drawn fragment is shorter, the fractional change might not be realistic, but it avoids crazy coverage in fragment mode
        for (int ii = 0; ii < insertLengths.length; ++ii) {
          if(insertLengths[ii] > sequence.length) {
            insertLengths[ii] = sequence.length;
          }
        }

        // draw a sequence according to max insert length, make RC if belt is long enough
        final List<byte[]> fwRc = new ArrayList<>(2);
        fwRc.add(sequence);
        if (insertLengths.length > 1) {
          final byte[] fw = fwRc.get(0);
          final byte[] rc = new byte[fw.length];
          for (int pos = 0, tgt = fw.length - 1; pos < fw.length; ++pos, --tgt) {
            rc[tgt] = EnumBP.ascii_rc(fw[pos]);
          }
          fwRc.add(rc);
        }

        final List<Locus> clrLoci = new ArrayList<>();

        final List<Integer> sectionEnds = new ArrayList<>(2 * insertLengths.length - 1);
        boolean skipIfShort = false;
        for (int insIdx = 0; insIdx < insertLengths.length; ++insIdx) {
          final int insertLength = insertLengths[insIdx];
          final boolean firstClr = insIdx == 0;
          final boolean lastClr = insIdx + 1 == insertLengths.length;
          final int begin = firstClr ? sequence.length - insertLength : 0;
          final int end = lastClr ? insertLength : sequence.length;
          final boolean isShort = insertLength < Heuristics.SMRT_INSERT_FRACTION * sequence.length && !firstClr && !lastClr;
          if (!isShort || !skipIfShort) {
            if (!firstClr) {
              // prepend with a "perfect" adaptor sequence
              read.addASCIIBases(Heuristics.SMRT_ADAPTOR_STRING, Heuristics.SMRT_ADAPTOR_STRING, Heuristics.SMRT_ADAPTOR_SCORE);
              sectionEnds.add(read.size());
            }
            AppendState deletion = null;
            for (Iterator<Context> itr = new HPIterator(fwRc.get(insIdx % 2), begin, end, drawer.getLeftFlank(), drawer.getRightFlank(), drawer.getHpAnchor()); itr.hasNext();) {
              final Context con = itr.next();
              if (null != con) {
                deletion = drawer.appendTo(read, con, deletion, gen, localBaseCounter);
              }
            }

            //per clr coordinates
            int clrBegin = locus.getBegin0();
            int clrEnd = locus.getEnd0();
            final boolean readIsRc = (insIdx % 2 == 1) ^ locus.isRc();
            if (firstClr) {
              if(readIsRc)
                clrEnd = locus.getBegin0() + insertLength;
              else
                clrBegin = locus.getEnd0() - insertLength;
            }
            if (lastClr) {
              if(readIsRc)
                clrBegin = locus.getEnd0() - insertLength;
              else
                clrEnd = locus.getBegin0() + insertLength;
            }
            clrLoci.add(new Locus(locus.getChrom(), clrBegin, clrEnd, readIsRc));
            sectionEnds.add(read.size());
            if (isShort) {
              skipIfShort = true;
            }
          }
          if (!isShort) {
            skipIfShort = false;
          }
        }

        writer.addLast(read, sectionEnds, lenScore.getSecond(), locus, clrLoci);
        numBases += read.size();
      }
      for (int index = 0; index < localBaseCounter.length; ++index) {
        baseCounter.getAndAdd(index, localBaseCounter[index]);
      }
      synchronized (log) {
        log.info(toString());
        log.info("generated " + writer.size() + " reads.");
        log.info("Memory usage: " + Monitor.PeakMemoryUsage());
      }
      return writer.size();
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("simulated statistics\n");
    long[] tmp = new long[baseCounter.length()];
    for (int index = 0; index < tmp.length; ++index) {
      tmp[index] = baseCounter.get(index);
    }
    sb.append(EnumEvent.getPrettyStats(tmp));
    sb.append("\n");
    for (int index = 0; index < tmp.length; ++index) {
      tmp[index] = eventCounter.get(index);
    }
    sb.append(EnumEvent.getPrettyStats(tmp));
    sb.append("\n");
    for (ConcurrentHashMap.Entry<String, AtomicLong> entry : nameCounter.entrySet()) {
      sb.append(entry.getKey());
      sb.append(" ");
      sb.append(entry.getValue().get());
      sb.append("\n");
    }
    return sb.toString();
  }
}
