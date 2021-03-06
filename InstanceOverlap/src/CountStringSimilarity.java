import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


public class CountStringSimilarity {
	private boolean useSamples;
	//similarity measure strings
	private String jaccardS = "jaccard";
	private String jaroS = "jaro";
	private String scaledLevensteinS = "scaledLevenstein";
	private String tfidfS = "tfidf";
	private String jaroWinklerS ="jaroWinkler";
	private String mongeElkanS = "mongeElkan";
	private String exactMatchS = "exactMatch";
	private String softTfidfS = "softTfidf";
	private String allS = "all";
	private HashSet<String> simMeasuresThresholdH;
	private HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocksPerInstance;
	

	public HashMap<String, HashMap<String, Integer>> run(String className, 
			ClassMapping cM, 
			StringMeasures stringMeasures, 
			boolean useSamples, 
			ArrayList<Double> thresholdsH, 
			ArrayList<Double> thresholdsL, 
			ArrayList<Double> thresholdsJaccard,
			HashSet<String> simMeasuresThresholdH,
			int maxBlockSize) {
		System.out.println("Start CountStringSimilarity.run()");
		this.useSamples = useSamples;
		//long startTime = System.nanoTime();
		this.simMeasuresThresholdH = simMeasuresThresholdH;
		

			
		CountStringSimilarityResults results = new CountStringSimilarityResults();
		/*StringSimilarityPairs resultPairsD2y = new StringSimilarityPairs();
		StringSimilarityPairs resultPairsD2o = new StringSimilarityPairs();
		StringSimilarityPairs resultPairsY2d = new StringSimilarityPairs();
		StringSimilarityPairs resultPairsO2d = new StringSimilarityPairs();*/
	
		//instanceLabels: HashMap<k, <HashMap<kgClass,<HashMap<instanceURI, <HashSet<englishLabels>>>>
		HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> kKgClassInstanceLabels = new HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>>();
		HashMap<String, HashMap<String, Integer>> kKgClassInstanceCounts = new HashMap<String, HashMap<String, Integer>>();
		HashMap<String, ArrayList<String>> classMap = cM.getClassMap(className);//key: d,w,y,o,n ; value:kgC
		System.out.println(classMap);
		
		//get instances for each kgClass with all labels
		kKgClassInstanceLabels = getInstanceLabels(classMap);
		/*System.out.println(instanceLabels.get("d"));
		System.out.println(instanceLabels.get("y"));
		System.out.println(instanceLabels.get("n"));
		System.out.println(instanceLabels.get("o"));
		System.out.println(instanceLabels.get("w"));
		*/
		for (String k : kKgClassInstanceLabels.keySet()) {
			for (String kgClass : kKgClassInstanceLabels.get(k).keySet()) {
				int kgClassSize = kKgClassInstanceLabels.get(k).get(kgClass).size();			
				if (kKgClassInstanceCounts.containsKey(k)) {
					kKgClassInstanceCounts.get(k).put(kgClass, kgClassSize);
				} else {
					HashMap<String, Integer> kgClassMap = new HashMap<>();
					kgClassMap.put(kgClass, kgClassSize);
					kKgClassInstanceCounts.put(k, kgClassMap);
				}
			}
		}
		
		//STANDARD TOKEN BLOCKING
		//create blocks
		/*
		 *HashMap<token, <HashMap<k, HashMap<kgClass, <HashSet<URIs>>>>>> 
		 */
		HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks = createBlocks(kKgClassInstanceLabels);
		HashMap<String, Integer> sortedBlocks = blockCleaning(blocks, 2, maxBlockSize);
		getBlockDistribution(className, blocks);
		getMatchedStringPairs(results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, blocks, sortedBlocks);
				
		return kKgClassInstanceCounts;
		
		//long startTime = System.nanoTime();
		//System.out.println("EXECUTION TIME: " +  ((System.nanoTime() - startTime)/1000000000) + " seconds." );
	}

