package com.google.developers.event.http;

import com.google.api.server.spi.SystemServiceServlet;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.campaign.CampaignAPI;
import com.google.developers.event.campaign.CampaignPage;
import com.google.developers.event.checkin.CheckInAPI;
import com.google.developers.event.checkin.CheckInPage;
import com.google.developers.event.checkin.LabelAPI;
import com.google.developers.event.checkin.LogoAPI;
import com.google.developers.event.eventbrite.EventBriteAPI;
import com.google.developers.event.eventbrite.EventBritePage;
import com.google.developers.event.mail.BounceHandlerServlet;
import com.google.developers.event.mail.MailReceiverServlet;
import com.google.developers.event.qrcode.RegistrationAPI;
import com.google.developers.event.qrcode.TicketAPI;
import com.google.developers.event.qrcode.TicketPage;
import com.google.gdata.util.ServiceException;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;

public class DefaultServletModule extends ServletModule implements Path {

	@Override
	protected void configureServlets() {

		/*
		 * /api/401/;jsessionid=37fycpy88nx7
		 */
		serve("/api/401/*").with(UnauthorizedServlet.class);

		/*
		 * A servlet is a singleton, and is allowed to be registered only once.
		 */
//		serve("/api/check-in").with(CheckInServlet.class);

		serve("/api/label").with(LabelAPI.class);
		serve("/api/logo").with(LogoAPI.class);
		serve("/api/chapters").with(ChaptersAPI.class);
		serve("/api/events").with(EventsAPI.class);
		serve("/api/activities").with(ActivitiesAPI.class);
		serve("/api/participants").with(RegistrationAPI.class);
		serve("/api/user").with(UserAPI.class);

		serve("/api/check-in").with(CheckInAPI.class);
		serve("/api/ticket").with(TicketAPI.class);
		serve("/api/campaign").with(CampaignAPI.class);
		serve("/api/eventbrite").with(EventBriteAPI.class);

		serve(EVENTS_API_URL + "*").with(EventsAPI.class);

		{
			HashMap<String, String> params = new HashMap<>();

			/*
			 * comma delimited fqn of classes
			 */
			params.put("services", HelloWorldEndpoints.class.getCanonicalName());

			serve("/_ah/spi/*").with(SystemServiceServlet.class, params);
			bind(SystemServiceServlet.class).in(Singleton.class);
		}

		serve("/_ah/mail/*").with(MailReceiverServlet.class);
		serve("/_ah/bounce").with(BounceHandlerServlet.class);

		serve(OAUTH2ENTRY_PAGE_URL).with(OAuth2EntryPage.class);
		serve(OAUTH2CALLBACK_PAGE_URL).with(OAuth2CallbackPage.class);
		serve(OAUTH2REVOKE_PAGE_URL).with(OAuth2RevokePage.class);

		serveRegex(CHECK_IN_PAGE_URL + "[0-9a-z]+").with(CheckInPage.class);
		serveRegex(TICKET_PAGE_URL + "[0-9a-z]+").with(TicketPage.class);
		serveRegex(CAMPAIGN_PAGE_URL + "[0-9a-z]+").with(CampaignPage.class);
		serveRegex(EVENT_BRITE_PAGE_URL + "[0-9a-z]+").with(EventBritePage.class);
	}

	public static ActiveEvent getActiveEvent(
			HttpServletRequest req, SpreadsheetManager spreadsheetManager,
			String path) throws IOException, ServiceException {

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
