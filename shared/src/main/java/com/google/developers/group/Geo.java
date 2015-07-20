package com.google.developers.group;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 7/19/15.
 */
public class Geo {

	@Key
	private float lat;

	@Key
	private float lng;

	public float getLat() {
		return lat;
	}

	public void setLat(float lat) {
		this.lat = lat;
	}

	public float getLng() {
		return lng;
	}

	public void setLng(float lng) {
		this.lng = lng;
	}
}
