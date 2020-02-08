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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopal.exceptions.ExceededMaxCallGraphSize;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OPALPlugin extends Plugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL implements KafkaConsumer<String>, KafkaProducer {

        private static Logger logger = LoggerFactory.getLogger(OPALPlugin.class);

        private static org.apache.kafka.clients.producer.KafkaProducer<Object, String> kafkaProducer;
        final String CONSUME_TOPIC = "maven.packages";
        final String PRODUCE_TOPIC = "opal_callgraphs";
        private final long CONSUMER_TIME = 1; // 1 minute for generating a call graph
        private boolean processedRecord;
        ExtendedRevisionCallGraph lastCallGraphGenerated = null;
        private String pluginError = "";


        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(CONSUME_TOPIC));
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in eu.fasten.core.data.RevisionCallGraph format,
         * and produce them to the Producer that is provided for this Object.
         *
         * @param kafkaRecord A record including maven coordinates in the JSON format.
         *                e.g. {
         *                "groupId": "com.g2forge.alexandria",
         *                "artifactId": "alexandria",
         *                "version": "0.0.9",
         *                "date": "1574072773"
         *                }
         */
        @Override
        public void consume(final String topic, final ConsumerRecord<String, String> kafkaRecord){

            try {

                processedRecord = false;

                JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());
                MavenCoordinate mavenCoordinate = new MavenCoordinate(kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());

                logger.info("Generating RevisionCallGraph for {} ...", mavenCoordinate.getCoordinate());

                try {
                    lastCallGraphGenerated = ExtendedRevisionCallGraph.create("mvn", mavenCoordinate,
                        Long.parseLong(kafkaConsumedJson.get("date").toString()));
                    //ExecutorService OPALExecutor = Executors.newSingleThreadExecutor();
                    //OPALExecutor.submit(() -> {
                        //try {

//                        } catch (FileNotFoundException e) {
//                            setPluginError(e.getClass().getSimpleName());
//                            logger.error("Could not download the JAR file of Maven coordinate: {}", mavenCoordinate.getCoordinate());
//                            e.printStackTrace();
//                        }
                    //}).get(CONSUMER_TIME, TimeUnit.MINUTES);
                    //OPALExecutor.shutdown();

                    if(lastCallGraphGenerated != null && !lastCallGraphGenerated.isCallGraphEmpty()){
                        logger.info("RevisionCallGraph successfully generated for {}!", mavenCoordinate.getCoordinate());

                        logger.info("Producing generated call graph for {} to Kafka ...", lastCallGraphGenerated.uri.toString());

                        ProducerRecord<Object, String> record = new ProducerRecord<>(this.PRODUCE_TOPIC,
                            lastCallGraphGenerated.uri.toString(), lastCallGraphGenerated.toJSON().toString());

                        kafkaProducer.send(record, ((recordMetadata, e) -> {
                            if (recordMetadata != null) {
                                logger.debug("Sent: {} to {}", lastCallGraphGenerated.uri.toString(), this.PRODUCE_TOPIC);
                            } else {
                                e.printStackTrace();
                            }
                        }));
                        processedRecord = true;
                    }else {
                        logger.error("The graph of {} was empty.", mavenCoordinate.getCoordinate());
                    }

                } catch (NullPointerException e) {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("Null pointer. It might not have a Kafka producer due to test purposes. {}", e.getStackTrace());
//                } catch (TimeoutException e){
//                    setPluginError(e.getClass().getSimpleName());
//                    logger.info("Exceeded allowed time for generation of the call graph: {}", mavenCoordinate.getCoordinate());
                } catch (FileNotFoundException e) {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("Could not download the JAR file of Maven coordinate: {}", mavenCoordinate.getCoordinate());
                    e.printStackTrace();
                } catch (ExceededMaxCallGraphSize e) {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("{} : {}", e.getMessage(), mavenCoordinate.getCoordinate());
                } catch (Exception e) {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("", e.getStackTrace());
                    e.printStackTrace();
                }
            } catch (JSONException e){
                logger.error("Couldn't parse the record as JSON: {}\n{}", kafkaRecord.value(), e.getStackTrace());
            }
        }

        @Override
        public String producerTopic() {
            return this.PRODUCE_TOPIC;
        }

        /**
         * This method should be called before calling consume method.
         * It sets the KafkaProducer of this Object to what is passed to it.
         *
         * @param producer org.apache.kafka.clients.producer.KafkaProducer.
         */
        @Override
        public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer<Object, String> producer) {
            this.kafkaProducer = producer;
        }

        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Generates call graphs for Java packages using OPAL";
        }

        @Override
        public boolean recordProcessSuccessful(){
            return this.processedRecord;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void setPluginError(String exceptionType) {
            this.pluginError = exceptionType;
        }

        @Override
        public String getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {
            if(this.lastCallGraphGenerated != null){
                this.lastCallGraphGenerated.clear();
                this.lastCallGraphGenerated = null;
            }
        }
    }
}
