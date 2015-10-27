package com.google.developers.api;

import com.google.developers.event.EventBriteAttendeesResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by renfeng on 10/27/15.
 */
public class EventBriteManager {

	private final String token;

	public EventBriteManager(String token) {
		this.token = token;
	}

	public List<EventBriteAttendeesResponse> listAttendees(String eventId) {
		return new ArrayList<>();
	}
}
