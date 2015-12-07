package com.google.developers.api;

import com.google.gdata.client.GoogleService;

public abstract class ServiceManager<T extends GoogleService> {

	private T service;

	public final T getService() {
		return service;
	}

	protected void setService(T service) {
		this.service = service;
	}
}
