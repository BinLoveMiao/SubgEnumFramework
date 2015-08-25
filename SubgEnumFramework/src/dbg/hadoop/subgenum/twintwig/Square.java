package dbg.hadoop.subgenum.twintwig;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;

import dbg.hadoop.subgraphs.io.HVArray;
import dbg.hadoop.subgraphs.io.HVArrayGroupComparator;
import dbg.hadoop.subgraphs.io.HVArraySign;
import dbg.hadoop.subgraphs.io.HVArraySignComparator;
import dbg.hadoop.subgraphs.io.HyperVertexAdjList;
import dbg.hadoop.subgraphs.utils.BloomFilterOpr;
import dbg.hadoop.subgraphs.utils.Config;
import dbg.hadoop.subgraphs.utils.HyperVertex;
import dbg.hadoop.subgraphs.utils.TwinTwigGenerator;
import dbg.hadoop.subgraphs.utils.Utility;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import com.hadoop.compression.lzo.LzoCodec;


public class Square{
	public static void main(String[] args) throws Exception{
		int valuePos = 0;
		String numReducers = "1";
		String inputFilePath = "";
		String jarFile = "";
		boolean enableBloomFilter = false;
		
		double bfProbFP = 0.001;
		String outputCompress = "true";
		String maxInputSize = "0";
		
		for (int i = 0; i < args.length; ++i) {
			// System.out.println("args[" + i + "] = " + args[i]);
			if (args[i].contains("mapred.reduce.tasks")) {
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					numReducers = args[i].substring(valuePos);
				}
			}
			else if(args[i].contains("mapred.input.file")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					inputFilePath = args[i].substring(valuePos);
				}
			}
			else if(args[i].contains("enable.bloom.filter")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					enableBloomFilter = Boolean.valueOf(args[i].substring(valuePos));
				}
			}
			else if(args[i].contains("bloom.filter.false.positive.rate")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					bfProbFP = Double.parseDouble(args[i].substring(valuePos));
				}
			}
			else if(args[i].contains("jar.file.name")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					jarFile = args[i].substring(valuePos);
				}
			}
			else if(args[i].contains("output.compress")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					outputCompress = args[i].substring(valuePos);
				}
			}
			else if(args[i].contains("map.input.max.size")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					maxInputSize = args[i].substring(valuePos);
				}
			}

		}

		String workDir = Utility.getWorkDir(inputFilePath);
		
		if (workDir.toLowerCase().contains("hdfs")) {
			int pos = workDir.substring("hdfs://".length()).indexOf("/")
					+ "hdfs://".length();
			Utility.setDefaultFS(workDir.substring(0, pos));
		} else {
			Utility.setDefaultFS("");
		}
		
		String outputDir = workDir + "tt.square.res";
		
		Configuration conf = new Configuration();
		if(enableBloomFilter){
			conf.setBoolean("enable.bloom.filter", true);
			conf.setDouble("bloom.filter.false.positive.rate", bfProbFP);
			String bloomFilterFileName = "bloomFilter." + Config.TWINTWIG1 + "." + bfProbFP;
			DistributedCache.addCacheFile(new URI(new Path(workDir).toUri()
				.toString() + "/" + Config.bloomFilterFileDir + "/" + bloomFilterFileName), conf);
		}
		
		String[] opts = { workDir, outputDir, numReducers, jarFile };
		ToolRunner.run(conf, new SquareDriver(), opts);

		Utility.getFS().delete(new Path(outputDir));
	}
}

class SquareDriver extends Configured implements Tool{

