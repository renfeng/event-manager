package hu.dushu.developers.server.http;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;
import hu.dushu.developers.event.server.DevelopersSharedModule;
import hu.dushu.developers.event.server.http.DevelopersSharedServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;

public class DevelopersRankingListener extends GuiceServletContextListener {

	private static final Logger logger = LoggerFactory
			.getLogger(DevelopersRankingListener.class);

	private Injector injector;

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		/*-
		 * keytool -list -alias dushu.hu-1 -keystore jssecacerts -storepass changeit
		dushu.hu-1, Aug 23, 2013, trustedCertEntry,
		Certificate fingerprint (MD5): A7:96:DA:D3:F2:91:90:8B:93:80:E1:31:A9:35:4B:9F
		 */
		System.setProperty("javax.net.ssl.trustStore", sce.getServletContext()
				.getRealPath("WEB-INF/jssecacerts"));

		/*
		 * TODO switch to production
		 */
		injector = Guice.createInjector(Stage.DEVELOPMENT,
				new DevelopersSharedServletModule(), new DevelopersSharedModule());
		logger.info("created injector");

		super.contextInitialized(sce);

		// restore(injector);

		return;
	}

	@Override
	protected Injector getInjector() {
		return injector;
	}

}
