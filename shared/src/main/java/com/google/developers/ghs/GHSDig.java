package com.google.developers.ghs;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by renfeng on 10/28/15.
 */
public class GHSDig {

	public String dig(String domain) throws IOException {

		HttpTransport transport = new NetHttpTransport();
		HttpRequestFactory factory = transport.createRequestFactory();
		HashMap<String, Object> data = new HashMap<>();
//		data.put("csrfmiddlewaretoken", "plG2SGjTVwbE5KBXu4Sf9BKfInmHcTPA");
		data.put("csrfmiddlewaretoken", "did5hECGR9WXmufXUOzFBONxOs8QPqLX");
		data.put("domain", domain);
		data.put("nameserver", "");
		data.put("typ", "A");
		UrlEncodedContent content = new UrlEncodedContent(data);
		GenericUrl url = new GenericUrl("https://toolbox.googleapps.com/apps/dig/lookup");
		HttpRequest request = factory.buildPostRequest(url, content);
//		request.getHeaders().set("Referer", "https://toolbox.googleapps.com/apps/dig/");
		request.getHeaders().setAccept("application/json, text/javascript, */*; q=0.01");

		/*
		 * FIXME 500
		 */
		HttpResponse response = request.execute();
		GoogleAppsDigLookupResponse dig = response.parseAs(GoogleAppsDigLookupResponse.class);

		/*
id 27806
opcode QUERY
rcode NOERROR
flags QR RD RA
;QUESTION
ghs.google.com. IN A
;ANSWER
ghs.google.com. 21442 IN CNAME ghs.l.google.com.
ghs.l.google.com. 142 IN A 74.125.28.121
;AUTHORITY
;ADDITIONAL
		 */
		String digResponse = dig.getResponse();
		String anchor = " IN A ";
		int start = digResponse.lastIndexOf(anchor);
		int end = digResponse.indexOf("\n", start);
		return digResponse.substring(start + anchor.length(), end);
	}
}
