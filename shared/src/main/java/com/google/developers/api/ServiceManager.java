package com.google.developers.api;

import com.google.gdata.client.GoogleService;

import java.io.IOException;

public abstract class ServiceManager<T extends GoogleService> {

//	private final GoogleRefreshTokenRequest tokenRequest;

	private T service;
//	private long expiration;

//	public ServiceManager(HttpTransport transport, JsonFactory jsonFactory) {
//		tokenRequest = new GoogleRefreshTokenRequest(transport, jsonFactory,
//				DevelopersSharedModule.REFRESH_TOKEN,
//				DevelopersSharedModule.CLIENT_ID,
//				DevelopersSharedModule.CLIENT_SECRET);
//	}

	public final T getService() throws IOException {

//		long now = System.currentTimeMillis();
//
//		if (now >= getExpiration()) {
//			GoogleTokenResponse tokenResponse = tokenRequest.execute();
//			String accessToken = tokenResponse.getAccessToken();
//			setExpiration(now + tokenResponse.getExpiresInSeconds());
//
//			service.setHeader("Authorization", "Bearer " + accessToken);
//		}

		return service;
	}

	protected void setService(T service) {
		this.service = service;
	}

//	public long getExpiration() {
//		return expiration;
//	}
//
//	public void setExpiration(long expiration) {
//		this.expiration = expiration;
//	}

}
