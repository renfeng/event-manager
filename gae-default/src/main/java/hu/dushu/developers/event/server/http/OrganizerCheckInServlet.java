package hu.dushu.developers.event.server.http;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by renfeng on 6/13/15.
 */
@Singleton
public class OrganizerCheckInServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		/*
		 * TODO auto retrieve event name from meta spreadsheet
		 */

		resp.getWriter().write(req.getRemoteAddr());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		UserService userService = UserServiceFactory.getUserService();
		if (!userService.isUserAdmin()) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		}

	}
}
