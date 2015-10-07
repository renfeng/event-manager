package com.google.developers.event.qrcode;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.DriveManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.Path;
import com.google.gdata.data.spreadsheet.CellEntry;
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
import java.net.URL;
import java.util.Map;

/**
 * Created by frren on 2015-09-29.
 */
@Singleton
public class ParticipantsServlet extends HttpServlet
		implements Path, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(ParticipantsServlet.class);

	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;
	private final DriveManager driveManager;

	@Inject
	public ParticipantsServlet(
			JsonFactory jsonFactory, SpreadsheetManager spreadsheetManager, DriveManager driveManager) {
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
		this.driveManager = driveManager;
	}

	@Override
	protected void doGet(final HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, SEND_QR_URL);
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

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				generator.writeStartObject();

				generator.writeFieldName("nick");
				generator.writeString(valueMap.get(registerNameColumn));

				generator.writeFieldName("email");
				generator.writeString(valueMap.get(registerEmailColumn));

				generator.writeFieldName("qrCode");
				generator.writeString(valueMap.get(QR_CODE_COLUMN));

				generator.writeEndObject();

				return true;
			}
		};
		try {
			cellFeedProcessor.processForBatchUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		generator.writeEndArray();
		generator.writeEndObject();
		generator.flush();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		final String email = req.getParameter("email");
		final String qrCode = req.getParameter("qrCode");

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, SEND_QR_URL);
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

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

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
			cellFeedProcessor.processForBatchUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}
}
