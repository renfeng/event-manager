package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
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
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by renfeng on 8/3/15.
 */
@Singleton
public class OAuth2RevokeServlet extends AbstractAppEngineAuthorizationCodeServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(OAuth2RevokeServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;
	private final DataStoreFactory dataStoreFactory;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public OAuth2RevokeServlet(
			HttpTransport transport,
			JsonFactory jsonFactory,
			OAuth2Utils oauth2Utils,
			DataStoreFactory dataStoreFactory,
			SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
		this.dataStoreFactory = dataStoreFactory;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		/*
		 * https://developers.google.com/identity/protocols/OAuth2WebServer?hl=en
		 */

		/*
		 * get G+ ID
		 * https://developers.google.com/+/web/api/rest/latest/people/get
		 */
		AuthorizationCodeFlow authFlow = initializeFlow();
		Credential credential = authFlow.loadCredential(getUserId(req));
		// Build the Plus object using the credentials
		Plus plus = new Plus.Builder(transport, jsonFactory, credential).setApplicationName("").build();
		// Make the API call
		Person profile = plus.people().get("me").execute();
		final String gplusId = profile.getId();
		logger.trace("https://plus.google.com/" + gplusId);
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
		if (cellEntry != null) {
			String token = cellEntry.getCell().getInputValue();
			cellEntry.changeInputValueLocal(null);
			try {
				cellEntry.update();

				HttpRequestFactory factory = transport.createRequestFactory();
				GenericUrl url = new GenericUrl("https://accounts.google.com/o/oauth2/revoke?token=" + token);
				HttpRequest request = factory.buildGetRequest(url);
				request.setThrowExceptionOnExecuteError(false);
				HttpResponse response = request.execute();
				if (response.getStatusCode() == 200) {
				/*
				 * The token can be an access token or a refresh token.
				 * If the token is an access token and it has a corresponding refresh token,
				 * the refresh token will also be revoked.
				 * https://developers.google.com/identity/protocols/OAuth2WebServer?hl=en
				 */
				}
			} catch (ServiceException e) {
				logger.error("failed to save refresh token for chapter, " + gplusId, e);
			}
		}


	}

	@Override
	protected AuthorizationCodeFlow initializeFlow() throws ServletException, IOException {
		return oauth2Utils.initializeFlow();
	}

	@Override
	protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
		return oauth2Utils.getRedirectUri(req);
	}
}
