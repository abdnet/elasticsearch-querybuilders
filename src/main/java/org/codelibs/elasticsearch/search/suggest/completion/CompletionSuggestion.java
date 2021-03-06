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
package org.codelibs.elasticsearch.search.suggest.completion;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.suggest.Lookup;
import org.codelibs.elasticsearch.common.io.stream.StreamInput;
import org.codelibs.elasticsearch.common.io.stream.StreamOutput;
import org.codelibs.elasticsearch.common.text.Text;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.search.suggest.Suggest;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.codelibs.elasticsearch.search.suggest.Suggest.COMPARATOR;

/**
 * Suggestion response for {CompletionSuggester} results
 *
 * Response format for each entry:
 * {
 *     "text" : STRING
 *     "score" : FLOAT
 *     "contexts" : CONTEXTS
 * }
 *
 * CONTEXTS : {
 *     "CONTEXT_NAME" : ARRAY,
 *     ..
 * }
 *
 */
public final class CompletionSuggestion extends Suggest.Suggestion<CompletionSuggestion.Entry> {

    public static final int TYPE = 4;

    public CompletionSuggestion() {
    }

    public CompletionSuggestion(String name, int size) {
        super(name, size);
    }

    /**
     * @return the result options for the suggestion
     */
    public List<Entry.Option> getOptions() {
        if (entries.isEmpty() == false) {
            assert entries.size() == 1 : "CompletionSuggestion must have only one entry";
            return entries.get(0).getOptions();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return whether there is any hits for the suggestion
     */
    public boolean hasScoreDocs() {
        return getOptions().size() > 0;
    }

    private static final class OptionPriorityQueue extends org.apache.lucene.util.PriorityQueue<Entry.Option> {

        private final Comparator<Suggest.Suggestion.Entry.Option> comparator;

        OptionPriorityQueue(int maxSize, Comparator<Suggest.Suggestion.Entry.Option> comparator) {
            super(maxSize);
            this.comparator = comparator;
        }

        @Override
        protected boolean lessThan(Entry.Option a, Entry.Option b) {
            int cmp = comparator.compare(a, b);
            if (cmp != 0) {
                return cmp > 0;
            }
            return Lookup.CHARSEQUENCE_COMPARATOR.compare(a.getText().string(), b.getText().string()) > 0;
        }

        Entry.Option[] get() {
            int size = size();
            Entry.Option[] results = new Entry.Option[size];
            for (int i = size - 1; i >= 0; i--) {
                results[i] = pop();
            }
            return results;
        }
    }

    /**
     * Reduces suggestions to a single suggestion containing at most
     * top {CompletionSuggestion#getSize()} options across <code>toReduce</code>
     */
    public static CompletionSuggestion reduceTo(List<Suggest.Suggestion<Entry>> toReduce) {
        if (toReduce.isEmpty()) {
            return null;
        } else {
            final CompletionSuggestion leader = (CompletionSuggestion) toReduce.get(0);
            final Entry leaderEntry = leader.getEntries().get(0);
            final String name = leader.getName();
            if (toReduce.size() == 1) {
                return leader;
            } else {
                // combine suggestion entries from participating shards on the coordinating node
                // the global top <code>size</code> entries are collected from the shard results
                // using a priority queue
                OptionPriorityQueue priorityQueue = new OptionPriorityQueue(leader.getSize(), COMPARATOR);
                for (Suggest.Suggestion<Entry> suggestion : toReduce) {
                    assert suggestion.getName().equals(name) : "name should be identical across all suggestions";
                    for (Entry.Option option : ((CompletionSuggestion) suggestion).getOptions()) {
                        if (option == priorityQueue.insertWithOverflow(option)) {
                            // if the current option has overflown from pq,
                            // we can assume all of the successive options
                            // from this shard result will be overflown as well
                            break;
                        }
                    }
                }
                final CompletionSuggestion suggestion = new CompletionSuggestion(leader.getName(), leader.getSize());
                final Entry entry = new Entry(leaderEntry.getText(), leaderEntry.getOffset(), leaderEntry.getLength());
                Collections.addAll(entry.getOptions(), priorityQueue.get());
                suggestion.addTerm(entry);
                return suggestion;
            }
        }
    }

    @Override
    public Suggest.Suggestion<Entry> reduce(List<Suggest.Suggestion<Entry>> toReduce) {
        return reduceTo(toReduce);
    }

    public void setShardIndex(int shardIndex) {
        if (entries.isEmpty() == false) {
            for (Entry.Option option : getOptions()) {
                option.setShardIndex(shardIndex);
            }
        }
    }

    @Override
    public int getType() {
        return TYPE;
    }

    @Override
    protected Entry newEntry() {
        return new Entry();
    }

    public static final class Entry extends Suggest.Suggestion.Entry<CompletionSuggestion.Entry.Option> {

        public Entry(Text text, int offset, int length) {
            super(text, offset, length);
        }

        protected Entry() {
            super();
        }

        @Override
        protected Option newOption() {
            return new Option();
        }

        public static class Option extends Suggest.Suggestion.Entry.Option {
            private Map<String, Set<CharSequence>> contexts;
            private ScoreDoc doc;

            public Option(int docID, Text text, float score, Map<String, Set<CharSequence>> contexts) {
                super(text, score);
                this.doc = new ScoreDoc(docID, score);
                this.contexts = contexts;
            }

            protected Option() {
                super();
            }

            @Override
            protected void mergeInto(Suggest.Suggestion.Entry.Option otherOption) {
                // Completion suggestions are reduced by
                // org.codelibs.elasticsearch.search.suggest.completion.CompletionSuggestion.reduce()
                throw new UnsupportedOperationException();
            }

            public Map<String, Set<CharSequence>> getContexts() {
                return contexts;
            }

            public ScoreDoc getDoc() {
                return doc;
            }


            public void setShardIndex(int shardIndex) {
                this.doc.shardIndex = shardIndex;
            }

            @Override
            protected XContentBuilder innerToXContent(XContentBuilder builder, Params params) throws IOException {
                throw new UnsupportedOperationException("querybuilders does not support this operation.");
            }

            @Override
            public void readFrom(StreamInput in) throws IOException {
                super.readFrom(in);
                throw new UnsupportedOperationException("querybuilders does not support this operation.");
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                throw new UnsupportedOperationException("querybuilders does not support this operation.");
            }

            @Override
            public String toString() {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("text:");
                stringBuilder.append(getText());
                stringBuilder.append(" score:");
                stringBuilder.append(getScore());
                stringBuilder.append(" context:[");
                for (Map.Entry<String, Set<CharSequence>> entry: contexts.entrySet()) {
                    stringBuilder.append(" ");
                    stringBuilder.append(entry.getKey());
                    stringBuilder.append(":");
                    stringBuilder.append(entry.getValue());
                }
                stringBuilder.append("]");
                return stringBuilder.toString();
            }

        }
    }

}
