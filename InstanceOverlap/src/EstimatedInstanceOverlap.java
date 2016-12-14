import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


public class EstimatedInstanceOverlap {

	public void run(ArrayList<String> classNames, ClassMapping cM) throws IOException {
		
		//for each x2y (d2y, d2o, y2d, o2d)
		String k = "d";
		String x2y = "d2y";
		
		
			//for each className
			for (String className : classNames) {
				HashMap<String, ArrayList<String>> classMap = cM.getClassMap(className);
				if (classMap.containsKey(k)) {
					//for each KGclass
					for (String kgClass : classMap.get(k)) {
						// read partial gold standard (owl:sameAs links)
						
						HashSet<Pair<String, String>> r_p = readGoldStandard(k, x2y, kgClass);
						//System.out.println("r_p.size(): " + r_p.size());
						
						//for each simMeasure
						String simMeasure = "scaledLevenstein";
						//String simMeasure = "jaroWinkler";
						
							//for each threshold
							String threshold = "0.9";
							
								// read matching alignment
								HashSet<Pair<String, String>> a = readStringMatchingAlignment(k, x2y, kgClass, simMeasure, threshold);
								//System.out.println("a.size(): " + a.size());
								//get partial matching alignment
								HashSet<Pair<String, String>> a_p = getPartialMatchingAlignment(a, r_p);
								//System.out.println("a_p.size(): " + a_p.size());
								
								int tp = getTruePositives(r_p, a_p);
								
								double recall = (double) tp / r_p.size();
								double precision = (double) tp / a_p.size();
								
								double fMeasure = (2 * precision * recall) / (precision + recall);
								
								double estimatedOverlap = (precision * a.size()) / recall;
								
								System.out.println(x2y+", "+ kgClass + ", "+ simMeasure + ", " + threshold + ", "+ precision + ", " + recall + ", "+ fMeasure + ", " + estimatedOverlap);
								
					}
					
				}
			}
		
		
	}
	private int getTruePositives(HashSet<Pair<String, String>> r_p,
			HashSet<Pair<String, String>> a_p) {
		int tp = 0;
		for (Pair<String, String> rPair : r_p) {
			for (Pair<String, String> aPair : a_p) {
				if (rPair.equals(aPair)) {
					tp += 1;
				}
			}
		}
		return tp;
	}
	/**
	   * Defined as the subset of A which contains all elements in A which share at least one entity with an element in R′
	   * @param A
	   * @param R'
	   * @return HashSet<Pair<String, String> containing all pairs in the partial alignment
	   */
	private HashSet<Pair<String, String>> getPartialMatchingAlignment(
			HashSet<Pair<String, String>> a, HashSet<Pair<String, String>> r_p) {
		
		HashSet<Pair<String, String>> a_p = new HashSet<Pair<String, String>>();
		
		//get left and right entity of the partial gold standard R'
		HashSet<String> leftEntities = new HashSet<String>();
		HashSet<String> rightEntities = new HashSet<String>();
		for (Pair<String,String> r_p_pair : r_p) {
			leftEntities.add(r_p_pair.getLeft());
			rightEntities.add(r_p_pair.getRight());
		}
		
		//create A': add pair if A shares at least one entity with an element in the partial gold standard R'
		for (Pair<String, String> a_pair : a) {
			String aLeft = a_pair.getLeft();
			String aRight = a_pair.getRight();
			// check if at least one entity is shared
			if (leftEntities.contains(aLeft) || rightEntities.contains(aRight)) {
				a_p.add(a_pair);
			}
			
			
		}
		return a_p;
	}

	private HashSet<Pair<String, String>> readStringMatchingAlignment(String k,
			String x2y, String kgClass, String simMeasure, String threshold) throws FileNotFoundException, IOException {
		String fileName = getFolderPath(k, x2y) + simMeasure + "/" + threshold + "/" + kgClass+".tsv";
		return readPairs(fileName);
	}

	private HashSet<Pair<String, String>> readGoldStandard(String k,
			String x2y, String kgClass) throws IOException {
		
		String fileName = getFolderPath(k, x2y) + "owlSameAs/"+kgClass+".tsv";
		return readPairs(fileName);
	}

	

	private HashSet<Pair<String, String>> readPairs(String fileName) throws FileNotFoundException, IOException {
		HashSet<Pair<String, String>> pairSet = new HashSet<Pair<String, String>>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		    	String[] values = line.split("\t");
		    	//delete yago link for d2y case
				if (values[1].contains("http://yago-knowledge.org/resource/")) {
					//delete <http://yago-knowledge.org/resource/WORD_TO_KEEP>
					values[1] = "<" + values[1].substring(36, values[1].length());
				}
		    	Pair<String, String> p = new ImmutablePair<String, String>(values[0], values[1]);
		    	pairSet.add(p);
		    }
		}
		return pairSet;
	}
	
	private String getFolderPath(String k, String x2y) {
		String folder = "";
		switch(k) {
		case "d":
			folder = "DBpedia"; 
			break;
		case "y":
			folder ="YAGO";
			break;
		case "o":
			folder = "OpenCyc";
			break;
		}
		return "/Users/curtis/SeminarPaper_KG_files/"+folder+"/"+x2y+"/";
	}

}
