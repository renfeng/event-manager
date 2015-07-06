package hu.dushu.developers.event.server.google;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import hu.dushu.developers.event.server.EventParticipant;
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

public class SpreadsheetManager extends ServiceManager<SpreadsheetService> {

	private static final Logger logger = LoggerFactory
			.getLogger(SpreadsheetManager.class);

	@Inject
	public SpreadsheetManager(
			@Named("refreshToken") String refreshToken,
			@Named("clientId") String clientId,
			@Named("clientSecret") String clientSecret,
			HttpTransport transport, JsonFactory jsonFactory) {

		super(refreshToken, clientId, clientSecret, transport, jsonFactory);

		SpreadsheetService service = new SpreadsheetService(
				"GDG Event Management");
		service.setProtocolVersion(SpreadsheetService.Versions.V3);
		setService(service);

		return;
	}

	public List<EventParticipant> getGoogleGroupsMember(
			String spreadsheet, String worksheet) throws IOException, ServiceException {

		List<EventParticipant> participants = new ArrayList<>();

		List<ListEntry> rows = getListEntries(spreadsheet, worksheet);
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

	public List<ListEntry> getListEntries(String url, String worksheet)
			throws IOException, ServiceException {

		URL listFeedURL = getFeedURL(url, worksheet, Namespaces.LIST_LINK_REL);
		if (listFeedURL == null) {
			throw new IllegalArgumentException(
					"invalid Spreadsheet url: " + url + ", worksheet(optional): " + worksheet);
		}

		/*-
		 * https://developers.google.com/google-apps/spreadsheets/#sheets_api_urls_visibilities_and_projections
		 */
		// Make a request to the API and get all spreadsheets.
		ListFeed listFeed = getService().getFeed(listFeedURL, ListFeed.class);

		return listFeed.getEntries();
	}

	public List<CellEntry> getCellEntries(String url, String worksheet)
			throws IOException, ServiceException {

		URL cellFeedURL = getFeedURL(url, worksheet, Namespaces.CELLS_LINK_REL);
		if (cellFeedURL == null) {
			throw new IllegalArgumentException(
					"invalid Spreadsheet url: " + url + ", worksheet(optional): " + worksheet);
		}

		/*-
		 * https://developers.google.com/google-apps/spreadsheets/#sheets_api_urls_visibilities_and_projections
		 */
		// Make a request to the API and get all spreadsheets.
		CellFeed listFeed = getService().getFeed(cellFeedURL, CellFeed.class);

		return listFeed.getEntries();
	}

	public List<EventParticipant> getEventParticipants(
			String spreadsheet, final Date cutoffDate, Map<String, String> valueMap)
			throws IOException, ServiceException, ParseException {

		final List<EventParticipant> participants = new ArrayList<>();

		/*
		 * converted to cell feed, see hu.dushu.developers.event.server.EventManager.importContactsFromSpreadsheets
		 * the benefit is no need to trim punctuations
		 */

		final String nicknameTag = valueMap.get("nickname");
		final String emailAddressTag = valueMap.get("emailAddress");
		final String phoneNumberTag = valueMap.get("phoneNumber");

		final String timestampTag = valueMap.get("timestamp");
		final String timestampDateFormat = valueMap.get("timestamp.dateFormat");
		final String timestampDateFormatLocale = valueMap.get("timestamp.dateFormat.locale");

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
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(this) {
				@Override
				public boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
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
							logger.warn("error parsing date on row: " + rowNotation, ex);
						}
					}
					return true;
				}
			};
			cellFeedProcessor.process(spreadsheet, timestampTag, nicknameTag, emailAddressTag, phoneNumberTag);
		} else {
			/*
			 * cutoff date as event date
			 */
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(this) {
				@Override
				public boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
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
			cellFeedProcessor.process(spreadsheet, timestampTag, nicknameTag, emailAddressTag, phoneNumberTag);
		}

		return participants;
	}

	/**
	 * @param url
	 * @param sheetName
	 * @param linkRelKind accepts Namespaces.LIST_LINK_REL, and Namespaces.CELLS_LINK_REL
	 * @return
	 * @throws IOException
	 * @throws ServiceException
	 */
	public URL getFeedURL(String url, String sheetName, String linkRelKind) throws IOException,
			ServiceException {

		URL listFeedURL;

		/*-
		 * https://docs.google.com/spreadsheets/d/1y6RNHHA8HDZEbJ1L8QsCiilwGChN8u6ZmeJ3bJi6EyQ/edit#gid=187918259
		 */
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
			listFeedURL = getFeedURL(key, gridId, sheetName, linkRelKind);
		} else {
			listFeedURL = null;
		}

		return listFeedURL;
	}

	private URL getFeedURL(String key, String gridId, String sheetName, String linkRelKind)
			throws IOException, ServiceException {

		URL url = new URL("https://spreadsheets.google.com/feeds/worksheets/"
				+ key + "/private/full");

		// Make a request to the API and get all spreadsheets.
		WorksheetFeed feed = getService().getFeed(url, WorksheetFeed.class);
		List<WorksheetEntry> worksheets = feed.getEntries();

		WorksheetEntry entry = null;
		if (gridId == null && sheetName == null) {
			/*
			 * There is always a sheet.
			 *
			 * "You can't remove the last sheet is a document."
			 */
			entry = worksheets.get(0);
		} else {
			// Iterate through each worksheet in the spreadsheet.
			if (gridId != null) {
				for (WorksheetEntry worksheet : worksheets) {
					Link link = worksheet
							.getLink(
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
			if (entry == null && sheetName != null) {
				for (WorksheetEntry worksheet : worksheets) {
					if (worksheet.getTitle().getPlainText().equals(sheetName)) {
						entry = worksheet;
					}
				}
			}
		}

		URL result;
		if (linkRelKind.equals(Namespaces.LIST_LINK_REL)) {
			result = entry.getListFeedUrl();
		} else if (linkRelKind.equals(Namespaces.CELLS_LINK_REL)) {
			result = entry.getCellFeedUrl();
		} else {
			result = null;
		}

		return result;
	}
}
