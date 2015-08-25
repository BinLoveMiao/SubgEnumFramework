package dbg.hadoop.subgenum.twintwig;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
import org.apache.hadoop.util.ToolRunner;

import com.hadoop.compression.lzo.LzoCodec;

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

public class FiveClique{
	public static void main(String[] args) throws Exception{
		String inputFilePath = "";
		double falsePositive = 0.001;
		boolean enableBF = true;
		int maxSize = 0;
		boolean isHyper = false;
		
		String jarFile = "";
		String numReducers = "";

		int valuePos = 0;
		for (int i = 0; i < args.length; ++i) {
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
			else if (args[i].contains("bloom.filter.false.positive.rate")) {
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					falsePositive = Double.parseDouble(args[i].substring(valuePos));
				}
			} 
			else if (args[i].contains("enable.bloom.filter")) {
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					enableBF = Boolean.parseBoolean(args[i].substring(valuePos));
				}
			}
			else if(args[i].contains("jar.file.name")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					jarFile = args[i].substring(valuePos);
				}
			}
			else if(args[i].contains("map.input.max.size")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					maxSize = Integer.valueOf(args[i].substring(valuePos));
				}
			}
			else if (args[i].contains("is.hypergraph")){
				valuePos = args[i].lastIndexOf("=") + 1;
				if (valuePos != 0) {
					isHyper = Boolean.valueOf(args[i].substring(valuePos));
				}
			}
		}
		
		if(inputFilePath.isEmpty()){
			System.err.println("Input file not specified!");
			System.exit(-1);;
		}
		
		String workDir = Utility.getWorkDir(inputFilePath);
		
		if (workDir.toLowerCase().contains("hdfs")) {
			int pos = workDir.substring("hdfs://".length()).indexOf("/")
					+ "hdfs://".length();
			Utility.setDefaultFS(workDir.substring(0, pos));
		} else {
			Utility.setDefaultFS("");
		}
		
		String stageOneOutput = workDir + "tt.5clique.tmp.1";
		String stageTwoOutput = workDir + "tt.5clique.tmp.2";
		String stageThreeOutput = workDir + "tt.5clique.tmp.3";
		String stageFourOutput = workDir + "tt.5clique.res";
		
		Configuration conf = new Configuration();
		conf.setBoolean("enable.bloom.filter", enableBF);
		conf.setFloat("bloom.filter.false.positive.rate", (float)falsePositive);
		
		if(enableBF){
			String bloomFilterFileName = "bloomFilter." + Config.EDGE + "." + falsePositive;
			DistributedCache.addCacheFile(new URI(new Path(workDir).toUri()
					.toString() + "/" + Config.bloomFilterFileDir + "/" + bloomFilterFileName), conf);
		}
		
		String adjListDir = isHyper ? workDir + Config.hyperGraphAdjList + "." + maxSize :
				workDir + Config.adjListDir + "." + maxSize;
		
		// The parameters: <inputDir> <outputDir> <numReducers> <jarFile>
		String opts[] = {adjListDir, stageOneOutput, numReducers, jarFile};		
		ToolRunner.run(conf, new FourCliqueStageOneDriver(), opts);
		
		String opts2[] = {adjListDir, stageOneOutput, stageTwoOutput, numReducers, jarFile};
		ToolRunner.run(conf, new FourCliqueStageTwoDriver(), opts2);
		
		conf.setInt("twin.twig.output.key.map", 3);
		String opts3[] = {adjListDir, stageTwoOutput, stageThreeOutput, numReducers, jarFile};
		ToolRunner.run(conf, new FiveCliqueStageThreeDriver(), opts3);
		
		conf.setInt("twin.twig.output.key.map", 7);
		String opts4[] = {adjListDir, stageThreeOutput, stageFourOutput, numReducers, jarFile};
		ToolRunner.run(conf, new FiveCliqueStageFourDriver(), opts4);
		
		Utility.getFS().delete(new Path(stageOneOutput));
		Utility.getFS().delete(new Path(stageTwoOutput));
		Utility.getFS().delete(new Path(stageThreeOutput));
		Utility.getFS().delete(new Path(stageFourOutput));
	}
}

