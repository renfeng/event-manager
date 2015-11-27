package com.google.developers.event.checkin;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.MetaSpreadsheet;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.OAuth2EntryPage;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by renfeng on 6/17/15.
 */
@Singleton
public class CheckInAPI extends OAuth2EntryPage
		implements Path, MetaSpreadsheet, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(CheckInAPI.class);

	@Inject
	public CheckInAPI(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doPost(final HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		// Get the stored credentials using the Authorization Flow
//		GoogleAuthorizationCodeFlow authFlow = initializeFlow();
//		Credential credential = authFlow.loadCredential(getUserId(req));
		Credential credential = getCredential();

		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, CHECK_IN_PAGE_URL);
			if (activeEvent == null) {
				//req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
				resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		/*
		 * retrieve the urls of register and check-in for the event
		 */

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

		final String uuid = req.getParameter("uuid");
		final ThreadLocal<String> emailThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> numberThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> errorThreadLocal = new ThreadLocal<>();

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			int number = 1;
			CellEntry checkInCell;
			CellEntry clientIpCell;

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				if (CHECK_IN_COLUMN.equals(columnName)) {
					checkInCell = cell;
				} else if (CLIENT_IP_COLUMN.equals(columnName)) {
					clientIpCell = cell;
				}
			}

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				String qrCode = valueMap.get(QR_CODE_COLUMN);
				if (qrCode != null && qrCode.equals(uuid)) {
					if (valueMap.containsKey(CHECK_IN_COLUMN) && valueMap.containsKey(CLIENT_IP_COLUMN)) {
						errorThreadLocal.set("Already checked in.");
						return false;
					}

					emailThreadLocal.set(valueMap.get(registerEmailColumn));
					nameThreadLocal.set(valueMap.get(registerNameColumn));
					numberThreadLocal.set(String.format("%03d", number));

					String timestampDateFormat = activeEvent.getDateFormat();
					String timestampDateFormatLocale = activeEvent.getLocale();
					String timestampTimezone = activeEvent.getTimezone();

					DateFormat dateFormat;
					if (timestampDateFormatLocale != null) {
						dateFormat = new SimpleDateFormat(timestampDateFormat,
								Locale.forLanguageTag(timestampDateFormatLocale));
					} else {
						dateFormat = new SimpleDateFormat(timestampDateFormat);
					}
					if (timestampTimezone != null) {
						/*
						 * How to set time zone of a java.util.Date? - Stack Overflow
						 * http://stackoverflow.com/a/2891412/333033
						 */
						dateFormat.setTimeZone(TimeZone.getTimeZone(timestampTimezone));
					}

					updateCell(checkInCell, dateFormat.format(new Date()));
					updateCell(clientIpCell, req.getRemoteAddr());

					return false;
				}

				number++;

				return true;
			}
		};
		try {
			boolean isSuccess = cellFeedProcessor.processForUpdate(
					spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn,
					QR_CODE_COLUMN, CHECK_IN_COLUMN, CLIENT_IP_COLUMN);
			if (isSuccess) {
				if (errorThreadLocal.get() == null) {
					if (emailThreadLocal.get() == null) {
						errorThreadLocal.set("Invalid QR code");
					}
				}
			} else {
				errorThreadLocal.set("Check-in failed");
			}
		} catch (ServiceException e) {
			errorThreadLocal.set(e.getResponseBody());
		}

		/*
		 * headers must be set before body
		 *
		 * java - Response encoding of Google App Engine (can not change response encoding) - Stack Overflow
		 * http://stackoverflow.com/a/9447068/333033
		 */
		resp.setContentType("text/javascript; charset=utf-8");

		/*
		 * return nick name for label printer
		 */
		String name = nameThreadLocal.get();
		String number = numberThreadLocal.get();
		String error = errorThreadLocal.get();

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("number");
		generator.writeString(number);
		generator.writeFieldName("name");
		generator.writeString(name);
		generator.writeFieldName("error");
		generator.writeString(error);
		generator.writeEndObject();
		generator.flush();
	}
}
