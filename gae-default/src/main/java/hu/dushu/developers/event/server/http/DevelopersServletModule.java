package hu.dushu.developers.event.server.http;

import com.google.inject.servlet.ServletModule;

public class DevelopersServletModule extends ServletModule implements Path {

	@Override
	protected void configureServlets() {

		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
		serve(UNAUTHORIZED_API + "*").with(UnauthorizedServlet.class);

		/*
		 * begin developers urls
		 */

		serve(EVENTS_URL + "*").with(EventsServlet.class);

		serve("/organizer-check-in").with(OrganizerCheckInServlet.class);
		serve("/checkin").with(CheckInServlet.class);
		serve("/label").with(LabelServlet.class);
		serve("/logo").with(LogoServlet.class);

		/*
		 * end developers urls
		 */

		return;
	}

}
