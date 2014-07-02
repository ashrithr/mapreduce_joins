package com.cloudwick.mapreduce.joins.mapside.dc.mapfile;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.net.URI;

/**
 * Description goes here
 *
 * @author ashrith
 */
public class Driver extends Configured implements Tool {

    @Override
    public int run(String[] args) throws Exception {

        if (args.length != 3) {
            System.out.printf("Two parameters are required for DriverMapSideJoinDCacheMapFile- <input dir> " +
                  "<dpts map file> <output dir>\n");
            return -1;
        }

        Job job = new Job(getConf());
        Configuration conf = job.getConfiguration();
        job.setJobName("Map-side join with mapfile in DCache");
        DistributedCache.addCacheArchive(new URI(args[1]), conf);

        job.setJarByClass(Driver.class);
        FileInputFormat.setInputPaths(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        job.setMapperClass(MapperDC.class);
        job.setNumReduceTasks(0);

        boolean success = job.waitForCompletion(true);
        return success ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new Configuration(), new Driver(), args);
        System.exit(exitCode);
    }
}
