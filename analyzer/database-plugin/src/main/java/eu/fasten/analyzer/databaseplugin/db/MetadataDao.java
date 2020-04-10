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

package eu.fasten.analyzer.databaseplugin.db;

import com.github.t9t.jooq.json.JsonbDSL;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Query;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataDao {

    private DSLContext context;
    private final Logger logger = LoggerFactory.getLogger(MetadataDao.class.getName());

    public MetadataDao(DSLContext context) {
        this.context = context;
    }

    public DSLContext getContext() {
        return this.context;
    }

    public void setContext(DSLContext context) {
        this.context = context;
    }

    /**
     * Inserts a record in 'packages' table in the database.
     *
     * @param packageName Name of the package
     * @param forge       Forge of the package
     * @param projectName Project name to which package belongs
     * @param repository  Repository to which package belongs
     * @param createdAt   Timestamp when package was created
     * @return ID of the new record
     */
    public long insertPackage(String packageName, String forge, String projectName,
                              String repository, Timestamp createdAt) {
        var packageId = this.findPackage(packageName, forge);
        if (packageId != -1L) {
            logger.debug("Duplicate package: '" + packageName + "; " + forge
                    + "' already exists with ID=" + packageId);
            this.updatePackage(packageId, projectName, repository, createdAt);
            return packageId;
        } else {
            var resultRecord = context.insertInto(Packages.PACKAGES,
                    Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE,
                    Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                    Packages.PACKAGES.CREATED_AT)
                    .values(packageName, forge, projectName, repository, createdAt)
                    .returning(Packages.PACKAGES.ID).fetchOne();
            return resultRecord.getValue(Packages.PACKAGES.ID);
        }
    }

    /**
     * Searches 'packages' table for certain package record.
     *
     * @param packageName Name of the package
     * @param forge       Forge of the package
     * @return ID of the record found or -1 otherwise
     */
    public long findPackage(String packageName, String forge) {
        var resultRecords = context.selectFrom(Packages.PACKAGES)
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(Packages.PACKAGES.FORGE.eq(forge)).fetch();
        if (resultRecords == null || resultRecords.isEmpty()) {
            return -1L;
        } else {
            return resultRecords.getValues(Packages.PACKAGES.ID).get(0);
        }
    }

    /**
     * Updates nullable attributes of certain package record.
     *
     * @param packageId   ID of the package record
     * @param projectName Project name for the package
     * @param repository  Repository for the package
     * @param timestamp   Timestamp for the package
     */
    public void updatePackage(long packageId, String projectName, String repository,
                              Timestamp timestamp) {
        context.update(Packages.PACKAGES).set(Packages.PACKAGES.PROJECT_NAME, projectName)
                .set(Packages.PACKAGES.REPOSITORY, repository)
                .set(Packages.PACKAGES.CREATED_AT, timestamp)
                .where(Packages.PACKAGES.ID.eq(packageId)).execute();
    }

    /**
     * Inserts multiple records in the 'packages' table in the database.
     *
     * @param packageNames List of names of the packages
     * @param forges       List of forges of the packages
     * @param projectNames List of names of the projects
     * @param repositories List of repositories
     * @param createdAt    List of timestamps
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertPackages(List<String> packageNames,
                                     List<String> forges, List<String> projectNames,
                                     List<String> repositories, List<Timestamp> createdAt)
            throws IllegalArgumentException {
        if (packageNames.size() != forges.size() || forges.size() != projectNames.size()
                || projectNames.size() != repositories.size()
                || repositories.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = packageNames.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackage(packageNames.get(i), forges.get(i), projectNames.get(i),
                    repositories.get(i), createdAt.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in 'package_versions' table in the database.
     *
     * @param packageId   ID of the package (references 'packages.id')
     * @param cgGenerator Tool used to generate this callgraph
     * @param version     Version of the package
     * @param createdAt   Timestamp when the package version was created
     * @param metadata    Metadata of the package version
     * @return ID of the new record
     */
    public long insertPackageVersion(long packageId, String cgGenerator, String version,
                                     Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var packageVersionId = this.findPackageVersion(packageId, cgGenerator, version);
        if (packageVersionId != -1L) {
            logger.debug("Duplicate package version: '" + packageId + "; " + cgGenerator + "; "
                    + version + "' already exists with ID=" + packageVersionId);
            this.updatePackageVersion(packageVersionId, createdAt, metadataJsonb);
            return packageVersionId;
        } else {
            var resultRecord = context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                    PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID,
                    PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                    PackageVersions.PACKAGE_VERSIONS.VERSION,
                    PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                    PackageVersions.PACKAGE_VERSIONS.METADATA)
                    .values(packageId, cgGenerator, version, createdAt, metadataJsonb)
                    .returning(PackageVersions.PACKAGE_VERSIONS.ID).fetchOne();
            return resultRecord.getValue(PackageVersions.PACKAGE_VERSIONS.ID);
        }
    }

    /**
     * Searches 'package_versions' table for certain package version record.
     *
     * @param packageId ID of the package
     * @param generator Callgraph generator
     * @param version   Package version
     * @return ID of the package version found or -1 otherwise
     */
    public long findPackageVersion(long packageId, String generator, String version) {
        var resultRecords = context.selectFrom(PackageVersions.PACKAGE_VERSIONS)
                .where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(packageId))
                .and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(generator))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)).fetch();
        if (resultRecords == null || resultRecords.isEmpty()) {
            return -1L;
        } else {
            return resultRecords.getValues(PackageVersions.PACKAGE_VERSIONS.ID).get(0);
        }
    }

    /**
     * Updates timestamp and metadata of certain package version record.
     *
     * @param packageVersionId ID of the package version record
     * @param timestamp        New timestamp for package version
     * @param metadata         New metadata for package version
     */
    public void updatePackageVersion(long packageVersionId, Timestamp timestamp, JSONB metadata) {
        context.update(PackageVersions.PACKAGE_VERSIONS)
                .set(PackageVersions.PACKAGE_VERSIONS.CREATED_AT, timestamp)
                .set(PackageVersions.PACKAGE_VERSIONS.METADATA, metadata)
                .where(PackageVersions.PACKAGE_VERSIONS.ID.eq(packageVersionId)).execute();
    }

    /**
     * Inserts multiple records in the 'package_versions' table in the database.
     *
     * @param packageId    ID of the common package (references 'packages.id')
     * @param cgGenerators List of code generators
     * @param versions     List of versions
     * @param createdAt    List of timestamps
     * @param metadata     List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertPackageVersions(long packageId, List<String> cgGenerators,
                                            List<String> versions, List<Timestamp> createdAt,
                                            List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (cgGenerators.size() != versions.size() || versions.size() != createdAt.size()
                || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = cgGenerators.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackageVersion(packageId, cgGenerators.get(i), versions.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'dependencies' table in the database.
     *
     * @param packageVersionId ID of the package version (references 'package_versions.id')
     * @param dependencyId     ID of the dependency package (references 'packages.id')
     * @param versionRanges    Ranges of valid versions
     * @return ID of the package (packageId)
     */
    public long insertDependency(long packageVersionId, long dependencyId, String[] versionRanges) {
        var foundPackageId = this.findDependency(packageVersionId, dependencyId, versionRanges);
        if (foundPackageId != -1L) {
            logger.debug("Duplicate dependency: '" + packageVersionId + "; " + dependencyId + "; "
                    + Arrays.toString(versionRanges)
                    + "' already exists with ID=" + foundPackageId);
            return foundPackageId;
        } else {
            var resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                    Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                    Dependencies.DEPENDENCIES.DEPENDENCY_ID,
                    Dependencies.DEPENDENCIES.VERSION_RANGE)
                    .values(packageVersionId, dependencyId, versionRanges)
                    .returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID).fetchOne();
            return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID);
        }
    }

    /**
     * Searches 'dependencies' table for certain dependency record.
     *
     * @param packageVersionId ID of the package version
     * @param dependencyId     ID of the dependency
     * @param versionRanges    Version ranges of the dependency
     * @return ID the of the record found or -1 otherwise
     */
    public long findDependency(long packageVersionId, long dependencyId, String[] versionRanges) {
        var resultRecords = context.selectFrom(Dependencies.DEPENDENCIES)
                .where(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(packageVersionId))
                .and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))
                .and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class)
                        .eq(versionRanges)).fetch();
        if (resultRecords == null || resultRecords.isEmpty()) {
            return -1L;
        } else {
            return resultRecords.getValues(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID).get(0);
        }
    }

    /**
     * Inserts multiple 'dependencies' int the database for certain package.
     *
     * @param packageVersionId ID of the package version
     * @param dependenciesIds  List of IDs of dependencies
     * @param versionRanges    List of version ranges
     * @return ID of the package (packageId)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public long insertDependencies(long packageVersionId, List<Long> dependenciesIds,
                                   List<String[]> versionRanges)
            throws IllegalArgumentException {
        if (dependenciesIds.size() != versionRanges.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = dependenciesIds.size();
        for (int i = 0; i < length; i++) {
            insertDependency(packageVersionId, dependenciesIds.get(i), versionRanges.get(i));
        }
        return packageVersionId;
    }

    /**
     * Inserts a record in  'modules' table in the database.
     *
     * @param packageVersionId ID of the package version where the module belongs
     *                         (references 'package_versions.id')
     * @param namespaces       Namespaces of the module
     * @param sha256           SHA256 of the module
     * @param createdAt        Timestamp when the module was created
     * @param metadata         Metadata of the module
     * @return ID of the new record
     */
    public long insertModule(long packageVersionId, String namespaces, byte[] sha256,
                             Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var moduleId = this.findModule(packageVersionId, namespaces);
        if (moduleId != -1L) {
            logger.debug("Duplicate module: '" + packageVersionId + "; " + namespaces
                    + "' already exists with ID=" + moduleId);
            this.updateModule(moduleId, sha256, createdAt, metadataJsonb);
            return moduleId;
        } else {
            var resultRecord = context.insertInto(Modules.MODULES,
                    Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACES,
                    Modules.MODULES.SHA256, Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)
                    .values(packageVersionId, namespaces, sha256, createdAt, metadataJsonb)
                    .returning(Modules.MODULES.ID).fetchOne();
            return resultRecord.getValue(Modules.MODULES.ID);
        }
    }

    /**
     * Searches 'modules' table for certain module record.
     *
     * @param packageVersionId ID of the package version
     * @param namespaces       Namespaces of the module
     * @return ID of the record found or -1 otherwise
     */
    public long findModule(long packageVersionId, String namespaces) {
        var resultRecords = context.selectFrom(Modules.MODULES)
                .where(Modules.MODULES.PACKAGE_VERSION_ID.eq(packageVersionId))
                .and(Modules.MODULES.NAMESPACES.eq(namespaces)).fetch();
        if (resultRecords == null || resultRecords.isEmpty()) {
            return -1L;
        } else {
            return resultRecords.getValues(Modules.MODULES.ID).get(0);
        }
    }

    /**
     * Updates SHA256, timestamp and metadata of certain module record.
     *
     * @param moduleId  ID of the module record
     * @param sha256    New SHA256 for the module
     * @param timestamp New timestamp for the module
     * @param metadata  New metadata for the module
     */
    public void updateModule(long moduleId, byte[] sha256, Timestamp timestamp, JSONB metadata) {
        context.update(Modules.MODULES)
                .set(Modules.MODULES.SHA256, sha256)
                .set(Modules.MODULES.CREATED_AT, timestamp)
                .set(Modules.MODULES.METADATA, metadata)
                .where(Modules.MODULES.ID.eq(moduleId)).execute();
    }

    /**
     * Inserts multiple records in the 'module' table in the database.
     *
     * @param packageVersionId ID of the common package version
     * @param namespacesList   List of namespaces
     * @param sha256s          List of SHA256s
     * @param createdAt        List of timestamps
     * @param metadata         List of metadata objects
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertModules(long packageVersionId, List<String> namespacesList,
                                    List<byte[]> sha256s, List<Timestamp> createdAt,
                                    List<JSONObject> metadata) throws IllegalArgumentException {
        if (namespacesList.size() != sha256s.size() || sha256s.size() != createdAt.size()
                || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = namespacesList.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertModule(packageVersionId, namespacesList.get(i), sha256s.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'callables' table in the database.
     *
     * @param moduleId       ID of the module where the callable belongs (references 'modules.id')
     * @param fastenUri      URI of the callable in FASTEN
     * @param isResolvedCall 'true' if call is resolved, 'false' otherwise
     * @param createdAt      Timestamp when the callable was created
     * @param metadata       Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(Long moduleId, String fastenUri, boolean isResolvedCall,
                               Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var callableId = this.findCallable(fastenUri, isResolvedCall);
        if (callableId != -1L) {
            logger.debug("Duplicate callable: '" + fastenUri + "; " + isResolvedCall
                    + "' already exists with ID=" + callableId);
            this.updateCallable(callableId, createdAt, metadataJsonb);
            return callableId;
        } else {
            var resultRecord = context.insertInto(Callables.CALLABLES,
                    Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                    Callables.CALLABLES.IS_RESOLVED_CALL, Callables.CALLABLES.CREATED_AT,
                    Callables.CALLABLES.METADATA)
                    .values(moduleId, fastenUri, isResolvedCall, createdAt, metadataJsonb)
                    .returning(Callables.CALLABLES.ID).fetchOne();
            return resultRecord.getValue(Callables.CALLABLES.ID);
        }
    }

    /**
     * Searches 'callables' table for certain callable record.
     *
     * @param fastenUri      FASTEN URI of the callable
     * @param isResolvedCall is callable a resolved call or not
     * @return ID of the record found or -1 otherwise
     */
    public long findCallable(String fastenUri, boolean isResolvedCall) {
        var resultRecords = context.selectFrom(Callables.CALLABLES)
                .where(Callables.CALLABLES.FASTEN_URI.eq(fastenUri))
                .and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(isResolvedCall)).fetch();
        if (resultRecords == null || resultRecords.isEmpty()) {
            return -1L;
        } else {
            return resultRecords.getValues(Callables.CALLABLES.ID).get(0);
        }
    }

    /**
     * Updates timestamp and metadata of certain callable record.
     *
     * @param callableId ID of the callable record
     * @param timestamp  New timestamp for the callable
     * @param metadata   New metadata for the callable
     */
    public void updateCallable(long callableId, Timestamp timestamp, JSONB metadata) {
        context.update(Callables.CALLABLES)
                .set(Callables.CALLABLES.CREATED_AT, timestamp)
                .set(Callables.CALLABLES.METADATA, metadata)
                .where(Callables.CALLABLES.ID.eq(callableId)).execute();
    }

    /**
     * Inserts multiple records in the 'callables' table in the database.
     *
     * @param moduleId         ID of the common module
     * @param fastenUris       List of FASTEN URIs
     * @param areResolvedCalls List of IsResolvedCall booleans
     * @param createdAt        List of timestamps
     * @param metadata         List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertCallables(long moduleId, List<String> fastenUris,
                                      List<Boolean> areResolvedCalls, List<Timestamp> createdAt,
                                      List<JSONObject> metadata) throws IllegalArgumentException {
        if (fastenUris.size() != areResolvedCalls.size()
                || areResolvedCalls.size() != metadata.size()
                || metadata.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = fastenUris.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertCallable(moduleId, fastenUris.get(i),
                    areResolvedCalls.get(i), createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'edges' table in the database.
     *
     * @param sourceId ID of the source callable (references 'callables.id')
     * @param targetId ID of the target callable (references 'callables.id')
     * @param metadata Metadata of the edge between source and target
     * @return ID of the source callable (sourceId)
     */
    public long insertEdge(long sourceId, long targetId, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString())
                : JSONB.valueOf("{}");
        var resultRecord = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.METADATA)
                .values(sourceId, targetId, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA))
                .returning(Edges.EDGES.SOURCE_ID).fetchOne();
        return resultRecord.getValue(Edges.EDGES.SOURCE_ID);
    }

    /**
     * Inserts multiple records in the 'edges' table in the database.
     *
     * @param sourceIds List of IDs of source callables
     * @param targetIds List of IDs of target callables
     * @param metadata  List of metadata objects
     * @return List of IDs of source callables (sourceIds)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertEdges(List<Long> sourceIds, List<Long> targetIds,
                                  List<JSONObject> metadata) throws IllegalArgumentException {
        if (sourceIds.size() != targetIds.size() || targetIds.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = sourceIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertEdge(sourceIds.get(i), targetIds.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Executes batch insert for 'edges' table.
     *
     * @param edges List of edges records to insert
     */
    public void batchInsertEdges(List<EdgesRecord> edges) {
        Query batchQuery = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.METADATA)
                .values((Long) null, (Long) null, (JSONB) null)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA));
        var batchBind = context.batch(batchQuery);
        for (var edge : edges) {
            batchBind = batchBind.bind(edge.getSourceId(), edge.getTargetId(), edge.getMetadata());
        }
        batchBind.execute();
    }
}