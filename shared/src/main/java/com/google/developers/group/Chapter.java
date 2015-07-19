package com.google.developers.group;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.Key;

import java.io.IOException;
import java.util.List;

/**
 * Created by renfeng on 7/19/15.
 */
public class Chapter {

	public static List<Chapter> list(HttpTransport transport, final JsonFactory jsonFactory) throws IOException {

		/*
		 * https://developers.google.com/groups/directorygroups/
		 */
		GenericUrl url = new GenericUrl("https://developers.google.com/groups/directorygroups/");

		HttpRequestFactory factory = transport.createRequestFactory(new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest request) throws IOException {
				request.setParser(new JsonObjectParser(jsonFactory));
			}
		});

		HttpRequest request = factory.buildGetRequest(url);
		HttpResponse response = request.execute();
		DirectoryGroups directoryGroups = response.parseAs(DirectoryGroups.class);

		return directoryGroups.getGroups();
	}

	@Key
	private String status;

	@Key
	private String site;

	@Key
	private String street;

	@Key
	private Geo geo;

	@Key
	private String city;

	@Key
	private String name;

	@Key
	private String country;

	@Key("chapter_id")
	private String chapterID;

	@Key
	private String state;

	@Key("gplus_id")
	private String gplusID;

	@Key("group_type")
	private String groupType;

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getSite() {
		return site;
	}

	public void setSite(String site) {
		this.site = site;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public Geo getGeo() {
		return geo;
	}

	public void setGeo(Geo geo) {
		this.geo = geo;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getChapterID() {
		return chapterID;
	}

	public void setChapterID(String chapterID) {
		this.chapterID = chapterID;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getGplusID() {
		return gplusID;
	}

	public void setGplusID(String gplusID) {
		this.gplusID = gplusID;
	}

	public String getGroupType() {
		return groupType;
	}

	public void setGroupType(String groupType) {
		this.groupType = groupType;
	}
}
