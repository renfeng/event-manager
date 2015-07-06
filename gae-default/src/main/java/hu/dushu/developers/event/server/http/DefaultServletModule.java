package hu.dushu.developers.event.server.http;

import com.google.inject.servlet.ServletModule;

public class DefaultServletModule extends ServletModule implements Path {

	@Override
	protected void configureServlets() {

		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
		serve(UNAUTHORIZED_API + "*").with(UnauthorizedServlet.class);

		serve(EVENTS_URL + "*").with(EventsServlet.class);

		serve("/api/check-in").with(CheckInServlet.class);
		serve("/api/label").with(LabelServlet.class);
		serve("/api/logo").with(LogoServlet.class);
	}

}
