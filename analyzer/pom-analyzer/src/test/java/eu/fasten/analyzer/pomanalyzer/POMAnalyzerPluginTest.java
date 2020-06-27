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

package eu.fasten.analyzer.pomanalyzer;

import eu.fasten.analyzer.pomanalyzer.pom.data.DependencyData;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class POMAnalyzerPluginTest {

    private POMAnalyzerPlugin.POMAnalyzer pomAnalyzer;

    @BeforeEach
    public void setup() {
        pomAnalyzer = new POMAnalyzerPlugin.POMAnalyzer();
        pomAnalyzer.setTopic("fasten.maven.pkg");
    }

    @Test
    public void consumeTest() {
        var record = new JSONObject("{" +
                "\"payload\": {" +
                "\"artifactId\": \"junit\"," +
                "\"groupId\": \"junit\"," +
                "\"version\": \"4.12\"" +
                "}}").toString();
        var repoUrl = "http://github.com/junit-team/junit/tree/master";
        var dependencyData = DependencyData.fromJSON(new JSONObject("{\n" +
                "   \"dependencyManagement\":{\n" +
                "      \"dependencies\":[\n" +
                "\n" +
                "      ]\n" +
                "   },\n" +
                "   \"dependencies\":[\n" +
                "      {\n" +
                "         \"groupId\":\"org.hamcrest\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hamcrest-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\",\n" +
                "         \"version\":\"1.3\"\n" +
                "      }\n" +
                "   ]\n" +
                "}\n"));
        pomAnalyzer.consume(record);
        var output = pomAnalyzer.produce();
        assertTrue(output.isPresent());
        var json = new JSONObject(output.get());
        assertEquals("junit", json.getString("artifactId"));
        assertEquals("junit", json.getString("groupId"));
        assertEquals("4.12", json.getString("version"));
        assertEquals(repoUrl, json.getString("repoUrl"));
        assertEquals(dependencyData, DependencyData.fromJSON(json.getJSONObject("dependencyData")));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.maven.pkg"));
        assertEquals(topics, pomAnalyzer.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.maven.pkg"));
        assertEquals(topics1, pomAnalyzer.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        pomAnalyzer.setTopic(differentTopic);
        assertEquals(topics2, pomAnalyzer.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "POM Analyzer plugin";
        assertEquals(name, pomAnalyzer.name());
    }

    @Test
    public void descriptionTest() {
        var description = "POM Analyzer plugin. Consumes Maven coordinate from Kafka topic, "
                + "downloads pom.xml of that coordinate and analyzes it "
                + "extracting relevant information such as dependency information "
                + "and repository URL, then inserts that data into Metadata Database "
                + "and produces it to Kafka topic.";
        assertEquals(description, pomAnalyzer.description());
    }

    @Test
    public void versionTest() {
        var version = "0.0.1";
        assertEquals(version, pomAnalyzer.version());
    }
}
