package com.google.developers.event.http;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.MetaSpreadsheet;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by renfeng on 6/17/15.
 */
@Singleton
public class CheckInServlet extends HttpServlet
		implements Path, MetaSpreadsheet, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(CheckInServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public CheckInServlet(HttpTransport transport, JsonFactory jsonFactory,
						  SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.getRequestDispatcher("/check-in/index.html").forward(req, resp);
	}

	@Override
	protected void doPost(final HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

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

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();
		final List<CellEntry> entries = batchRequest.getEntries();

		final String uuid = req.getParameter("uuid");
		final ThreadLocal<String> emailThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> numberThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> errorThreadLocal = new ThreadLocal<>();

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

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

					updateCell(entries, checkInCell, dateFormat.format(new Date()));
					updateCell(entries, clientIpCell, req.getRemoteAddr());

					return false;
				}

				number++;

				return true;
			}

			private void updateCell(List<CellEntry> entries, CellEntry cellEntry, String value) {

				String inputValue = cellEntry.getCell().getInputValue();
				if (SpreadsheetManager.diff(inputValue, value)) {
					CellEntry batchEntry = new CellEntry(cellEntry);
					batchEntry.changeInputValueLocal(value);

					BatchUtils.setBatchId(batchEntry, batchEntry.getId());
					BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.UPDATE);

					entries.add(batchEntry);
				}
			}
		};
		try {
			cellFeedProcessor.processForBatchUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn,
					QR_CODE_COLUMN, CHECK_IN_COLUMN, CLIENT_IP_COLUMN);

			if (errorThreadLocal.get() == null) {
				if (emailThreadLocal.get() != null) {
					WorksheetEntry sheet = spreadsheetManager.getWorksheet(registerURL);

					/*
					 * batchLink will be null for list feed
					 */
					URL cellFeedUrl = sheet.getCellFeedUrl();
					SpreadsheetService ssSvc = spreadsheetManager.getService();
					CellFeed cellFeed = ssSvc.getFeed(cellFeedUrl, CellFeed.class);
					Link batchLink = cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);
					CellFeed batchResponse = ssSvc.batch(new URL(batchLink.getHref()), batchRequest);

					// Check the results
					boolean isSuccess = true;
					for (CellEntry entry : batchResponse.getEntries()) {
						String batchId = BatchUtils.getBatchId(entry);
						if (!BatchUtils.isSuccess(entry)) {
							isSuccess = false;
							BatchStatus status = BatchUtils.getBatchStatus(entry);
							logger.debug("{} failed ({}) {}", batchId, status.getReason(), status.getContent());
							break;
						}
					}

					logger.debug(isSuccess ? "Batch operations successful." : "Batch operations failed");
					logger.debug("{} ms elapsed", System.currentTimeMillis() - startTime);

					if (!isSuccess) {
						errorThreadLocal.set("Check-in failed");
					}
				} else {
					errorThreadLocal.set("Invalid QR code");
				}
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
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
