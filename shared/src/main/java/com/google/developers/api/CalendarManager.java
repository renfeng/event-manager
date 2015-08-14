package com.google.developers.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by frren on 2015-08-13.
 */
public class CalendarManager extends ClientManager<Calendar> {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
	public CalendarManager(
			@Named("refreshToken") String refreshToken,
			@Named("clientId") String clientId,
			@Named("clientSecret") String clientSecret,
			HttpTransport transport, JsonFactory jsonFactory) {

		super(refreshToken, clientId, clientSecret, transport, jsonFactory);

		this.transport = transport;
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void updateCredential(GoogleCredential credential) {

		Calendar calendar = new Calendar.Builder(transport, jsonFactory, credential)
				.setApplicationName("GDG Event Management").build();

		setClient(calendar);
	}

	/**
	 *
	 * @return maps G+ event url to its object
	 * @throws IOException
	 */
	public Map<String, Event> listEvents() throws IOException {

		Map<String, Event> result = new HashMap<>();

		// Iterate over the events in the specified calendar
		String pageToken = null;
		do {
			/*
			 * event start/end time cannot be in the year 10000, or later, i.e. 9999-12-31 11:59pm is the last
			  * valid end time for an event.
			 */
			Events events = getClient().events().list("primary").setPageToken(pageToken).execute();
			List<Event> items = events.getItems();
			for (Event event : items) {
//				System.out.println(event.getSummary());
				result.put(event.getHtmlLink(), event);
			}
			pageToken = events.getNextPageToken();
		} while (pageToken != null);

		return result;
	}
}
