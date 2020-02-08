/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.javacgopal.data.callgraph;

import eu.fasten.analyzer.javacgopal.data.*;
import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;
import eu.fasten.analyzer.javacgopal.scalawrapper.ScalaFunction2;
import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.data.FastenURI;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.Chain;
import org.opalj.collection.immutable.ConstArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.Iterable;
import scala.collection.JavaConversions;

/**
 * Call graphs that are not still fully resolved.
 * e.g. isolated call graphs which within-artifact calls (edges) are known as resolved calls and
 * Cross-artifact calls are known as unresolved calls.
 */
public class PartialCallGraph {

    private static Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    /**
     * Calls that their target's packages are not still known and need to be resolved in later on, e.g. in a merge phase.
     */
    private List<UnresolvedCall> unresolvedCalls;
    /**
     * Calls that their sources and targets are fully resolved.
     */
    private List<ResolvedCall> resolvedCalls;
    /**
     * ClassHierarchy of the under investigation artifact.
     * For every class in the form of org.opalj.br.ObjectType it specify a single eu.fasten.analyzer.javacgopal.data.Type.
     */
    private Map<ObjectType, TypeOPAL> classHierarchy;

    public PartialCallGraph(final List<UnresolvedCall> unresolvedCalls, final List<ResolvedCall> ResolvedCalls, final Map<ObjectType, TypeOPAL> classHierarchy) {

        this.unresolvedCalls = unresolvedCalls;
        this.resolvedCalls = ResolvedCalls;
        this.classHierarchy = classHierarchy;
    }

    /**
     * Using this constructor it is possible to directly retrieve calls in eu.fasten.analyzer.javacgopal.graph.PartialCallGraph.
     * e.g. add edges to resolved calls one by one when scala is being used.
     */

    public PartialCallGraph(final File file) {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
        this.classHierarchy = new HashMap<>();
        this.generatePartialCallGraph(file);
    }

    /**
     * This constructor creates the list of UnresolvedCalls from a list of org.opalj.ai.analyses.cg.UnresolvedMethodCall.
     */
    public void setUnresolvedCalls(final List<UnresolvedMethodCall> unresolvedMethodCalls) {
        for (UnresolvedMethodCall unresolvedMethodCall : unresolvedMethodCalls) {
            this.unresolvedCalls.add(new UnresolvedCall(unresolvedMethodCall.caller(), unresolvedMethodCall.pc(), unresolvedMethodCall.calleeClass(), unresolvedMethodCall.calleeName(), unresolvedMethodCall.calleeDescriptor()));
        }
    }

    public void setResolvedCalls(final List<ResolvedCall> resolvedCalls) {
        this.resolvedCalls = resolvedCalls;
    }

    /**
     * Sets classHierarchy of the current call graph.
     * Note: For the classes that are inside the under investigation artifact methods are also known,
     * so we set them in the classHierarchy.
     *
     * @param artifactInOpalFormat org.opalj.br.analyses.Project.
     */
    public void setClassHierarchy(final Project artifactInOpalFormat) {

        final Map<ObjectType, List<org.opalj.br.Method>> allMethods = new HashMap<>();
        artifactInOpalFormat.allMethodsWithBody().foreach((JavaToScalaConverter.asScalaFunction1(
            (Object method) -> putMethod(allMethods, (org.opalj.br.Method) method)))
        );

        final Set<ObjectType> currentArtifactClasses = new HashSet<>(allMethods.keySet());
        final Set<ObjectType> libraryClasses = new HashSet<>(JavaConversions.mapAsJavaMap(artifactInOpalFormat.classHierarchy().supertypes()).keySet());
        libraryClasses.removeAll(currentArtifactClasses);

        for (ObjectType currentClass : currentArtifactClasses) {

            final TypeOPAL type = new TypeOPAL();

            type.setSupers(artifactInOpalFormat.classHierarchy(), currentClass);
            type.getMethods().addAll(allMethods.get(currentClass));
            this.classHierarchy.put(currentClass, type);
        }

//        for (ObjectType libraryClass : libraryClasses) {
//            Type type = new Type();
//            type.setSupers(artifactInOpalFormat.classHierarchy(), libraryClass);
//            this.classHierarchy.put(libraryClass, type);
//        }

    }

