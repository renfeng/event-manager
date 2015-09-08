package com.google.developers.event;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.gdata.util.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by +FrankR on 6/22/15.
 */
public class ActiveEvent implements Serializable {

	private static final Logger logger = LoggerFactory
			.getLogger(ActiveEvent.class);

	public static final String GROUP_COLUMN = "Google Contact Group";
	public static final String REGISTER_CUTOFF_DATE_COLUMN = "Register Cutoff Date";
	public static final String CHECK_IN_CUTOFF_DATE_COLUMN = "Check-in Cutoff Date";
	public static final String GPLUS_EVENT_COLUMN = "Google+ Event";
	public static final String REGISTER_FORM_RESPONSE_SPREADSHEET_URL_COLUMN = "Register Form Response Spreadsheet URL";
	public static final String EMAIL_ADDRESS_COLUMN = "emailAddress";
	public static final String NICKNAME_COLUMN = "nickname";
	public static final String FEEDBACK_FORM_RESPONSE_SPREADSHEET_URL_COLUMN = "Feedback Form Response Spreadsheet URL";

	/*
	 * retrieve the urls of register and check-in for the latest event
	 */
	private String registerResponsesURL;
	private String checkInResponsesURL;
	private String registerEmailColumn;
	private String registerNameColumn;
	private String checkInEmailColumn;
	private String checkInTimestampColumn;
	private String checkInFormURL;
	private String checkInEmailEntry;
	private String checkInClientIp;

	/*
	 * for label printer
	 */
	private String label;

	/*
	 * displayed on self check-in page
	 */
	private String logo;

	private Date registerCutoffDate;
	private Date checkInCutoffDate;

	private String event;

	public static ActiveEvent get(String gplusEventUrl, SpreadsheetManager spreadsheetManager)
			throws IOException, ServiceException {

		MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
		ActiveEvent activeEvent = (ActiveEvent) syncCache.get(gplusEventUrl);
		if (activeEvent == null) {
			activeEvent = new ActiveEvent(gplusEventUrl, spreadsheetManager);
			syncCache.put(gplusEventUrl, activeEvent);
		}

		return activeEvent;
	}

	public ActiveEvent(final String gplusEventUrl, SpreadsheetManager spreadsheetManager)
			throws IOException, ServiceException {

		if (gplusEventUrl == null) {
			throw new IllegalArgumentException("missing gplusEventUrl");
		}

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				if (gplusEventUrl.equals(valueMap.get(GPLUS_EVENT_COLUMN))) {
					setRegisterResponsesURL(valueMap.get(REGISTER_FORM_RESPONSE_SPREADSHEET_URL_COLUMN));
					setRegisterEmailColumn(valueMap.get(EMAIL_ADDRESS_COLUMN));
					setRegisterNameColumn(valueMap.get(NICKNAME_COLUMN));
					setRegisterCutoffDate(getDate("Register Cutoff Date", valueMap));
					setLabel(valueMap.get("Label"));
					setLogo(valueMap.get("Logo"));
					setCheckInCutoffDate(getDate("Check-in Cutoff Date", valueMap));
					return false;
				}

				return true;
			}

			private Date getDate(String column, Map<String, String> valueMap) {
				Date date = null;
				try {
					String dateString = valueMap.get(column);

					String timestampDateFormat = valueMap.get("timestamp.dateFormat");
					String timestampDateFormatLocale = valueMap.get("timestamp.dateFormat.locale");
					String timestampTimezone = valueMap.get("timestamp.timeZone");

					if (dateString != null && timestampDateFormat != null) {
						/*
						 * http://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
						 */
						final DateFormat dateFormat;
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
						date = dateFormat.parse(dateString);
					}
				} catch (ParseException e) {
					logger.error("failed to parse cutoff date", e);
				}
				return date;
			}
		};
		cellFeedProcessor.process(
				spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
				GROUP_COLUMN, REGISTER_CUTOFF_DATE_COLUMN, CHECK_IN_CUTOFF_DATE_COLUMN,
				REGISTER_FORM_RESPONSE_SPREADSHEET_URL_COLUMN, EMAIL_ADDRESS_COLUMN, NICKNAME_COLUMN,
				"timestamp", "timestamp.dateFormat", "timestamp.dateFormat.locale", "timestamp.timeZone",
				"Label", "Logo", GPLUS_EVENT_COLUMN);
	}

	public String getRegisterResponsesURL() {
		return registerResponsesURL;
	}

	public void setRegisterResponsesURL(String registerResponsesURL) {
		this.registerResponsesURL = registerResponsesURL;
	}

	public String getCheckInResponsesURL() {
		return checkInResponsesURL;
	}

	public void setCheckInResponsesURL(String checkInResponsesURL) {
		this.checkInResponsesURL = checkInResponsesURL;
	}

	public String getRegisterEmailColumn() {
		return registerEmailColumn;
	}

	public void setRegisterEmailColumn(String registerEmailColumn) {
		this.registerEmailColumn = registerEmailColumn;
	}

	public String getCheckInEmailColumn() {
		return checkInEmailColumn;
	}

	public void setCheckInEmailColumn(String checkInEmailColumn) {
		this.checkInEmailColumn = checkInEmailColumn;
	}

	public String getCheckInFormURL() {
		return checkInFormURL;
	}

	public void setCheckInFormURL(String checkInFormURL) {
		this.checkInFormURL = checkInFormURL;
	}

	public String getCheckInEmailEntry() {
		return checkInEmailEntry;
	}

	public void setCheckInEmailEntry(String checkInEmailEntry) {
		this.checkInEmailEntry = checkInEmailEntry;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public Date getRegisterCutoffDate() {
		return registerCutoffDate;
	}

	public void setRegisterCutoffDate(Date registerCutoffDate) {
		this.registerCutoffDate = registerCutoffDate;
	}

	public Date getCheckInCutoffDate() {
		return checkInCutoffDate;
	}

	public void setCheckInCutoffDate(Date checkInCutoffDate) {
		this.checkInCutoffDate = checkInCutoffDate;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getCheckInClientIp() {
		return checkInClientIp;
	}

	public void setCheckInClientIp(String checkInClientIp) {
		this.checkInClientIp = checkInClientIp;
	}

	public String getCheckInTimestampColumn() {
		return checkInTimestampColumn;
	}

	public void setCheckInTimestampColumn(String checkInTimestampColumn) {
		this.checkInTimestampColumn = checkInTimestampColumn;
	}

	public String getRegisterNameColumn() {
		return registerNameColumn;
	}

	public void setRegisterNameColumn(String registerNameColumn) {
		this.registerNameColumn = registerNameColumn;
	}
}
