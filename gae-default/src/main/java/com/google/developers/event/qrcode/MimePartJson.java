package com.google.developers.event.qrcode;

import com.google.api.client.util.Key;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by frren on 2015-12-14.
 */
public class MimePartJson {

	@Key("multipart/related")
	private List<MimePartJson> multipartRelated;

	@Key("multipart/alternative")
	private MimePartJson multipartAlternative;

	@Key("image/jpeg")
	private InlineImage jpg;

	@Key("text/plain")
	private String text;

	@Key("text/html")
	private String html;

	public List<MimePartJson> getMultipartRelated() {
		return multipartRelated;
	}

	public void setMultipartRelated(List<MimePartJson> multipartRelated) {
		this.multipartRelated = multipartRelated;
	}

	public MimePartJson getMultipartAlternative() {
		return multipartAlternative;
	}

	public void setMultipartAlternative(MimePartJson multipartAlternative) {
		this.multipartAlternative = multipartAlternative;
	}

	public InlineImage getJpg() {
		return jpg;
	}

	public void setJpg(InlineImage jpg) {
		this.jpg = jpg;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getHtml() {
		return html;
	}

	public void setHtml(String html) {
		this.html = html;
	}
}
