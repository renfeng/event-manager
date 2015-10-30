package com.google.developers.ghs;

import org.junit.Test;

import java.io.IOException;
import java.util.Date;

/**
 * Created by frren on 2015-10-30.
 */
public class GHSDigTest {

	@Test
	public void test() throws IOException {

		GHSDig dig = new GHSDig();
		while (true) {
			String ip = dig.dig("ghs.google.com");
			System.out.println(ip + " " + new Date());
		}
	}
}
