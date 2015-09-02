package com.google.developers.event.http;

import com.google.api.client.http.*;
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
import java.io.IOException;
import java.net.URLEncoder;

/**
 * Created by renfeng on 6/22/15.
 */
@Singleton
public class LogoServlet extends HttpServlet implements Path {

	private static final Logger logger = LoggerFactory
			.getLogger(LogoServlet.class);

	private final HttpTransport transport;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public LogoServlet(HttpTransport transport, SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
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
		String urlBase = requestURL.substring(0, requestURL.indexOf(req.getRequestURI())) + CHECK_IN_URL;
		if (!referer.startsWith(urlBase) || referer.equals(urlBase)) {
			req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
			return;
		}

		String gplusEventUrl = "https://plus.google.com/events/" + referer.substring(urlBase.length());

		ActiveEvent activeEvent;
		try {
			activeEvent = ActiveEvent.get(gplusEventUrl, spreadsheetManager);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		HttpRequestFactory factory = transport.createRequestFactory();

		String logo = activeEvent.getLogo();
		GenericUrl url = new GenericUrl(
				"https://googledrive.com/host/0B8bvxFOa9pJlfkVwbVlnWDF3TzJxdzJJZDMySzAwQzhyVmozMHRYSVBaX1NCMHpYd25jYnc/"
						+ URLEncoder.encode(logo, "UTF-8"));
		HttpRequest request = factory.buildGetRequest(url);

		HttpResponse response = request.execute();
		if (response.getStatusCode() == 200) {
//			resp.addHeader("Content-Type", "text/javascript");
			IOUtils.copy(response.getContent(), resp.getOutputStream());
		} else {
			resp.setStatus(response.getStatusCode());
		}
	}
}
