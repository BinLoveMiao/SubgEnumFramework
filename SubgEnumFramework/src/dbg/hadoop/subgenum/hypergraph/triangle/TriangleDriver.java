package dbg.hadoop.subgenum.hypergraph.triangle;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;  
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;  
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
//import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;

import com.hadoop.compression.lzo.LzoCodec;

import dbg.hadoop.subgraphs.io.HVArrayGroupComparator;
import dbg.hadoop.subgraphs.io.HVArraySignComparator;
import dbg.hadoop.subgraphs.io.HVArraySign;
import dbg.hadoop.subgraphs.io.HVArray;

public class TriangleDriver extends Configured implements Tool{

	public int run(String[] args) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {
		Configuration conf = getConf();
		// The parameters: <graphFileDir> <adjListDir> <outputDir> <numReducers> <jarFile>
		int numReducers = Integer.parseInt(args[3]);
		
		conf.setBoolean("mapreduce.map.output.compress", true);
		conf.set("mapreduce.map.output.compress.codec", "com.hadoop.compression.lzo.LzoCodec");
		
		Job job = new Job(conf, "Triangle");
		((JobConf)job.getConfiguration()).setJar(args[4]);
		//JobConf job = new JobConf(getConf(), this.getClass());
		
		job.setReducerClass(TriangleReducer.class);
		
		job.setMapOutputKeyClass(HVArraySign.class);
		job.setMapOutputValueClass(HVArray.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(HVArray.class);
		
		job.setSortComparatorClass(HVArraySignComparator.class);
		job.setGroupingComparatorClass(HVArrayGroupComparator.class);

		//job.setInputFormatClass(SequenceFileInputFormat.class);
		//if(enableOutputCompress){
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType
								(job, CompressionType.BLOCK);
		SequenceFileOutputFormat.setOutputCompressorClass(job, LzoCodec.class);
		
		job.setNumReduceTasks(numReducers);
		//FileInputFormat.setInputPaths(job, new Path(args[0]));
		MultipleInputs.addInputPath(job, 
				new Path(args[0]),
				SequenceFileInputFormat.class,
				EdgeMapper.class);
		
		MultipleInputs.addInputPath(job, 
				new Path(args[1]),
				SequenceFileInputFormat.class,
				TriangleMapper.class);
		
		FileOutputFormat.setOutputPath(job, new Path(args[2]));

		job.waitForCompletion(true);
		return 0;
	}
}