	/**
	 * Clean blocks that have minBlockSize < blockSize > maxBlockSize
	 * @param blocks
	 * @param minBlockSize
	 * @return sortedBlockSizes
	 */
	private HashMap<String, Integer> blockCleaning(
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks,
			int minBlockSize,
			int maxBlockSize) {
		System.out.println("Removing blocks...");
		HashMap<String, Integer> blockSizes = removeBlocks(blocks, minBlockSize, maxBlockSize);
		
		System.out.println("Clean blocks...");
		HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> removeFromBlock = new HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>>();
		for (String k : this.blocksPerInstance.keySet()) {
			for (String kgClass : this.blocksPerInstance.get(k).keySet()) {
				for (String uri : this.blocksPerInstance.get(k).get(kgClass).keySet()) {
					int smallestBlockSize = 0;
					ArrayList<String> smallestBlockingKeys = new ArrayList<>();
					boolean first = true;
					//compare block sizes
					for (String blockingKey : this.blocksPerInstance.get(k).get(kgClass).get(uri)) {
						//check if blockingKey is in blockSizes (blocks with one element are already removed)
						if (blockSizes.containsKey(blockingKey)) {
							if (first) {
								smallestBlockSize = blockSizes.get(blockingKey);
								smallestBlockingKeys.add(blockingKey);
								first = false;
							} else {
								//check block size of current block
								if (blockSizes.get(blockingKey) == smallestBlockSize) {
									smallestBlockingKeys.add(blockingKey);
								} else if (blockSizes.get(blockingKey) < smallestBlockSize) {
									//add previous block to remove list
									addMultipleToRemoveList(removeFromBlock, smallestBlockingKeys, k, kgClass, uri);
									//update current item
									smallestBlockingKeys.clear();
									smallestBlockSize = blockSizes.get(blockingKey);
									smallestBlockingKeys.add(blockingKey);
								} else {
									//add current to remove list
									addSingleToRemoveList(removeFromBlock, blockingKey, k, kgClass, uri);
								}
							}
						}
					}
				}		
			}
		}
		//remove instances from blocks
		int removeCounter = 0;
		for (String bk : removeFromBlock.keySet()) {
			for (String k : removeFromBlock.get(bk).keySet()) {
				for (String kgClass : removeFromBlock.get(bk).get(k).keySet()) {
					for (String removeUri : removeFromBlock.get(bk).get(k).get(kgClass)) {
						blocks.get(bk).get(k).get(kgClass).remove(removeUri);
						removeCounter++;
					}
				}
				
			}
		}
		System.out.println(removeCounter + " instances removed from unspecific blocks");
		System.out.println("Removing blocks...");
		blockSizes = removeBlocks(blocks, minBlockSize, maxBlockSize);
		
		HashMap<String, Integer> sortedBlockDist = sortByValue(blockSizes);
		return sortedBlockDist;
		
	}
	private HashMap<String, Integer> removeBlocks(
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks,
			int minBlockSize,
			int maxBlockSize) {
		HashSet<String> blocksToDelete = new HashSet<String>();
		HashMap<String, Integer> blockSizes = new HashMap<String, Integer>();
		for (String blockingKey : blocks.keySet()) {
			int c = 0;
			for (String k : blocks.get(blockingKey).keySet()) {
				for (String kgClass : blocks.get(blockingKey).get(k).keySet()) {
					c += blocks.get(blockingKey).get(k).get(kgClass).size();
				}
			}
			if (c < minBlockSize || (c > maxBlockSize && maxBlockSize!=0) ) {
				blocksToDelete.add(blockingKey);
			} else {
				blockSizes.put(blockingKey, c);
			}
		}	
		for (String blockingKey : blocksToDelete) {
			blocks.remove(blockingKey);
		}
		System.out.println(blocksToDelete.size() + " blocks removed  " + minBlockSize+ " <= blocks.size() >= " + maxBlockSize);

		return blockSizes;
	}

	private void addMultipleToRemoveList(
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> removeFromBlock,
			ArrayList<String> bks,
			String k,
			String kgClass,
			String uri) {
		for (String bk : bks) {
			addSingleToRemoveList(removeFromBlock, bk, k, kgClass, uri);
		}
		
	}

