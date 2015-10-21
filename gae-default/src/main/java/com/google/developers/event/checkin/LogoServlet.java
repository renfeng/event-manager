package com.google.developers.event.checkin;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.DriveManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by renfeng on 6/22/15.
 */
@Singleton
public class LogoServlet extends HttpServlet implements Path {

	private static final Logger logger = LoggerFactory.getLogger(LogoServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;

	@Inject
	public LogoServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

//		Credential credential = GoogleOAuth2.getGlobalCredential(transport, jsonFactory);
		// Get the stored credentials using the Authorization Flow
		Credential credential = oauth2Utils.initializeFlow().loadCredential(
				UserServiceFactory.getUserService().getCurrentUser().getEmail());

		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);
		DriveManager driveManager = new DriveManager(transport, jsonFactory, credential);

		ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, CHECK_IN_URL);
			if (activeEvent == null) {
				req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
				return;
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		IOUtils.copy(new ByteArrayInputStream(activeEvent.getLogoCache(driveManager)),
				resp.getOutputStream());
	}
}
