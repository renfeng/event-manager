package com.google.developers.api;

/**
 * Created by frren on 2015-10-08.
 */
public interface GoogleAPIScope {

	/*
	 * https://developers.google.com/google-apps/spreadsheets/authorize#authorize_requests_with_oauth_20
	 */
	String SPREADSHEETS_V3 = "https://spreadsheets.google.com/feeds/";

	/*
	 * https://developers.google.com/google-apps/contacts/v3/#authorizing_requests_with_oauth_20
	 */
	String CONTACTS_V3 = "https://www.google.com/m8/feeds/";

	/*
	 * TODO Drive API v2
	 */

	/*
	 * TODO Gmail API v1
	 * https://developers.google.com/gmail/api/auth/scopes
	 */
	String GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send";

	/*
	 * Google+ API v1
	 * https://developers.google.com/+/web/api/rest/oauth#authorization-scopes
	 * see com.google.api.services.plus.PlusScopes
	 */

	/*
	 * TODO Calendar API v3
	 */
}
