/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.developers.event.http;

import com.google.api.client.extensions.appengine.datastore.AppEngineDataStoreFactory;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.plus.PlusScopes;
import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Inject;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Set;

class AdminOAuth2Utils implements Path {

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
	 * globally shared instance across your application.
	 */
	private static final AppEngineDataStoreFactory DATA_STORE_FACTORY =
			AppEngineDataStoreFactory.getDefaultInstance();

	private static GoogleClientSecrets clientSecrets = null;
	private static final Set<String> SCOPES = Collections.singleton(PlusScopes.PLUS_ME);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
	public AdminOAuth2Utils(HttpTransport transport, JsonFactory jsonFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
	}

	private GoogleClientSecrets getClientSecrets() throws IOException {
		if (clientSecrets == null) {
//			clientSecrets = GoogleClientSecrets.load(jsonFactory,
//					new InputStreamReader(Utils.class.getResourceAsStream("/client_secrets.json")));
//			Preconditions.checkArgument(!clientSecrets.getDetails().getClientId().startsWith("Enter ")
//							&& !clientSecrets.getDetails().getClientSecret().startsWith("Enter "),
//					"Download client_secrets.json file from https://code.google.com/apis/console/?api=plus "
//							+ "into plus-appengine-sample/src/main/resources/client_secrets.json");
			clientSecrets = new GoogleClientSecrets();
			clientSecrets.set("client_id", DevelopersSharedModule.getMessage("clientId"));
			clientSecrets.set("client_secret", DevelopersSharedModule.getMessage("clientSecret"));
		}
		return clientSecrets;
	}

	GoogleAuthorizationCodeFlow initializeFlow() throws IOException {
		return new GoogleAuthorizationCodeFlow.Builder(
				transport, jsonFactory, getClientSecrets(), SCOPES).setDataStoreFactory(
				DATA_STORE_FACTORY).setAccessType("offline").build();
	}

	String getRedirectUri(HttpServletRequest req) {
		GenericUrl requestUrl = new GenericUrl(req.getRequestURL().toString());
		requestUrl.setRawPath(ADMIN_OAUTH2CALLBACK);
		return requestUrl.build();
	}
}
