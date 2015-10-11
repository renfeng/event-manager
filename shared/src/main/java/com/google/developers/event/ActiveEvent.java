package com.google.developers.event;

import com.google.api.client.http.*;
import com.google.api.services.drive.model.File;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.DriveManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.gdata.util.ServiceException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by +FrankR on 6/22/15.
 */
public class ActiveEvent implements Serializable, MetaSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(ActiveEvent.class);

	private String gplusEventUrl;

	private String event;

	/*
	 * retrieve the urls of register and check-in for the latest event
	 */
	private Date registerCutoffDate;
	private String registerResponsesURL;
	private String registerEmailColumn;
	private String registerNameColumn;

	private String ticketEmailTemplate;
	private String ticketEmailSubject;
	private String ticketEmailCc;
	private String ticketEmailBcc;
	private Date eventStartTime;
	private Date eventEndTime;
	private String eventLocation;
	private String eventTransit;
	private String eventPointOfContact;

	private Date checkInCutoffDate;

	/*
	 * for label printer
	 */
	private String label;

	/*
	 * displayed on self check-in page
	 */
	private String logo;

	private String dateFormat;
	private String locale;
	private String timezone;

	/*
	 * cache
	 */
	private String templateCache;
	private byte[] logoCache;

	public static ActiveEvent get(String gplusEventUrl, SpreadsheetManager spreadsheetManager)
			throws IOException, ServiceException {

		MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
		ActiveEvent activeEvent = (ActiveEvent) syncCache.get(gplusEventUrl);
		if (activeEvent == null) {
			activeEvent = new ActiveEvent(gplusEventUrl, spreadsheetManager);
			if (activeEvent.getGplusEventUrl() != null) {
				syncCache.put(gplusEventUrl, activeEvent);
			} else {
				activeEvent = null;
			}
		}

		return activeEvent;
	}

	public ActiveEvent(final String gplusEventUrl, SpreadsheetManager spreadsheetManager)
			throws IOException, ServiceException {

		if (gplusEventUrl == null) {
			throw new IllegalArgumentException("missing gplusEventUrl");
		}

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				if (gplusEventUrl.equals(valueMap.get(GPLUS_EVENT_COLUMN))) {
					setEvent(valueMap.get(GROUP_COLUMN));
					setRegisterCutoffDate(getDate(valueMap.get(REGISTER_CUTOFF_DATE_COLUMN)));
					setRegisterResponsesURL(valueMap.get(REGISTER_FORM_RESPONSE_SPREADSHEET_URL_COLUMN));
					setRegisterEmailColumn(valueMap.get(EMAIL_ADDRESS_COLUMN));
					setRegisterNameColumn(valueMap.get(NICKNAME_COLUMN));

					setCheckInCutoffDate(getDate(valueMap.get(CHECK_IN_CUTOFF_DATE_COLUMN)));

					setTicketEmailTemplate(valueMap.get(TICKET_EMAIL_TEMPLATE));
					setTicketEmailSubject(valueMap.get(TICKET_EMAIL_SUBJECT));
					setTicketEmailCc(valueMap.get(TICKET_EMAIL_CC));
					setTicketEmailBcc(valueMap.get(TICKET_EMAIL_BCC));
					setEventStartTime(getDate(valueMap.get(EVENT_START_TIME)));
					setEventEndTime(getDate(valueMap.get(EVENT_END_TIME)));
					setEventLocation(valueMap.get(EVENT_LOCATION));
					setEventTransit(valueMap.get(EVENT_TRANSIT));
					setEventPointOfContact(valueMap.get(EVENT_POINST_OF_CONTACT));

					setLabel(valueMap.get(LABEL_COLUMN));
					setLogo(valueMap.get(LOGO_COLUMN));

					setDateFormat(valueMap.get(TIMESTAMP_DATE_FORMAT_COLUMN));
					setLocale(valueMap.get(TIMESTAMP_DATE_FORMAT_LOCALE_COLUMN));
					setTimezone(valueMap.get(TIMESTAMP_TIME_ZONE_COLUMN));

					ActiveEvent.this.gplusEventUrl = gplusEventUrl;

					return false;
				}

				return true;
			}

			private Date getDate(String dateString) {
				Date date = null;
				try {
					String timestampDateFormat = getDateFormat();
					String timestampDateFormatLocale = getLocale();
					String timestampTimezone = getTimezone();

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
		cellFeedProcessor.processForBatchUpdate(
				spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
				GROUP_COLUMN, REGISTER_CUTOFF_DATE_COLUMN, CHECK_IN_CUTOFF_DATE_COLUMN,
				REGISTER_FORM_RESPONSE_SPREADSHEET_URL_COLUMN, EMAIL_ADDRESS_COLUMN, NICKNAME_COLUMN,
				TIMESTAMP_COLUMN, TIMESTAMP_DATE_FORMAT_COLUMN, TIMESTAMP_DATE_FORMAT_LOCALE_COLUMN,
				TIMESTAMP_TIME_ZONE_COLUMN, LABEL_COLUMN, LOGO_COLUMN, GPLUS_EVENT_COLUMN,
				TICKET_EMAIL_TEMPLATE, TICKET_EMAIL_SUBJECT, TICKET_EMAIL_CC, TICKET_EMAIL_BCC,
				EVENT_START_TIME, EVENT_END_TIME, EVENT_LOCATION, EVENT_POINST_OF_CONTACT);
	}

	public String getRegisterResponsesURL() {
		return registerResponsesURL;
	}

	public void setRegisterResponsesURL(String registerResponsesURL) {
		this.registerResponsesURL = registerResponsesURL;
	}

	public String getRegisterEmailColumn() {
		return registerEmailColumn;
	}

	public void setRegisterEmailColumn(String registerEmailColumn) {
		this.registerEmailColumn = registerEmailColumn;
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

	public String getRegisterNameColumn() {
		return registerNameColumn;
	}

	public void setRegisterNameColumn(String registerNameColumn) {
		this.registerNameColumn = registerNameColumn;
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getTicketEmailTemplate() {
		return ticketEmailTemplate;
	}

	public void setTicketEmailTemplate(String ticketEmailTemplate) {
		this.ticketEmailTemplate = ticketEmailTemplate;
	}

	public String getTicketEmailSubject() {
		return ticketEmailSubject;
	}

	public void setTicketEmailSubject(String ticketEmailSubject) {
		this.ticketEmailSubject = ticketEmailSubject;
	}

	public String getTicketEmailCc() {
		return ticketEmailCc;
	}

	public void setTicketEmailCc(String ticketEmailCc) {
		this.ticketEmailCc = ticketEmailCc;
	}

	public String getTicketEmailBcc() {
		return ticketEmailBcc;
	}

	public void setTicketEmailBcc(String ticketEmailBcc) {
		this.ticketEmailBcc = ticketEmailBcc;
	}

	public Date getEventStartTime() {
		return eventStartTime;
	}

	public void setEventStartTime(Date eventStartTime) {
		this.eventStartTime = eventStartTime;
	}

	public Date getEventEndTime() {
		return eventEndTime;
	}

	public void setEventEndTime(Date eventEndTime) {
		this.eventEndTime = eventEndTime;
	}

	public String getEventLocation() {
		return eventLocation;
	}

	public void setEventLocation(String eventLocation) {
		this.eventLocation = eventLocation;
	}

	public String getEventTransit() {
		return eventTransit;
	}

	public void setEventTransit(String eventTransit) {
		this.eventTransit = eventTransit;
	}

	public String getEventPointOfContact() {
		return eventPointOfContact;
	}

	public void setEventPointOfContact(String eventPointOfContact) {
		this.eventPointOfContact = eventPointOfContact;
	}

	public String getTemplateCache(DriveManager driveManager, HttpTransport transport) throws IOException {

		if (templateCache == null) {
			String templateURL = getTicketEmailTemplate();
			File file = driveManager.getClient().files().get(templateURL).execute();
			String downloadUrl = file.getExportLinks().get("text/plain");
			System.out.println(downloadUrl);

			HttpRequestFactory factory = transport.createRequestFactory();
			HttpRequest request = factory.buildGetRequest(new GenericUrl(downloadUrl));
			HttpResponse response = request.execute();

			logger.debug("default character set: {}", Charset.defaultCharset());
			templateCache = IOUtils.toString(response.getContent(), "UTF-8");
			updateCache();
		}

		return templateCache;
	}

	public void updateCache() {
		MemcacheService syncCache = MemcacheServiceFactory.getMemcacheService();
		if (this.getGplusEventUrl() != null) {
			syncCache.put(this.getGplusEventUrl(), this);
		}
	}

	public byte[] getLogoCache(DriveManager driveManager) throws IOException {

		if (logoCache == null) {
			logoCache = IOUtils.toByteArray(driveManager.getClient().files().get(getLogo())
					.executeMediaAsInputStream());
			updateCache();
		}

		return logoCache;
	}

	public String getGplusEventUrl() {
		return gplusEventUrl;
	}

	public void setGplusEventUrl(String gplusEventUrl) {
		this.gplusEventUrl = gplusEventUrl;
	}
}