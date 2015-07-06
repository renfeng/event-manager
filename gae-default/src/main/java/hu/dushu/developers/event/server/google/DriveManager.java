package hu.dushu.developers.event.server.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;

import java.io.IOException;

/**
 * Created by renfeng on 6/22/15.
 */
public class DriveManager {

	private final GoogleRefreshTokenRequest tokenRequest;

	private Drive service;
	private long expiration;

	public DriveManager(String refreshToken, String clientId,
	                    String clientSecret, HttpTransport transport,
	                    JsonFactory jsonFactory) {

		tokenRequest = new GoogleRefreshTokenRequest(transport, jsonFactory,
				refreshToken, clientId, clientSecret);

		/*
		 * FIXME how to create credentials out of client id, secret, and refresh token
		 *
		 * https://developers.google.com/drive/web/credentials
		 */
//		service = new Drive(transport, jsonFactory, )

		return;
	}

	public final Drive getService() throws IOException {

		long now = System.currentTimeMillis();

		/*
		 * FIXME how to set accessToken
		 */
//		if (now >= getExpiration()) {
//			GoogleTokenResponse tokenResponse = tokenRequest.execute();
//			String accessToken = tokenResponse.getAccessToken();
//			setExpiration(now + tokenResponse.getExpiresInSeconds());
//
//			service.setHeader("Authorization", "Bearer " + accessToken);
//		}

		return service;
	}

	protected void setService(Drive service) {
		this.service = service;
	}

	public long getExpiration() {
		return expiration;
	}

	public void setExpiration(long expiration) {
		this.expiration = expiration;
	}

}
