/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.codelibs.elasticsearch.index.fielddata.plain;

import org.codelibs.elasticsearch.index.fielddata.AtomicNumericFieldData;
import org.codelibs.elasticsearch.index.fielddata.FieldData;
import org.codelibs.elasticsearch.index.fielddata.ScriptDocValues;
import org.codelibs.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.codelibs.elasticsearch.index.fielddata.SortedNumericDoubleValues;

/**
 * Specialization of {@link AtomicNumericFieldData} for integers.
 */
abstract class AtomicLongFieldData implements AtomicNumericFieldData {

    private final long ramBytesUsed;
    /** True if this numeric data is for a boolean field, and so only has values 0 and 1. */
    private final boolean isBoolean;

    AtomicLongFieldData(long ramBytesUsed, boolean isBoolean) {
        this.ramBytesUsed = ramBytesUsed;
        this.isBoolean = isBoolean;
    }

    @Override
    public long ramBytesUsed() {
        return ramBytesUsed;
    }

    @Override
    public final ScriptDocValues getScriptValues() {
        if (isBoolean) {
            return new ScriptDocValues.Booleans(getLongValues());
        } else {
            return new ScriptDocValues.Longs(getLongValues());
        }
    }

    @Override
    public final SortedBinaryDocValues getBytesValues() {
        return FieldData.toString(getLongValues());
    }

    @Override
    public final SortedNumericDoubleValues getDoubleValues() {
        return FieldData.castToDouble(getLongValues());
    }

    @Override
    public void close() {}
}