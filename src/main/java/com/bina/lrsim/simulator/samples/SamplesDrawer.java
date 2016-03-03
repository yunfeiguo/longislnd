package com.bina.lrsim.simulator.samples;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.bina.lrsim.bioinfo.KmerIntIntCounter;
import com.bina.lrsim.simulator.samples.pool.AddBehavior;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.Pair;
import org.apache.log4j.Logger;

import com.bina.lrsim.bioinfo.Context;
import com.bina.lrsim.bioinfo.Heuristics;
import com.bina.lrsim.bioinfo.Kmerizer;
import com.bina.lrsim.pb.EnumDat;
import com.bina.lrsim.pb.PBReadBuffer;
import com.bina.lrsim.pb.PBSpec;
import com.bina.lrsim.simulator.EnumEvent;
import com.bina.lrsim.simulator.Event;
import com.bina.lrsim.simulator.samples.pool.AppendState;
import com.bina.lrsim.simulator.samples.pool.BaseCallsPool;
import com.bina.lrsim.simulator.samples.pool.HPBCPool;

/**
 * Created by bayo on 5/10/15.
 * <p/>
 * Class for drawing sequence samples based on sequencing context
 */
public class SamplesDrawer extends Samples {
  private final static Logger log = Logger.getLogger(SamplesDrawer.class.getName());
  final EnumMap<EnumEvent, BaseCallsPool> kmer_event_drawer_ = new EnumMap<EnumEvent, BaseCallsPool>(EnumEvent.class);
  private HPBCPool hp_event_drawer_;
  private final PBSpec spec;
  private final long[] custom_frequency;

  /**
   * Constructor
   * 
   * @param prefixes a list of prefixes storing sampled data
   * @param spec specification of data fields, etc
   * @param max_sample maximum number of samples stored in compressed way
   * @param custom_frequency if not null, override per-kmer event frequency with this
   * @param len_limits restrict length properties
   * @throws IOException
   */
  public SamplesDrawer(String[] prefixes, PBSpec spec, int max_sample, long[] custom_frequency, boolean artificial_clean_ins, LengthLimits len_limits) throws IOException {
    this(prefixes[0], spec, 0/* must use 0 here */, custom_frequency, artificial_clean_ins, len_limits);
    for (int ii = 1; ii < prefixes.length; ++ii) {
      accumulateStats(new SamplesDrawer(prefixes[ii], spec, 0/* must use 0 here */, custom_frequency, artificial_clean_ins, len_limits));
    }
//    log.info(this.toString());
    allocateEventDrawer(spec, max_sample);
    loadEvents(prefixes, max_sample, artificial_clean_ins);
    super.filterScoreLength(len_limits);
  }

  /**
   * Constructor
   * 
   * @param prefix
   * @param prefix prefix of files storing sampled data
   * @param spec specification of data fields, etc
   * @param max_sample maximum number of samples stored in compressed way
   * @param custom_frequency if not null, override per-kmer event frequency with this
   * @param len_limits restrict length properties
   * @throws IOException
   */
  public SamplesDrawer(String prefix, PBSpec spec, int max_sample, long[] custom_frequency, boolean artificial_clean_ins, LengthLimits len_limits) throws IOException {
    super(prefix);
    this.spec = spec;
    this.custom_frequency = custom_frequency;
    if (this.custom_frequency != null) {
      log.info("using custom event frequencies");
    } else {
      log.info("using sampled event frequencies");
    }
    log.info("loaded bulk statistics from " + prefix);
    allocateEventDrawer(spec, max_sample);
    loadEvents(prefix, max_sample, artificial_clean_ins);
    super.filterScoreLength(len_limits);
  }

