package com.google.developers.event;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 10/27/15.
 */
public class EventBriteBarcodes {

	@Key
	private String status;

	@Key
	private String barcode;

	@Key
	private String created;

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}
}
