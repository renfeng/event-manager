package com.google.developers.event;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;

/**
 * Created by frren on 2015-07-04.
 */
public class StandaloneStartupListener extends GuiceServletContextListener {

	private static final Logger logger = LoggerFactory
			.getLogger(StandaloneStartupListener.class);

	private Injector injector;

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		/*
		 * TODO switch to production
		 */
		injector = Guice.createInjector(Stage.DEVELOPMENT,
				new StandaloneServletModule());

		super.contextInitialized(sce);

//		/*
//		 * properties - Load java.util.logging.config.file for default initialization - Stack Overflow
//		 * http://stackoverflow.com/a/5903474/333033
//		 */
//		try {
//			LogManager.getLogManager().readConfiguration(BacklogListener.class.getResourceAsStream("logging.properties"));
//		} catch (IOException e) {
//			java.util.logging.Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
//			java.util.logging.Logger.getAnonymousLogger().severe(e.getMessage());
//		}

		return;
	}

	@Override
	protected Injector getInjector() {
		return injector;
	}

}