  private AddBehavior calculateAddBehavior(long[] custom_frequency) {
    if (null == custom_frequency) { return new AddBehavior(0, 0, Integer.MAX_VALUE); }
    double custom = 0;
    for (long entry : custom_frequency)
      custom += entry;
    if (custom_frequency[EnumEvent.MATCH.ordinal()] == custom) { return new AddBehavior(0, 0, Integer.MAX_VALUE); }
    custom = 1.0 - custom_frequency[EnumEvent.MATCH.ordinal()] / custom;
    final int custom_q = (int) (-10 * Math.log10(custom) + 0.5);

    double intrinsic = 0;
    for (long entry : super.event_base_count_ref())
      intrinsic += entry;
    if (super.event_base_count_ref()[EnumEvent.MATCH.ordinal()] == intrinsic) { return new AddBehavior(0, 0, Integer.MAX_VALUE); }
    intrinsic = 1.0 - super.event_base_count_ref()[EnumEvent.MATCH.ordinal()] / intrinsic;
    final int intrinsic_q = (int) (-10 * Math.log10(intrinsic) + 0.5);

    final int delta_q = (int) (10 * Math.log10(intrinsic / custom) + 0.5);

    return new AddBehavior(delta_q, 0, Math.max(custom_q, intrinsic_q));
  }

  private void allocateEventDrawer(PBSpec spec, int max_sample) {
    hp_event_drawer_ = new HPBCPool(spec, 1 << (2 * (1 + 2 * hp_anchor())), -1);
    for (EnumEvent event : EnumSet.allOf(EnumEvent.class)) {
      // final int cap = (event.equals(EnumEvent.MATCH) ) ? max_sample : -1;
      final int cap = max_sample;
      try {
        kmer_event_drawer_.put(event, (BaseCallsPool) event.pool.getDeclaredConstructor(new Class[] {PBSpec.class, int.class, int.class}).newInstance(spec, num_kmer(), cap));
      } catch (ReflectiveOperationException e) {
        log.info(e, e);
      }
    }
  }

  /**
   * Returns a read length/score drawn from sampled data
   *
   * @param gen random number generator
   * @return read length and region score
   */
  public final Pair<int[], Integer> getRandomLengthScore(RandomGenerator gen) {
    final int index = gen.nextInt(getLengthSize());
    return new Pair<int[], Integer>(getLength(index), getScore(index));
  }

  /**
   * For the given sequencing context, append to buffer a sequence drawn from the sampled data
   *
   * @param buffer visitor to take the randomly generated sequence
   * @param context sequencing context
   * @param gen random number generator
   * @param base_counter base counter for ins/del/sub/mat
   * @return about current state
   */
  public AppendState appendTo(PBReadBuffer buffer, Context context, AppendState deletion, RandomGenerator gen, long[] base_counter) {
    final int old_length = buffer.size();
    if (context.hp_len() == 1) {
      EnumEvent ev = randomEvent(context, gen);
      // ignore the previous event if it's already a deletion
      final int org_length = buffer.size();
      AppendState result = kmer_event_drawer_.get(ev).appendTo(buffer, context, ev.equals(EnumEvent.DELETION) ? null : deletion, gen);

      final boolean badLength;
      switch(ev) {
        case DELETION:
          badLength = buffer.size() != org_length;
          break;
        case INSERTION:
          badLength = buffer.size() <= org_length + 1;
          break;
        case MATCH:
        case SUBSTITUTION:
          badLength = buffer.size() != org_length + 1;
          if (buffer.size() != org_length + 1) {
            throw new RuntimeException("length increased by " + (buffer.size() - org_length) + " for " + ev.name());
          }
          break;
        default:
          badLength = false;
      }
      if (badLength) {
        throw new RuntimeException("length increased by " + (buffer.size() - org_length) + " for " + ev.name());
      }

      ++base_counter[ev.ordinal()];
      if (ev == EnumEvent.INSERTION) {
        base_counter[ev.ordinal()] += buffer.size() - old_length - 2;
      }
      if (!result.success) { throw new RuntimeException("kmer draw"); }
      // return a signal for deletion event
      return (ev == EnumEvent.DELETION && result.last_event != null && result.last_event.length > 0) ? result : null;
    } else {
      // do not do full hp if custom frequency is provided
      // custom hp drawing is possible improvement
      final boolean hp_sampling;
      if (custom_frequency == null) {
        final AppendState result = hp_event_drawer_.appendTo(buffer, context, deletion, gen);
        hp_sampling = result.success;
      } else {
        // might want to do something with remodeling homopolymer indels -- uniform error does not work
        hp_sampling = false;
      }
      if (hp_sampling) {
        final int differential = buffer.size() - old_length - context.hp_len();
        if (differential == 0) {
          base_counter[EnumEvent.MATCH.ordinal()] += context.hp_len();
          // log.info("homo match");
        } else if (differential < 0) {
          base_counter[EnumEvent.DELETION.ordinal()] += -differential;
          // log.info("homo del");

        } else {
          base_counter[EnumEvent.MATCH.ordinal()] += context.hp_len() - 1;
          base_counter[EnumEvent.INSERTION.ordinal()] += differential;
          // log.info("homo ins");
        }
        return null; // homopolyer does not pash deletion tag
      } else {
        int count = 0;
        // decompose extended kmer to kmer
        for (Iterator<Context> itr = context.decompose(left_flank(), right_flank()); itr.hasNext();) {
          deletion = this.appendTo(buffer, itr.next(), deletion, gen, base_counter);
          ++count;
        }
        if (count != context.hp_len()) {
          log.info(count + " vs " + context.hp_len());
          throw new RuntimeException("bad decomposition");
        }
        return deletion;
      }
    }
  }

