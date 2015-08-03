package com.google.developers.event.http;

import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;

public class RankingStartupListener extends GuiceServletContextListener {

	private static final Logger logger = LoggerFactory
			.getLogger(RankingStartupListener.class);

	private Injector injector;

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		/*
		 * TODO switch to production
		 */
		injector = Guice.createInjector(Stage.DEVELOPMENT,
				new DevelopersSharedModule(),
				new DevelopersSharedServletModule(),
				new RankingServletModule());
		logger.info("created injector");

		super.contextInitialized(sce);

		return;
	}

	@Override
	protected Injector getInjector() {
		return injector;
	}

}
