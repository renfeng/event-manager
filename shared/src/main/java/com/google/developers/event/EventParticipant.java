package com.google.developers.event;

import java.util.Date;

public class EventParticipant {

	private String nickname;
	private String emailAddress;
	private String phoneNumber;

	private Date timestamp;

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	@Override
	public String toString() {
		return (getNickname() != null ? getNickname() : "(anonymous)") +
				(getEmailAddress() != null ? " <" + getEmailAddress() + "> " : "(missing email)") +
				(getPhoneNumber() != null ? getPhoneNumber() : "(missing phone number)");
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
}
