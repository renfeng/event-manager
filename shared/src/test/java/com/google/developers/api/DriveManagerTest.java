package com.google.developers.api;

import com.google.api.client.http.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by renfeng on 9/27/15.
 */
public class DriveManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {
		DriveManager drive = injector.getInstance(DriveManager.class);
		Drive service = drive.getClient();
		File file = service.files().get("1HyyFJfqms_ZII3kNHr9smIXFadMYNRQvXsOhLOsUhLg").execute();
//		IOUtils.toString(downloadFile(service, file));
		String downloadUrl = file.getExportLinks().get("text/html");
		System.out.println(downloadUrl);

		HttpTransport transport = injector.getInstance(HttpTransport.class);
		HttpRequestFactory factory = transport.createRequestFactory();
		HttpRequest request = factory.buildGetRequest(new GenericUrl(downloadUrl));
		HttpResponse response = request.execute();
		System.out.println(IOUtils.toString(response.getContent()));
	}
}
