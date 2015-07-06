package hu.dushu.developers.event.server.http;

import com.google.inject.servlet.ServletModule;

public class RankingServletModule extends ServletModule {

	@Override
	protected void configureServlets() {
		serve("/cron/refresh-ranking").with(RankingServlet.class);
	}

}