  /**
   * load events from a single file
   *
   * @param prefix file prefix
   * @param max_sample
   * @throws IOException
   */
  private void loadEvents(String prefix, int max_sample, boolean artificial_clean_ins) throws IOException {
    loadEvents(new String[] {prefix}, max_sample, artificial_clean_ins);
  }

  /**
   * Load the sampled events
   *
   * @param prefixes prefixes of the event files
   * @throws IOException
   */
  private void loadEvents(String[] prefixes, int max_sample, boolean artificial_clean_ins) throws IOException {
    final int num_src = prefixes.length;
    for (Suffixes suf : EnumSet.of(Suffixes.EVENTS, Suffixes.HP)) {
      DataInputStream[] dis = new DataInputStream[num_src];
      for (int ii = 0; ii < num_src; ++ii) {
        dis[ii] = new DataInputStream(new BufferedInputStream(new FileInputStream(suf.filename(prefixes[ii])), 1000000));
      }
      loadEvents(dis, max_sample, artificial_clean_ins);
      for (int ii = 0; ii < num_src; ++ii) {
        dis[ii].close();
      }
    }
  }

  /**
   * Load the sampled events
   *
   * @param dis a list of datastream
   * @throws IOException
   */
  private void loadEvents(DataInputStream[] dis, int max_sample, boolean artificial_clean_ins) throws IOException {
    // Bottleneck code
    AddBehavior ab = calculateAddBehavior(this.custom_frequency);
    if (max_sample < 1) return;
    log.info("loading events");
    final int num_src = dis.length;
    Event buffer = new Event(spec);
    long count = 0;

    long[] event_count = new long[EnumEvent.values.length];
    long[] logged_event_count = new long[EnumEvent.values.length];
    // long num_logged_event = 0;
    // final long max_logged_event = EnumEvent.num_logged_events() * num_kmer() * (long) max_sample;
    KmerIntIntCounter num_logged_events = new KmerIntIntCounter(k(), EnumEvent.values.length, 1);
    final int min_event_index = EnumEvent.SUBSTITUTION.ordinal(); // assumes substitution is the rarest events
    final long[] min_event_threshold = new long[num_kmer()]; // -1 for done, 0 or 1 for not done
    for (int kk = 0; kk < num_kmer(); ++kk) {
      min_event_threshold[kk] = kmer_event_count_ref()[kk * EnumEvent.values.length + min_event_index] == 0 ? 0 : 1;
    }
    int n_kmer_done = 0;

    long num_hp_events = 0;

    long raw_ins = 0;
    long mod_ins = 0;

    final boolean[] src_done = new boolean[num_src];

    for (int src = 0, n_src_done = 0; n_src_done < num_src && n_kmer_done < num_kmer() /* && num_logged_event < max_logged_event */; src = (src + 1) % num_src) {
      if (dis[src].available() > 0) {
        buffer.read(dis[src]);
        final int bufferKmer = buffer.kmer();

        if (buffer.hp_len() == 1) {
          final EnumEvent bufferEvent = buffer.event();
          if (buffer.size() > 1 && bufferEvent != EnumEvent.INSERTION) {
            throw new RuntimeException(bufferEvent.name() + " with length " + buffer.size());
          }

          final byte centerBase = Kmerizer.getKmerByte(bufferKmer, left_flank() + 1 + right_flank(), left_flank());
          switch(bufferEvent) {
            case DELETION:
              break;
            case SUBSTITUTION:
              if (centerBase == buffer.get(0, EnumDat.BaseCall)) {
                throw new RuntimeException("matching base for " + bufferEvent.name() + " event");
              }
              break;
            case MATCH:
              if (centerBase != buffer.get(0, EnumDat.BaseCall)) {
                throw new RuntimeException("mismatching base for " + bufferEvent.name() + " event");
              }
              break;
            case INSERTION:
              if (artificial_clean_ins) {
                final byte nextBase = Kmerizer.getKmerByte(bufferKmer, left_flank() + 1 + right_flank(), left_flank() + 1);
                final int mid_point = (buffer.size() + 1) / 2;
                final byte mid_point_base = buffer.get(mid_point, EnumDat.BaseCall);
                final boolean mid_point_different = mid_point_base != centerBase && mid_point_base != nextBase;
                boolean changed = false;
                for (int index = 0; index < buffer.size(); ++index) {
                  final byte base = buffer.get(index, EnumDat.BaseCall);
                  if (base != centerBase && base != nextBase) {
                    buffer.set(index, EnumDat.BaseCall, centerBase);
                    changed = true;
                  }
                }
                ++raw_ins;
                if (buffer.size() % 2 == 0 && mid_point_different) {
                  buffer.set(mid_point, EnumDat.BaseCall, ThreadLocalRandom.current().nextBoolean() ? centerBase : nextBase);
                  changed = true;
                }
                if (changed) {
                  ++mod_ins;
                  // log.info("bayo changed: " + new String(kmer_sequence) + " " + buffer.toString());
                }
              }
              if (buffer.size() - 1 > Heuristics.MAX_INS_LENGTH) {
                int hp_length = 1;
                final byte[] kmerSequence = Kmerizer.toByteArray(bufferKmer, left_flank() + 1 + right_flank());
                for (int pos = left_flank() + 1; pos < kmerSequence.length && kmerSequence[pos] == centerBase; ++pos, ++hp_length) {
                }
                for (int pos = left_flank() - 1; pos >= 0 && kmerSequence[pos] == centerBase; --pos, ++hp_length) {
                }
                if (hp_length <= Math.min(left_flank(), right_flank())) {
                  buffer.resize(Heuristics.MAX_INS_LENGTH + 1);
                }
              }
          }

          ++event_count[bufferEvent.ordinal()];
          if (kmer_event_drawer_.get(bufferEvent).add(buffer, ab)) {
            final int eventIndex = bufferEvent.ordinal();
            ++logged_event_count[eventIndex];
            num_logged_events.increment(bufferKmer, eventIndex, 0);
            if (min_event_threshold[bufferKmer] >= 0 && num_logged_events.get(bufferKmer, min_event_index, 0) >= min_event_threshold[bufferKmer]) {
              boolean done = true;
              long sum = 0;
              long sum_possible = 0;
              for (EnumEvent event : EnumEvent.values) {
                final long loc = num_logged_events.get(bufferKmer, event.ordinal(), 0);
                final long loc_logged_total = kmer_event_count_ref()[bufferKmer * EnumEvent.values.length + event.ordinal()];
                final long loc_possible = Math.max(loc_logged_total / event.recordEvery, loc_logged_total > 0 ? 1 : 0);
                done = done && (loc > 0 || loc_possible == 0);
                sum += loc;
                sum_possible += loc_possible;
              }
              done = done && (sum >= max_sample || sum >= sum_possible);
              if (done) {
                ++n_kmer_done;
                min_event_threshold[bufferKmer] = -1;
              }
            }
            // ++num_logged_event;
          }
        } else {
          if (Heuristics.DISCARD_DIRTY_HOMOPOLYMER_SAMPLES) {
            final byte center_base = Kmerizer.getKmerByte(buffer.kmer(), 2 * hp_anchor() + 1, hp_anchor());
            int match = 0;
            for (int pos = 0; pos < buffer.size(); ++pos) {
              if (buffer.get(pos, EnumDat.BaseCall) == center_base) {
                ++match;
              }
            }
            if (2 * match >= buffer.size()) {
              hp_event_drawer_.add(buffer, ab);
              ++num_hp_events;
            }
          } else {
            hp_event_drawer_.add(buffer, ab);
            ++num_hp_events;
          }
        }
        ++count;
        if (count % 10000000 == 1) {
          log.info("loaded " + count + " events" + Arrays.toString(logged_event_count) + "/" + Arrays.toString(event_count));
          log.info("loaded " + num_hp_events + " hp events");
          log.info("pruning criterions " + n_kmer_done + "/" + num_kmer());
        }
      } else if (!src_done[src]) {
        src_done[src] = true;
        ++n_src_done;
      }
    }
    if(n_kmer_done >= num_kmer()) {
      log.info("load has been pruned");
    }
    log.info("loaded " + count + " events");
    log.info("loaded " + num_hp_events + " hp events");
    log.info("modified ins: " + mod_ins + "/" + raw_ins);
  }

