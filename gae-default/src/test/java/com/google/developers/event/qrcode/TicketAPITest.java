package com.google.developers.event.qrcode;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.FileReader;
import java.io.IOException;

/**
 * Created by renfeng on 12/14/15.
 */
public class TicketAPITest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {

		JsonFactory jsonFactory = injector.getInstance(JsonFactory.class);
		JsonParser parser = jsonFactory.createJsonParser(IOUtils.toString(
				new FileReader("src/test/resources/testmail.json")));
		EmailJson json = parser.parse(EmailJson.class);

	}
}
