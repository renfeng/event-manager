package com.google.developers.event.http;

import com.google.developers.api.DriveManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.qrcode.ParticipantsServlet;
import com.google.developers.event.qrcode.SendQrServlet;
import com.google.gdata.util.ServiceException;
import com.google.inject.servlet.ServletModule;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class DefaultServletModule extends ServletModule implements Path {

	@Override
	protected void configureServlets() {

		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
		serve("/api/401/*").with(UnauthorizedServlet.class);

//		serve("/api/check-in").with(CheckInServlet.class);
		serve("/api/label").with(LabelServlet.class);
		serve("/api/logo").with(LogoServlet.class);
		serve("/api/chapters").with(ChaptersServlet.class);
		serve("/api/events").with(EventsServlet.class);
		serve("/api/activities").with(ActivitiesServlet.class);
		serve("/api/participants").with(ParticipantsServlet.class);

		serve(OAUTH2ENTRY).with(OAuth2EntryServlet.class);
		serve(OAUTH2CALLBACK).with(OAuth2CallbackServlet.class);
		serve(OAUTH2REVOKE).with(OAuth2RevokeServlet.class);

		serve(EVENTS_URL + "*").with(EventsServlet.class);

		serveRegex("/api/check-in|" + CHECK_IN_URL + "[0-9a-z]+").with(CheckInServlet.class);
		serveRegex("/api/send-qr|" + SEND_QR_URL + "[0-9a-z]+").with(SendQrServlet.class);
	}

	public static ActiveEvent getActiveEvent(
			HttpServletRequest req, SpreadsheetManager spreadsheetManager,
			String path)
			throws IOException, ServiceException {

		/*
		 * TODO https://github.com/google/guice/wiki/AssistedInject
		 */

		/*
		 * retrieve event id from http header, referer
		 * e.g.
		 * https://plus.google.com/events/c2vl1u3p3pbglde0gqhs7snv098
		 * https://developers.google.com/events/6031536915218432/
		 * https://hub.gdgx.io/events/6031536915218432
		 */
		String referer = req.getHeader("Referer");
		if (referer == null) {
			return null;
		}

//		Pattern gplusEventPattern = Pattern.compile("https://plus.google.com/events/" +
//				"[^/]+");
//		Pattern devsiteEventPattern = Pattern.compile("https://developers.google.com/events/" +
//				"[^/]+/");
//		Pattern gdgxHubEventPattern = Pattern.compile("https://hub.gdgx.io/events/" +
//				"([^/]+)");
		String requestURL = req.getRequestURL().toString();
		String urlBase = requestURL.substring(0, requestURL.indexOf(req.getRequestURI())) + path;
		if (!referer.startsWith(urlBase) || referer.equals(urlBase)) {
			return null;
		}

		String gplusEventUrl = "https://plus.google.com/events/" + referer.substring(urlBase.length());

		return ActiveEvent.get(gplusEventUrl, spreadsheetManager);
	}
}
