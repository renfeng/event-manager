package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.GoogleOAuth2;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Entry sevlet for the Plus App Engine Sample. Demonstrates how to make an authenticated API call
 * using OAuth2 helper classes.
 *
 * @author Nick Miceli
 */
@Singleton
public class OAuth2EntryPage
		extends AbstractAppEngineAuthorizationCodeServlet
		implements SessionKey {

	private static final ThreadLocal<HttpServletRequest> requestThreadLocal = new ThreadLocal<>();

	protected final HttpTransport transport;
	protected final JsonFactory jsonFactory;
	protected final OAuth2Utils oauth2Utils;

	@Inject
	public OAuth2EntryPage(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		// Get the stored credentials using the Authorization Flow
//		AuthorizationCodeFlow authFlow = initializeFlow();
//		Credential credential = authFlow.loadCredential(getUserId(req));
		Credential credential = getCredential();

		// Build the Plus object using the credentials
		Plus plus = new Plus.Builder(transport, jsonFactory, credential)
				.setApplicationName(GoogleOAuth2.APPLICATION_NAME).build();
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
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		requestThreadLocal.set(req);
		super.service(req, resp);
	}

	@Override
	protected GoogleAuthorizationCodeFlow initializeFlow() throws ServletException, IOException {

		GoogleAuthorizationCodeFlow flow = oauth2Utils.initializeFlow();

		String userId = getUserId(requestThreadLocal.get());
		Credential credential = flow.loadCredential(userId);
		if (credential == null || credential.getRefreshToken() == null) {
			flow = oauth2Utils.initializeFlowForceApprovalPrompt();
		}

		return flow;
	}

	@Override
	protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
		return oauth2Utils.getRedirectUri(req);
	}

	@Override
	protected String getUserId(HttpServletRequest req) throws ServletException, IOException {
		return UserServiceFactory.getUserService().getCurrentUser().getEmail();
	}

	@Override
	protected void onAuthorization(
			HttpServletRequest req, HttpServletResponse resp, AuthorizationCodeRequestUrl authorizationUrl)
			throws ServletException, IOException {

		HttpSession session = req.getSession();
		if (session.getAttribute(SessionKey.OAUTH2_ORIGIN) == null) {
			String origin = req.getHeader("Referer");
			if (origin == null) {
				origin = req.getRequestURL().toString();
			}
			session.setAttribute(SessionKey.OAUTH2_ORIGIN, origin);
		}

		super.onAuthorization(req, resp, authorizationUrl);
	}
}
