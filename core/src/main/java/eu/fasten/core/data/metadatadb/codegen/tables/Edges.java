/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.JSONB;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row3;
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
public class Edges extends TableImpl<EdgesRecord> {

    private static final long serialVersionUID = -1042839435;

    /**
     * The reference instance of <code>public.edges</code>
     */
    public static final Edges EDGES = new Edges();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<EdgesRecord> getRecordType() {
        return EdgesRecord.class;
    }

    /**
     * The column <code>public.edges.source_id</code>.
     */
    public final TableField<EdgesRecord, Long> SOURCE_ID = createField(DSL.name("source_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.edges.target_id</code>.
     */
    public final TableField<EdgesRecord, Long> TARGET_ID = createField(DSL.name("target_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.edges.metadata</code>.
     */
    public final TableField<EdgesRecord, JSONB> METADATA = createField(DSL.name("metadata"), org.jooq.impl.SQLDataType.JSONB.nullable(false), this, "");

    /**
     * Create a <code>public.edges</code> table reference
     */
    public Edges() {
        this(DSL.name("edges"), null);
    }

    /**
     * Create an aliased <code>public.edges</code> table reference
     */
    public Edges(String alias) {
        this(DSL.name(alias), EDGES);
    }

    /**
     * Create an aliased <code>public.edges</code> table reference
     */
    public Edges(Name alias) {
        this(alias, EDGES);
    }

    private Edges(Name alias, Table<EdgesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Edges(Name alias, Table<EdgesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> Edges(Table<O> child, ForeignKey<O, EdgesRecord> key) {
        super(child, key, EDGES);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.EDGES_SOURCE_ID, Indexes.EDGES_TARGET_ID, Indexes.UNIQUE_SOURCE_TARGET);
    }

    @Override
    public List<UniqueKey<EdgesRecord>> getKeys() {
        return Arrays.<UniqueKey<EdgesRecord>>asList(Keys.UNIQUE_SOURCE_TARGET);
    }

    @Override
    public List<ForeignKey<EdgesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<EdgesRecord, ?>>asList(Keys.EDGES__EDGES_SOURCE_ID_FKEY, Keys.EDGES__EDGES_TARGET_ID_FKEY);
    }

    public Callables edges_EdgesSourceIdFkey() {
        return new Callables(this, Keys.EDGES__EDGES_SOURCE_ID_FKEY);
    }

    public Callables edges_EdgesTargetIdFkey() {
        return new Callables(this, Keys.EDGES__EDGES_TARGET_ID_FKEY);
    }

    @Override
    public Edges as(String alias) {
        return new Edges(DSL.name(alias), this);
    }

    @Override
    public Edges as(Name alias) {
        return new Edges(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Edges rename(String name) {
        return new Edges(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Edges rename(Name name) {
        return new Edges(name, null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<Long, Long, JSONB> fieldsRow() {
        return (Row3) super.fieldsRow();
    }
}