  /**
   * For a given sequencing context, generate an event
   *
   * @param context sequencing context
   * @param gen random number generator
   * @return an event type
   */
  private EnumEvent randomEvent(Context context, RandomGenerator gen) {
    if (context.hp_len() == 1) {
      final int shift = EnumEvent.values.length * context.kmer();

      final long[] frequencies = Arrays.copyOfRange(kmer_event_count_ref(), shift, shift + EnumEvent.values.length);
      if (null != custom_frequency) {
        for (int ii = 0; ii < EnumEvent.values.length; ++ii) {
          if (frequencies[ii] > Heuristics.MIN_KMER_SAMPLES_FOR_NON_ZERO_CUSTOM_FREQUENCY) {
            frequencies[ii] = custom_frequency[ii];
          } else {
            frequencies[ii] = 0;
            // log.info("warning: not enough samples for custom sampling frequency. considering more training data or shorter sampling flank to discover such rare event");
          }
        }
      }

      long sum = 0;
      for (int ii = 0; ii < EnumEvent.values.length; ++ii) {
        sum += frequencies[ii];
      }
      final double p = gen.nextDouble();
      if (p < 0 || p > 1) { throw new RuntimeException("bad p=" + p); }
      double cdf = 0;
      for (int ii = 0; ii < EnumEvent.values.length; ++ii) {
        cdf += (double) (frequencies[ii]) / (double) (sum);
        if (p <= cdf) return EnumEvent.values[ii];
      }
      return EnumEvent.MATCH;
    } else {
      throw new UnsupportedOperationException("hp code path not implemented");
    }
  }

}
