package com.google.developers.event.http;

public interface Path {

	String EVENTS_URL = "/api/events/";

	String CHECK_IN_URL = "/check-in/";
	String SEND_QR_URL = "/authenticated/send-qr/";

	String OAUTH2ENTRY = "/authenticated/oauth2entry";
	String OAUTH2CALLBACK = "/authenticated/oauth2callback";
	String OAUTH2REVOKE = "/authenticated/oauth2revoke";
}
