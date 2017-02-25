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

package org.codelibs.elasticsearch.rest.action.admin.cluster;

import org.codelibs.elasticsearch.action.admin.cluster.shards.ClusterSearchShardsRequest;
import org.codelibs.elasticsearch.action.support.IndicesOptions;
import org.codelibs.elasticsearch.client.Requests;
import org.codelibs.elasticsearch.client.node.NodeClient;
import org.codelibs.elasticsearch.common.Strings;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.rest.BaseRestHandler;
import org.codelibs.elasticsearch.rest.RestController;
import org.codelibs.elasticsearch.rest.RestRequest;
import org.codelibs.elasticsearch.rest.action.RestToXContentListener;

import java.io.IOException;

import static org.codelibs.elasticsearch.rest.RestRequest.Method.GET;
import static org.codelibs.elasticsearch.rest.RestRequest.Method.POST;

public class RestClusterSearchShardsAction extends BaseRestHandler {

    @Inject
    public RestClusterSearchShardsAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(GET, "/_search_shards", this);
        controller.registerHandler(POST, "/_search_shards", this);
        controller.registerHandler(GET, "/{index}/_search_shards", this);
        controller.registerHandler(POST, "/{index}/_search_shards", this);
        controller.registerHandler(GET, "/{index}/{type}/_search_shards", this);
        controller.registerHandler(POST, "/{index}/{type}/_search_shards", this);
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        String[] indices = Strings.splitStringByCommaToArray(request.param("index"));
        final ClusterSearchShardsRequest clusterSearchShardsRequest = Requests.clusterSearchShardsRequest(indices);
        clusterSearchShardsRequest.local(request.paramAsBoolean("local", clusterSearchShardsRequest.local()));

        if (request.hasParam("type")) {
            String type = request.param("type");
            deprecationLogger.deprecated("type [" + type + "] doesn't have any effect in the search shards api, " +
                    "it should be rather omitted");
        }
        clusterSearchShardsRequest.routing(request.param("routing"));
        clusterSearchShardsRequest.preference(request.param("preference"));
        clusterSearchShardsRequest.indicesOptions(IndicesOptions.fromRequest(request, clusterSearchShardsRequest.indicesOptions()));
        return channel -> client.admin().cluster().searchShards(clusterSearchShardsRequest, new RestToXContentListener<>(channel));
    }
}