    /**
     * Adds an org.opalj.br.Method to the passed Map.
     *
     * @param methods a map of methods inside each class.
     *                Keys are org.opalj.ObjectType and values are a list of org.opalj.br.Method declared inside of that class.
     * @param method  the org.opalj.br.Method to be added.
     * @return if the value is successfully added returns true otherwise false.
     */
    public Boolean putMethod(final Map<ObjectType, List<org.opalj.br.Method>> methods, final org.opalj.br.Method method) {

        final var currentClass = method.declaringClassFile().thisType();

        final List<org.opalj.br.Method> resultMethods = new ArrayList<>();

        try {
            final var currentClassMethods = methods.get(currentClass);
            if (currentClassMethods == null) {
                resultMethods.add(method);
            } else {
                if (!currentClassMethods.contains(method)) {
                    resultMethods.addAll(currentClassMethods);
                    resultMethods.add(method);
                }
            }
            methods.put(currentClass, resultMethods);
            return true;

        } catch (Exception e) {
            logger.error("Couldn't add the method {} to the list of methods of the class:{} ", method, currentClass, e);
            return false;
        }
    }

    public List<UnresolvedCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<ResolvedCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public Map<ObjectType, TypeOPAL> getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Loads a given file, generates call graph and change the format of calls to (source -> target).
     *
     * @param artifactFile Java file that can be a jar or a folder containing jars.
     * @return A partial graph including ResolvedCalls, UnresolvedCalls and CHA.
     */
    public PartialCallGraph generatePartialCallGraph(final File artifactFile) {

        final Project artifactInOpalFormat = Project.apply(artifactFile);

        final ComputedCallGraph callGraphInOpalFormat = CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0(findEntryPoints(artifactInOpalFormat.allMethodsWithBody())),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA(),false).get(CHACallGraphKey$.MODULE$);

