package com.google.developers.event;

import java.util.Date;

/**
 * Created by renfeng on 5/22/15.
 */
public class EventActivities {

	Date register;
	Date checkIn;
	Date feedback;

	String sponsor;

	public String getSponsor() {
		return sponsor;
	}

	public void setSponsor(String sponsor) {
		this.sponsor = sponsor;
	}

	public Date getFeedback() {
		return feedback;
	}

	public void setFeedback(Date feedback) {
		this.feedback = feedback;
	}

	public Date getCheckIn() {
		return checkIn;
	}

	public void setCheckIn(Date checkIn) {
		this.checkIn = checkIn;
	}

	public Date getRegister() {
		return register;
	}

	public void setRegister(Date register) {
		this.register = register;
	}
}