	private void addSingleToRemoveList(
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> removeFromBlock,
			String bk,
			String k,
			String kgClass,
			String uri) {
		if (removeFromBlock.containsKey(bk)) {
			if (removeFromBlock.get(bk).containsKey(k)) {
				
				if (removeFromBlock.get(bk).get(k).containsKey(kgClass)) {
					removeFromBlock.get(bk).get(k).get(kgClass).add(uri);
				} else {//create kgClass
					HashSet<String> uris = createHashSet(uri);
					removeFromBlock.get(bk).get(k).put(kgClass, uris);
				
				}
				
			} else {//create k
				HashMap<String, HashSet<String>> kgClassUris = createHashMapWithHashSet(kgClass, uri);
				removeFromBlock.get(bk).put(k, kgClassUris);
			}
			
			//			
		} else {
			//create DoubleHashMap
			HashMap<String, HashMap<String, HashSet<String>>> dhm = createDoubleHashMapWithHashSet(k, kgClass, uri);
			removeFromBlock.put(bk, dhm);
		}
		
	}

	/**
	 * Get block distribution (number of elements per block) and save results to disk
	 * @param className
	 * @param blocks
	 */
	private void getBlockDistribution(String className,
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks) {
		HashMap<String, Integer> blockDist = new HashMap<String, Integer>();
		for (String token : blocks.keySet()) {
			int c = 0;
			for (String k : blocks.get(token).keySet()) {
				for (String kgClass : blocks.get(token).get(k).keySet()) {
					c += blocks.get(token).get(k).get(kgClass).size();
				}
			}
			
			blockDist.put(token, c);
		}
		HashMap<String, Integer> sortedBlockDist = sortByValue(blockDist);
		//System.out.println(sortedBlockDist);
		saveBlockDistributionToFile(className, sortedBlockDist);
					
	}
	/**
	 * Save block distribution (number of elements per block) to disk
	 * @param className
	 * @param sortedBlockDist
	 */
	private void saveBlockDistributionToFile(String className,
			Map<String, Integer> sortedBlockDist) {
		try {			
			BufferedWriter writer = new BufferedWriter(new FileWriter("./blockDistribution/blockDistribution_tokenBk4_"+className + ".tsv"));
			for (Entry<String, Integer> b : sortedBlockDist.entrySet()) {
				String s = b.getKey() + "\t" + b.getValue() + "\n"; 
				writer.write(s);
			}
			writer.close();
		} catch (IOException e) {	
			e.printStackTrace();
		}
		
	}

	
	/**
	 * Sort HashMap by value (from low to high)
	 * http://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values-java
	 * @param map
	 * @return
	 */
	public static <K, V extends Comparable<? super V>> HashMap<K, V> sortByValue(Map<K, V> map) {
	    return (HashMap<K, V>) map.entrySet()
	              .stream()
	              .sorted(Map.Entry.comparingByValue())//Collections.reverseOrder()
	              .collect(Collectors.toMap(
	                Map.Entry::getKey, 
	                Map.Entry::getValue, 
	                (e1, e2) -> e1, 
	                LinkedHashMap::new
	              ));
	}
	/**
	 * Create Blocks for Token Blocking. Minimum length per token is 4. Tokens with brackets are removed
	 * @param kKgClassInstanceLabels
	 * @return blocks
	 */
	private HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> createBlocks(
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> kKgClassInstanceLabels) {
		HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks = new HashMap<>();
		System.out.println("Start creating blocks...");
		//HashSet<String> stopWords = getStopWordsSet();
		this.blocksPerInstance = new HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>>();
		//for each k
		for (String k : kKgClassInstanceLabels.keySet()) {
			HashMap<String, HashMap<String, HashSet<String>>> map1 = new HashMap<String, HashMap<String, HashSet<String>>>();
			this.blocksPerInstance.put(k, map1);
			//for each kgClass
			for (String kgClass : kKgClassInstanceLabels.get(k).keySet()) {
				HashMap<String, HashSet<String>> map2 = new HashMap<String, HashSet<String>>();
				this.blocksPerInstance.get(k).put(kgClass, map2);
				//for each instance
				for (String uri : kKgClassInstanceLabels.get(k).get(kgClass).keySet()) {
					//for each label
					for (String label : kKgClassInstanceLabels.get(k).get(kgClass).get(uri)) {
						//tokenize label on any whitespace characters
						String blockingKey = "";
						for (String token : label.split("\\s+")) {
							//lower case
							token = token.toLowerCase();
							if (!token.equals("") && 
									!token.equals("null") && 
									token.length() >= 4 && 
									!token.contains("(") && 
									!token.contains(")") //&& 
									//!stopWords.contains(token)
									) {
								//create blocking key
								blockingKey = token;
//								blockingKey += token.subSequence(0, 3);
//							}//end for each token
//						}//end check if token in stopWords
								
						if (!blockingKey.equals("")) {
						//BLOCKS	
							//check if blockingKey already exists in blocks
							if (blocks.containsKey(blockingKey)) {
								//check if block for blockingKey contains k
								if (blocks.get(blockingKey).containsKey(k)) {
									//check if block for blockingKey and k contains kgClass 
									if (blocks.get(blockingKey).get(k).containsKey(kgClass)) {
										//add URI to block
										blocks.get(blockingKey).get(k).get(kgClass).add(uri);
									} else {//!blocks.get(token).get(k).containsKey(kgClass) -> create new hashmap for kgclass
										HashSet<String> uriSet = createHashSet(uri);
										blocks.get(blockingKey).get(k).put(kgClass, uriSet);
									}					
								} else {//!blocks.get(blockingKey).containsKey(k) -> create new hashmap for k
									HashMap<String, HashSet<String>> kgClassWithURI = createHashMapWithHashSet(kgClass, uri);
									blocks.get(blockingKey).put(k, kgClassWithURI);
								}
							} else {//!blocks.containsKey(blockingKey) -> create new block
								HashMap<String, HashMap<String, HashSet<String>>> kKGClassWithURI = createDoubleHashMapWithHashSet(k, kgClass, uri);
								blocks.put(blockingKey,kKGClassWithURI);
							}
						//INSTANCES PER BLOCK
							if (this.blocksPerInstance.get(k).get(kgClass).containsKey(uri)) {
								this.blocksPerInstance.get(k).get(kgClass).get(uri).add(blockingKey);
							} else {
								HashSet<String> bks = new HashSet<>();
								bks.add(blockingKey);
								this.blocksPerInstance.get(k).get(kgClass).put(uri, bks);
							}
						
						}//end if (!blockingKey.equals(""))
					}//end for each label
}}
				}//end for each instance URI
			}//end for each kgClass		
		}//end for each k
		System.out.println(blocks.size() + " blocks created.");
		return blocks;
	}
	/**
	 * Get stop words
	 * @return
	 */
	private HashSet<String> getStopWordsSet() {
		HashSet<String> stopWords = new HashSet<>();
		stopWords.add("the");
		stopWords.add("and");
		stopWords.add("and");
		return stopWords;
	}

