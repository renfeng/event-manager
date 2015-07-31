package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Entry sevlet for the Plus App Engine Sample. Demonstrates how to make an authenticated API call
 * using OAuth2 helper classes.
 *
 * @author Nick Miceli
 */
@Singleton
public class AdminOAuth2EntryServlet extends AbstractAppEngineAuthorizationCodeServlet {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final AdminOAuth2Utils adminOAuth2Utils;

	@Inject
	public AdminOAuth2EntryServlet(HttpTransport transport, JsonFactory jsonFactory, AdminOAuth2Utils adminOAuth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.adminOAuth2Utils = adminOAuth2Utils;
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
		// Get the stored credentials using the Authorization Flow
		AuthorizationCodeFlow authFlow = initializeFlow();
		Credential credential = authFlow.loadCredential(getUserId(req));
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
	}

	@Override
	protected AuthorizationCodeFlow initializeFlow() throws ServletException, IOException {
		return adminOAuth2Utils.initializeFlow();
	}

	@Override
	protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
		return adminOAuth2Utils.getRedirectUri(req);
	}
}
