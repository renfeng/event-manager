package com.google.developers.event.server.http;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.inject.servlet.GuiceFilter;

public class DevelopersSharedFilter extends GuiceFilter {

	// private static final Logger logger = LoggerFactory
	// .getLogger(DevelopersSharedFilter.class);

	@Override
	public void doFilter(ServletRequest servletRequest,
			ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {

		ModulesService modulesApi = ModulesServiceFactory.getModulesService();
		String instanceHostname = modulesApi.getCurrentInstanceId() + "."
				+ modulesApi.getCurrentVersion() + "."
				+ modulesApi.getCurrentModule();
		Logger logger = LoggerFactory.getLogger(instanceHostname);
		logger.info(logger.getName());

		logger.info("begin guice filter");
		super.doFilter(servletRequest, servletResponse, filterChain);
		logger.info("end guice filter");

		return;
	}

}
