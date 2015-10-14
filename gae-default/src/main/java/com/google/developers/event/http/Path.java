package com.google.developers.event.http;

public interface Path {

	String EVENTS_URL = "/api/events/";

	String CHECK_IN_URL = "/authenticated/check-in/";
	String TICKET_URL = "/authenticated/ticket/";
	String CAMPAIGN_URL = "/authenticated/campaign/";

	String OAUTH2ENTRY = "/authenticated/oauth2entry";
	String OAUTH2CALLBACK = "/authenticated/oauth2callback";
	String OAUTH2REVOKE = "/authenticated/oauth2revoke";
}
