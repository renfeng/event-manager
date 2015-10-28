package com.google.developers.ghs;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 10/28/15.
 */
public class GoogleAppsDigLookupResponse {

	@Key("error_html")
	private String errorHtml;

	@Key
	private String response;

	public String getErrorHtml() {
		return errorHtml;
	}

	public void setErrorHtml(String errorHtml) {
		this.errorHtml = errorHtml;
	}

	public String getResponse() {
		return response;
	}

	public void setResponse(String response) {
		this.response = response;
	}
}
