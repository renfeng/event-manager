package com.google.developers.event;

/**
 * Created by renfeng on 7/27/15.
 */
public class EventScore {

	/*
	 * a.k.a. contact group name (max 40 characters)
	 * a.k.a. Group column in meta spreadsheet
	 */
	private String name;

	private int value;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return getName() + "[" + getValue() + "]";
	}
}
