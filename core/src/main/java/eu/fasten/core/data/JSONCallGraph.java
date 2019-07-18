package eu.fasten.core.data;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.commons.io.output.NullOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;



public class JSONCallGraph {

	/** A constraint represents an interval of versions. It includes all versions between a given lower and upper bound. */
	public static class Constraint {
		/** Version must be not smaller than this (no lower bound, if <code>null</code>). */
		public final String lowerBound;
		/** Version must be not larger than this (no upper bound, if <code>null</code>). */
		public final String upperBound;

		/** Generate a constraint with given lower and upper bounds.
		 * 
		 * @param lowerBound the lower bound.
		 * @param upperBound the upper bound.
		 */
		public Constraint(final String lowerBound, final String upperBound) {
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
		}
		
		/** Generate a constraint on the basis of a specification. The spec must:
		 *  <ol>
		 *  	<li>start with a '['
		 *  	<li>end with a ']'
		 *  	<li>it contains at most one substring of the form '..'
		 *  	<li>if it contains no such substring, lower and upper bound coincide and are the trimmed version of whatever is between brackets
		 *      <li>if it contains one such substring, lower and upper bounds are whatever precedes and follows (respectively) the '..'
		 *  </ol>
		 * @param spec the specification.
		 */
		public Constraint(String spec) {
			if ((spec.charAt(0) != '[') || (spec.charAt(spec.length() - 1) != ']')) throw new IllegalArgumentException("Constraints must start with '[' and end with ']'");
			int pos = spec.indexOf("..");
			if (spec.indexOf("..", pos + 1) >= 0) throw new IllegalArgumentException("Constraints must contain exactly one ..");
			String lowerBound = spec.substring(1, pos >= 0? pos : spec.length() - 1).trim();
			String upperBound = spec.substring(pos >= 0? pos + 2 : 1, spec.length() - 1).trim();
			this.lowerBound = lowerBound.length() == 0? null : lowerBound;
			this.upperBound = upperBound.length() == 0? null : upperBound;
		}
		
		/** Given a {@link JSONArray} of specifications of constraints, it returns the corresponding array
		 *  of contraints.
		 *  
		 * @param jsonArray an array of strings, each being the {@linkplain #Constraint(String) specification} of a constraint.
		 * @return the corresponding array of constraints.
		 */
		public static Constraint[] constraints(JSONArray jsonArray) {
			Constraint[] c = new Constraint[jsonArray.length()];
			for (int i = 0; i < c.length; i++) 
				c[i] = new Constraint(jsonArray.getString(i));
			return c;
		}

		@Override
		public String toString() {
			return "[" + 
					(lowerBound == null? "" : lowerBound) +
					".." +
					(upperBound == null? "" : upperBound) +
					"]";
		}
	}
	
	public static class Dependency {
		public final String forge;
		public final String product;
		public final Constraint[] constraints;
		
		/** Create a dependency with given data.
		 * 
		 * @param forge the forge.
		 * @param product the product.
		 * @param constraint the array of constraints.
		 */
		public Dependency(String forge, String product, Constraint[] constraint) {
			this.forge = forge;
			this.product = product;
			this.constraints = constraint;
		}
		
		/** Create a dependency based on the given JSON Object.
		 * 
		 * @param json the JSON dependency object, as specified in Fasten Deliverable 2.1 
		 */
		public Dependency(JSONObject json) {
			this.forge = json.getString("forge");
			this.product = json.getString("product");
			//TODO
			//this.constraints = Constraint.constraints(json.getJSONArray("constraints"));
			this.constraints = new Constraint[] {new Constraint(json.getString("constraints"), null)};
		}
		
		/** Given an JSON array of dependencies (a depset as specified in Fasten Deliverable 2.1), it returns
		 *  the corresponding depset.
		 *   
		 * @param depset the JSON array of dependencies.
		 * @return the corresponding array of dependencies.
		 */
		public static Dependency[] depset(JSONArray depset) {
			Dependency[] d = new Dependency[depset.length()];
			for (int i = 0; i < d.length; i++) 
				d[i] = new Dependency(depset.getJSONObject(i));
			return d;
		}
		
	}
	
