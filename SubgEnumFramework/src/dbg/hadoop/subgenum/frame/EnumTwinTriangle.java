package dbg.hadoop.subgenum.frame;

import gnu.trove.map.hash.TLongIntHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.ToolRunner;

import dbg.hadoop.subgraphs.io.HVArray;
import dbg.hadoop.subgraphs.io.HyperVertexComparator;
import dbg.hadoop.subgraphs.utils.InputInfo;

public class EnumTwinTriangle {
	
	public static void run(InputInfo inputInfo) throws Exception{
		String workDir = inputInfo.workDir;
		
		Configuration conf = new Configuration();
		conf.setBoolean("result.compression", inputInfo.isResultCompression);
		
		String[] opts = { workDir + "triangle.res", "",
				workDir + "frame.twintriangle.res",  inputInfo.numReducers, inputInfo.jarFile };
		
		if (!inputInfo.isCountOnly) {
			ToolRunner.run(conf,
					new GeneralDriver(
							"Frame Square",
							EnumTwinTriangleMapper.class,
							EnumTwinTriangleReducer.class,
							LongWritable.class,
							HVArray.class, // OutputKV
							// LongWritable.class, HVArray.class, //MapOutputKV
							SequenceFileInputFormat.class,
							SequenceFileOutputFormat.class,
							HyperVertexComparator.class), opts);
		}
		else {
			ToolRunner.run(conf,
					new GeneralDriver(
							"Frame Square",
							EnumTwinTriangleMapper.class,
							EnumTwinTriangleCountReducer.class,
							NullWritable.class,
							LongWritable.class, // OutputKV
							LongWritable.class,
							HVArray.class, // MapOutputKV
							SequenceFileInputFormat.class,
							SequenceFileOutputFormat.class,
							HyperVertexComparator.class), opts);
		}
	}
	
	public static void countOnce(InputInfo inputInfo) throws Exception{
		if (inputInfo.isCountPatternOnce) {
			String[] opts = { inputInfo.workDir + "frame.twintriangle.res",
					inputInfo.workDir + "frame.twintriangle.cnt",
					inputInfo.numReducers, inputInfo.jarFile };
			
			if(inputInfo.isCountOnly) {
				ToolRunner.run(new Configuration(),  new GeneralPatternCountDriver(
						GeneralPatternCountIdentityMapper.class), opts);
			}
			else if(inputInfo.isResultCompression) {
				ToolRunner.run(new Configuration(), new GeneralPatternCountDriver(
					TwinTriangleCountMapper.class), opts);
			}
			else {
				System.out.println("Not count needed.");
			}
		}
	}
}

class EnumTwinTriangleMapper extends
	Mapper<NullWritable, HVArray, LongWritable, HVArray> {

	@Override
	public void map(NullWritable _key, HVArray _value, Context context)
			throws IOException, InterruptedException {
		context.write(new LongWritable(_value.getFirst()),
				new HVArray(_value.getSecond(), _value.getLast()));
		context.write(new LongWritable(_value.getSecond()),
				new HVArray(_value.getFirst(), _value.getLast()));
		context.write(new LongWritable(_value.getLast()),
				new HVArray(_value.getFirst(), _value.getSecond()));
	}
}

class EnumTwinTriangleReducer extends
	Reducer<LongWritable, HVArray, LongWritable, HVArray> {
	
	private static boolean isCompress = true;
	//MaxHeap<HVArray> heap = null;
	ArrayList<HVArray> heap = null;
	TLongIntHashMap firstItemMap = null;
	
	@Override
	public void reduce(LongWritable _key, Iterable<HVArray> values, Context context) 
			throws IOException, InterruptedException{
		if(heap.size() != 0){
			heap.clear();
		}
		for(HVArray val: values){
			heap.add(new HVArray(val));
		}
		//heap.sort();
		HVArray[] array = heap.toArray(new HVArray[0]);
		Arrays.sort(array);
		//Comparable<HVArray>[] array = heap.toArray();
		//System.out.println(heap);
		if(!isCompress){
			long curItem = -1L;
			firstItemMap.clear();
			for(int i = 0; i < array.length; ++i){
				long v1 = array[i].getFirst();
				if(curItem != v1){
					if(curItem == -1){
						firstItemMap.put(v1, 0);
					} else{
						firstItemMap.put(v1, firstItemMap.get(curItem));
					}
					curItem = v1;
				}
				firstItemMap.increment(v1);
			}
			for(int i = 0; i < array.length; ++i){
				long v1 = array[i].getFirst();
				long v2 = array[i].getSecond();

				for(int j = firstItemMap.get(v1); j < array.length; ++j){
					long v3 = array[j].getFirst();
					long v4 = array[j].getSecond();
					if(v2 != v3 && v2 != v4){
						long[] out = { v1, v2, v3, v4 };
						context.write(_key, new HVArray(out));
					}
				}
			}
		} 
		else{
			long[] outArray = new long[array.length * 2];
			for(int i = 0; i < array.length; ++i){
				outArray[2 * i] = array[i].getFirst();
				outArray[2 * i + 1] = array[i].getSecond();
			}
			context.write(_key, new HVArray(outArray));
		}
		
	}
	 
	@Override
	public void setup(Context context){
		isCompress = context.getConfiguration().getBoolean("result.compression", true);
		//heap = new MaxHeap<HVArray>(Config.HEAPINITSIZE);
		heap = new ArrayList<HVArray>();
		if(!isCompress)
			firstItemMap = new TLongIntHashMap();
	}
	
	@Override
	public void cleanup(Context context){
		heap = null;
		if(firstItemMap != null){
			firstItemMap.clear();
			firstItemMap = null;
		}
	}
}

