package hu.dushu.developers.event.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;
import hu.dushu.developers.event.server.http.DevelopersServletModule;
import hu.dushu.developers.event.server.http.DevelopersSharedServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import java.util.ResourceBundle;

/**
 * loader for the web application
 *
 * @author renfeng
 */
public class StartupListener extends GuiceServletContextListener {

	private static final Logger logger = LoggerFactory
			.getLogger(StartupListener.class);

	/*
	 * removed static keyword
	 */
	private Injector injector;

	/*
	 * XXX for deferred task - which i would gave up because of lack of logging
	 * output in eclipse junit, and unreadable payload on appengine.google.com
	 */

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		/*
		 * TODO switch to production
		 */
		logger.info("created injector");
		injector = Guice.createInjector(Stage.DEVELOPMENT,
				new DevelopersSharedServletModule(),
				new DevelopersSharedModule(),
				new DevelopersServletModule());

		super.contextInitialized(sce);
	}

	@Override
	protected Injector getInjector() {
		return injector;
	}

}