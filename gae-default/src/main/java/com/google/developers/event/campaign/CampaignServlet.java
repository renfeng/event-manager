package com.google.developers.event.campaign;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.event.CampaignSpreadsheet;
import com.google.developers.event.http.OAuth2EntryServlet;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;

/**
 * Created by renfeng on 10/13/15.
 */
public class CampaignServlet extends OAuth2EntryServlet
		implements Path, CampaignSpreadsheet {

	public CampaignServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}
}
