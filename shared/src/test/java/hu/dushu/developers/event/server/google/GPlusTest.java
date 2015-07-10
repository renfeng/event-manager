package hu.dushu.developers.event.server.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import hu.dushu.developers.event.server.DevelopersSharedModule;
import hu.dushu.developers.event.server.google.GPlusManager;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * Created by frren on 2015-07-10.
 */
public class GPlusTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {
		injector.getInstance(GPlusManager.class).x();
	}
}