	public int run(String[] args) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {
		// args: <working dir> <outputDir> <numReducers> <jarFile>
		Configuration conf = getConf();
		String workingDir = args[0];
		
		int numReducers = Integer.parseInt(args[2]);

		conf.setBoolean("mapred.compress.map.output", true);
		conf.set("mapred.map.output.compression.codec", "com.hadoop.compression.lzo.LzoCodec");
		//conf.set("mapred.map.output.compression.codec", "org.apache.hadoop.io.compress.DefaultCodec");
		
		Job job = new Job(conf, "TwinTwig Square");
		((JobConf)job.getConfiguration()).setJar(args[3]);
		
	    MultipleInputs.addInputPath(job, new Path(workingDir + Config.adjListDir + ".0"), 
	    		SequenceFileInputFormat.class, SquareMapper.class);

	    //job.setMapperClass(SquareMapper.class);			
		job.setReducerClass(SquareReducer.class);

		job.setMapOutputKeyClass(HVArraySign.class);
		job.setMapOutputValueClass(HVArray.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(HVArray.class);

		job.setSortComparatorClass(HVArraySignComparator.class);
		job.setGroupingComparatorClass(HVArrayGroupComparator.class);
		
		job.setNumReduceTasks(numReducers);
		
		FileOutputFormat.setOutputPath(job, new Path(args[1]));	
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(job, CompressionType.BLOCK);
		SequenceFileOutputFormat.setOutputCompressorClass(job, LzoCodec.class);

		job.waitForCompletion(true);
		return 0;
	}
}

class SquareMapper extends
		Mapper<LongWritable, HyperVertexAdjList, HVArraySign, HVArray> {

	private static BloomFilterOpr bloomfilterOpr = null;
	private static boolean enableBF;

	private TwinTwigGenerator ttwigGen = null;

	// The hypervertex set
	@Override
	public void map(LongWritable key, HyperVertexAdjList value, Context context)
			throws IOException, InterruptedException {
		if (enableBF) {
			ttwigGen = new TwinTwigGenerator(key.get(), value, bloomfilterOpr.get());
		} else {
			ttwigGen = new TwinTwigGenerator(key.get(), value);
		}
		ttwigGen.genTwinTwigOne(context, Config.SMALLSIGN, (byte) 3, (byte) 0);
		ttwigGen.genTwinTwigTwo(context, Config.LARGESIGN, (byte) 3);
		ttwigGen.genTwinTwigThree(context, Config.LARGESIGN, (byte) 3);
	}

	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		// We use bloomfilter as static. If it is already loaded, we will have
		// bloomfilterOpr != null, and we donot load it again in the case.
		enableBF = conf.getBoolean("enable.bloom.filter", false);
		if (enableBF && bloomfilterOpr == null) {
			bloomfilterOpr = new BloomFilterOpr(conf.getFloat(
					"bloom.filter.false.positive.rate", (float) 0.001), Config.TWINTWIG1);
			try {
				bloomfilterOpr.obtainBloomFilter(conf);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void cleanup(Context context) {
		if (ttwigGen != null) {
			ttwigGen.clear();
			ttwigGen = null;
		}
	}
}

class SquareReducer
	extends Reducer<HVArraySign, HVArray, NullWritable, HVArray> {
	private static TLongArrayList ttOneList = null;
	public void reduce(HVArraySign _key, Iterable<HVArray> values,
			Context context) throws IOException, InterruptedException {	
		if(_key.sign != Config.SMALLSIGN){
			return;
		}
		long v2 = _key.vertexArray.get(0);
		long v4 = _key.vertexArray.get(1);
		ttOneList = new TLongArrayList();

		for(HVArray value : values){
			if(_key.sign == Config.SMALLSIGN){
				ttOneList.add(value.getFirst());
			}
			else{
				for(long v1 : ttOneList.toArray()){
				    long array[] = {v1, v2, value.getFirst(), v4};
					context.write(NullWritable.get(), new HVArray(array));
			    }
			}
		}
		long[] ttOneArray = ttOneList.toArray();
		for(int i = 0; i < ttOneArray.length - 1; ++i){
			for(int j = i + 1; j < ttOneArray.length; ++j){
				long array[] = {ttOneArray[i], v2, ttOneArray[j], v4};
				if(array[0] > array[2]){
					long temp = array[2];
					array[2] = array[0];
					array[0] = temp;
				}
				context.write(NullWritable.get(), new HVArray(array));
			}
		}

		ttOneList.clear();
		ttOneList = null;
	}
}