/**
 * The five clique enumeration, stage three, driver. 
 * @author robeen
 *
 */
class FiveCliqueStageThreeDriver extends Configured implements Tool{

	public int run(String[] args) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {
		Configuration conf = getConf();
		// The parameters: <adjListDir> <stageTwoOutput> <outputDir> <numReducers> <jarFile>
		int numReducers = Integer.parseInt(args[3]);
		
		conf.setBoolean("mapreduce.map.output.compress", true);
		conf.set("mapreduce.map.output.compress.codec", "com.hadoop.compression.lzo.LzoCodec");
		
		Job job = new Job(conf, "TwinTwig FiveClique Stage three");
		((JobConf)job.getConfiguration()).setJar(args[4]);
		//JobConf job = new JobConf(getConf(), this.getClass());
		
		job.setReducerClass(FiveCliqueStageThreeReducer.class);
		
		job.setMapOutputKeyClass(HVArraySign.class);
		job.setMapOutputValueClass(HVArray.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(HVArray.class);
		
		job.setSortComparatorClass(HVArraySignComparator.class);
		job.setGroupingComparatorClass(HVArrayGroupComparator.class);
		
		job.setNumReduceTasks(numReducers);
		//FileInputFormat.setInputPaths(job, new Path(args[0]));
		MultipleInputs.addInputPath(job, 
				new Path(args[0]),
				SequenceFileInputFormat.class,
				FiveCliqueTwinTwigMapper.class);
		
		MultipleInputs.addInputPath(job, 
				new Path(args[1]),
				SequenceFileInputFormat.class,
				FiveCliqueStageThreeMapper.class);

		FileOutputFormat.setOutputPath(job, new Path(args[2]));
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(job, CompressionType.BLOCK);
		SequenceFileOutputFormat.setOutputCompressorClass(job, LzoCodec.class);

		job.waitForCompletion(true);
		return 0;
	}
}

class FiveCliqueTwinTwigMapper extends
		Mapper<LongWritable, HyperVertexAdjList, HVArraySign, HVArray> {

	private static BloomFilterOpr bloomfilterOpr = null;
	private static boolean enableBF;
	private static byte ttwigOutputKeyMap = (byte)3;
	private TwinTwigGenerator ttwigGen = null;

	// The hypervertex set
	@Override
	public void map(LongWritable key, HyperVertexAdjList value, Context context)
			throws IOException, InterruptedException {
		if (enableBF) {
			ttwigGen = new TwinTwigGenerator(key.get(), value,
					bloomfilterOpr.get());
		} else {
			ttwigGen = new TwinTwigGenerator(key.get(), value);
		}
		ttwigGen.genTwinTwigOne(context, Config.SMALLSIGN, ttwigOutputKeyMap, (byte) 0);
	}

	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		// We use bloomfilter as static. If it is already loaded, we will have
		// bloomfilterOpr != null, and we donot load it again in the case.
		enableBF = conf.getBoolean("enable.bloom.filter", false);
		ttwigOutputKeyMap = (byte) conf.getInt("twin.twig.output.key.map", 3);
		if (enableBF && bloomfilterOpr == null) {
			bloomfilterOpr = new BloomFilterOpr(conf.getFloat(
					"bloom.filter.false.positive.rate", (float) 0.001));
			try {
				bloomfilterOpr.obtainBloomFilter(conf);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

class FiveCliqueStageThreeMapper extends
		Mapper<NullWritable, HVArray, HVArraySign, HVArray> {
	
	@Override
	public void map(NullWritable key, HVArray value, Context context)
			throws IOException, InterruptedException {
		context.write(new HVArraySign(value.get(0), value.get(1), Config.LARGESIGN), 
				new HVArray(value.get(2), value.get(3)));
	}
}

class FiveCliqueStageThreeReducer extends
		Reducer<HVArraySign, HVArray, NullWritable, HVArray> {
	private static TLongArrayList ttOneList = null;
	private static BloomFilterOpr bloomfilterOpr = null;
	private static boolean enableBF;
	
	public void reduce(HVArraySign _key, Iterable<HVArray> values,
			Context context) throws IOException, InterruptedException {
		if (_key.sign != Config.SMALLSIGN) {
			return;
		}
		ttOneList = new TLongArrayList();
		
		long v1 = _key.vertexArray.get(0);
		long v2 = _key.vertexArray.get(1);
		
		boolean isOutput = true;

		for (HVArray value : values) {
			if (_key.sign == Config.SMALLSIGN) {
				ttOneList.add(value.getFirst());
			} else {
				for (long v0 : ttOneList.toArray()) {
					if(enableBF){
						isOutput = bloomfilterOpr.get().test(HyperVertex.VertexID(v0), 
								HyperVertex.VertexID(value.getFirst())) && 
								bloomfilterOpr.get().test(HyperVertex.VertexID(v0), 
										HyperVertex.VertexID(value.getSecond()));
					}
					if(isOutput){
						long array[] = { v0, v1, v2, value.getFirst(), value.getSecond() };
						context.write(NullWritable.get(), new HVArray(array));
					}
				}
			}
		}
		ttOneList.clear();
		ttOneList = null;
	}
	
	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		// We use bloomfilter as static. If it is already loaded, we will have
		// bloomfilterOpr != null, and we donot load it again in the case.
		enableBF = conf.getBoolean("enable.bloom.filter", false);
		if (enableBF && bloomfilterOpr == null) {
			bloomfilterOpr = new BloomFilterOpr(conf.getFloat(
					"bloom.filter.false.positive.rate", (float) 0.001));
			try {
				bloomfilterOpr.obtainBloomFilter(conf);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}

class FiveCliqueStageFourDriver extends Configured implements Tool{

	public int run(String[] args) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {
		Configuration conf = getConf();
		// The parameters: <adjListDir> <stageThreeOutput> <outputDir> <numReducers> <jarFile>
		int numReducers = Integer.parseInt(args[3]);
		
		conf.setBoolean("mapreduce.map.output.compress", true);
		conf.set("mapreduce.map.output.compress.codec", "com.hadoop.compression.lzo.LzoCodec");
		
		Job job = new Job(conf, "TwinTwig FiveClique Stage Four");
		((JobConf)job.getConfiguration()).setJar(args[4]);
		//JobConf job = new JobConf(getConf(), this.getClass());
		
		job.setReducerClass(FiveCliqueStageFourReducer.class);
		
		job.setMapOutputKeyClass(HVArraySign.class);
		job.setMapOutputValueClass(HVArray.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(HVArray.class);
		
		job.setSortComparatorClass(HVArraySignComparator.class);
		job.setGroupingComparatorClass(HVArrayGroupComparator.class);
		
		job.setNumReduceTasks(numReducers);
		//FileInputFormat.setInputPaths(job, new Path(args[0]));
		MultipleInputs.addInputPath(job, 
				new Path(args[0]),
				SequenceFileInputFormat.class,
				FiveCliqueTwinTwigMapper.class);
		
		MultipleInputs.addInputPath(job, 
				new Path(args[1]),
				SequenceFileInputFormat.class,
				FiveCliqueStageFourMapper.class);

		FileOutputFormat.setOutputPath(job, new Path(args[2]));
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputCompressionType(job, CompressionType.BLOCK);
		SequenceFileOutputFormat.setOutputCompressorClass(job, LzoCodec.class);

		job.waitForCompletion(true);
		return 0;
	}
}

class FiveCliqueStageFourMapper extends
		Mapper<NullWritable, HVArray, HVArraySign, HVArray> {
	@Override
	public void map(NullWritable key, HVArray value, Context context)
			throws IOException, InterruptedException {
		context.write(new HVArraySign(value.get(0), value.get(3), value.get(4),
				Config.LARGESIGN), new HVArray(value.get(1), value.get(2)));
	}
}

class FiveCliqueStageFourReducer extends
		Reducer<HVArraySign, HVArray, NullWritable, HVArray> {
	
	@Override
	public void reduce(HVArraySign _key, Iterable<HVArray> values,
			Context context) throws IOException, InterruptedException {
		if (_key.sign != Config.SMALLSIGN) {
			return;
		}

		for (HVArray value : values) {
			if (_key.sign == Config.SMALLSIGN) {
				continue;
			} else {
				context.write(NullWritable.get(), value);
			}
		}
	}
}
