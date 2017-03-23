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

package org.codelibs.elasticsearch.common.bytes;

import org.codelibs.elasticsearch.common.lease.Releasable;
import org.codelibs.elasticsearch.common.lease.Releasables;
import org.codelibs.elasticsearch.common.util.BigArrays;
import org.codelibs.elasticsearch.common.util.ByteArray;

/**
 * An extension to {PagedBytesReference} that requires releasing its content. This
 * class exists to make it explicit when a bytes reference needs to be released, and when not.
 */
public final class ReleasablePagedBytesReference extends PagedBytesReference implements Releasable {

    public ReleasablePagedBytesReference(BigArrays bigarrays, ByteArray byteArray, int length) {
        super(bigarrays, byteArray, length);
    }

    @Override
    public void close() {
        Releasables.close(byteArray);
    }

}
