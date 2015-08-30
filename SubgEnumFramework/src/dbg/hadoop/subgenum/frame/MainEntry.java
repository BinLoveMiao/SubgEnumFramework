package dbg.hadoop.subgenum.frame;


import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import dbg.hadoop.subgraphs.utils.InputInfo;
import dbg.hadoop.subgraphs.utils.Utility;

public class MainEntry{
	private static InputInfo inputInfo = null;
	private static Logger log = Logger.getLogger(MainEntry.class);
	
	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws Exception{
		inputInfo = new InputInfo(args);
		String workDir = inputInfo.workDir;
		String query = inputInfo.query.toLowerCase();
		long startTime = 0;
		long endTime = 0;
		// Square is query: q1
		if(query.compareTo("square") == 0 || query.compareTo("q1") == 0){
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.square.res"))){
				Utility.getFS().delete(new Path(workDir + "frame.square.res"));
			}
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.square.cnt"))){
				Utility.getFS().delete(new Path(workDir + "frame.square.cnt"));
			}
			log.info("Start enumerating square...");
			startTime = System.currentTimeMillis();
			
			EnumSquare.run(inputInfo);
			
			endTime=System.currentTimeMillis();
			log.info("Time elapsed: " + (endTime - startTime) / 1000 + "s");
			EnumSquare.countOnce(inputInfo);
		}
		// Chordal Square is query: q2
		else if(query.compareTo("chordalsquare") == 0 || query.compareTo("q2") == 0){
			// Delete existed output
			if (Utility.getFS().isDirectory(new Path(workDir + "frame.csquare.res"))) {
				Utility.getFS().delete(new Path(workDir + "frame.csquare.res"));
			}
			if (Utility.getFS().isDirectory(new Path(workDir + "frame.csquare.cnt"))) {
				Utility.getFS().delete(new Path(workDir + "frame.csquare.cnt"));
			}
			log.info("Start enumerating chordal square...");
			startTime = System.currentTimeMillis();
			
			EnumChordalSquare.run(inputInfo);
			
			endTime=System.currentTimeMillis();
			log.info("Time elapsed: " + (endTime - startTime) / 1000 + "s");
			
			EnumChordalSquare.countOnce(inputInfo);
		}
		// k-clique is query: q3
		else if (query.compareTo("clique") == 0 || query.compareTo("q3") == 0) {
			// Delete existed output
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.clique.res"))){
				Utility.getFS().delete(new Path(workDir + "frame.clique.res"));
			}
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.clique.cnt"))){
				Utility.getFS().delete(new Path(workDir + "frame.clique.cnt"));
			}
			log.info("Start enumerating " + inputInfo.cliqueNumVertices + "-clique...");
			startTime = System.currentTimeMillis();
			
			EnumClique.run(inputInfo);
			
			endTime=System.currentTimeMillis();
			log.info("Time elapsed: " + (endTime - startTime) / 1000 + "s");
			
			EnumClique.countOnce(inputInfo);
		}
		// House is query: q4
		else if (query.compareTo("house") == 0 || query.compareTo("q4") == 0) {
			if(!inputInfo.isSquareSkip){
				if(Utility.getFS().isDirectory(new Path(workDir + "frame.square.res"))){
					Utility.getFS().delete(new Path(workDir + "frame.square.res"));
				}
			}
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.house.tmp.2"))){
				Utility.getFS().delete(new Path(workDir + "frame.house.tmp.2"));
			}
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.house.res"))){
				Utility.getFS().delete(new Path(workDir + "frame.house.res"));
			}
			if(Utility.getFS().isDirectory(new Path(workDir + "frame.house.cnt"))){
				Utility.getFS().delete(new Path(workDir + "frame.house.cnt"));
			}
			log.info("Start enumerating house...");
			startTime = System.currentTimeMillis();
			
			EnumHouse.run(inputInfo);
			
			endTime=System.currentTimeMillis();
			log.info("Time elapsed: " + (endTime - startTime) / 1000 + "s");
			
			EnumHouse.countOnce(inputInfo);
		}
		// Solar Square is query: q5
		else if (query.compareTo("solarsquare") == 0 || query.compareTo("q5") == 0) {
			// Delete existed output
			if(!inputInfo.isChordalSquareSkip){
				if (Utility.getFS().isDirectory(new Path(workDir + "frame.csquare.res"))) {
					Utility.getFS().delete(new Path(workDir + "frame.csquare.res"));
				}
			}
			if (Utility.getFS().isDirectory(new Path(workDir + "frame.solarsquare.res"))) {
				Utility.getFS().delete(new Path(workDir + "frame.solarsquare.res"));
			}
			if (Utility.getFS().isDirectory(new Path(workDir + "frame.solarsquare.cnt"))) {
				Utility.getFS().delete(new Path(workDir + "frame.solarsquare.cnt"));
			}
			log.info("Start enumerating solar square...");
			startTime = System.currentTimeMillis();
			
			EnumSolarSquare.run(inputInfo);
			
			endTime=System.currentTimeMillis();
			log.info("Time elapsed: " + (endTime - startTime) / 1000 + "s");
			
			EnumSolarSquare.countOnce(inputInfo);
		}
	}
}