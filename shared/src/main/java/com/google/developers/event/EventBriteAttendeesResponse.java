package com.google.developers.event;

import com.google.api.client.util.Key;

import java.util.List;

/**
 * Created by renfeng on 10/27/15.
 */
public class EventBriteAttendeesResponse {

	@Key
	private EventBritePagination pagination;

	@Key
	private List<EventBriteAttendee> attendees;

	public EventBritePagination getPagination() {
		return pagination;
	}

	public void setPagination(EventBritePagination pagination) {
		this.pagination = pagination;
	}

	public List<EventBriteAttendee> getAttendees() {
		return attendees;
	}

	public void setAttendees(List<EventBriteAttendee> attendees) {
		this.attendees = attendees;
	}
}
