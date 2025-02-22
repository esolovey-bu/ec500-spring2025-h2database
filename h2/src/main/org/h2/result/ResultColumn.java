/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.result;

import java.io.IOException;

import org.h2.engine.Constants;
import org.h2.value.Transfer;
import org.h2.value.TypeInfo;

/**
 * A result set column of a remote result.
 */
public class ResultColumn {

    /**
     * The column alias.
     */
    final String alias;

    /**
     * The schema name or null.
     */
    final String schemaName;

    /**
     * The table name or null.
     */
    final String tableName;

    /**
     * The column name or null.
     */
    final String columnName;

    /**
     * The column type.
     */
    final TypeInfo columnType;

    /**
     * True if this is an identity column.
     */
    final boolean identity;

    /**
     * True if this column is nullable.
     */
    final int nullable;

    /**
     * Read an object from the given transfer object.
     *
     * @param in the object from where to read the data
     */
    ResultColumn(Transfer in) throws IOException {
        alias = in.readString();
        schemaName = in.readString();
        tableName = in.readString();
        columnName = in.readString();
        columnType = in.readTypeInfo();
        if (in.getVersion() < Constants.TCP_PROTOCOL_VERSION_20) {
            in.readInt();
        }
        identity = in.readBoolean();
        nullable = in.readInt();
    }

    /**
     * Write a result column to the given output.
     *
     * @param out the object to where to write the data
     * @param result the result
     * @param i the column index
     * @throws IOException on failure
     */
    public static void writeColumn(Transfer out, ResultInterface result, int i)
            throws IOException {
        out.writeString(result.getAlias(i));
        out.writeString(result.getSchemaName(i));
        out.writeString(result.getTableName(i));
        out.writeString(result.getColumnName(i));
        TypeInfo type = result.getColumnType(i);
        out.writeTypeInfo(type);
        if (out.getVersion() < Constants.TCP_PROTOCOL_VERSION_20) {
            out.writeInt(type.getDisplaySize());
        }
        out.writeBoolean(result.isIdentity(i));
        out.writeInt(result.getNullable(i));
    }

}
