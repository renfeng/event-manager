package com.google.developers.event;

import com.google.gdata.util.ServiceException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by renfeng on 5/22/15.
 */
public class EventManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException, ServiceException {

		EventManager eventManager = injector.getInstance(EventManager.class);
		eventManager.importContactsFromSpreadsheets();
//		eventManager.updateRanking();
//		eventManager.updateEventScore();

//		SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yyyy HH:mm:ss");
//		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+08"));
//		try {
//			Date date = dateFormat.parse("6/26/2015 20:00:00");
//			System.out.println(date);
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}

	}
}
