package com.google.developers.event.http;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
public class EventsServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = -6167173608588448196L;

	private final HttpRequestFactory factory;
	private final JsonFactory jsonFactory;

	@Inject
	public EventsServlet(HttpTransport transport, JsonFactory jsonFactory) {
		factory = transport.createRequestFactory();
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String jsonp = req.getParameter("callback");

		if (jsonp == null) {
			resp.setContentType("application/json");
		} else {
			resp.setContentType("text/javascript");
			resp.getWriter().print(jsonp + "(");
		}

		String uri = req.getRequestURI();
		String eventId = uri.substring(uri
				.indexOf(DefaultServletModule.EVENTS_URL)
				+ DefaultServletModule.EVENTS_URL.length());

		/*
		 * https://developers.google.com/events/5099753932062720/
		 */
		GenericUrl url = new GenericUrl(
				"https://developers.google.com/events/" + eventId);
		HttpRequest request = factory.buildGetRequest(url);
		HttpResponse response = request.execute();
		String html = IOUtils.toString(response.getContent());

		Document document = Jsoup.parse(html);

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("attendees");
		extractAttendees(document, generator);

		/*
		 * TODO retrieve organizer from associated Google+ event
		 */
//		generator.writeFieldName("organizers");
//		extractOrganizers(document, generator);

		Elements gplusEventLinkElement = document.select("header:contains(Event URL:) + a");
		if (gplusEventLinkElement.size() > 0) {
			String gplusEventLink = gplusEventLinkElement.attr("href");
			generator.writeFieldName("gplusEvent");
			generator.writeString(gplusEventLink);
			updateWithGPlus(gplusEventLink, generator);
		}

		generator.writeEndObject();
		generator.flush();

		if (jsonp != null) {
			resp.getWriter().print(");");
		}
	}

	private void updateWithGPlus(String gplusEventLink, JsonGenerator generator) throws IOException {

		GenericUrl url = new GenericUrl(gplusEventLink);
		HttpRequest request = factory.buildGetRequest(url);
		request.getHeaders().set("accept-language", "en-US,en;");

		HttpResponse response = request.execute();
		String html = IOUtils.toString(response.getContent());

		Document document = Jsoup.parse(html);

		generator.writeFieldName("gplusGuests");
		generator.writeStartArray();

		Elements guestElements = document.select("script");
		for (Element guestElement : guestElements) {
			String script = guestElement.outerHtml();
			if (script.contains("leo")) {
				generator.writeNumber(guestElement.siblingIndex());
			}
		}

		generator.writeEndArray();
	}

	private void extractAttendees(Document document, JsonGenerator generator) throws IOException {

		generator.writeStartArray();

		Elements attendeeElements = document.select("section.attendees li");
		for (Element attendeeElement : attendeeElements) {
			String id = attendeeElement.attr("id");
			id = id.substring(id.indexOf("-") + 1);

			Elements linkElement = attendeeElement.select("a");
			String link = linkElement.attr("href");
			String name = linkElement.attr("title");

			Elements avatarElement = linkElement.select("img");
			String avatar = avatarElement.attr("src");

			generator.writeStartObject();
			generator.writeFieldName("id");
			generator.writeString(id);
			generator.writeFieldName("name");
			generator.writeString(name);
			generator.writeFieldName("link");
			generator.writeString(link);
			generator.writeFieldName("avatar");
			generator.writeString(avatar);

			generator.writeEndObject();
		}

		generator.writeEndArray();
	}

	/**
	 * @deprecated the page is accessed anonymously, and the organizers are
	 * invisible
	 */
	@SuppressWarnings("unused")
	private void extractOrganizers(Document document, JsonGenerator generator) throws IOException {

		generator.writeStartArray();

		Elements attendeeElements = document.select("section.organizers li");

		for (Element attendeeElement : attendeeElements) {
			Elements linkElement = attendeeElement.select("a");
			String link = linkElement.attr("href");
			String name = linkElement.attr("title");

			Elements avatarElement = linkElement.select("img");
			String avatar = avatarElement.attr("src");

			generator.writeStartObject();
			generator.writeFieldName("name");
			generator.writeString(name);
			generator.writeFieldName("link");
			generator.writeString(link);
			generator.writeFieldName("avatar");
			generator.writeString(avatar);

			generator.writeEndObject();
		}

		generator.writeEndArray();
	}

}
