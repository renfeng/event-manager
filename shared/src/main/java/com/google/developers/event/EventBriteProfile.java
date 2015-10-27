package com.google.developers.event;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 10/27/15.
 */
public class EventBriteProfile {

	@Key("first_name")
	private String firstName;

	@Key("last_name")
	private String lastName;

	@Key("email")
	private String email;

	@Key("name")
	private String name;

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
