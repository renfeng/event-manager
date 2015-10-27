package com.google.developers.event.checkin;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.event.MetaSpreadsheet;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.OAuth2EntryPage;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by renfeng on 6/17/15.
 */
@Singleton
public class CheckInPage extends OAuth2EntryPage
		implements Path, MetaSpreadsheet, RegisterFormResponseSpreadsheet {

	@Inject
	public CheckInPage(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		req.getRequestDispatcher(CHECK_IN_PAGE_URL + "index.html").forward(req, resp);
	}
}
