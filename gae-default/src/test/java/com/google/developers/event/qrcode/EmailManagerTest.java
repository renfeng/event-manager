package com.google.developers.event.qrcode;

import com.google.appengine.tools.development.testing.LocalMemcacheServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.developers.event.DevelopersSharedModule;
import com.google.developers.event.qrcode.EmailManager;
import com.google.gdata.util.ServiceException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by frren on 2015-12-14.
 */
public class EmailManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	/*
	 * https://cloud.google.com/appengine/docs/java/tools/localunittesting#Java_Writing_Datastore_and_memcache_tests
	 */
	private final LocalServiceTestHelper helper =
			new LocalServiceTestHelper(new LocalMemcacheServiceTestConfig());

	@Before
	public void setUp() {
		helper.setUp();
	}

	@After
	public void tearDown() {
		helper.tearDown();
	}

	@Test
	public void test() throws IOException, MessagingException, ServiceException {
		EmailManager emailManager = injector.getInstance(EmailManager.class);
		emailManager.receive(IOUtils.toString(
				new FileReader("src/test/resources/testmail.txt")), "effective-forge-706");
	}
}
