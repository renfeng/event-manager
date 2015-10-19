package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.GoogleOAuth2;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ChapterSpreadsheet;
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
public class OAuth2RevokeServlet extends AbstractAppEngineAuthorizationCodeServlet
		implements ChapterSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(OAuth2RevokeServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;
	private final DataStoreFactory dataStoreFactory;

	@Inject
	public OAuth2RevokeServlet(
			HttpTransport transport, JsonFactory jsonFactory,
			OAuth2Utils oauth2Utils, DataStoreFactory dataStoreFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
		this.dataStoreFactory = dataStoreFactory;
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(getCredential());

		/*
		 * get G+ ID
		 * https://developers.google.com/+/web/api/rest/latest/people/get
		 */
//		AuthorizationCodeFlow authFlow = initializeFlow();
//		Credential credential = authFlow.loadCredential(getUserId(req));
		Credential credential = getCredential();

		// Build the Plus object using the credentials
		Plus plus = new Plus.Builder(transport, jsonFactory, credential)
				.setApplicationName(GoogleOAuth2.APPLICATION_NAME).build();
		// Make the API call
		Person profile = plus.people().get("me").execute();
		final String gplusId = profile.getId();
		logger.trace("https://plus.google.com/" + gplusId);
		final ThreadLocal<CellEntry> cellEntryThreadLocal = new ThreadLocal<>();
		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager.getService()) {

			Map<String, CellEntry> cellMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {
				if (gplusId.equals(valueMap.get(GPLUS_ID))) {
					cellEntryThreadLocal.set(cellMap.get(REFRESH_TOKEN));
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
					GPLUS_ID, REFRESH_TOKEN, CHAPTER_PAGE);
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
				 *
				 * If the revocation is successfully processed, then the status code of the response is 200.
				 * For error conditions, a status code 400 is returned along with an error code.
				 *
				 * https://developers.google.com/identity/protocols/OAuth2WebServer
				 */
				}
				resp.setStatus(response.getStatusCode());
			} catch (ServiceException e) {
				logger.error("failed to save refresh token for chapter, " + gplusId, e);
			}
		}

		// Get the Datastore Service
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		UserService userService = UserServiceFactory.getUserService();
		String email = userService.getCurrentUser().getEmail();
		Query q = new Query("Person").setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, email));
		// Use PreparedQuery interface to retrieve results
		PreparedQuery pq = datastore.prepare(q);
		for (Entity result : pq.asIterable()) {
			datastore.delete(result.getKey());
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
