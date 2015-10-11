package com.google.developers.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;

import java.io.IOException;
import java.util.List;

/**
 * Created by frren on 2015-07-10.
 */
public class GPlusManager extends ClientManager<Plus> {

	private static GPlusManager gPlusManager;

	public static GPlusManager getGlobalInstance(HttpTransport transport, JsonFactory jsonFactory)
			throws IOException {

		if (gPlusManager == null) {
			synchronized (GPlusManager.class) {
				if (gPlusManager == null) {
					gPlusManager = new GPlusManager(transport, jsonFactory,
							GoogleOAuthManager.createCredentialWithRefreshToken(transport, jsonFactory));
				}
			}
		}
		return gPlusManager;
	}

	public GPlusManager(HttpTransport transport, JsonFactory jsonFactory, Credential credential) {

		// Create a new authorized API client.
		Plus plus = new Plus.Builder(transport, jsonFactory, credential)
				.setApplicationName(GoogleOAuthManager.APPLICATION_NAME)
				.build();

		setClient(plus);
	}

	public void x() throws IOException {
		Plus plus = getClient();

		Plus.Activities.List listActivities = plus.activities().list("me", "public");
		listActivities.setMaxResults(5L);

		// Execute the request for the first page
		ActivityFeed activityFeed = listActivities.execute();

		// Unwrap the request and extract the pieces we want
		List<Activity> activities = activityFeed.getItems();

		// Loop through until we arrive at an empty page
		while (activities != null) {
			for (Activity activity : activities) {
				System.out.println("ID " + activity.getId() + " Content: " +
						activity.getObject().getContent());
			}

			// We will know we are on the last page when the next page token is null.
			// If this is the case, break.
			if (activityFeed.getNextPageToken() == null) {
				break;
			}

			// Prepare to request the next page of activities
			listActivities.setPageToken(activityFeed.getNextPageToken());

			// Execute and process the next page request
			activityFeed = listActivities.execute();
			activities = activityFeed.getItems();
		}
	}
}
