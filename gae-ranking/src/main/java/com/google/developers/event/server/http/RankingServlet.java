package com.google.developers.event.server.http;

import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.developers.event.server.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by renfeng on 7/5/15.
 */
@Singleton
public class RankingServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(RankingServlet.class);

	private final EventManager eventManager;

	@Inject
	public RankingServlet(EventManager eventManager) {
		this.eventManager = eventManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			eventManager.importContactsFromSpreadsheets();
		} catch (ServiceException e) {
			logger.error("failed to import contacts from spreadsheets", e);
		}
		try {
			eventManager.updateRanking();
		} catch (ServiceException e) {
			logger.error("failed to update ranking", e);
		}
	}
}
