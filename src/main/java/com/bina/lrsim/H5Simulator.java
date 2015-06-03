package com.bina.lrsim;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.log4j.Logger;

import com.bina.lrsim.bioinfo.WeightedReference;
import com.bina.lrsim.h5.pb.PBBaxSpec;
import com.bina.lrsim.h5.pb.PBCcsSpec;
import com.bina.lrsim.h5.pb.PBSpec;
import com.bina.lrsim.simulator.EnumEvent;
import com.bina.lrsim.simulator.Simulator;
import com.bina.lrsim.simulator.samples.SamplesDrawer;
import com.bina.lrsim.util.Monitor;

/**
 * Created by bayo on 5/11/15.
 */
public class H5Simulator {
  private final static Logger log = Logger.getLogger(H5Simulator.class.getName());
  private final static String usage = "parameters: out_dir movie_id read_type fasta model_prefix total_bases sample_per seed [" + EnumEvent.getListDescription() + "]";

  /**
   * create a file of simulated reads based on the given FASTA and model
   * 
   * @param args see log.info
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 8) {
      log.info(usage);
      System.exit(1);
    }
    final String out_dir = args[0];
    final String identifier = args[1].trim();
    final String read_type = args[2];
    final String fasta = args[3];
    final String model_prefixes = args[4];
    final long total_bases = Long.parseLong(args[5]);
    final int sample_per = Integer.parseInt(args[6]);
    final int seed = Integer.parseInt(args[7]);

    long[] events_frequency = null;
    if (args.length > 8) {
      String[] idsm = args[8].split(":");
      if (idsm.length != EnumEvent.values().length) {
        log.info(usage);
        log.info("event frequency must be a set of integers " + EnumEvent.getListDescription());
        events_frequency = new long[EnumEvent.values().length];
        for (int ii = 0; ii < events_frequency.length; ++ii) {
          events_frequency[ii] = Long.parseLong(idsm[ii]);
        }
      }
    }

    final PBSpec spec;

    switch (read_type) {
      case "bax":
        spec = new PBBaxSpec();
        break;
      case "ccs":
        spec = new PBCcsSpec();
        break;
      default:
        spec = null;
        log.info("read_type must be bax or ccs");
        log.info(usage);
        System.exit(1);
    }

    final SamplesDrawer samples = new SamplesDrawer(model_prefixes.split(","), spec, sample_per, events_frequency);

    log.info("Memory usage: " + Monitor.PeakMemoryUsage());

    final WeightedReference wr = new WeightedReference(fasta);

    final Simulator sim = new Simulator(wr);
    log.info("Memory usage: " + Monitor.PeakMemoryUsage());

    final RandomGenerator gen = new org.apache.commons.math3.random.MersenneTwister(seed);
    log.info("Memory usage: " + Monitor.PeakMemoryUsage());

    int current_file_index = 0;
    int simulated_reads = 0;
    final int target_chunk = (int) wr.size();

    final String movie_prefix = new SimpleDateFormat("'m'yyMMdd'_'HHmmss'_'").format(Calendar.getInstance().getTime());

    // the following can be parallelized
    for (long simulated_bases = 0; simulated_bases <= total_bases; ++current_file_index) {
      final String movie_name = movie_prefix + String.format("%05d", current_file_index) + "_c" + identifier + "_s1_p0";
      final int target = (int) Math.min(target_chunk, Math.max(0, total_bases - simulated_bases));
      log.info("simulating roughly " + target_chunk + " for " + movie_name);
      simulated_reads += sim.simulate(out_dir, movie_name, simulated_reads, samples, target, spec, gen);
      log.info("total number of reads is " + simulated_reads);
      simulated_bases += target + 1;
    }


    log.info("finished.");
  }

}
