package com.google.developers.event.qrcode;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.OAuth2EntryServlet;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by frren on 2015-09-29.
 */
@Singleton
public class RegistrationServlet extends OAuth2EntryServlet
		implements Path, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(RegistrationServlet.class);

	@Inject
	public RegistrationServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doGet(final HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		SpreadsheetManager spreadsheetManager = SpreadsheetManager.getGlobalInstance(transport, jsonFactory);

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, TICKET_URL);
			if (activeEvent == null) {
				throw new ServletException("missing active event");
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		String registerURL = activeEvent.getRegisterResponsesURL();
		if (registerURL == null) {
			throw new ServletException(
					"Missing URL to the register form responses of event, " + activeEvent.getEvent());
		}

		final String registerEmailColumn = activeEvent.getRegisterEmailColumn();
		if (registerEmailColumn == null) {
			throw new ServletException(
					"Missing emailAddress column mapping for the register form responses of event, " +
							activeEvent.getEvent());
		}

		final String registerNameColumn = activeEvent.getRegisterNameColumn();
		if (registerNameColumn == null) {
			throw new ServletException(
					"Missing nickname column mapping for the register form responses of event, " +
							activeEvent.getEvent());
		}

		/*
		 * TODO list participants
		 */

		/*
		 * headers must be set before body
		 *
		 * java - Response encoding of Google App Engine (can not change response encoding) - Stack Overflow
		 * http://stackoverflow.com/a/9447068/333033
		 */
		resp.setContentType("text/javascript; charset=utf-8");

		final JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("participants");
		generator.writeStartArray();

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			List<String> emailList = new ArrayList<>();

			CellEntry qrCodeCellEntry;

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				if (QR_CODE_COLUMN.equals(columnName)) {
					qrCodeCellEntry = cell;
				}
			}

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				String nick = valueMap.get(registerNameColumn);
				String email = valueMap.get(registerEmailColumn);
				String qrCode = valueMap.get(QR_CODE_COLUMN);

				if (emailList.contains(email)) {
					if (qrCode == null) {
						qrCodeCellEntry.changeInputValueLocal("dupe");
						qrCodeCellEntry.update();
					}
					return true;
				}
				emailList.add(email);

				generator.writeStartObject();

				generator.writeFieldName("nick");
				generator.writeString(nick);

				generator.writeFieldName("email");
				generator.writeString(email);

				generator.writeFieldName("qrCode");
				generator.writeString(qrCode);

				generator.writeEndObject();

				return true;
			}
		};
		try {
			cellFeedProcessor.processForUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		generator.writeEndArray();
		generator.writeEndObject();
		generator.flush();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		SpreadsheetManager spreadsheetManager = SpreadsheetManager.getGlobalInstance(transport, jsonFactory);

		final String email = req.getParameter("email");
		final String qrCode = req.getParameter("qrCode");

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, TICKET_URL);
			if (activeEvent == null) {
				throw new ServletException("missing active event");
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		String registerURL = activeEvent.getRegisterResponsesURL();
		if (registerURL == null) {
			throw new ServletException(
					"Missing URL to the register form responses of event, " + activeEvent.getEvent());
		}

		final String registerEmailColumn = activeEvent.getRegisterEmailColumn();
		if (registerEmailColumn == null) {
			throw new ServletException(
					"Missing emailAddress column mapping for the register form responses of event, " +
							activeEvent.getEvent());
		}

		final String registerNameColumn = activeEvent.getRegisterNameColumn();
		if (registerNameColumn == null) {
			throw new ServletException(
					"Missing nickname column mapping for the register form responses of event, " +
							activeEvent.getEvent());
		}

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			CellEntry qrCodeCellEntry;

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				if (QR_CODE_COLUMN.equals(columnName)) {
					qrCodeCellEntry = cell;
				}
			}

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				if (email.equals(valueMap.get(registerEmailColumn))) {
					/*
					 * https://developers.google.com/google-apps/spreadsheets/data#change_contents_of_a_cell
					 */
					qrCodeCellEntry.changeInputValueLocal(qrCode);
					qrCodeCellEntry.update();

					return false;
				}

				return true;
			}
		};
		try {
			cellFeedProcessor.processForUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}
}