	/**
	 * Create a new HashMap<String, HashMap<String, HashSet<String>>> with the params
	 * @param k1 key for the top HashMap
	 * @param k2 key for the second HashMap 
	 * @param initValue for the HashSet
	 */
	private HashMap<String, HashMap<String, HashSet<String>>> createDoubleHashMapWithHashSet(
			String k1, 
			String k2, 
			String initValue) {
		HashMap<String, HashMap<String, HashSet<String>>> map1 = new HashMap<>();
		HashMap<String, HashSet<String>> map2 = createHashMapWithHashSet(k2, initValue);
		map1.put(k1, map2);
		return map1;
	}

	/**
	 * Create a new HashSet<String>> with the init value
	 * @param initValue for the HashSet
	 */
	private HashSet<String> createHashSet(String initValue) {
		HashSet<String> set = new HashSet<>();
		set.add(initValue);
		return set;
	}
	

	/**
	 * Create a new HashMap<String, HashSet<String>> with the params
	 * @param key for the HashMap
	 * @param initValue for the HashSet
	 */
	private HashMap<String, HashSet<String>> createHashMapWithHashSet(
			String key, String initValue) {
		HashMap<String, HashSet<String>> map = new HashMap<>();
		HashSet<String> set = createHashSet(initValue);
		map.put(key, set);	
		return map;
	}
	/**
	 * Compare KG classes with each other to get matched string pairs
	 * @param results
	 * @param kKgClassInstanceLabels
	 * @param stringMeasures
	 * @param thresholds
	 * @param blocks
	 * @param sortedBlocks
	 */
	private void getMatchedStringPairs(
			CountStringSimilarityResults results,
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> kKgClassInstanceLabels,
			StringMeasures stringMeasures, 
			ArrayList<Double> thresholdsH,
			ArrayList<Double> thresholdsL,
			ArrayList<Double> thresholdsJaccard,
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks,
			HashMap<String, Integer> sortedBlocks) {
		boolean getPairs = true;
		if (stringMeasures.checkTFIDF()) {
			stringMeasures.trainTFIDF(kKgClassInstanceLabels);
		}
		//for each kg
		for (String fk : kKgClassInstanceLabels.keySet()) {
			switch (fk) {
				case "d":
					System.out.println("Start comparing D2Y");
					comparefKtK(fk, "y", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with D2Y. Start comparing D2W");
					comparefKtK(fk, "w", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with D2W. Start comparing D2O");
					comparefKtK(fk, "o", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with D2O. Start comparing D2N");
					comparefKtK(fk, "n", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with D2N");
					break;
				case "y":
					System.out.println("Start comparing Y2W");
					comparefKtK(fk, "w", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with Y2W. Start comparing Y2O");
					comparefKtK(fk, "o", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with Y2O. Start comparing Y2N");
					comparefKtK(fk, "n", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with Y2N");
					break;
				case "w":
					System.out.println("Start comparing W2O");
					comparefKtK(fk, "o", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with W2O. Start comparing W2N");
					comparefKtK(fk, "n", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with W2N");
					break;
				case "o":
					System.out.println("Start comparing O2N");
					comparefKtK(fk, "n", results, kKgClassInstanceLabels, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard, getPairs, blocks, sortedBlocks);
					System.out.println("Done with O2N");
					break;
			}
		}
		
		
	}

	/**
	 * Compare to KGs with each other
	 * @param fk
	 * @param tk
	 * @param results
	 * @param kKgClassInstanceLabels
	 * @param stringMeasures
	 * @param thresholds
	 * @param getPairs
	 * @param blocks
	 * @param sortedBlocks
	 */
	private void comparefKtK(String fk, String tk,
			CountStringSimilarityResults results, 
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> kKgClassInstanceLabels,
			StringMeasures stringMeasures, 
			ArrayList<Double> thresholdsH,
			ArrayList<Double> thresholdsL,
			ArrayList<Double> thresholdsJaccard,
			boolean getPairs, 
			HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> blocks, 
			HashMap<String, Integer> sortedBlocks) {
		// pairs <SimMeasure, threshold>, <fromURI, toURI>>
		HashMap<Pair<String,Double>, HashSet<Pair<String, String>>> kgClassInstancePairResults = new HashMap<Pair<String, Double>, HashSet<Pair<String, String>>>();
		//for each kgClass
		//long startTime = System.nanoTime();
		for (String kgClass :kKgClassInstanceLabels.get(fk).keySet()) {
			
			if (kKgClassInstanceLabels.get(tk) != null) {				
				//for each kg class in other kg
				for (String toKgClass : kKgClassInstanceLabels.get(tk).keySet()) {	
					System.out.println("Comparing " + fk +": " + kgClass + " with " + tk + ": " + toKgClass);
					//init 
					for (String simMeasure : stringMeasures.getUsedMeasures()) {
						ArrayList<Double> thresholds = thresholdsL;
						if (this.simMeasuresThresholdH.contains(simMeasure)) {
							thresholds = thresholdsH;
						} else if (simMeasure.equals("jaccard")) {
							thresholds = thresholdsJaccard;
						}
						for (Double t : thresholds) {
							HashSet<Pair<String, String>> emptySet = new HashSet<Pair<String,String>>();
							Pair<String, Double> k = new ImmutablePair<String, Double>(simMeasure, t);
							kgClassInstancePairResults.put(k, emptySet);
						}
					}
					
					//compare instanceLabels
					
					//BLOCKING
					//for each block
					int blockCounter = 0;
					//blocks: HashMap<token, <HashMap<k, HashMap<kgClass, <HashSet<URIs>>>>>>
					//for (String block : blocks.keySet()) {
					for (String block : sortedBlocks.keySet()) {
						//check if block contains fk and tk
						if (blocks.get(block).containsKey(fk) && blocks.get(block).containsKey(tk)) {
							if (blocks.get(block).get(fk).containsKey(kgClass) && blocks.get(block).get(tk).containsKey(toKgClass)) {
								HashMap<String, HashSet<String>> entries1 = getInstaceLabelsFromBlocks(blocks.get(block).get(fk).get(kgClass),
										kKgClassInstanceLabels.get(fk).get(kgClass));
								HashMap<String, HashSet<String>> entries2 = getInstaceLabelsFromBlocks(blocks.get(block).get(tk).get(toKgClass),
										kKgClassInstanceLabels.get(tk).get(toKgClass));
								
								
								entries1.entrySet()
								.stream()
								// DO NOT USE PARALLEL HERE!
								//.parallel()
								.forEach(instanceWithLabels -> {compareLabelsWithOtherKG(results, 
																	fk, kgClass, instanceWithLabels, tk, 
																	entries2,
																	//kKgClassInstanceLabels.get(tk).get(toKgClass), 
																	toKgClass, stringMeasures, thresholdsH, thresholdsL, thresholdsJaccard,
																	kgClassInstancePairResults, getPairs);
								});
								
							} //block does not contain kgClass or toKgClass (or both)
						} //block does not contain fk or tk (or both)
						blockCounter++;
						if (blockCounter % (blocks.size()/10) == 0) {
							System.out.println(blockCounter + " of " + blocks.size() + " blocks processed. Current block size: " + sortedBlocks.get(block));
						}
					}//done with blocking
					
					//no blocking
					/*kKgClassInstanceLabels.get(fk).get(kgClass).entrySet()
						.stream()
						.parallel()
						.forEach(instanceWithLabels -> {compareLabelsWithOtherKG(results, 
															fk, kgClass, instanceWithLabels, tk, 
															kKgClassInstanceLabels.get(tk).get(toKgClass), 
															toKgClass, stringMeasures, thresholds, 
															kgClassInstancePairResults, getPairs);
						});
					
					*/
					/*
					for (Entry<String, HashSet<String>> instanceWithLabels : kKgClassInstanceLabels.get(fk).get(kgClass).entrySet()) {
						
						//to kg
						if(kKgClassInstanceLabels.get(tk) != null) {
							compareLabelsWithOtherKG(results, fk, kgClass, instanceWithLabels, tk, kKgClassInstanceLabels.get(tk), stringMeasures, thresholds, kgClassInstancePairResults, getPairs);
						}
						counter +=1;
						if (counter % 1000 == 0) {
							System.out.println(counter + " instances compared for " + fk + "2" + tk + ":" + kgClass + " in  " + ((System.nanoTime() - startTime)/1000000000) + " seconds.");
							startTime = System.nanoTime();
						}
					}
					*/
					//save results to disk
					//System.out.println(kgClassInstancePairResults);
					saveInstancePairResultsToDisk(fk, tk, kgClass, toKgClass, kgClassInstancePairResults, stringMeasures);
					kgClassInstancePairResults.clear();
				}
			}
		}
	
	}
		
	/**
	* Return all instances with all labels that are in the block
	* @param hashSet with uris in the block
	* @param hashMap with all uris and labels
	*/
	private HashMap<String, HashSet<String>> getInstaceLabelsFromBlocks(
		HashSet<String> urisInBlock, 
		HashMap<String, HashSet<String>> allURIsWithLabels) {	
		HashMap<String, HashSet<String>> results = new HashMap<>();
		for (String uriInBlock : urisInBlock) {
			if (allURIsWithLabels.containsKey(uriInBlock)) {
				results.put(uriInBlock, allURIsWithLabels.get(uriInBlock));
			}
		}		
		return results;
	}
	/**
	* Save pairs to disk
	* @param fk
	* @param tk
	* @param kgClass
	* @param toKgClass
	* @param kgClassInstancePairResults
	* @param stringMeasures
	*/
	private void saveInstancePairResultsToDisk(String fk, 
			String tk,
			String kgClass,
			String toKgClass, 
			HashMap<Pair<String, Double>, 
			HashSet<Pair<String, String>>> kgClassInstancePairResults,
			StringMeasures stringMeasures) {
			for (Pair<String, Double> simMeasurePair : kgClassInstancePairResults.keySet()) {
				//String threshold = getThreshold(simMeasure, stringMeasures);//1.0 for exact match
				String simMeasure = simMeasurePair.getLeft();
				Double threshold = simMeasurePair.getRight();
				Path savePath = Paths.get("./simMeasureResults/"+fk+"2"+tk+"_"+kgClass+"_"+toKgClass+"_"+simMeasure+"_"+threshold+".tsv");
				savePairsToDisk(savePath, kgClassInstancePairResults.get(simMeasurePair));
			}
	}
	/**
	 * Write TSV to disk
	 * @param savePath
	 * @param pairSet
	 */
	private void savePairsToDisk(Path savePath,
				HashSet<Pair<String, String>> pairSet) {
			if (!pairSet.isEmpty()) {	
				try {
					BufferedWriter out = Files.newBufferedWriter(savePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					
					for (Pair<String, String> p : pairSet) {
						String s = p.getLeft() + "\t" + p.getRight() + "\n";	
						out.write(s);
					}
					out.close();
					//System.out.println(pairSet.size() + " line(s) written to " + savePath.toString());
				} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
				}
			}
			
		}


	/**
	 * Compare instance labels with all labels of another instance
	 * @param results
	 * @param fK
	 * @param fromKgClass
	 * @param instanceWithLabels
	 * @param tK
	 * @param otherKGinstancesWithLabels
	 * @param toKgClass
	 * @param stringMeasures
	 * @param thresholds
	 * @param kgClassInstancePairResults
	 * @param getPairs
	 */
	private void compareLabelsWithOtherKG(CountStringSimilarityResults results, 
			String fK, 
			String fromKgClass, 
			Entry<String, HashSet<String>> instanceWithLabels,
			String tK, 
			HashMap<String, HashSet<String>> otherKGinstancesWithLabels,
			String toKgClass, 
			StringMeasures stringMeasures, 
			ArrayList<Double> thresholdsH,
			ArrayList<Double> thresholdsL,
			ArrayList<Double> thresholdsJaccard, 
			HashMap<Pair<String, Double>, HashSet<Pair<String, String>>> kgClassInstancePairResults, 
			boolean getPairs) {	
		
		String fromURI = instanceWithLabels.getKey();
		HashSet<String> labels = instanceWithLabels.getValue();
		
		//simResults <SimMeasure, threshold>, match>
		HashMap<Pair<String, Double>, Boolean> simResults = new HashMap<Pair<String,Double>, Boolean>();	
		
				//for each instance in other kg
				//System.out.println(fK + "_" + fromKgClass + " to " + tK + "_" + toKgClass);
				for (Entry<String, HashSet<String>> otherKGinstanceWithLabels : otherKGinstancesWithLabels.entrySet()) {
					String toURI = otherKGinstanceWithLabels.getKey();
					// instanceResults<SimMeasure, threshold>, booleanMatch>
					HashMap<Pair<String, Double>, Boolean> instanceResults = stringMeasures.getBlankInstanceResultsContainer(thresholdsH, thresholdsL, thresholdsJaccard);
										
					// for each label in fromKG
					labelloop:
					for (String label : labels) {
						if (label != null && !label.equals("null")) {
						for (String otherLabel : otherKGinstanceWithLabels.getValue()) {
							if (otherLabel != null && !otherLabel.equals("null")) {
							
								//System.out.println(label + " AND " + otherLabel);
								//System.out.println(stringMeasures.getSimilarityScores(label, otherLabel));
								
								simResults = stringMeasures.getSimilarityResult(label, otherLabel, thresholdsH, thresholdsL);
								//if (simResults.get("softTfidf"))
								//	System.out.println(label + " and " + otherLabel + ": "+ simResults);

								//update instanceResults
								instanceResults = updateInstanceResults(instanceResults, simResults);
								

								//check if all sim measures are true: break loop 
								if (!instanceResults.containsValue(false)) {
									//System.out.println("break loop: all labels are matched");
									break labelloop;
								}
								
							}
						}
					}
					
				}
				//create pair
				Pair<String, String> uriPair = new ImmutablePair<String, String>(fromURI, toURI);
				//check if at least one label matches
				for (Pair<String, Double> simMeasureP : instanceResults.keySet()) {
					//check if match is true
					if(instanceResults.get(simMeasureP)) {
						//check which similarity measure
						if (simMeasureP.getLeft().equals(jaccardS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, jaccardS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(jaroS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, jaroS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(scaledLevensteinS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, scaledLevensteinS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(tfidfS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, tfidfS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(jaroWinklerS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, jaroWinklerS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(mongeElkanS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, jaroWinklerS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(exactMatchS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, exactMatchS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(allS)) {
							if (getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						} else if (simMeasureP.getLeft().equals(softTfidfS)) {
							//results.addInstanceCount(fK, fromKgClass, tK, toKgClass, softTfidfS, simMeasureP.getRight());
							if(getPairs && kgClassInstancePairResults.containsKey(simMeasureP)) {
								kgClassInstancePairResults.get(simMeasureP).add(uriPair);
							}
						}
					}
				}
			}		
		
	}

	/**
	 * Update instance results
	 * @param instanceResults
	 * @param simResults
	 * @return
	 */
	private HashMap<Pair<String, Double>, Boolean> updateInstanceResults(
			HashMap<Pair<String, Double>, Boolean> instanceResults, 
			HashMap<Pair<String, Double>, Boolean> simResults) {
		// get all string similarity measures
		for (Pair<String, Double> instanceResultS : instanceResults.keySet()) {
			//if string sim measure is not true
			if (!instanceResults.get(instanceResultS)) {
				//update if simResult is true
				if (simResults.get(instanceResultS)) {
					instanceResults.put(instanceResultS, true);
				}
			}
		}
		return instanceResults;
	}
	/**
	 * Get all instance labels 
	 * @param classMap
	 * @return
	 */
	private HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> getInstanceLabels(
			HashMap<String, ArrayList<String>> classMap) {
		HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>> instanceLabels = new HashMap<String, HashMap<String, HashMap<String, HashSet<String>>>>();
		
		for (String k : classMap.keySet()) {
			
			if (k.equals("d") || k.equals("y") || k.equals("w") ||k.equals("o") || k.equals("n")) {
			
			    for (String kgClass : classMap.get(k)) {
			    	//System.out.println(kgClass);
			    	//get all instance labels for the kgClass and save them in the instanceLabels object
			    	
			    	HashMap<String, HashSet<String>> instanceLabelsForKgClass= getInstanceLabelsForKgClass(k, kgClass);			    	 
			    	if (instanceLabels.containsKey(k)) {
			    		instanceLabels.get(k).put(kgClass, instanceLabelsForKgClass);
			    	} else {
			    		HashMap<String, HashMap<String, HashSet<String>>> instanceLabelsForSingleKgClass = new HashMap<String, HashMap<String,HashSet<String>>>();
			    		instanceLabelsForSingleKgClass.put(kgClass, instanceLabelsForKgClass);
			    		instanceLabels.put(k, instanceLabelsForSingleKgClass);
			    	}			    			    	
			    }
			}
		}
		return instanceLabels;
	}
	/**
	 * Get all instance labels for a specific class
	 * @param k
	 * @param kgClass
	 * @return
	 */
	private HashMap<String, HashSet<String>> getInstanceLabelsForKgClass(
			String k, String kgClass) {
		HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass = new HashMap<String, HashSet<String>>();
		
		//get file paths 
		Path filePath = null;	
		filePath = Paths.get("./InstanceLabels/");
		instanceLabelsForSingleKgClass = readFile(filePath, k, kgClass);
		return instanceLabelsForSingleKgClass;
	}
	
	/**
	 * Read file from disk
	 * @param filePath
	 * @param k
	 * @param kgClass
	 * @return
	 */
	private HashMap<String, HashSet<String>> readFile(Path filePath,
			String k,
			String kgClass) {
		HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass = new HashMap<String, HashSet<String>>();
		Path fileName = Paths.get(filePath + "/" + k + "_" + kgClass + "InstancesWithLabels.txt");
		try (Stream<String> stream = Files.lines(fileName)) {
			stream.forEach(line -> addLineToHashMap(line, kgClass, instanceLabelsForSingleKgClass));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return instanceLabelsForSingleKgClass;
	}
	
	/**
	 * Add a line (having labels) to the HashMap
	 * @param line
	 * @param kgClass
	 * @param instanceLabelsForSingleKgClass
	 */
	private static void addLineToHashMap(String line, String kgClass,
			HashMap<String, HashSet<String>> instanceLabelsForSingleKgClass) {
		String[] words = line.split("\\t");
		HashSet<String> allLabels = new HashSet<String>();
		for (int i = 1; i < words.length; i++) {
			allLabels.add(words[i]);
			//System.out.println(words[i]);
		}
		
		instanceLabelsForSingleKgClass.put(words[0], allLabels);		
	}

}
