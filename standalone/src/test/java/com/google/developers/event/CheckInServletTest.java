package com.google.developers.event;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by frren on 2015-08-22.
 */
public class CheckInServletTest {

	@Test
	public void test() {
		System.out.println(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date()));
	}
}