class EnumTwinTriangleCountReducer extends
		Reducer<LongWritable, HVArray, NullWritable, LongWritable> {

	private static ArrayList<HVArray> heap = null;
	private static TLongIntHashMap firstItemMap = null;
	private static boolean isCompress = true; 

	@Override
	public void reduce(LongWritable _key, Iterable<HVArray> values,
			Context context) throws IOException, InterruptedException {
		if (heap.size() != 0) {
			heap.clear();
		}
		for (HVArray val : values) {
			heap.add(new HVArray(val));
		}
		// heap.sort();
		HVArray[] array = heap.toArray(new HVArray[0]);
		Arrays.sort(array);
		// Comparable<HVArray>[] array = heap.toArray();
	
		long curItem = -1L;
		long count = 0L;
		long v1, v2, v3, v4;
		firstItemMap.clear();
		for (int i = 0; i < array.length; ++i) {
			v1 = array[i].getFirst();
			if (curItem != v1) {
				if (curItem == -1) {
					firstItemMap.put(v1, 0);
				} else {
					firstItemMap.put(v1, firstItemMap.get(curItem));
				}
				curItem = v1;
			}
			firstItemMap.increment(v1);
		}
		for (int i = 0; i < array.length; ++i) {
			v1 = array[i].getFirst();
			v2 = array[i].getSecond();
			
			if (!isCompress) {
				for (int j = firstItemMap.get(v1); j < array.length; ++j) {
					v3 = array[j].getFirst();
					v4 = array[j].getSecond();
					if (v2 != v3 && v2 != v4) {
						++count;
					}
				}
			}
			else {
				int index = firstItemMap.get(v1);
				while(index < array.length && array[index].getFirst() <= v2){
					v3 = array[index].getFirst();
					v4 = array[index].getSecond();
					if(v3 != v2 && v4 != v2){
						count += 1;
					}
					index += 1;
				}
				if(index < array.length) {
					count += (array.length - index);
				}
			}
		}
		if(count > 0)
			context.write(NullWritable.get(), new LongWritable(count));
	}

	@Override
	public void setup(Context context) {
		heap = new ArrayList<HVArray>();
		firstItemMap = new TLongIntHashMap();
		isCompress = context.getConfiguration().getBoolean("result.compression", true);
	}

	@Override
	public void cleanup(Context context) {
		heap = null;
		if (firstItemMap != null) {
			firstItemMap.clear();
			firstItemMap = null;
		}
	}
}

class TwinTriangleCountMapper extends
		Mapper<LongWritable, HVArray, NullWritable, LongWritable> {
	
	private static TLongIntHashMap firstItemMap = null;
	
	@Override
	public void map(LongWritable _key, HVArray _value, Context context)
			throws IOException, InterruptedException {
		long[] array = _value.toArrays();
		assert(array.length % 2 == 0);

		firstItemMap.clear();
		long curItem = -1L;
		long count = 0L;

		for(int i = 0; i < array.length; i += 2){
			long v1 = array[i];
			if(curItem != v1){
				if(curItem == -1){
					firstItemMap.put(v1, 0);
				} else{
					firstItemMap.put(v1, firstItemMap.get(curItem));
				}
				curItem = v1;
			}
			firstItemMap.adjustValue(v1, 2);
		}
		
		for(int i = 0; i < array.length; i += 2){
			long v1 = array[i];
			long v2 = array[i + 1];
			int index = firstItemMap.get(v1);

			while(index < array.length && array[index] <= v2){
				if(array[index] != v2 && array[index + 1] != v2){
					count += 1;
				}
				index += 2;
			}
			if(index < array.length)
				count += (array.length - index) / 2;
		}
		if(count > 0)
			context.write(NullWritable.get(), new LongWritable(count));
	}
	
	@Override
	public void setup(Context context){
		firstItemMap = new TLongIntHashMap();
	}
	
	@Override
	public void cleanup(Context context){
		firstItemMap.clear();
		firstItemMap = null;
	}
}
