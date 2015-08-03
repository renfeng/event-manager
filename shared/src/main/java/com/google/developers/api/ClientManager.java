package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

import java.io.IOException;

/**
 * Created by frren on 2015-07-10.
 */
public abstract class ClientManager<T extends AbstractGoogleJsonClient> {

	private final GoogleRefreshTokenRequest tokenRequest;

	private T client;
	private long expiration;

	public ClientManager(String refreshToken, String clientId,
						 String clientSecret, HttpTransport transport,
						 JsonFactory jsonFactory) {
		tokenRequest = new GoogleRefreshTokenRequest(transport, jsonFactory,
				refreshToken, clientId, clientSecret);
	}

	public final T getClient() throws IOException {

		long now = System.currentTimeMillis();

		if (now >= getExpiration()) {
			GoogleTokenResponse tokenResponse = tokenRequest.execute();
			setExpiration(now + tokenResponse.getExpiresInSeconds());

			// Build credential from stored token data.
			updateCredential(new GoogleCredential().setFromTokenResponse(tokenResponse));
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
