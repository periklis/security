/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.security.dlic.rest.api;

import com.amazon.opendistroforelasticsearch.security.auditlog.AuditLog;
import com.amazon.opendistroforelasticsearch.security.configuration.AdminDNs;
import com.amazon.opendistroforelasticsearch.security.configuration.IndexBaseConfigurationRepository;
import com.amazon.opendistroforelasticsearch.security.dlic.rest.validation.AbstractConfigurationValidator;
import com.amazon.opendistroforelasticsearch.security.dlic.rest.validation.SecurityConfigValidator;
import com.amazon.opendistroforelasticsearch.security.privileges.PrivilegesEvaluator;
import com.amazon.opendistroforelasticsearch.security.ssl.transport.PrincipalExtractor;
import com.amazon.opendistroforelasticsearch.security.support.ConfigConstants;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;

import java.io.IOException;
import java.nio.file.Path;


public class OpenDistroSecurityConfigAction extends PatchableResourceApiAction {

	private final boolean allowPutOrPatch;

	@Inject
	public OpenDistroSecurityConfigAction(final Settings settings, final Path configPath, final RestController controller, final Client client,
			final AdminDNs adminDNs, final IndexBaseConfigurationRepository cl, final ClusterService cs,
			final PrincipalExtractor principalExtractor, final PrivilegesEvaluator evaluator, ThreadPool threadPool, AuditLog auditLog) {
		super(settings, configPath, controller, client, adminDNs, cl, cs, principalExtractor, evaluator, threadPool, auditLog);
		allowPutOrPatch = settings.getAsBoolean(ConfigConstants.OPENDISTRO_SECURITY_UNSUPPORTED_RESTAPI_ALLOW_SECURITYCONFIG_MODIFICATION, false);
	}

	@Override
	protected void registerHandlers(RestController controller, Settings settings) {
		controller.registerHandler(Method.GET, "/_opendistro/_security/api/securityconfig/", this);

		boolean enablePutOrPatch = settings.getAsBoolean(ConfigConstants.OPENDISTRO_SECURITY_UNSUPPORTED_RESTAPI_ALLOW_SECURITYCONFIG_MODIFICATION, false);
		if (enablePutOrPatch) {
			controller.registerHandler(Method.PUT, "/_opendistro/_security/api/securityconfig/{name}", this);
			controller.registerHandler(Method.PATCH, "/_opendistro/_security/api/securityconfig/", this);
		}
	}

	@Override
	protected void handleApiRequest(RestChannel channel, RestRequest request, Client client) throws IOException {
		if (request.method() == Method.PATCH && !allowPutOrPatch) {
			notImplemented(channel, Method.PATCH);
		} else {
			super.handleApiRequest(channel, request, client);
		}
	}

	@Override
	protected void handleGet(RestChannel channel, RestRequest request, Client client, final Settings.Builder additionalSettingsBuilder) {
		final Tuple<Long, Settings> configurationSettings = loadAsSettings(getConfigName(), true);
		channel.sendResponse(new BytesRestResponse(RestStatus.OK, convertToJson(channel, configurationSettings.v2())));
	}

	@Override
	protected void handlePut(RestChannel channel, final RestRequest request, final Client client, final Settings.Builder additionalSettings)
			throws IOException {
		if (allowPutOrPatch) {
			if (!"opendistro_security".equals(request.param("name"))) {
				badRequestResponse(channel, "name must be opendistro_security");
				return;
			}
			super.handlePut(channel, request, client, additionalSettings);
		} else {
			notImplemented(channel, Method.PUT);
		}
	}

	@Override
	protected void handleDelete(RestChannel channel, final RestRequest request, final Client client, final Settings.Builder additionalSettings) {
		notImplemented(channel, Method.DELETE);
	}

	@Override
	protected void handlePost(RestChannel channel, final RestRequest request, final Client client, final Settings.Builder additionalSetting)
			throws IOException {
		notImplemented(channel, Method.POST);
	}

	@Override
	protected AbstractConfigurationValidator getValidator(RestRequest request, BytesReference ref, Object... param) {
		return new SecurityConfigValidator(request, ref, this.settings, param);
	}

	@Override
	protected String getConfigName() {
		return ConfigConstants.CONFIGNAME_CONFIG;
	}

	@Override
	protected Endpoint getEndpoint() {
		return Endpoint.CONFIG;
	}

	@Override
	protected String getResourceName() {
		// not needed, no single resource
		return null;
	}

}
