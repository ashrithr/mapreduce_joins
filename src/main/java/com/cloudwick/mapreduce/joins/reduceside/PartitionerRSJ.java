package com.cloudwick.mapreduce.joins.reduceside;


import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

/**
 * Even though the map output key is composite, we want to partition by the natural join key of empNo, therefore a
 * custom partitioner is in order.
 *
 * @author ashrith
 */
public class PartitionerRSJ extends Partitioner<CompositeKeyWritableRSJ, Text> {

    @Override
    public int getPartition(CompositeKeyWritableRSJ key, Text value, int numReduceTasks) {
        // Partitions on joinKey (EmployeeID)
        return (key.getJoinKey().toString().hashCode() % numReduceTasks);
    }
}
