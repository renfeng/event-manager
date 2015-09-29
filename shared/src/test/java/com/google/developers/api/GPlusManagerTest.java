package com.google.developers.api;

import com.google.developers.event.DevelopersSharedModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by frren on 2015-07-10.
 */
public class GPlusManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException {
		injector.getInstance(GPlusManager.class).x();
	}
}
