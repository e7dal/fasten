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


package eu.fasten.analyzer;


import eu.fasten.analyzer.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.data.fastenJSON.CanonicalJSON;
import eu.fasten.analyzer.data.type.MavenResolvedCoordinate;
import eu.fasten.analyzer.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.generator.WalaUFIAdapter;
import eu.fasten.core.data.JSONCallGraph;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class CallGraphGenerator {

    private static Logger logger = LoggerFactory.getLogger(CallGraphGenerator.class);


    private static List<MavenResolvedCoordinate> buildClasspath(String mavenCoordinate){
        logger.debug("Building classpath for {}", mavenCoordinate);
        var artifacts = Maven.resolver().
                resolve(mavenCoordinate).
                withTransitivity().
                asResolvedArtifact();

        var paths = Arrays.asList(artifacts).stream().
                map(MavenResolvedCoordinate::of).
                collect(Collectors.toList());
        logger.debug("The classpath for {} is {}", mavenCoordinate, paths);
        return paths;
    }


    public static JSONCallGraph generateCallGraph(String coordinate){

        try {
            List<MavenResolvedCoordinate> path = buildClasspath(coordinate);
            logger.debug("Building generator using generator....");
            long start = System.currentTimeMillis();
            WalaCallGraph cg = WalaCallgraphConstructor.build(path);
            logger.debug("Call graph construction took {}ms", System.currentTimeMillis() - start);

            //return CanonicalJSON.toJson(WalaUFIAdapter.wrap(cg), "");
            return null;
        } catch (Exception e) {
            logger.error("An exception occurred for {}", coordinate, e);
            return null;
        }
    }
}

