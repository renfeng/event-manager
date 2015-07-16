package com.google.developers.event.server.google;

/**
 * Created by renfeng on 6/19/15.
 */
public class OAuthCredentials {

	private String refreshToken;
	private String clientId;
	private String clientSecret;

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}
}
