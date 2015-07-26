package com.google.developers.event;

import com.google.api.client.extensions.appengine.datastore.AppEngineDataStoreFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.MemcacheKey;
import com.google.developers.api.SpreadsheetManager;
import com.google.inject.*;
import com.google.inject.name.Names;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;

public class DevelopersSharedModule implements Module, MemcacheKey {

	@Override
	public void configure(Binder binder) {

//		binder.bind(HttpTransport.class).toInstance(new UrlFetchTransport());
		binder.bind(HttpTransport.class).toInstance(new NetHttpTransport());

		/*
		 * TODO HH?
		 */
		binder.bind(DateFormat.class).toInstance(
				new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'"));

		binder.bind(JsonFactory.class).toInstance(
				JacksonFactory.getDefaultInstance());

		/*
		 * Global instance of the {@link DataStoreFactory}. The best practice is
		 * to make it a single globally shared instance across your application.
		 */
		binder.bind(DataStoreFactory.class).toInstance(
				AppEngineDataStoreFactory.getDefaultInstance());

		binder.bind(String.class)
				.annotatedWith(Names.named("refreshToken"))
				.toInstance(getMessage("refreshToken"));
		binder.bind(String.class)
				.annotatedWith(Names.named("clientId"))
				.toInstance(getMessage("clientId"));
		binder.bind(String.class)
				.annotatedWith(Names.named("clientSecret"))
				.toInstance(getMessage("clientSecret"));

		return;
	}

	/**
	 * binds ActiveEvent to an instance stored in memcache
	 *
	 * @param spreadsheetManagerProvider
	 * @return
	 * @throws IOException
	 */
	@Provides
	@Inject
	ActiveEvent provideActiveEvent(Provider<SpreadsheetManager> spreadsheetManagerProvider) throws IOException {

		MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
		ActiveEvent activeEvent = (ActiveEvent) syncCache.get(ACTIVE_EVENT);
		if (activeEvent == null) {
			activeEvent = new ActiveEvent(spreadsheetManagerProvider.get());
		}

		return activeEvent;
	}

	public static String getMessage(String key) {
		/*
		 * http://docs.oracle.com/javase/tutorial/i18n/intro/steps.html
		 */
		ResourceBundle bundle = ResourceBundle.getBundle("message");
		return bundle.getString(key);
	}

}
