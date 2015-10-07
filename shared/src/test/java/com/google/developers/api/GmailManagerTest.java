package com.google.developers.api;

import com.google.api.client.http.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by renfeng on 9/27/15.
 */
public class GmailManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {
		GmailManager manager = injector.getInstance(GmailManager.class);
		Gmail gmail = manager.getClient();
		Gmail.Users.Messages.List list = gmail.users().messages().list("me").setQ("from:renfeng.cn@gmail.com ");
		ListMessagesResponse response = list.execute();
		for (Message message:response.getMessages()) {
//			Message m = gmail.users().messages().get("me", message.getId()).execute();
			System.out.println("Message id: " + message.getId());
			System.out.println(message.toPrettyString());
		}
	}
}
