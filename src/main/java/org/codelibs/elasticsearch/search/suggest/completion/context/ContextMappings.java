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

package org.codelibs.elasticsearch.search.suggest.completion.context;

import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.search.suggest.document.ContextQuery;
import org.apache.lucene.util.CharsRefBuilder;
import org.codelibs.elasticsearch.ElasticsearchParseException;
import org.codelibs.elasticsearch.Version;
import org.codelibs.elasticsearch.common.xcontent.ToXContent;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ContextMappings indexes context-enabled suggestion fields
 * and creates context queries for defined {ContextMapping}s
 */
public class ContextMappings implements ToXContent {
    private final List<ContextMapping> contextMappings;
    private final Map<String, ContextMapping> contextNameMap;

    public ContextMappings(List<ContextMapping> contextMappings) {
        if (contextMappings.size() > 255) {
            // we can support more, but max of 255 (1 byte) unique context types per suggest field
            // seems reasonable?
            throw new UnsupportedOperationException("Maximum of 10 context types are supported was: " + contextMappings.size());
        }
        this.contextMappings = contextMappings;
        contextNameMap = new HashMap<>(contextMappings.size());
        for (ContextMapping mapping : contextMappings) {
            contextNameMap.put(mapping.name(), mapping);
        }
    }

    /**
     * @return number of context mappings
     * held by this instance
     */
    public int size() {
        return contextMappings.size();
    }

    /**
     * Returns a context mapping by its name
     */
    public ContextMapping get(String name) {
        ContextMapping contextMapping = contextNameMap.get(name);
        if (contextMapping == null) {
            throw new IllegalArgumentException("Unknown context name[" + name + "], must be one of " + contextNameMap.size());
        }
        return contextMapping;
    }

    /**
     * Wraps a {CompletionQuery} with context queries
     *
     * @param query base completion query to wrap
     * @param queryContexts a map of context mapping name and collected query contexts
     * @return a context-enabled query
     */
    public ContextQuery toContextQuery(CompletionQuery query, Map<String, List<ContextMapping.InternalQueryContext>> queryContexts) {
        ContextQuery typedContextQuery = new ContextQuery(query);
        if (queryContexts.isEmpty() == false) {
            CharsRefBuilder scratch = new CharsRefBuilder();
            scratch.grow(1);
            for (int typeId = 0; typeId < contextMappings.size(); typeId++) {
                scratch.setCharAt(0, (char) typeId);
                scratch.setLength(1);
                ContextMapping mapping = contextMappings.get(typeId);
                List<ContextMapping.InternalQueryContext> internalQueryContext = queryContexts.get(mapping.name());
                if (internalQueryContext != null) {
                    for (ContextMapping.InternalQueryContext context : internalQueryContext) {
                        scratch.append(context.context);
                        typedContextQuery.addContext(scratch.toCharsRef(), context.boost, !context.isPrefix);
                        scratch.setLength(1);
                    }
                }
            }
        }
        return typedContextQuery;
    }

    /**
     * Maps an output context list to a map of context mapping names and their values
     *
     * @return a map of context names and their values
     *
     */
    public Map<String, Set<CharSequence>> getNamedContexts(List<CharSequence> contexts) {
        Map<String, Set<CharSequence>> contextMap = new HashMap<>(contexts.size());
        for (CharSequence typedContext : contexts) {
            int typeId = typedContext.charAt(0);
            assert typeId < contextMappings.size() : "Returned context has invalid type";
            ContextMapping mapping = contextMappings.get(typeId);
            Set<CharSequence> contextEntries = contextMap.get(mapping.name());
            if (contextEntries == null) {
                contextEntries = new HashSet<>();
                contextMap.put(mapping.name(), contextEntries);
            }
            contextEntries.add(typedContext.subSequence(1, typedContext.length()));
        }
        return contextMap;
    }

    /**
     * Loads {ContextMappings} from configuration
     *
     * Expected configuration:
     *  List of maps representing {ContextMapping}
     *  [{"name": .., "type": .., ..}, {..}]
     *
     */
    public static ContextMappings load(Object configuration, Version indexVersionCreated) throws ElasticsearchParseException {
        final List<ContextMapping> contextMappings;
        if (configuration instanceof List) {
            contextMappings = new ArrayList<>();
            List<Object> configurations = (List<Object>)configuration;
            for (Object contextConfig : configurations) {
                contextMappings.add(load((Map<String, Object>) contextConfig, indexVersionCreated));
            }
            if (contextMappings.size() == 0) {
                throw new ElasticsearchParseException("expected at least one context mapping");
            }
        } else if (configuration instanceof Map) {
            contextMappings = Collections.singletonList(load(((Map<String, Object>) configuration), indexVersionCreated));
        } else {
            throw new ElasticsearchParseException("expected a list or an entry of context mapping");
        }
        return new ContextMappings(contextMappings);
    }

    private static ContextMapping load(Map<String, Object> contextConfig, Version indexVersionCreated) {
        throw new UnsupportedOperationException("querybuilders does not support this operation.");
    }

    /**
     * Writes a list of objects specified by the defined {ContextMapping}s
     *
     * see {ContextMapping#toXContent(XContentBuilder, Params)}
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        for (ContextMapping contextMapping : contextMappings) {
            builder.startObject();
            contextMapping.toXContent(builder, params);
            builder.endObject();
        }
        return builder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextMappings);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || (obj instanceof ContextMappings) == false) {
            return false;
        }
        ContextMappings other = ((ContextMappings) obj);
        return contextMappings.equals(other.contextMappings);
    }
}
