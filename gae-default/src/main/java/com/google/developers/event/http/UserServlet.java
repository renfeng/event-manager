package com.google.developers.event.http;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by renfeng on 10/16/15.
 */
@Singleton
public class UserServlet extends HttpServlet {

	private final JsonFactory jsonFactory;

	@Inject
	public UserServlet(JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String userAgent = req.getHeader("User-Agent");

		UserService userService = UserServiceFactory.getUserService();

		String referer = req.getHeader("Referer");
		if (referer == null) {
			referer = "/authenticated/";
		}

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("email");
		generator.writeString(userService.getCurrentUser().getEmail());
		generator.writeFieldName("login");
		generator.writeString(userService.createLoginURL(referer));
		generator.writeFieldName("logout");
		generator.writeString(userService.createLogoutURL(referer));
		generator.writeFieldName("agent");
		generator.writeString(userAgent);
		generator.writeEndObject();
		generator.flush();
	}
}
