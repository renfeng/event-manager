package com.google.developers.group;

import com.google.api.client.util.Key;

import java.util.List;

/**
 * Created by renfeng on 7/19/15.
 */
public class DirectoryGroups {

	@Key
	private List<Chapter> groups;

	@Key
	private String success;

	public List<Chapter> getGroups() {
		return groups;
	}

	public void setGroups(List<Chapter> groups) {
		this.groups = groups;
	}

	public String getSuccess() {
		return success;
	}

	public void setSuccess(String success) {
		this.success = success;
	}
}
