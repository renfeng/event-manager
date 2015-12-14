package com.google.developers.event.qrcode;

import com.google.api.client.util.Key;

/**
 * Created by frren on 2015-12-14.
 */
public class EmailJson extends MimePartJson {

	@Key
	private int size;

	@Key
	private String from;

	@Key
	private String to;

	@Key
	private String cc;

	@Key
	private String subject;

	/*
	 * TODO deprecate the fields above
	 */

	@Key("message-id")
	private String messageId;

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getCc() {
		return cc;
	}

	public void setCc(String cc) {
		this.cc = cc;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}
}
