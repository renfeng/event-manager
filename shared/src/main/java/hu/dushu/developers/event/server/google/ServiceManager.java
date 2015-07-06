package hu.dushu.developers.event.server.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.gdata.client.GoogleService;

import java.io.IOException;

public abstract class ServiceManager<T extends GoogleService> {

	private final GoogleRefreshTokenRequest tokenRequest;

	private T service;
	private long expiration;

	public ServiceManager(String refreshToken, String clientId,
			String clientSecret, HttpTransport transport,
			JsonFactory jsonFactory) {

		tokenRequest = new GoogleRefreshTokenRequest(transport, jsonFactory,
				refreshToken, clientId, clientSecret);

		return;
	}

	public final T getService() throws IOException {

		long now = System.currentTimeMillis();

		if (now >= getExpiration()) {
			GoogleTokenResponse tokenResponse = tokenRequest.execute();
			String accessToken = tokenResponse.getAccessToken();
			setExpiration(now + tokenResponse.getExpiresInSeconds());

			service.setHeader("Authorization", "Bearer " + accessToken);
		}

		return service;
	}

	protected void setService(T service) {
		this.service = service;
	}

	public long getExpiration() {
		return expiration;
	}

	public void setExpiration(long expiration) {
		this.expiration = expiration;
	}

}
