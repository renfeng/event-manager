package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.inject.name.Named;

import java.io.IOException;

/**
 * Created by renfeng on 6/22/15.
 */
public class DriveManager extends ClientManager<Drive> {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	public DriveManager(
			@Named("refreshToken") String refreshToken,
			@Named("clientId") String clientId,
			@Named("clientSecret") String clientSecret,
			HttpTransport transport, JsonFactory jsonFactory) {

		super(refreshToken, clientId, clientSecret, transport, jsonFactory);

		this.transport = transport;
		this.jsonFactory = jsonFactory;

		return;
	}

	@Override
	protected void updateCredential(GoogleCredential credential) {

		Drive drive = new Drive.Builder(transport, jsonFactory, credential)
				.setApplicationName("GDG Event Management")
				.build();

		setClient(drive);
	}

	public void x() throws IOException {
		Drive drive = getClient();
	}

}
