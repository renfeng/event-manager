package com.google.developers;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by renfeng on 10/28/15.
 */
public class GHSHunt {

	public static void main(String... args) {
		if (args.length == 0) {
			return;
		}
		String host = args[0];
		List<String> hostAddressList = new ArrayList<>();
		Security.setProperty("networkaddress.cache.ttl", "0");
		while (true) {
			try {
				InetAddress address = InetAddress.getByName(host);
				String hostAddress = address.getHostAddress();
				if (hostAddressList.contains(hostAddress)) {
					continue;
				}
				System.out.println(hostAddress + " " + new Date());
				hostAddressList.add(hostAddress);
			} catch (UnknownHostException ex) {
				/*
				 * ignore
				 */
			}
		}
	}
}