        return toPartialGraph(callGraphInOpalFormat);

    }

    /**
     * Given a call graph in OPAL format returns a call graph in eu.fasten.analyzer.javacgopal.graph.PartialCallGraph format.
     *
     * @param callGraphInOpalFormat Is an object of OPAL ComputedCallGraph.
     * @return eu.fasten.analyzer.javacgopal.graph.PartialCallGraph includes all the calls(as java List) and ClassHierarchy.
     */
    public PartialCallGraph toPartialGraph(final ComputedCallGraph callGraphInOpalFormat) {

        callGraphInOpalFormat.callGraph().foreachCallingMethod(JavaToScalaConverter.asScalaFunction2(setResolvedCalls()));

        this.setUnresolvedCalls(new ArrayList<>(JavaConversions.asJavaCollection(callGraphInOpalFormat.unresolvedMethodCalls().toList())));

        this.setClassHierarchy(callGraphInOpalFormat.callGraph().project());

        return this;
    }

    /**
     * Adds resolved calls of OPAL call graph to resolvedCalls of this object.
     *
     * @return eu.fasten.analyzer.javacgopal.scalawrapper.ScalaFunction2 As a fake scala function to be passed to the scala.
     */
    public ScalaFunction2 setResolvedCalls() {
        return (org.opalj.br.Method callerMethod, scala.collection.Map<Object, Iterable<org.opalj.br.Method>> calleeMethodsObject) -> {
            Collection<Iterable<org.opalj.br.Method>> calleeMethodsCollection =
                JavaConversions.asJavaCollection(calleeMethodsObject.valuesIterator().toList());

            final List<org.opalj.br.Method> calleeMethodsList = new ArrayList<>();
            for (Iterable<org.opalj.br.Method> i : calleeMethodsCollection) {
                for (org.opalj.br.Method j : JavaConversions.asJavaIterable(i)) {
                    calleeMethodsList.add(j);
                }
            }
            return resolvedCalls.add(new ResolvedCall(callerMethod, calleeMethodsList));

        };
    }

    /**
     * Computes the entrypoints as a pre step of call graph generation.
     *
     * @param allMethods Is all of the methods in an OPAL-loaded project.
     * @return An iterable of entrypoints to be consumed by scala-written OPAL.
     */
    public static Iterable<org.opalj.br.Method> findEntryPoints(final ConstArray allMethods) {

        return (Iterable<org.opalj.br.Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((org.opalj.br.Method) method).isAbstract()) && !((org.opalj.br.Method) method).isPrivate()));

    }

    /**
     * Creates the call graph in a FASTEN supported format from a eu.fasten.analyzer.javacgopal.graph.PartialCallGraph.
     *
     * @param forge            The forge of the under investigation artifact. e.g. assuming we are making revision call graph of
     *                         "com.google.guava:guava:jar:28.1-jre", this artifact exists on maven repository so the forge is mvn.
     * @param coordinate       Maven coordinate of the artifact in the format of groupId:ArtifactId:version.
     * @param timestamp        The timestamp (in seconds from UNIX epoch) in which the artifact is released on the forge.
     * @param partialCallGraph A partial call graph of artifact including resolved calls, unresolved calls (calls without specified product) and CHA.
     * @return The given revision's call graph in FASTEN format. All nodes are in URI format.
     */
    public static RevisionCallGraph createRevisionCallGraph(final String forge, final MavenCoordinate coordinate, final long timestamp, final PartialCallGraph partialCallGraph) {

        return new RevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.toURIGraph());
    }


    /**
     * Converts all of the members of the classHierarchy to FastenURIs.
     *
     * @param classHierarchy Map<org.obalj.br.ObjectType, eu.fasten.analyzer.javacgopal.data.Type>
     * @return Map<eu.fasten.core.data.FastenURI, eu.fasten.analyzer.javacgopal.graph.ExtendedRevisionCallGraph.Type>
     */

    public static Map<FastenURI, TypeURI> toURIHierarchy(final Map<ObjectType, TypeOPAL> classHierarchy) {

        final Map<FastenURI, TypeURI> URIclassHierarchy = new HashMap<>();

        for (ObjectType aClass : classHierarchy.keySet()) {

            final var type = classHierarchy.get(aClass);

            final LinkedList<FastenURI> superClassesURIs;
            if(type.getSuperClasses() != null){
                superClassesURIs = toURIClasses(type.getSuperClasses());
            }else {
                logger.warn("There is no super type for {}", aClass);
                superClassesURIs = new LinkedList<>();
            }

                URIclassHierarchy.put(
                Method.getTypeURI(aClass),
                new TypeURI(
                    toURIMethods(type.getMethods()),
                    superClassesURIs,
                    toURIInterfaces(type.getSuperInterfaces())
                )
            );
        }
        return URIclassHierarchy;
    }

    /**
     * Converts a list of interfaces to a list of FastenURIs.
     *
     * @param interfaces A list of org.obalj.br.ObjectType
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> interfaces) {
        final List<FastenURI> classURIs = new ArrayList<>();
        for (ObjectType aClass : interfaces) {
            classURIs.add(Method.getTypeURI(aClass));
        }
        return classURIs;
    }

    /**
     * Converts a list of classes to a list of FastenURIs.
     *
     * @param classes A list of org.opalj.collection.immutable.Chain<org.obalj.br.ObjectType>
     * @return A list of eu.fasten.core.data.FastenURI.
     */
    public static LinkedList<FastenURI> toURIClasses(final Chain<ObjectType> classes){
        final LinkedList<FastenURI> classURIs = new LinkedList<>();

            classes.foreach(JavaToScalaConverter.asScalaFunction1(
                aClass -> classURIs.add(Method.getTypeURI((ObjectType) aClass))));

        return classURIs;
    }

    /**
     * Converts a list of methods to a list of FastenURIs.
     *
     * @param methods A list of org.obalj.br.Method
     * @return A list of eu.fasten.core.data.FastenURIs.
     */
    public static List<FastenURI> toURIMethods(final List<org.opalj.br.Method> methods) {
        final List<FastenURI> methodsURIs = new ArrayList<>();
        for (org.opalj.br.Method method : methods) {
            methodsURIs.add(
                Method.toCanonicalSchemelessURI(
                    null,
                    method.declaringClassFile().thisType(),
                    method.name(),
                    method.descriptor()
                )
            );
        }
        return methodsURIs;
    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @param partialCallGraph Partial graph with org.opalj.br.Method nodes.
     * @return A graph of all nodes in URI format represented in a List of eu.fasten.core.data.FastenURIs.
     */
    public static ArrayList<FastenURI[]> toURIGraph(final PartialCallGraph partialCallGraph) {

        final var graph = new ArrayList<FastenURI[]>();
        final int resolvedSize = partialCallGraph.getResolvedCalls().size();
        partialCallGraph.removeDuplicateResolvedCall();

        logger.info("Converting resolved calls to URIs ...");
        final AtomicInteger callNumber = new AtomicInteger();

        partialCallGraph.resolvedCalls.forEach(resolvedCall -> {
            final var URICalls = resolvedCall.toURICalls();
            if (URICalls.size() != 0) {

                callNumber.addAndGet(1);
                graph.addAll(URICalls);

                if (!isJUnitTest()) {
                    System.out.printf("> Processed: %d %% -> %d / %d \r",
                        (callNumber.get() * 100 / resolvedSize),
                        callNumber.get(),
                        resolvedSize);
                }

            }
        });
        logger.info("\nResolved calls have been converted to URIs");

        logger.info("Converting unresolved calls to URIs ...");
        partialCallGraph.unresolvedCalls.stream().distinct().collect(Collectors.toList()).forEach(unresolvedCall -> {
            final FastenURI[] URICall = unresolvedCall.toURICall();
            if (URICall[0] != null && URICall[1] != null) {

                graph.add(URICall);
            }
        });
        logger.info("Unresolved calls have been converted to URIs.");

        return graph;
    }

    /**
     * Checks whether the environment is test.
     * @return true if tests are running, otherwise false.
     */
    public static boolean isJUnitTest() {
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        final List<StackTraceElement> list = Arrays.asList(stackTrace);
        for (StackTraceElement element : list) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes duplicate resolved calls generated by OPAL from this object.
     *
     * @return unique resolved calls of this object.
     */
    private List<ResolvedCall> removeDuplicateResolvedCall() {

        final AtomicInteger numOfDups = new AtomicInteger();
        final AtomicInteger numOfAllArcs = new AtomicInteger();
        final AtomicInteger numOfUniqueArcs = new AtomicInteger();

        logger.info("Removing duplicated arcs from resolved Calls ...");
        this.resolvedCalls.forEach(currentCalls -> {
            int numOfTargets = currentCalls.getTargets().size();
            numOfAllArcs.addAndGet(numOfTargets);
            currentCalls.setTargets(currentCalls.getTargets().stream().distinct().collect(Collectors.toList()));
            numOfTargets = numOfTargets - currentCalls.getTargets().size();
            numOfUniqueArcs.addAndGet(currentCalls.getTargets().size());
            numOfDups.addAndGet(numOfTargets);
        });
        logger.info("From {} arcs in resolved Calls {} duplicated have been removed. number of unique arcs: {}. number of source nodes: {}", numOfAllArcs, numOfDups, numOfUniqueArcs, this.resolvedCalls.size());

        return this.resolvedCalls;

    }

    /**
     * Converts all nodes (entities) of a partial graph to URIs.
     *
     * @return A graph of all nodes in URI format represented in a List of eu.fasten.core.data.FastenURI.
     */
    public ArrayList<FastenURI[]> toURIGraph() {
        return toURIGraph(this);
    }


    public void clearGraph() {
        this.resolvedCalls = null;
        this.unresolvedCalls = null;
    }

    public void clearClassHierarchy() {
        this.classHierarchy.forEach((objectType, type) -> {
            objectType = null;
            type.setMethods(null);
            type.setSuperClasses(null);
            type.setSuperInterfaces(null);
        });
        this.classHierarchy.clear();
    }
}
