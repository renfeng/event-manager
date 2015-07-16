package com.google.developers.event.server;

/**
 * Created by renfeng on 5/22/15.
 */
public class EventActivities {

	String register;
	String checkIn;
	String feedback;

	String sponsor;

	public String getSponsor() {
		return sponsor;
	}

	public void setSponsor(String sponsor) {
		this.sponsor = sponsor;
	}

	public String getFeedback() {
		return feedback;
	}

	public void setFeedback(String feedback) {
		this.feedback = feedback;
	}

	public String getCheckIn() {
		return checkIn;
	}

	public void setCheckIn(String checkIn) {
		this.checkIn = checkIn;
	}

	public String getRegister() {
		return register;
	}

	public void setRegister(String register) {
		this.register = register;
	}
}
