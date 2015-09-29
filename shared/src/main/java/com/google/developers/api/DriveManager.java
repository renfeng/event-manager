package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by renfeng on 6/22/15.
 */
public class DriveManager extends ClientManager<Drive> {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
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

	/**
	 * Download a file's content.
	 *
	 * @param service Drive API service instance.
	 * @param file    Drive File instance.
	 * @return InputStream containing the file's content if successful,
	 * {@code null} otherwise.
	 */
	private static InputStream downloadFile(Drive service, File file) {

		/*
		 * https://developers.google.com/drive/web/manage-downloads
		 */

		if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
			try {
				// uses alt=media query parameter to request content
				return service.files().get(file.getId()).executeMediaAsInputStream();
			} catch (IOException e) {
				// An error occurred.
				e.printStackTrace();
				return null;
			}
		} else {
			// The file doesn't have any content stored on Drive.
			return null;
		}
	}
}
