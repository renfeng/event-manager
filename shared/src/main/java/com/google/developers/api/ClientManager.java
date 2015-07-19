package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;

import java.io.IOException;

/**
 * Created by frren on 2015-07-10.
 */
public abstract class ClientManager <T extends AbstractGoogleJsonClient> {

	private final GoogleRefreshTokenRequest tokenRequest;
	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final String clientId;
	private final String clientSecret;

	private T client;
	private long expiration;

	public ClientManager(String refreshToken, String clientId,
	                      String clientSecret, HttpTransport transport,
	                      JsonFactory jsonFactory) {

		tokenRequest = new GoogleRefreshTokenRequest(transport, jsonFactory,
				refreshToken, clientId, clientSecret);

		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.clientId = clientId;
		this.clientSecret = clientSecret;

		return;
	}

	public final T getClient() throws IOException {

		long now = System.currentTimeMillis();

		if (now >= getExpiration()) {
			GoogleTokenResponse tokenResponse = tokenRequest.execute();
			String accessToken = tokenResponse.getAccessToken();
			setExpiration(now + tokenResponse.getExpiresInSeconds());

//			client.setHeader("Authorization", "Bearer " + accessToken);
			// Build credential from stored token data.
			GoogleCredential credential;
			credential = new GoogleCredential.Builder()
					.setJsonFactory(jsonFactory)
					.setTransport(transport)
					.setClientSecrets(clientId, clientSecret).build()
					.setFromTokenResponse(tokenResponse);
			updateCredential(credential);
		}

		return client;
	}

	protected abstract void updateCredential(GoogleCredential credential);

	protected void setClient(T client) {
		this.client = client;
	}

	public long getExpiration() {
		return expiration;
	}

	public void setExpiration(long expiration) {
		this.expiration = expiration;
	}

}
