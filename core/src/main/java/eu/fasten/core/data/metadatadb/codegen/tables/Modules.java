/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.ModulesRecord;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.JSONB;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row6;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.12.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Modules extends TableImpl<ModulesRecord> {

    private static final long serialVersionUID = 875529255;

    /**
     * The reference instance of <code>public.modules</code>
     */
    public static final Modules MODULES = new Modules();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ModulesRecord> getRecordType() {
        return ModulesRecord.class;
    }

    /**
     * The column <code>public.modules.id</code>.
     */
    public final TableField<ModulesRecord, Long> ID = createField(DSL.name("id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.field("nextval('modules_id_seq'::regclass)", org.jooq.impl.SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>public.modules.package_id</code>.
     */
    public final TableField<ModulesRecord, Long> PACKAGE_ID = createField(DSL.name("package_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.modules.namespaces</code>.
     */
    public final TableField<ModulesRecord, String> NAMESPACES = createField(DSL.name("namespaces"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>public.modules.sha256</code>.
     */
    public final TableField<ModulesRecord, byte[]> SHA256 = createField(DSL.name("sha256"), org.jooq.impl.SQLDataType.BLOB, this, "");

    /**
     * The column <code>public.modules.created_at</code>.
     */
    public final TableField<ModulesRecord, Timestamp> CREATED_AT = createField(DSL.name("created_at"), org.jooq.impl.SQLDataType.TIMESTAMP, this, "");

    /**
     * The column <code>public.modules.metadata</code>.
     */
    public final TableField<ModulesRecord, JSONB> METADATA = createField(DSL.name("metadata"), org.jooq.impl.SQLDataType.JSONB, this, "");

    /**
     * Create a <code>public.modules</code> table reference
     */
    public Modules() {
        this(DSL.name("modules"), null);
    }

    /**
     * Create an aliased <code>public.modules</code> table reference
     */
    public Modules(String alias) {
        this(DSL.name(alias), MODULES);
    }

    /**
     * Create an aliased <code>public.modules</code> table reference
     */
    public Modules(Name alias) {
        this(alias, MODULES);
    }

    private Modules(Name alias, Table<ModulesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Modules(Name alias, Table<ModulesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> Modules(Table<O> child, ForeignKey<O, ModulesRecord> key) {
        super(child, key, MODULES);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.MODULES_PKEY);
    }

    @Override
    public Identity<ModulesRecord, Long> getIdentity() {
        return Keys.IDENTITY_MODULES;
    }

    @Override
    public UniqueKey<ModulesRecord> getPrimaryKey() {
        return Keys.MODULES_PKEY;
    }

    @Override
    public List<UniqueKey<ModulesRecord>> getKeys() {
        return Arrays.<UniqueKey<ModulesRecord>>asList(Keys.MODULES_PKEY);
    }

    @Override
    public List<ForeignKey<ModulesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<ModulesRecord, ?>>asList(Keys.MODULES__MODULES_PACKAGE_ID_FKEY);
    }

    public PackageVersions packageVersions() {
        return new PackageVersions(this, Keys.MODULES__MODULES_PACKAGE_ID_FKEY);
    }

    @Override
    public Modules as(String alias) {
        return new Modules(DSL.name(alias), this);
    }

    @Override
    public Modules as(Name alias) {
        return new Modules(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Modules rename(String name) {
        return new Modules(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Modules rename(Name name) {
        return new Modules(name, null);
    }

    // -------------------------------------------------------------------------
    // Row6 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row6<Long, Long, String, byte[], Timestamp, JSONB> fieldsRow() {
        return (Row6) super.fieldsRow();
    }
}
