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

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.plus.PlusScopes;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class OAuth2Utils implements Path {

	private static final List<String> SCOPES = Arrays.asList(
			PlusScopes.PLUS_ME,
			"https://www.google.com/m8/feeds/",
			"https://spreadsheets.google.com/feeds/");

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final DataStoreFactory dataStoreFactory;
	private final String clientId;
	private final String clientSecret;

	@Inject
	public OAuth2Utils(
			@Named("clientId") String clientId,
			@Named("clientSecret") String clientSecret,
			HttpTransport transport, JsonFactory jsonFactory, DataStoreFactory dataStoreFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.dataStoreFactory = dataStoreFactory;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	GoogleAuthorizationCodeFlow initializeFlow() throws IOException {
		return new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientId, clientSecret, SCOPES)
				.setDataStoreFactory(dataStoreFactory)
				.setAccessType("offline").build();
	}

	String getRedirectUri(HttpServletRequest req) {
		GenericUrl requestUrl = new GenericUrl(req.getRequestURL().toString());
		requestUrl.setRawPath(OAUTH2CALLBACK);
		return requestUrl.build();
	}
}
