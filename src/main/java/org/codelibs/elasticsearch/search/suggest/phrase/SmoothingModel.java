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

package org.codelibs.elasticsearch.search.suggest.phrase;

import org.codelibs.elasticsearch.common.ParseFieldMatcher;
import org.codelibs.elasticsearch.common.ParsingException;
import org.codelibs.elasticsearch.common.io.stream.NamedWriteable;
import org.codelibs.elasticsearch.common.xcontent.ToXContent;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.common.xcontent.XContentParser;
import org.codelibs.elasticsearch.index.query.QueryParseContext;
import org.codelibs.elasticsearch.search.suggest.phrase.WordScorer.WordScorerFactory;

import java.io.IOException;

public abstract class SmoothingModel implements NamedWriteable, ToXContent {

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(getWriteableName());
        innerToXContent(builder,params);
        builder.endObject();
        return builder;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SmoothingModel other = (SmoothingModel) obj;
        return doEquals(other);
    }

    @Override
    public final int hashCode() {
        /*
         * Override hashCode here and forward to an abstract method to force
         * extensions of this class to override hashCode in the same way that we
         * force them to override equals. This also prevents false positives in
         * CheckStyle's EqualsHashCode check.
         */
        return doHashCode();
    }

    protected abstract int doHashCode();

    public static SmoothingModel fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();
        parseContext.getParseFieldMatcher();
        XContentParser.Token token;
        String fieldName = null;
        SmoothingModel model = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                fieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (LinearInterpolation.PARSE_FIELD.match(fieldName)) {
                    model = LinearInterpolation.innerFromXContent(parseContext);
                } else if (Laplace.PARSE_FIELD.match(fieldName)) {
                    model = Laplace.innerFromXContent(parseContext);
                } else if (StupidBackoff.PARSE_FIELD.match(fieldName)) {
                    model = StupidBackoff.innerFromXContent(parseContext);
                } else {
                    throw new IllegalArgumentException("suggester[phrase] doesn't support object field [" + fieldName + "]");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(),
                        "[smoothing] unknown token [" + token + "] after [" + fieldName + "]");
            }
        }
        return model;
    }

    public abstract WordScorerFactory buildWordScorerFactory();

    /**
     * subtype specific implementation of "equals".
     */
    protected abstract boolean doEquals(SmoothingModel other);

    protected abstract XContentBuilder innerToXContent(XContentBuilder builder, Params params) throws IOException;
}