	/** The forge. */
	public final String forge;
	/** The product. */
	public final String product;
	/** The version. */
	public final String version;
	/** The timestamp (if specified, or -1) in seconds from UNIX Epoch. */
	public final long timestamp;
	/** The depset. */
	public final Dependency[] depset;
	/** The URI of this revision. */
	public final FastenURI uri;
	/** The forgeless URI of this revision. */
	public final FastenURI forgelessUri;
	/** The graph expressed as a list of pairs of {@link FastenURI}. Recall that, according to D2.1:
	 * <ol>
	 *  <li>they are in schemeless canonical form;
	 *  <li>the entity specified is a function or an attribute (not a type); if it is an attribute, it must appear as the second URI of a pair;
	 *  <li>the forge-product-version, if present, must only contain the product part; this happens exactly when the product is different from the product specified by this JSON object, that is, if and only if the URI is that of an external node;
	 *  <li>the first URI of each pair always refers to this product;
	 *  <li>the namespace is always present.
	 * </ol>
	 */
	public ArrayList<FastenURI[]> graph;
	
	
	/** Creates a JSON call graph with given data.
	 * 
	 * @param forge the forge.
	 * @param product the product.
	 * @param version the version.
	 * @param timestamp the timestamp (in seconds from UNIX epoch); optional: if not present, it is set to -1.
	 * @param depset the depset.
	 * @param graph the call graph (no control is done on the graph).
	 */
	public JSONCallGraph(String forge, String product, String version, long timestamp, Dependency[] depset, ArrayList<FastenURI[]> graph) {
		this.forge = forge;
		this.product = product;
		this.version = version;
		this.timestamp = timestamp;
		this.depset = depset;
		uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
		forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
		this.graph = graph;
	}
	
	/** Creates a JSON call graph for a given JSON Object, as specified in Deliverable D2.1.
	 *  The timestamp is optional (if not specified, it is set to -1). 
	 *  Moreover, the list of arcs is checked in that all involved URIs must be:
	 * <ol>
	 *  <li>they are in schemeless canonical form;
	 *  <li>the entity specified is a function or an attribute (not a type); if it is an attribute, it must appear as the second URI of a pair;
	 *  <li>the forge-product-version, if present, must only contain the product part; this happens exactly when the product is different from the product specified by this JSON object, that is, if and only if the URI is that of an external node;
	 *  <li>the first URI of each pair always refers to this product;
	 *  <li>the namespace is always present.
	 * </ol>
	 *  Arcs not satisfying these properties are discarded, and a suitable error message is printed
	 *  over the given print stream (typically, <code>err</code>, but can also be <code>null</code> in
	 *  which case a null print stream will be used).
	 *  
	 * @param json the JSON Object.
	 */
	public JSONCallGraph(JSONObject json, PrintStream err) throws JSONException, URISyntaxException {
		if (err == null) err = new PrintStream(new NullOutputStream());		
		this.forge = json.getString("forge");
		this.product = json.getString("product");
		this.version = json.getString("version");
		long ts;
		try {
			ts = json.getLong("timestamp");
		} catch (JSONException exception) {
			ts = -1;
		}
		this.timestamp = ts;
		this.depset = Dependency.depset(json.getJSONArray("depset"));
		uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
		forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
		this.graph = new ArrayList<FastenURI[]>();
		JSONArray jsonArray = json.getJSONArray("graph");
		int numberOfArcs = jsonArray.length();
		for (int i = 0; i < numberOfArcs; i++) {
			JSONArray pair = jsonArray.getJSONArray(i);
			FastenURI[] arc = new FastenURI[] {
					new FastenURI(pair.getString(0)),
					new FastenURI(pair.getString(1)) };
			int correctNodesInArc = 0;
			// Check the graph content
			for (int j = 0; j < arc.length; j++) {
				FastenURI node = arc[j];
				// URI in schemeless canonical form
				if (node.getScheme() != null) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " should be schemeless");
				else if (!node.toString().equals(node.canonicalize().toString())) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " not in canonical form [" + node.canonicalize() + "]");
				// No forge, no version
				else if (node.getForge() != null || node.getVersion() != null) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": forges and versions cannot be specified: " + node);
				// Product cannot coincide with this product
				else if (node.getProduct() != null && uri.getProduct().equals(node.getProduct())) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": product of node " + node + " equals the product specified by this JSON object, and should hence be omitted");
				// If product is specified, the node must be the source
				else if (node.getProduct() != null  && j == 0) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": node " + node + " is external, and cannot appear as source of an arc");
				// Check that namespace is present
				else if (node.getNamespace() == null) err.println("Ignoring arc " + i + "/" + numberOfArcs + ": namespace is not present in node " + pair.getString(i));
				// TODO we should also check that it is a function or an attribute, not a type!
				else correctNodesInArc++;
			}
			if (correctNodesInArc == 2) this.graph.add(arc);
		}
		err.println("Stored " + this.graph.size() + " arcs of the " + numberOfArcs + " specified");
	}
	
	public static void main(String[] args) throws JSONException, FileNotFoundException, URISyntaxException {
		if (args.length != 1) {
			System.err.println("Should provide exactly one argument (the file containing the JSON Object specifying the call graph)");
			System.exit(1);
		}
		JSONObject json = new JSONObject(new JSONTokener(new FileReader(args[0])));
		JSONCallGraph callGraph = new JSONCallGraph(json, System.err);
		// TODO do something with the graph?
	}
	
	

}