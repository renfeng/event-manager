package com.google.developers.event;

import com.google.inject.servlet.ServletModule;

/**
 * Created by frren on 2015-07-04.
 */
public class StandaloneServletModule extends ServletModule {

	@Override
	protected void configureServlets() {
		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
//		serve(UnauthorizedServlet.URL_PATTERN).with(UnauthorizedServlet.class);

		serve("/api/setup").with(SetupServlet.class);
		serve("/api/check-in").with(CheckInServlet.class);
	}
}
