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

package org.codelibs.elasticsearch.search.aggregations.bucket.filter;

import org.codelibs.elasticsearch.common.io.stream.StreamInput;
import org.codelibs.elasticsearch.common.io.stream.StreamOutput;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.index.query.MatchAllQueryBuilder;
import org.codelibs.elasticsearch.index.query.QueryBuilder;
import org.codelibs.elasticsearch.index.query.QueryParseContext;
import org.codelibs.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.AggregatorFactories;
import org.codelibs.elasticsearch.search.aggregations.AggregatorFactory;
import org.codelibs.elasticsearch.search.aggregations.InternalAggregation.Type;
import org.codelibs.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Objects;

public class FilterAggregationBuilder extends AbstractAggregationBuilder<FilterAggregationBuilder> {
    public static final String NAME = "filter";
    private static final Type TYPE = new Type(NAME);

    private final QueryBuilder filter;

    /**
     * @param name
     *            the name of this aggregation
     * @param filter
     *            Set the filter to use, only documents that match this
     *            filter will fall into the bucket defined by this
     *            {Filter} aggregation.
     */
    public FilterAggregationBuilder(String name, QueryBuilder filter) {
        super(name, TYPE);
        if (filter == null) {
            throw new IllegalArgumentException("[filter] must not be null: [" + name + "]");
        }
        this.filter = filter;
    }

    /**
     * Read from a stream.
     */
    public FilterAggregationBuilder(StreamInput in) throws IOException {
        super(in, TYPE);
        filter = in.readNamedWriteable(QueryBuilder.class);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(filter);
    }

    @Override
    protected AggregatorFactory<?> doBuild(SearchContext context, AggregatorFactory<?> parent,
            AggregatorFactories.Builder subFactoriesBuilder) throws IOException {
        throw new UnsupportedOperationException("querybuilders does not support this operation.");
    }

    @Override
    protected XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
        if (filter != null) {
            filter.toXContent(builder, params);
        }
        return builder;
    }

    public static FilterAggregationBuilder parse(String aggregationName, QueryParseContext context) throws IOException {
        QueryBuilder filter = context.parseInnerQueryBuilder().orElse(new MatchAllQueryBuilder());
        return new FilterAggregationBuilder(aggregationName, filter);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(filter);
    }

    @Override
    protected boolean doEquals(Object obj) {
        FilterAggregationBuilder other = (FilterAggregationBuilder) obj;
        return Objects.equals(filter, other.filter);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }
}
