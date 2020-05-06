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

package eu.fasten.analyzer.metadataplugin;

import eu.fasten.analyzer.metadataplugin.db.MetadataDao;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataDatabasePlugin extends Plugin {

    public MetadataDatabasePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MetadataDBExtension implements KafkaPlugin<String, String>, DBConnector {

        private String topic = "fasten.OPAL.out";
        private static DSLContext dslContext;
        private boolean processedRecord = false;
        private Throwable pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(MetadataDBExtension.class.getName());
        private boolean restartTransaction = false;
        private final int transactionRestartLimit = 3;

        @Override
        public void setDBConnection(DSLContext dslContext) {
            MetadataDBExtension.dslContext = dslContext;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(new ArrayList<>(Collections.singletonList(topic)));
        }

        @Override
        public void setTopic(String topicName) {
            this.topic = topicName;
        }

        @Override
        public void consume(String record) {
            final var consumedJson = new JSONObject(record).getJSONObject("payload");
            final var artifact = consumedJson.optString("product") + "@"
                    + consumedJson.optString("version");
            this.processedRecord = false;
            this.restartTransaction = false;
            this.pluginError = null;
            ExtendedRevisionCallGraph callgraph;
            try {
                callgraph = new ExtendedRevisionCallGraph(consumedJson);
            } catch (JSONException | IllegalArgumentException e) {
                logger.error("Error parsing JSON callgraph for '" + artifact + "'", e);
                processedRecord = false;
                setPluginError(e);
                return;
            }

            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabase(callgraph, metadataDao);
                        } catch (RuntimeException e) {
                            logger.error("Error saving to the database: '" + artifact + "'", e);
                            processedRecord = false;
                            setPluginError(e);
                            if (e instanceof DataAccessException) {
                                logger.info("Restarting transaction for '" + artifact + "'");
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError() == null) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved the '" + artifact + "' callgraph metadata "
                                    + "to the database with package ID = " + id);
                        }
                    });
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < transactionRestartLimit);
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        /**
         * Saves a callgraph to the database to appropriate tables.
         *
         * @param callGraph   Call graph to save to the database.
         * @param metadataDao Data Access Object to insert records in the database
         * @return Package ID saved in the database
         */
        public long saveToDatabase(ExtendedRevisionCallGraph callGraph, MetadataDao metadataDao) {
            final var timestamp = this.getProperTimestamp(callGraph.timestamp);
            final long packageId = metadataDao.insertPackage(callGraph.product, callGraph.forge,
                    null, null, null);

            final long packageVersionId = metadataDao.insertPackageVersion(packageId,
                    callGraph.getCgGenerator(), callGraph.version, timestamp, null);

            var depIds = new ArrayList<Long>();
            var depVersions = new ArrayList<String[]>();
            for (var depList : callGraph.depset) {
                for (var dependency : depList) {
                    var constraints = dependency.constraints;
                    var versions = new String[constraints.size()];
                    for (int i = 0; i < constraints.size(); i++) {
                        versions[i] = constraints.get(i).toString();
                    }
                    depVersions.add(versions);
                    var depId = metadataDao.insertPackage(dependency.product, dependency.forge,
                            null, null, null);
                    depIds.add(depId);
                }
            }
            if (depIds.size() > 0) {
                metadataDao.insertDependencies(packageVersionId, depIds, depVersions);
            }

            final var cha = callGraph.getClassHierarchy();
            var internalCallables = new ArrayList<CallablesRecord>();
            for (var fastenUri : cha.keySet()) {
                var type = cha.get(fastenUri);
                var moduleMetadata = new JSONObject();
                moduleMetadata.put("superInterfaces",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperInterfaces()));
                moduleMetadata.put("superClasses",
                        ExtendedRevisionCallGraph.Type.toListOfString(type.getSuperClasses()));
                long moduleId = metadataDao.insertModule(packageVersionId, fastenUri.toString(),
                        null, moduleMetadata);
                var fileName = type.getSourceFileName();
                var fileId = metadataDao.insertFile(packageVersionId, fileName, null, null, null);
                metadataDao.insertModuleContent(moduleId, fileId);
                for (var methodEntry : type.getMethods().entrySet()) {
                    var localId = (long) methodEntry.getKey();
                    var uri = methodEntry.getValue().toString();
                    internalCallables.add(new CallablesRecord(localId, moduleId, uri, true, null, null));
                }
            }

            final var graph = callGraph.getGraph();
            var internalEdges = new ArrayList<EdgesRecord>(graph.getInternalCalls().size());
            final var internalCalls = graph.getInternalCalls();
            for (var call : internalCalls) {
                var sourceLocalId = (long) call.get(0);
                var targetLocalId = (long) call.get(1);
                internalEdges.add(new EdgesRecord(sourceLocalId, targetLocalId, JSONB.valueOf("{}")));
            }

            final var externalCalls = graph.getExternalCalls();
            var externalEdges = new ArrayList<EdgesRecord>(graph.getExternalCalls().size());
            for (var callEntry : externalCalls.entrySet()) {
                var call = callEntry.getKey();
                var sourceLocalId = (long) call.getKey();
                var uri = call.getValue().toString();
                var targetId = metadataDao.insertCallable(null, uri, false, null, null);
                var edgeMetadata = new JSONObject(callEntry.getValue());
                externalEdges.add(new EdgesRecord(sourceLocalId, targetId,
                        JSONB.valueOf(edgeMetadata.toString())));
            }

            final int batchSize = 4096;
            var internalCallablesIds = new ArrayList<Long>(internalCallables.size());
            final var internalCallablesIterator = internalCallables.iterator();
            while (internalCallablesIterator.hasNext()) {
                var callablesBatch = new ArrayList<CallablesRecord>(batchSize);
                while (internalCallablesIterator.hasNext() && callablesBatch.size() < batchSize) {
                    callablesBatch.add(internalCallablesIterator.next());
                }
                var ids = metadataDao.batchInsertCallables(callablesBatch);
                internalCallablesIds.addAll(ids);
            }

            var internalLidToGidMap = new HashMap<Long, Long>();
            for (int i = 0; i < internalCallables.size(); i++) {
                internalLidToGidMap.put(internalCallables.get(i).getId(), internalCallablesIds.get(i));
            }
            for (var edge : internalEdges) {
                edge.setSourceId(internalLidToGidMap.get(edge.getSourceId()));
                edge.setTargetId(internalLidToGidMap.get(edge.getTargetId()));
            }
            for (var edge : externalEdges) {
                edge.setSourceId(internalLidToGidMap.get(edge.getSourceId()));
            }

            var edges = new ArrayList<EdgesRecord>(graph.size());
            edges.addAll(internalEdges);
            edges.addAll(externalEdges);
            final var edgesIterator = edges.iterator();
            while (edgesIterator.hasNext()) {
                var edgesBatch = new ArrayList<EdgesRecord>(batchSize);
                while (edgesIterator.hasNext() && edgesBatch.size() < batchSize) {
                    edgesBatch.add(edgesIterator.next());
                }
                metadataDao.batchInsertEdges(edgesBatch);
            }

            return packageId;
        }

        private Timestamp getProperTimestamp(long timestamp) {
            if (timestamp == -1) {
                return null;
            } else {
                if (timestamp / (1000L * 60 * 60 * 24 * 365) < 1L) {
                    return new Timestamp(timestamp * 1000);
                } else {
                    return new Timestamp(timestamp);
                }
            }
        }

        @Override
        public String name() {
            return "Metadata plugin";
        }

        @Override
        public String description() {
            return "Metadata plugin. "
                    + "Consumes ExtendedRevisionCallgraph-formatted JSON objects from Kafka topic"
                    + " and populates metadata database with consumed data.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }

    }
}
