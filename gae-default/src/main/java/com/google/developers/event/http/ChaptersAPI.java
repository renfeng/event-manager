package com.google.developers.event.http;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.group.Chapter;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Created by renfeng on 7/19/15.
 */
@Singleton
public class ChaptersAPI extends HttpServlet {

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
	public ChaptersAPI(HttpTransport transport, JsonFactory jsonFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String jsonp = req.getParameter("callback");

		if (jsonp == null) {
			resp.setContentType("application/json");
		} else {
			resp.setContentType("text/javascript");
			resp.getWriter().print(jsonp + "(");
		}

		List<Chapter> groups = Chapter.list(transport, jsonFactory);

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartArray();
		for (Chapter c : groups) {
			generator.writeStartObject();
			generator.writeFieldName("name");
			generator.writeString(c.getName());
			generator.writeFieldName("gplusID");
			generator.writeString(c.getGplusID());
			generator.writeEndObject();
		}

		generator.writeEndArray();
		generator.flush();

		if (jsonp != null) {
			resp.getWriter().print(");");
		}
	}
}
