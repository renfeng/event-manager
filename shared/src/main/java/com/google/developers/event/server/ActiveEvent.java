package com.google.developers.event.server;

import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.developers.event.server.google.CellFeedProcessor;
import com.google.developers.event.server.google.ContactManager;
import com.google.developers.event.server.google.SpreadsheetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Created by +FrankR on 6/22/15.
 */
//@Singleton
public class ActiveEvent {

	private static final Logger logger = LoggerFactory
			.getLogger(ActiveEvent.class);

	private final SpreadsheetManager spreadsheetManager;

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

	@Inject
	public ActiveEvent(SpreadsheetManager spreadsheetManager) {
		this.spreadsheetManager = spreadsheetManager;
	}

	public void refresh(final Date eventDate) throws IOException, ServiceException {

		if (eventDate == null) {
			throw new IllegalArgumentException("missing eventDate");
		}

		if (getRegisterCutoffDate() != null &&
				getCheckInCutoffDate() != null &&
				getRegisterCutoffDate().before(eventDate) &&
				getCheckInCutoffDate().after(eventDate)) {
			return;
		}

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

			private final Map<String, Map<String, String>> map = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
					throws IOException, ServiceException {

				String event = valueMap.get("Group");
				Map<String, String> lastValueMap = map.get(event);
				if (lastValueMap != null) {
					if (isRegisterClosed(valueMap) && isCheckinOpen(lastValueMap)) {
						logger.info("Event: " + event);
						setEvent(event);
						return false;
					} else if (isRegisterClosed(lastValueMap) && isCheckinOpen(valueMap)) {
						logger.info("Event: " + event);
						setEvent(event);
						return false;
					}
				} else {
					map.put(event, valueMap);
				}

				return true;
			}

			private boolean isRegisterClosed(Map<String, String> valueMap) {

				boolean result = false;

				String activity = valueMap.get("Activity");
				if (activity != null) {
					Matcher matcher = ContactManager.ACTIVITY_PATTERN.matcher(activity);
					if (matcher.matches()) {
						String activityType = matcher.group(2);
						if ("Register".equals(activityType)) {
							Date date = getDate(valueMap);
							if (date != null && date.before(eventDate)) {
								setRegisterResponsesURL(valueMap.get("URL"));
								setRegisterEmailColumn(valueMap.get("emailAddress"));
								setRegisterNameColumn(valueMap.get("nickname"));

								setRegisterCutoffDate(date);
								result = true;
							}
						}
					}
				}

				return result;
			}

			private boolean isCheckinOpen(Map<String, String> valueMap) {

				boolean result = false;

				String activity = valueMap.get("Activity");
				if (activity != null) {
					Matcher matcher = ContactManager.ACTIVITY_PATTERN.matcher(activity);
					if (matcher.matches()) {
						String activityType = matcher.group(2);
						if ("Check-in".equals(activityType)) {
							Date date = getDate(valueMap);
							if (date != null && date.after(eventDate)) {
								setCheckInResponsesURL(valueMap.get("URL"));
								setCheckInEmailColumn(valueMap.get("emailAddress"));
								setCheckInTimestampColumn(valueMap.get("timestamp"));

								setCheckInFormURL(valueMap.get("formResponse"));
								setCheckInEmailEntry(valueMap.get("emailEntry"));
								setCheckInClientIp(valueMap.get("clientIp"));

								setLabel(valueMap.get("Label"));
								setLogo(valueMap.get("Logo"));

								setCheckInCutoffDate(date);
								result = true;
							}
						}
					}
				}

				return result;
			}

			private Date getDate(Map<String, String> valueMap) {
				Date date = null;
				try {
					String dateString = valueMap.get("Date");

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
				"https://docs.google.com/spreadsheets/d/1heiZJfKi3LmXy-Mg13nSSdhthwIZxOZ32m3tJuKhKI4/edit#gid=0",
				"Group", "Date", "Activity", "URL", "emailAddress", "nickname",
				"timestamp", "timestamp.dateFormat", "timestamp.dateFormat.locale", "timestamp.timeZone",
				"formResponse", "emailEntry", "clientIp", "Label", "Logo");
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
