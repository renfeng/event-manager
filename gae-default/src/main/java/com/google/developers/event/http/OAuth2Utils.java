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
import com.google.developers.api.GoogleAPIScope;
import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Inject;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class OAuth2Utils implements Path, GoogleAPIScope {

	/*
	 * https://developers.google.com/oauthplayground/
	 */
	private static final List<String> SCOPES = Arrays.asList(
			PlusScopes.PLUS_ME, CONTACTS_V3, SPREADSHEETS_V3, DRIVE_API_V2, GMAIL_SEND);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final DataStoreFactory dataStoreFactory;

	@Inject
	public OAuth2Utils(HttpTransport transport, JsonFactory jsonFactory, DataStoreFactory dataStoreFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.dataStoreFactory = dataStoreFactory;
	}

	public GoogleAuthorizationCodeFlow initializeFlow() throws IOException {
		return new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory,
				DevelopersSharedModule.CLIENT_ID, DevelopersSharedModule.CLIENT_SECRET, SCOPES)
				.setDataStoreFactory(dataStoreFactory)
				.setAccessType("offline").build();
	}

	public GoogleAuthorizationCodeFlow initializeFlowForceApprovalPrompt() throws IOException {
		/*
		 * https://developers.google.com/identity/protocols/OAuth2WebServer#offline
		 * Important: When your application receives a refresh token, it is important to store that refresh token for
		 * future use. If your application loses the refresh token, it will have to re-prompt the user for consent
		 * before obtaining another refresh token. If you need to re-prompt the user for consent, include the
		 * approval_prompt parameter in the authorization code request, and set the value to force.
		 */
		return new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory,
				DevelopersSharedModule.CLIENT_ID, DevelopersSharedModule.CLIENT_SECRET, SCOPES)
				.setDataStoreFactory(dataStoreFactory).setApprovalPrompt("force")
				.setAccessType("offline").build();
	}

	String getRedirectUri(HttpServletRequest req) {
		GenericUrl requestUrl = new GenericUrl(req.getRequestURL().toString());
		requestUrl.setRawPath(OAUTH2CALLBACK);
		return requestUrl.build();
	}
}
