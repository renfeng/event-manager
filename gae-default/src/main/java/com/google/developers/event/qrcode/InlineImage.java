package com.google.developers.event.qrcode;

import com.google.api.client.util.Key;

/**
 * Created by frren on 2015-12-14.
 */
public class InlineImage {

	@Key
	private String cid;

	@Key
	private String gPhotoId;

	public String getCid() {
		return cid;
	}

	public void setCid(String cid) {
		this.cid = cid;
	}

	public String getgPhotoId() {
		return gPhotoId;
	}

	public void setgPhotoId(String gPhotoId) {
		this.gPhotoId = gPhotoId;
	}
}
