import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Stream;


public class CountSameAs {


	public void run(ArrayList<String> classNames, ClassMapping cM, boolean d2y, boolean d2o,
			boolean y2d, boolean o2d) {
		System.out.println("Start CountSameAs.run()");
		long startTime = System.nanoTime();

		//get all instances of all classes
		//for every class (can contain more than one className for each KG)
		for (String className : classNames) {
			HashMap<String, ArrayList<String>> classMap = cM.getClassMap(className);
			System.out.println(classMap);
			
			//initialize HashMaps for getting the instances of the kgClassNames
			HashMap<String, HashSet<String>> dInstances = new HashMap<String, HashSet<String>>();
			HashMap<String, HashSet<String>> yInstances = new HashMap<String, HashSet<String>>();
			HashMap<String, HashSet<String>> oInstances = new HashMap<String, HashSet<String>>();
			//HashMap<String, HashSet<String>> nInstances = new HashMap<String, HashSet<String>>();
			//HashMap<String, HashSet<String>> wInstances = new HashMap<String, HashSet<String>>();
			
			//initialize the HashMaps for the sameAs counts
			HashMap<String, Integer> d2oCountMap = new HashMap<String, Integer>();
			HashMap<String, Integer> d2yCountMap = new HashMap<String, Integer>();
			HashMap<String, Integer> y2dCountMap = new HashMap<String, Integer>();
			HashMap<String, Integer> o2dCountMap = new HashMap<String, Integer>();
			
			String k = "";
			String countString = "";
			//dbpedia
			if (d2y || d2o) {
				k = "d";
				Path filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/DBpedia/resultsWithLabel/");
				getAllInstances(k, filePath, classMap, dInstances);
				//initialize countMap for DBpedia	
				if (classMap.containsKey(k)) {
					for (String kgClassName : classMap.get(k)) {
						d2oCountMap.put(kgClassName, 0);
						d2yCountMap.put(kgClassName, 0);
					
					}
				}
				//count owl:sameAs link		
				//DBpedia to YAGO
				if (d2y) {
					Path d2yPath = Paths.get("/Users/curtis/SeminarPaper_KG_files/DBpedia/owlSameAs/yago_links.nt");
					countString = "DBpedia:" + className + " to YAGO counts:";
					getCounts("d", d2yPath, cM, className, dInstances, d2yCountMap, countString);
				}
				//DBpedia to OpenCyc
				if (d2o) {
					Path d2oPath = Paths.get("/Users/curtis/SeminarPaper_KG_files/DBpedia/owlSameAs/opencyc_links.nt");
					
					countString = "DBpedia:" + className + " to OpenCyc counts:";
					getCounts("d", d2oPath, cM, className, dInstances, d2oCountMap, countString);
				}
			}
			//yago
			if (y2d) {
				k = "y";
				Path filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/YAGO/resultsWithLabel/");
				getAllInstances(k, filePath, classMap, yInstances);
				//initialize countMap for YAGO
				if (classMap.containsKey(k)) {
					for (String kgClassName : classMap.get(k)) {
						y2dCountMap.put(kgClassName, 0);
					}
				}
				//count owl:sameAs link		
				Path y2dPath = Paths.get("/Users/curtis/SeminarPaper_KG_files/YAGO/yagoDBpediaInstances.ttl");
				countString = "YAGO:" + className + " to DBpedia counts:"; 
				getCounts("y", y2dPath, cM, className, yInstances, y2dCountMap, countString);
			}
			//opencyc
			if (o2d) {
				k = "o";
				Path filePath = Paths.get("/Users/curtis/SeminarPaper_KG_files/OpenCyc/resultsWithLabel/");
				getAllInstances(k, filePath, classMap, oInstances);
				//initialize countMap for YAGO
				if (classMap.containsKey(k)) {
					for (String kgClassName : classMap.get(k)) {
						o2dCountMap.put(kgClassName, 0);
					}
				}
				//count owl:sameAs link
				Path o2dPath = Paths.get("/Users/curtis/SeminarPaper_KG_files/OpenCyc/opencyc-latest.nt");
				
				countString = "OpenCyc:" + className + " to DBpedia counts:"; 
				getCounts("o", o2dPath, cM, className, oInstances, o2dCountMap, countString);
			}
		}
		

		

		
	
		System.out.println("EXECUTION TIME: " +  ((System.nanoTime() - startTime)/1000000000) + " seconds." );
	}
	
	private static void getAllInstances(String k, Path filePath,
			HashMap<String, ArrayList<String>> classMap,
			HashMap<String, HashSet<String>> instances) {
		//for each className in the KG
		if (classMap.containsKey(k)) {
			for (String kgClassName : classMap.get(k)) {
				instances.put(kgClassName, new HashSet<String>());
				Path fileName = Paths.get(filePath + "/" + kgClassName + "InstancesWithLabels.txt");
				try (Stream<String> stream = Files.lines(fileName)) {
					stream.forEach(line -> getInstance(line, kgClassName, instances));
				} catch (IOException e) {
					e.printStackTrace();
				}
			//System.out.println(dInstances.get(kgClassName));
			}
		}
		
	}

	private static void getCounts(String kg, Path path, ClassMapping cM,
			String className,
			HashMap<String, HashSet<String>> kgInstances,
			HashMap<String, Integer> dCountMap, String countString) {
		try (Stream<String> stream = Files.lines(path)) {
			stream.forEach(line -> checkAndCountLinks(line, kg, cM, className, kgInstances, dCountMap));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(countString + dCountMap);
		
	}

	private static void checkAndCountLinks(String line, String kg,
			ClassMapping cM, String className, HashMap<String, HashSet<String>> instances,
			HashMap<String, Integer> countMap) {
		//split file on whitespace for DBpedia
		String[] words = line.split("\\s+");
		//for all classes
		//for (String className : className) {
			HashMap<String, ArrayList<String>> classMap = cM.getClassMap(className);
			//for all kgClassNames
			if (classMap.containsKey(kg)) {
				for (String kgClassName : classMap.get(kg)) {
					//check if s (word[0] is contained in the instance list
					if (words[0] != null) {
						//special case for OpenCyc (full file has to be used)
						if (!kg.equals("o") || 
								(words[1] != null && words[1].equals("<http://www.w3.org/2002/07/owl#sameAs>") &&
								words[2] != null && words[2].contains("<http://dbpedia.org/resource/"))
								) {
							if(instances.containsKey(kgClassName)) {
								if(instances.get(kgClassName).contains(words[0])) {
									//System.out.println(words[0]);
									countMap.put(kgClassName, countMap.get(kgClassName) + 1);
								}
							}
							//check if p is owl:sameAs
						/*	if (words[1] != null) {
								if (!words[1].equals("<http://www.w3.org/2002/07/owl#sameAs>")) {
									break;
								}
							}*/
						} //DBpedia and YAGO
						
						
					}
				}
			}
			
		//}
	}

	/**
	  * Split line on tab and add first element (instance uri) to HashMap
	  * @param line
	 * @param kgClassName 
	 * @param dInstances 
	 */
	private static void getInstance(String line, String kgClassName, HashMap<String, HashSet<String>> dInstances) {
		String[] words = line.split("\\t");
		dInstances.get(kgClassName).add(words[0]);
	}

	
}
