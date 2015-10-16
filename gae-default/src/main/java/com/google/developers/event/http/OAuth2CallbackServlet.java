/*
 * Copyright (c) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeCallbackServlet;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.GoogleOAuthManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ChapterSpreadsheet;
import com.google.developers.event.DevelopersSharedModule;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.util.ServiceException;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * HTTP servlet to process access granted from user.
 *
 * @author Nick Miceli
 */
@Singleton
public class OAuth2CallbackServlet
		extends AbstractAppEngineAuthorizationCodeCallbackServlet
		implements Path, ChapterSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(OAuth2CallbackServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils auth2Utils;

	@Inject
	public OAuth2CallbackServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.auth2Utils = oauth2Utils;
	}

	@Override
	protected void onSuccess(HttpServletRequest req, HttpServletResponse resp, Credential credential)
			throws ServletException, IOException {

		SpreadsheetManager spreadsheetManager = SpreadsheetManager.getGlobalInstance(transport, jsonFactory);

		final String refreshToken = credential.getRefreshToken();
		if (refreshToken != null) {
			/*
			 * get G+ ID
			 * https://developers.google.com/+/web/api/rest/latest/people/get
			 */
			// Build the Plus object using the credentials
			Plus plus = new Plus.Builder(transport, jsonFactory, credential)
					.setApplicationName(GoogleOAuthManager.APPLICATION_NAME).build();
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
				CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager.getService()) {

					CellEntry refreshTokenCell;

					@Override
					protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
							throws IOException, ServiceException {
						if (gplusId.equals(valueMap.get("gplusID"))) {
							if (diff(refreshTokenCell.getCell().getInputValue(), refreshToken)) {
								refreshTokenCell.changeInputValueLocal(refreshToken);
								try {
									refreshTokenCell.update();
								} catch (ServiceException e) {
									logger.error("failed to save refresh token for chapter, " + gplusId, e);
								}
							}
							return false;
						}

						refreshTokenCell = null;

						return true;
					}

					@Override
					protected void processDataColumn(CellEntry cell, String columnName) {
						if (REFRESH_TOKEN.equals(columnName)) {
							refreshTokenCell = cell;
						}
					}
				};
				try {
					processor.process(spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("chapter")),
							GPLUS_ID, REFRESH_TOKEN, CHAPTER_PAGE);
				} catch (ServiceException e) {
					logger.error("failed to load refresh token for chapter, " + gplusId, e);
				}
			}
		}

		HttpSession session = req.getSession();
		String redirUrl = (String) session.getAttribute(SessionKey.OAUTH2_ORIGIN);
		if (redirUrl == null) {
			redirUrl = OAUTH2ENTRY;
		} else {
			session.removeAttribute(SessionKey.OAUTH2_ORIGIN);
		}
		/*
		 * FIXME redir works like a forward...
		 */
		resp.sendRedirect(redirUrl);
	}

	@Override
	protected void onError(
			HttpServletRequest req, HttpServletResponse resp, AuthorizationCodeResponseUrl errorResponse)
			throws ServletException, IOException {
		String nickname = UserServiceFactory.getUserService().getCurrentUser().getNickname();
		resp.getWriter().print("<h3>Hey " + nickname + ", why don't you want to play with me?</h1>");
		resp.setStatus(200);
		resp.addHeader("Content-Type", "text/html");
	}

	@Override
	protected AuthorizationCodeFlow initializeFlow() throws ServletException, IOException {
		return auth2Utils.initializeFlow();
	}

	@Override
	protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
		return auth2Utils.getRedirectUri(req);
	}

	@Override
	protected String getUserId(HttpServletRequest req) throws ServletException, IOException {
		return UserServiceFactory.getUserService().getCurrentUser().getEmail();
	}
}
