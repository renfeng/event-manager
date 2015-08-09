package com.google.developers.event.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

/**
 * Created by renfeng on 7/23/15.
 */
public class ActivitiesServletTest {

	@Test
	public void test(){

		String html =  "<b>Launching Google beacon platform</b><br /><br />Helping your apps work smarter: Introducing the <a rel=\"nofollow\" class=\"1ot-hashtag\" href=\"https://plus.google.com/s/%23GoogleBeaconPlatform\">#GoogleBeaconPlatform</a> and the <a rel=\"nofollow\" class=\"ot-hashtag\" href=\"https://plus.google.com/s/%23Eddystone\">#Eddystone</a> BLE beacon format.\ufeff";
		// <b>Launching Google beacon platform</b><br /><br />Helping your apps work smarter: Introducing the <a rel="nofollow" class="ot-hashtag" href="https://plus.google.com/s/%23GoogleBeaconPlatform">#GoogleBeaconPlatform</a> and the <a rel="nofollow" class="ot-hashtag" href="https://plus.google.com/s/%23Eddystone">#Eddystone</a> BLE beacon format.﻿

		Document document = Jsoup.parse(html);
		//Elements hashTagAnchor = document.select("a[class='ot-hashtag']");
		Elements hashTagAnchor = document.select("a[class=ot-hashtag]");
		for (Element t : hashTagAnchor) {
			String hashTag = t.text();
			System.out.println(hashTag);
		}

	}
}
