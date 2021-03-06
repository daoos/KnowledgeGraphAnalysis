import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class InstanceObject {
 private String className;
 private HashSet<String> instances;
 private HashMap<String, Set<String>> instancesWithLabel;
 private int duplicateCounter;
 private int duplicateCounterMap;
 
 public InstanceObject(String className) {
	 this.className = className;
	 this.instances = new HashSet<String>();
	 this.instancesWithLabel = new HashMap<String, Set<String>>();
	 this.duplicateCounter = 0;
	 this.duplicateCounterMap = 0;
 }
 /**
  * Add instance to instanceSet
  * @param label (string)
 */
 public void addInstance(String label) {
	 if (!this.instances.contains(label)) {
		 this.instances.add(label);
	 } else {
		 this.duplicateCounter += 1;
	 }
 }
 public void addInstance(String instance, String label) {
	 if (!this.instancesWithLabel.containsKey(instance)) {
		 Set<String> labels = new HashSet<String>();
		 labels.add(removeLanguageTag(label));
		 this.instancesWithLabel.put(instance, labels);
	 } else {
		 Set<String> labels = this.instancesWithLabel.get(instance);
		 labels.add(removeLanguageTag(label));
		 this.duplicateCounterMap += 1;
	 }
 }
 private String removeLanguageTag(String label) {
	// remove the @eng language tag of the label
	return label.substring(0, label.length()-4);
}
public HashSet<String> getInstances() {
	 return this.instances;
 }
 public HashMap<String, Set<String>> getInstancesMap() {
	 return this.instancesWithLabel;
 }
 
 public int getNumberOfInstances() {
	 return this.instances.size();
 }
 public int getNumberOfInstancesMap() {
	 return this.instancesWithLabel.size();
 }
 public int getDuplicateCounter() {
	 return this.duplicateCounter;
 }
 public int getDuplicateCounterMap() {
	 return this.duplicateCounterMap;
 }
public void printAll() {
	System.out.println(this.className);
		
	
}
 
}
