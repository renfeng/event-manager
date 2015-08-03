package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
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
public class OAuth2EntryServlet extends AbstractAppEngineAuthorizationCodeServlet {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;

	@Inject
	public OAuth2EntryServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
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
}
