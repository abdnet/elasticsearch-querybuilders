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

package org.codelibs.elasticsearch.search.aggregations.pipeline.bucketscript;

import org.codelibs.elasticsearch.common.io.stream.StreamInput;
import org.codelibs.elasticsearch.common.io.stream.StreamOutput;
import org.codelibs.elasticsearch.script.CompiledScript;
import org.codelibs.elasticsearch.script.ExecutableScript;
import org.codelibs.elasticsearch.script.Script;
import org.codelibs.elasticsearch.script.ScriptContext;
import org.codelibs.elasticsearch.search.DocValueFormat;
import org.codelibs.elasticsearch.search.aggregations.AggregationExecutionException;
import org.codelibs.elasticsearch.search.aggregations.InternalAggregation;
import org.codelibs.elasticsearch.search.aggregations.InternalAggregation.ReduceContext;
import org.codelibs.elasticsearch.search.aggregations.InternalAggregations;
import org.codelibs.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.codelibs.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation.Bucket;
import org.codelibs.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;
import org.codelibs.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.codelibs.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.codelibs.elasticsearch.search.aggregations.pipeline.BucketHelpers.resolveBucketValue;

public class BucketScriptPipelineAggregator extends PipelineAggregator {
    private final DocValueFormat formatter;
    private final GapPolicy gapPolicy;
    private final Script script;
    private final Map<String, String> bucketsPathsMap;

    public BucketScriptPipelineAggregator(String name, Map<String, String> bucketsPathsMap, Script script, DocValueFormat formatter,
            GapPolicy gapPolicy, Map<String, Object> metadata) {
        super(name, bucketsPathsMap.values().toArray(new String[bucketsPathsMap.size()]), metadata);
        this.bucketsPathsMap = bucketsPathsMap;
        this.script = script;
        this.formatter = formatter;
        this.gapPolicy = gapPolicy;
    }

    /**
     * Read from a stream.
     */
    @SuppressWarnings("unchecked")
    public BucketScriptPipelineAggregator(StreamInput in) throws IOException {
        super(in);
        script = new Script(in);
        formatter = in.readNamedWriteable(DocValueFormat.class);
        gapPolicy = GapPolicy.readFrom(in);
        bucketsPathsMap = (Map<String, String>) in.readGenericValue();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        script.writeTo(out);
        out.writeNamedWriteable(formatter);
        gapPolicy.writeTo(out);
        out.writeGenericValue(bucketsPathsMap);
    }

    @Override
    public String getWriteableName() {
        return BucketScriptPipelineAggregationBuilder.NAME;
    }

    @Override
    public InternalAggregation reduce(InternalAggregation aggregation, ReduceContext reduceContext) {
        InternalMultiBucketAggregation<InternalMultiBucketAggregation, InternalMultiBucketAggregation.InternalBucket> originalAgg = (InternalMultiBucketAggregation<InternalMultiBucketAggregation, InternalMultiBucketAggregation.InternalBucket>) aggregation;
        List<? extends Bucket> buckets = originalAgg.getBuckets();

        CompiledScript compiledScript = reduceContext.scriptService().compile(script, ScriptContext.Standard.AGGS,
                Collections.emptyMap());
        List newBuckets = new ArrayList<>();
        for (Bucket bucket : buckets) {
            Map<String, Object> vars = new HashMap<>();
            if (script.getParams() != null) {
                vars.putAll(script.getParams());
            }
            boolean skipBucket = false;
            for (Map.Entry<String, String> entry : bucketsPathsMap.entrySet()) {
                String varName = entry.getKey();
                String bucketsPath = entry.getValue();
                Double value = resolveBucketValue(originalAgg, bucket, bucketsPath, gapPolicy);
                if (GapPolicy.SKIP == gapPolicy && (value == null || Double.isNaN(value))) {
                    skipBucket = true;
                    break;
                }
                vars.put(varName, value);
            }
            if (skipBucket) {
                newBuckets.add(bucket);
            } else {
                ExecutableScript executableScript = reduceContext.scriptService().executable(compiledScript, vars);
                Object returned = executableScript.run();
                if (returned == null) {
                    newBuckets.add(bucket);
                } else {
                    if (!(returned instanceof Number)) {
                        throw new AggregationExecutionException("series_arithmetic script for reducer [" + name()
                                + "] must return a Number");
                    }
                    final List<InternalAggregation> aggs = StreamSupport.stream(bucket.getAggregations().spliterator(), false).map((p) -> {
                        return (InternalAggregation) p;
                    }).collect(Collectors.toList());
                    aggs.add(new InternalSimpleValue(name(), ((Number) returned).doubleValue(), formatter,
                            new ArrayList<>(), metaData()));
                    InternalMultiBucketAggregation.InternalBucket newBucket = originalAgg.createBucket(new InternalAggregations(aggs),
                            (InternalMultiBucketAggregation.InternalBucket) bucket);
                    newBuckets.add(newBucket);
                }
            }
        }
        return originalAgg.create(newBuckets);
    }
}