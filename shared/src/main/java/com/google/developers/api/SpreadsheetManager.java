package com.google.developers.api;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.event.EventParticipant;
import com.google.developers.event.MetaSpreadsheet;
import com.google.gdata.client.spreadsheet.FeedURLFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpreadsheetManager extends ServiceManager<SpreadsheetService> implements MetaSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(SpreadsheetManager.class);

	public static boolean diff(String oldInputValue, String newInputValue) {
		return (newInputValue != null && !newInputValue.equals(oldInputValue)) ||
				(newInputValue == null && oldInputValue != null && oldInputValue.length() > 0);
	}

	@Inject
	public SpreadsheetManager(
			@Named("refreshToken") String refreshToken,
			@Named("clientId") String clientId,
			@Named("clientSecret") String clientSecret,
			HttpTransport transport, JsonFactory jsonFactory) {

		super(refreshToken, clientId, clientSecret, transport, jsonFactory);

		SpreadsheetService service = new SpreadsheetService("GDG Event Management");
		service.setProtocolVersion(SpreadsheetService.Versions.V3);

		/*
		 * Unable to process batch request for spread sheet. - Google Groups
		 * https://groups.google.com/d/msg/google-appengine/PVqNF8AumdY/gZNMJKpObowJ
		 *
		 * Timeouts and Errors | API Client Library for Java | Google Developers
		 * https://developers.google.com/api-client-library/java/google-api-java-client/errors
		 */
		service.setConnectTimeout(3 * 60000);
		service.setReadTimeout(3 * 60000);

		setService(service);
	}

	public List<EventParticipant> getGoogleGroupsMember(String spreadsheet)
			throws IOException, ServiceException {

		List<EventParticipant> participants = new ArrayList<>();

		List<ListEntry> rows = getListEntries(spreadsheet);
		for (ListEntry row : rows) {
			CustomElementCollection elements = row.getCustomElements();
			/*
			 * won't accept registration after the event begins
			 */
			String nickname = elements.getValue("nickname");
			String emailAddress = elements.getValue("emailAddress");
//			String phoneNumber = elements.getValue("");

			Date date;
			{
				Calendar calendar = Calendar.getInstance();
				calendar.set(
						Integer.parseInt(elements.getValue("joinYear")),
						Integer.parseInt(elements.getValue("joinMonth")),
						Integer.parseInt(elements.getValue("joinDay")),
						Integer.parseInt(elements.getValue("joinHour")),
						Integer.parseInt(elements.getValue("joinMinute")),
						Integer.parseInt(elements.getValue("joinSecond")));

				String timeZone = elements.getValue("timeZone");
				if ("Brunei Time".equals(timeZone)) {
					calendar.add(Calendar.HOUR_OF_DAY, 0);
				} else if ("America/Los_Angeles".equals(timeZone)) {
					calendar.add(Calendar.HOUR_OF_DAY, 15);
				}

				date = calendar.getTime();
			}

			EventParticipant participant = new EventParticipant();
			participant.setNickname(nickname);
			participant.setEmailAddress(emailAddress);
//			participant.setPhoneNumber(null);
			participant.setTimestamp(date);

			participants.add(participant);
		}

		return participants;
	}

	public List<ListEntry> getListEntries(String url) throws IOException, ServiceException {

		WorksheetEntry entry = getWorksheet(url);
		if (entry == null) {
			throw new IllegalArgumentException("invalid Spreadsheet url: " + url);
		}
		URL listFeedURL = entry.getListFeedUrl();

		/*-
		 * https://developers.google.com/google-apps/spreadsheets/#sheets_api_urls_visibilities_and_projections
		 */
		// Make a request to the API and get all spreadsheets.
		ListFeed listFeed = getService().getFeed(listFeedURL, ListFeed.class);

		return listFeed.getEntries();
	}

	public List<CellEntry> getCellEntries(String url) throws IOException, ServiceException {

		WorksheetEntry entry = getWorksheet(url);
		if (entry == null) {
			throw new IllegalArgumentException("invalid Spreadsheet url: " + url);
		}
		URL cellFeedURL = entry.getCellFeedUrl();

		/*-
		 * https://developers.google.com/google-apps/spreadsheets/#sheets_api_urls_visibilities_and_projections
		 */
		// Make a request to the API and get all spreadsheets.
		CellFeed cellFeed = getService().getFeed(cellFeedURL, CellFeed.class);

		return cellFeed.getEntries();
	}

	public List<EventParticipant> getEventParticipants(
			String spreadsheet, final Date cutoffDate, Map<String, String> valueMap)
			throws IOException, ServiceException, ParseException {

		final List<EventParticipant> participants = new ArrayList<>();

		/*
		 * converted to cell feed, see EventManager.importContactsFromSpreadsheets
		 * the benefit is no need to trim punctuations
		 */

		final String nicknameTag = valueMap.get(NICKNAME_COLUMN);
		final String emailAddressTag = valueMap.get(EMAIL_ADDRESS_COLUMN);
		final String phoneNumberTag = valueMap.get(PHONE_NUMBER_COLUMN);

		final String timestampTag = valueMap.get(TIMESTAMP_COLUMN);
		final String timestampDateFormat = valueMap.get(TIMESTAMP_DATE_FORMAT_COLUMN);
		final String timestampDateFormatLocale = valueMap.get(TIMESTAMP_DATE_FORMAT_LOCALE_COLUMN);

		if (timestampTag != null && timestampDateFormat != null) {
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
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(getService()) {
				@Override
				public boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
						throws IOException, ServiceException {
					String timestamp = valueMap.get(timestampTag);
					if (timestamp != null) {
						try {
							/*
							 * won't accept registration after the event begins
							 */
							Date date = dateFormat.parse(timestamp);
							if (cutoffDate == null || !date.after(cutoffDate)) {
								String nickname = valueMap.get(nicknameTag);
								String emailAddress = valueMap.get(emailAddressTag);
								String phoneNumber = valueMap.get(phoneNumberTag);

								EventParticipant participant = new EventParticipant();
								participant.setNickname(nickname);
								participant.setEmailAddress(emailAddress);
								participant.setPhoneNumber(phoneNumber);
								participant.setTimestamp(date);

								participants.add(participant);
							}
						} catch (ParseException ex) {
							logger.warn("error parsing date on row: " + getRow(), ex);
						}
					}
					return true;
				}
			};
			cellFeedProcessor.process(
					getWorksheet(spreadsheet), timestampTag, nicknameTag, emailAddressTag, phoneNumberTag);
		} else {
			/*
			 * cutoff date as event date
			 */
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(getService()) {
				@Override
				public boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
						throws IOException, ServiceException {
					String nickname = valueMap.get(nicknameTag);
					String emailAddress = valueMap.get(emailAddressTag);
					String phoneNumber = valueMap.get(phoneNumberTag);

					EventParticipant participant = new EventParticipant();
					participant.setNickname(nickname);
					participant.setEmailAddress(emailAddress);
					participant.setPhoneNumber(phoneNumber);
					participant.setTimestamp(cutoffDate);

					participants.add(participant);
					return true;
				}
			};
			cellFeedProcessor.process(
					getWorksheet(spreadsheet), timestampTag, nicknameTag, emailAddressTag, phoneNumberTag);
		}

		return participants;
	}

	/**
	 * @param url e.g. https://docs.google.com/spreadsheets/d/1y6RNHHA8HDZEbJ1L8QsCiilwGChN8u6ZmeJ3bJi6EyQ/edit#gid=187918259
	 * @return
	 * @throws IOException
	 * @throws ServiceException
	 */
	public WorksheetEntry getWorksheet(String url) throws IOException, ServiceException {

		WorksheetEntry entry;

		String SPREADSHEET_PATTERN = "[^/]+";
		String GRID_ID_PATTERN = "#gid=(\\d+)";
		String PREFIX_PATTERN = "https://docs.google.com/spreadsheets/d/";
		String SUFFIX_PATTERN = "/edit(?:" + GRID_ID_PATTERN + ")";
		Pattern pattern = Pattern.compile("(?:" + PREFIX_PATTERN + ")?" + "("
				+ SPREADSHEET_PATTERN + ")" + "(?:" + SUFFIX_PATTERN + ")?");
		int SPREADSHEET_GROUP = 1;
		int GRID_ID_GROUP = 2;

		Matcher matcher = pattern.matcher(url);
		if (matcher.find()) {
			String key = matcher.group(SPREADSHEET_GROUP);
			String gridId = matcher.group(GRID_ID_GROUP);
			entry = getWorksheet(key, gridId);
		} else {
			entry = null;
		}

		return entry;
	}

	public WorksheetEntry getWorksheet(String key, String gridId)
			throws IOException, ServiceException {

		WorksheetEntry entry = null;
		// Iterate through each worksheet in the spreadsheet.
		if (gridId != null) {
			List<WorksheetEntry> worksheets = listWorksheets(key);
			for (WorksheetEntry worksheet : worksheets) {
				Link link = worksheet.getLink(
						"http://schemas.google.com/visualization/2008#visualizationApi",
						"application/atom+xml");
				if (link.getHref().endsWith("gid=" + gridId)) {
					entry = worksheet;
					break;
				}
			}
			if (entry == null) {
				for (WorksheetEntry worksheet : worksheets) {
					for (Link link : worksheet.getLinks()) {
						if (link.getHref().endsWith("gid=" + gridId)) {
							entry = worksheet;
							break;
						}
					}
				}
			}
		}
//		if (entry == null) {
//			/*
//			 * There is always a sheet.
//			 *
//			 * "You can't remove the last sheet is a document."
//			 */
//			entry = worksheets.get(0);
//		}
		return entry;
	}

	public List<WorksheetEntry> listWorksheets(String key) throws IOException, ServiceException {
		//		URL url = new URL("https://spreadsheets.google.com/feeds/worksheets/"
//				+ key + "/private/full");
		FeedURLFactory urlFactory = FeedURLFactory.getDefault();
		URL url = urlFactory.getWorksheetFeedUrl(key, "private", "full");

		// Make a request to the API and get all spreadsheets.
		WorksheetFeed feed = getService().getFeed(url, WorksheetFeed.class);
		return feed.getEntries();
	}
}
