package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.event.DevelopersSharedModule;

import java.io.IOException;

/**
 * Created by renfeng on 6/19/15.
 */
public class GoogleOAuthManager {

	public static final String APPLICATION_NAME = "GDG Event Management";

	private static GoogleCredential credential;

	public static GoogleCredential createCredentialWithRefreshToken(
			HttpTransport transport, JsonFactory jsonFactory) throws IOException {

		if (credential == null) {
			synchronized (GoogleOAuthManager.class) {
				if (credential == null) {
					x(transport, jsonFactory);
				}
			}
		}

		return credential;
	}

	private static void x(HttpTransport transport, JsonFactory jsonFactory) throws IOException {

		GoogleRefreshTokenRequest tokenRequest = new GoogleRefreshTokenRequest(
				transport, jsonFactory,
				DevelopersSharedModule.REFRESH_TOKEN,
				DevelopersSharedModule.CLIENT_ID,
				DevelopersSharedModule.CLIENT_SECRET);

		GoogleTokenResponse tokenResponse = tokenRequest.execute();

		/*
		 * com.google.api.client.googleapis.auth.oauth2.GoogleCredential
		 */
		credential = new GoogleCredential.Builder().setTransport(transport).setJsonFactory(jsonFactory)
				.setClientSecrets(DevelopersSharedModule.CLIENT_ID, DevelopersSharedModule.CLIENT_SECRET)
				.build().setFromTokenResponse(tokenResponse);
	}
}
