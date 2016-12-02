import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Stream;


public class CountStringSimilarity {

	public void run(ArrayList<String> classNames, ClassMapping cM) {
		System.out.println("Start CountStringSimilarity.run()");
		long startTime = System.nanoTime();
		
		
		//result Object: HashMap<stringMeasureName, <HashMap<x2y, intScore>>
		HashMap<String, HashMap<String, Integer>> results = new HashMap<String, HashMap<String, Integer>>();
		
		//instanceLabels: HashMap<k, <HashMap<kgClass, <HashSet<englishLabels>>>
		HashMap<String, HashMap<String, HashSet<String>>> instanceLabels = new HashMap<String, HashMap<String, HashSet<String>>>();
		
		//for each class
		for (String className : classNames) {
			//clear all objects
			results.clear();
			instanceLabels.clear();
			HashMap<String, ArrayList<String>> classMap = cM.getClassMap(className);//key: d,w,y,o,n ; value:kgC
			System.out.println(classMap);
			
			//get instances for each kgClass with all labels
			instanceLabels = getInstanceLabels(classMap);
			
			
			
			//print results
			System.out.println(results);
		}
			
		
		System.out.println("EXECUTION TIME: " +  ((System.nanoTime() - startTime)/1000000000) + " seconds." );
	}

	private HashMap<String, HashMap<String, HashSet<String>>> getInstanceLabels(
			HashMap<String, ArrayList<String>> classMap) {
		HashMap<String, HashMap<String, HashSet<String>>> instanceLabels = new HashMap<String, HashMap<String, HashSet<String>>>();
		//HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass = new HashMap<String, HashSet<String>>();
		for (String k : classMap.keySet()) {
		    for (String kgClass : classMap.get(k)) {
		    	//System.out.println(kgClass);
		    	//get all instance labels for the kgClass and save them in the instanceLabels object
		    	instanceLabels.put(k, getInstanceLabelsForKgClass(k, kgClass));
		    }
		}
		return instanceLabels;
	}

	private HashMap<String, HashSet<String>> getInstanceLabelsForKgClass(
			String k, String kgClass) {
		HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass = new HashMap<String, HashSet<String>>();
		
		//get file paths 
		Path filePath = null;				
		
		System.out.println(k + ": "+kgClass);
		switch (k) {
			case "d":
				System.out.println("d found");
				filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/DBpedia/resultsWithLabel/");
				break;
			case "y":
				filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/YAGO/resultsWithLabel/");
				break;
			case "o":
				filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/OpenCyc/resultsWithLabel/");
				break;
			case "n":
				filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/NELL/resultsWithLabel/");
				break;
			case "w":
				filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/Wikidata/resultsWithLabel/");
				break;
			default:
				System.out.println("error in getInstanceLabelsForKgClass(). No matching k found");
		}
		instanceLabelsForSingleKgClass = readFile(filePath, kgClass);
		
		return instanceLabelsForSingleKgClass;
	}

	private HashMap<String, HashSet<String>> readFile(Path filePath,
			String kgClass) {
		HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass = new HashMap<String, HashSet<String>>();
		Path fileName = Paths.get(filePath + "/" + kgClass + "InstancesWithLabels.txt");
		try (Stream<String> stream = Files.lines(fileName)) {
			stream.forEach(line -> addLineToHashMap(line, kgClass, instanceLabelsForSingleKgClass));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return instanceLabelsForSingleKgClass;
	}

	private static void addLineToHashMap(String line, String kgClass,
			HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass) {
		String[] words = line.split("\\t");
		HashSet<String> allLabels = new HashSet<String>();
		for (int i = 1; i < words.length; i++) {
			allLabels.add(words[i]);
			System.out.println(words[i]);
		}
		
		instanceLabelsForSingleKgClass.put(words[0], allLabels);		
	}

}