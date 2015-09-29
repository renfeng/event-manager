package com.google.developers.event.qrcode;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.Path;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by frren on 2015-09-29.
 */
@Singleton
public class ParticipantsServlet extends HttpServlet
		implements Path, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(ParticipantsServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public ParticipantsServlet(HttpTransport transport, JsonFactory jsonFactory,
							   SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		/*
		 * retrieve event id from http header, referer
		 * e.g.
		 * https://plus.google.com/events/c2vl1u3p3pbglde0gqhs7snv098
		 * https://developers.google.com/events/6031536915218432/
		 * https://hub.gdgx.io/events/6031536915218432
		 */
		String referer = req.getHeader("Referer");
//		Pattern gplusEventPattern = Pattern.compile("https://plus.google.com/events/" +
//				"[^/]+");
//		Pattern devsiteEventPattern = Pattern.compile("https://developers.google.com/events/" +
//				"[^/]+/");
//		Pattern gdgxHubEventPattern = Pattern.compile("https://hub.gdgx.io/events/" +
//				"([^/]+)");
		String requestURL = req.getRequestURL().toString();
		String urlBase = requestURL.substring(0, requestURL.indexOf(req.getRequestURI())) + SEND_QR_URL;
		if (!referer.startsWith(urlBase) || referer.equals(urlBase)) {
			//req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String gplusEventUrl = "https://plus.google.com/events/" + referer.substring(urlBase.length());

		final ActiveEvent activeEvent;
		try {
			activeEvent = ActiveEvent.get(gplusEventUrl, spreadsheetManager);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
		/*
		 * TODO list participants
		 */
	}
}
