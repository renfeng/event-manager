package com.google.developers.group;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 7/19/15.
 */
public class Geo {

	@Key
	private String lat;

	@Key
	private String lng;

	public String getLng() {
		return lng;
	}

	public void setLng(String lng) {
		this.lng = lng;
	}

	public String getLat() {
		return lat;
	}

	public void setLat(String lat) {
		this.lat = lat;
	}
}
