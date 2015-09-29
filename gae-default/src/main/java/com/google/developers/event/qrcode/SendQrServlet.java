package com.google.developers.event.qrcode;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.api.SpreadsheetManager;
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
public class SendQrServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(SendQrServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public SendQrServlet(HttpTransport transport, JsonFactory jsonFactory,
						 SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.getRequestDispatcher("/send-qr/index.html").forward(req, resp);
	}
}
