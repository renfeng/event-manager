package com.google.developers.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by renfeng on 6/22/15.
 */
public class DriveManager extends ClientManager<Drive> {

	private static DriveManager driveManager;

	public static DriveManager getGlobalInstance(HttpTransport transport, JsonFactory jsonFactory)
			throws IOException {

		if (driveManager == null) {
			synchronized (DriveManager.class) {
				if (driveManager == null) {
					driveManager = new DriveManager(transport, jsonFactory,
							GoogleOAuthManager.createCredentialWithRefreshToken(transport, jsonFactory));
				}
			}
		}
		return driveManager;
	}

	public DriveManager(HttpTransport transport, JsonFactory jsonFactory,
						Credential credential) {

		Drive drive = new Drive.Builder(transport, jsonFactory, credential)
				.setApplicationName(GoogleOAuthManager.APPLICATION_NAME)
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
