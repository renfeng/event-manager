package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.DevelopersSharedModule;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry sevlet for the Plus App Engine Sample. Demonstrates how to make an authenticated API call
 * using OAuth2 helper classes.
 *
 * @author Nick Miceli
 */
@Singleton
public class OAuth2EntryServlet extends AbstractAppEngineAuthorizationCodeServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(OAuth2EntryServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public OAuth2EntryServlet(
			HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils,
			SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		// Get the stored credentials using the Authorization Flow
		AuthorizationCodeFlow authFlow = initializeFlow();
		Credential credential = authFlow.loadCredential(getUserId(req));

		String refreshToken = credential.getRefreshToken();
		if (refreshToken != null) {
			/*
			 * get G+ ID
			 * https://developers.google.com/+/web/api/rest/latest/people/get
			 */
			// Build the Plus object using the credentials
			Plus plus = new Plus.Builder(transport, jsonFactory, credential).setApplicationName("").build();
			// Make the API call
			Person profile = plus.people().get("me").execute();
			final String gplusId = profile.getId();
			logger.trace("https://plus.google.com/" + gplusId);

			/*
			 * check if the G+ ID is owned by a GDG chapter
			 * e.g. the G+ ID, 100160462017014431473 matches a devsite page,
			 * https://developers.google.com/groups/chapter/100160462017014431473/
			 */
			HttpRequestFactory factory = transport.createRequestFactory();
			GenericUrl url = new GenericUrl("https://developers.google.com/groups/chapter/" + gplusId + "/");
			HttpRequest request = factory.buildGetRequest(url);
			request.setThrowExceptionOnExecuteError(false);
			HttpResponse response = request.execute();
			if (response.getStatusCode() == 200) {
				/*
				 * TODO write to meta spreadsheet refresh token - the contacts api will be invoked to store participants
				 *
				 * so far, it's enabled to collect participant data
				 *
				 * later, only organizer(s) of an event will be able to submit URLs of Google Spreadsheets for
				 * register, check-in, and feedback
				 */
				final ThreadLocal<CellEntry> cellEntryThreadLocal = new ThreadLocal<>();
				CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

					Map<String, CellEntry> cellMap = new HashMap<>();

					@Override
					protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
							throws IOException, ServiceException {
						if (gplusId.equals(valueMap.get("gplusID"))) {
							cellEntryThreadLocal.set(cellMap.get("refreshToken"));
							return false;
						}

						cellMap = new HashMap<>();

						return true;
					}

					@Override
					protected void processDataColumn(CellEntry cell, String columnName) {
						cellMap.put(columnName, cell);
					}
				};
				try {
					processor.process(spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("chapter")),
							"gplusID", "refreshToken", "chapterPage");
				} catch (ServiceException e) {
					logger.error("failed to load refresh token for chapter, " + gplusId, e);
				}
				CellEntry cellEntry = cellEntryThreadLocal.get();
				if (SpreadsheetManager.diff(cellEntry.getCell().getInputValue(),refreshToken)){
					cellEntry.changeInputValueLocal(refreshToken);
					try {
						cellEntry.update();
					} catch (ServiceException e) {
						logger.error("failed to save refresh token for chapter, " + gplusId, e);
					}
				}
			}
		}


		// Build the Plus object using the credentials
		Plus plus = new Plus.Builder(transport, jsonFactory, credential).setApplicationName("").build();
		// Make the API call
		Person profile = plus.people().get("me").execute();
		// Send the results as the response
		PrintWriter respWriter = resp.getWriter();
		resp.setStatus(200);
		resp.setContentType("text/html");
		respWriter.println("<img src='" + profile.getImage().getUrl() + "'>");
		respWriter.println("<a href='" + profile.getUrl() + "'>" + profile.getDisplayName() + "</a>");

		UserService userService = UserServiceFactory.getUserService();
		respWriter.println("<div class=\"header\"><b>" + req.getUserPrincipal().getName() + "</b> | "
				+ "<a href=\"" + userService.createLogoutURL(req.getRequestURL().toString())
				+ "\">Log out</a> | "
				+ "<a href=\"http://code.google.com/p/google-api-java-client/source/browse"
				+ "/calendar-appengine-sample?repo=samples\">See source code for "
				+ "this sample</a></div>");
	}

	@Override
	protected AuthorizationCodeFlow initializeFlow() throws ServletException, IOException {
		return oauth2Utils.initializeFlow();
	}

	@Override
	protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
		return oauth2Utils.getRedirectUri(req);
	}

	@Override
	protected String getUserId(HttpServletRequest req) throws ServletException, IOException {
		return UserServiceFactory.getUserService().getCurrentUser().getEmail();
	}
}
