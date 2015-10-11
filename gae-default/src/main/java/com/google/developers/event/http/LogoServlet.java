package com.google.developers.event.http;

import com.google.developers.api.DriveManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by renfeng on 6/22/15.
 */
@Singleton
public class LogoServlet extends HttpServlet implements Path {

	private static final Logger logger = LoggerFactory.getLogger(LogoServlet.class);

	private final DriveManager driveManager;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public LogoServlet(DriveManager driveManager, SpreadsheetManager spreadsheetManager) {
		this.driveManager = driveManager;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, CHECK_IN_URL);
			if (activeEvent == null) {
				req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
				return;
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		IOUtils.copy(new ByteArrayInputStream(activeEvent.getLogoCache(driveManager)),
				resp.getOutputStream());
	}
}
