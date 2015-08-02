package com.google.developers.event.http;

import com.google.inject.servlet.ServletModule;

public class DefaultServletModule extends ServletModule implements Path {

	@Override
	protected void configureServlets() {

		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
		serve("/api/401/*").with(UnauthorizedServlet.class);

		serve("/api/check-in").with(CheckInServlet.class);
		serve("/api/label").with(LabelServlet.class);
		serve("/api/logo").with(LogoServlet.class);
		serve("/api/chapters").with(ChaptersServlet.class);
		serve("/api/events").with(EventsServlet.class);
		serve("/api/activities").with(GPlusServlet.class);

		serve(OAUTH2ENTRY).with(OAuth2EntryServlet.class);
		serve(OAUTH2CALLBACK).with(OAuth2CallbackServlet.class);

		serve(EVENTS_URL + "*").with(EventsServlet.class);
	}

}
