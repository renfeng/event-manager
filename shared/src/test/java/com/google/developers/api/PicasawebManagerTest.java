package com.google.developers.api;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.developers.event.DevelopersSharedModule;
import com.google.gdata.util.ServiceException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by renfeng on 12/7/15.
 */
public class PicasawebManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException, ServiceException {
		HttpTransport transport = injector.getInstance(HttpTransport.class);
		JsonFactory jsonFactory = injector.getInstance(JsonFactory.class);
		PicasawebManager picasawebManager = new PicasawebManager(
				GoogleOAuth2.getGlobalCredential(transport, jsonFactory));
		String url = picasawebManager.getPhotoUrl("6225503339996130066");

		HttpRequestFactory factory = transport.createRequestFactory();
		HttpRequest request = factory.buildGetRequest(new GenericUrl(url));
		HttpResponse response = request.execute();
		System.out.println(response.getContentType());
		response.download(new FileOutputStream("/tmp/x.jpg"));
	}
}
