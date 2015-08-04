package com.google.developers.event;

import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.ContactManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EventManager {

	private static final Logger logger = LoggerFactory
			.getLogger(EventManager.class);

	/*
	 * 2000000
	 * Size: Up to 2 million cells.
	 * https://support.google.com/docs/answer/37603?hl=en
	 */
	public static final int MAX_CELLS = 2000000;

	/*
	 * The literal value of the cell element is the calculated value of the cell,
	 * without formatting applied. If the cell contains a formula, the calculated value is given here.
	 * The Sheets API has no concept of formatting, and thus cannot manipulate formatting of cells.
	 * https://developers.google.com/google-apps/spreadsheets/data#work_with_cell-based_feeds
	 */
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("M/d/yyyy");

	private final SpreadsheetManager spreadsheetManager;
	private final ContactManager contactManager;

	@Inject
	public EventManager(SpreadsheetManager spreadsheetManager, ContactManager contactManager) {
		this.spreadsheetManager = spreadsheetManager;
		this.contactManager = contactManager;
	}

	public void importContactsFromSpreadsheet(
			String spreadsheetKey, String eventName, String activity, int cutoffYear, int cutoffMonth, int cutoffDay)
			throws IOException, ServiceException, ParseException {

		/*
		 * separated the date of registration, check-in and feedback
		 */

		Date cutoffDate;
		{
			Calendar calendar = Calendar.getInstance();
			calendar.set(cutoffYear, cutoffMonth - 1, cutoffDay);
			cutoffDate = calendar.getTime();
		}

		Map<String, String> columnMap;
		{
			Properties properties = new Properties();
			InputStream inStream = getClass().getResourceAsStream(spreadsheetKey + ".properties");
			try {
				properties.load(inStream);
			} finally {
				inStream.close();
			}
			columnMap = new HashMap<>();
			Enumeration<?> keys = properties.propertyNames();
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();
				columnMap.put(key, properties.getProperty(key));
			}
		}

		List<EventParticipant> participants = spreadsheetManager.getEventParticipants(
				spreadsheetKey, cutoffDate, columnMap);

		contactManager.dedupeGroups();

		ContactGroupEntry eventGroup = contactManager.findGroupByName(eventName);
		if (eventGroup == null) {
			eventGroup = contactManager.createGroup(eventName);
		}

		while (participants.size() > 0) {
			participants = contactManager.importContactsFromSpreadsheet(participants, eventGroup, activity);
		}

		return;
	}

	public void importContactsFromSpreadsheet(String spreadsheet)
			throws IOException, ServiceException, ParseException {

		List<EventParticipant> participants = spreadsheetManager.getGoogleGroupsMember(spreadsheet);

		contactManager.dedupeGroups();

		ContactGroupEntry eventGroup = contactManager.findGroupByName("Google Groups");
		if (eventGroup == null) {
			eventGroup = contactManager.createGroup("Google Groups");
		}

		while (participants.size() > 0) {
			participants = contactManager.importContactsFromSpreadsheet(participants, eventGroup, "Google Groups");
		}

		return;
	}

	public void importContactsFromSpreadsheets() throws IOException, ServiceException {

		contactManager.dedupeGroups();

		/*
		 * cell update
		 */
		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

			CellEntry statusCell;
			int statusColumn;

			@Override
			public void processDataColumn(CellEntry cell, String columnName) {
				if ("Status".equals(columnName)) {
					statusCell = cell;
				}
			}

			@Override
			public void processHeaderColumn(int column, String columnName) {
				if ("Status".equals(columnName)) {
					statusColumn = column;
				}
			}

			@Override
			public boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				/*
				 * now I've read all the data I need in a row
				 */
				String status = importContactsFromSpreadsheet(valueMap);
				if (status != null) {
					if (statusCell != null) {
						if (!status.equals(statusCell.getCell().getValue())) {
							statusCell.changeInputValueLocal(status);
							statusCell.update();
						}
					} else {
						/*
						 * google.com#q=google spreadsheet +api insert cell
						 * http://stackoverflow.com/a/12936664/333033
						 * http://stackoverflow.com/a/30187245/333033
						 */
						statusCell = new CellEntry(getRow() + 1, statusColumn, status);
						spreadsheetManager.getService().insert(cellFeedURL, statusCell);
					}
				}

				statusCell = null;
				return true;
			}
		};
		cellFeedProcessor.process(
				spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
				"Group", "Activity", "Date", "Status", "URL", "nickname", "emailAddress", "phoneNumber",
				"timestamp", "timestamp.dateFormat", "timestamp.dateFormat.locale");

		return;
	}

	private String importContactsFromSpreadsheet(Map<String, String> valueMap)
			throws IOException, ServiceException {

		String status = valueMap.get("Status");
		if (status == null) {
			String event = valueMap.get("Group");
			String url = valueMap.get("URL");
			if (url == null) {
				status = "missing url";
			} else {
				String date = valueMap.get("Date");
				String activity = valueMap.get("Activity");
				String timestampDateFormat = valueMap.get("timestamp.dateFormat");
				String timestampDateFormatLocale = valueMap.get("timestamp.dateFormat.locale");

				try {
					Date cutoffDate;
					if (date != null) {
						DateFormat dateFormat;
						if (timestampDateFormatLocale != null) {
							dateFormat = new SimpleDateFormat(timestampDateFormat,
									Locale.forLanguageTag(timestampDateFormatLocale));
						} else {
							dateFormat = new SimpleDateFormat(timestampDateFormat);
						}

						cutoffDate = dateFormat.parse(date);
					} else {
						cutoffDate = null;
					}

					List<EventParticipant> participants = spreadsheetManager.getEventParticipants(
							url, cutoffDate, valueMap);

					ContactGroupEntry eventGroup = contactManager.findGroupByName(event);
					if (eventGroup == null) {
						eventGroup = contactManager.createGroup(event);
					}

					/*
					 * this loop fixes e-tag exceptions
					 */
					while (participants.size() > 0) {
						List<EventParticipant> participants2 = contactManager.importContactsFromSpreadsheet(
								participants, eventGroup, activity);
						if (participants2.size() == participants.size()) {
							throw new Exception("failed to update participants: " +
									Arrays.toString(participants2.toArray()));
						}
						participants = participants2;
					}

					logger.debug("done: " + event);
					status = "Done";
				} catch (ServiceException ex) {
					String message;
					if (ex.getResponseBody() != null &&
							ex.getResponseContentType().match(new ContentType("text/html"))) {
						Document document = Jsoup.parse(ex.getResponseBody());

						String s = ex.getClass().getName();
						String localizedMessage = ex.getLocalizedMessage();
						message = (localizedMessage != null ? (s + ": " + localizedMessage) : s) + "\n" +
								document.text();
					} else {
						message = ex.toString();
					}
					logger.debug("error: " + message);
					status = message;
				} catch (Exception ex) {
					String message = ex.toString();
					logger.debug("error: " + message);
					status = message;
				}
			}
		}

		return status;
	}

	public void updateRanking() throws IOException, ServiceException {

		/*
		 * cutoff date: a year ago
		 *
		 * TODO what about 180 days ago? to match pulse
		 */
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.YEAR, -1);
		List<ParticipantStatistics> activities = contactManager.listActivities(calendar.getTime());
		updateStreakRanking(activities);
		updateCreditRanking(activities);

		/*
		 * https://developers.google.com/gdata/samples/spreadsheet_sample
		 * jsonp
		 * topeka, credit ranking, https://spreadsheets.google.com/feeds/list/1heiZJfKi3LmXy-Mg13nSSdhthwIZxOZ32m3tJuKhKI4/om8tjsu/public/basic?alt=json-in-script&callback=myFunc
		 * topeka, streak ranking, https://spreadsheets.google.com/feeds/list/1heiZJfKi3LmXy-Mg13nSSdhthwIZxOZ32m3tJuKhKI4/o5tcjjn/public/basic?alt=json-in-script&callback=myFunc
		 *
		 * web, https://docs.google.com/spreadsheets/d/1heiZJfKi3LmXy-Mg13nSSdhthwIZxOZ32m3tJuKhKI4/pub
		 *
		 * change streak to count groups instead of activities (as for credit)
		 * TODO referrer score = (first seen) == (####-##-## Register)
		 */

		return;
	}

	public void updateStreakRanking(List<ParticipantStatistics> activities)
			throws IOException, ServiceException {

		final List<ParticipantStatistics> activitiesWithStreak = new ArrayList<>();
		for (ParticipantStatistics p : activities) {
			/*
			 * it's just for distinguishing people attended at least one event from those who didn't at all,
			 * in the last 90 days
			 */
			if (p.getLatestStreak() != null && p.getLatestStreak().getCount() > 0) {
				activitiesWithStreak.add(p);
			}
		}

		Collections.sort(activitiesWithStreak, new Comparator<ParticipantStatistics>() {
			@Override
			public int compare(ParticipantStatistics o1, ParticipantStatistics o2) {

				int result;

				Streak streak1 = o1.getLatestStreak();
				Streak streak2 = o2.getLatestStreak();
				/*
				 * it's guaranteed not to be null, and greater than zero
				 */
//				if (streak1 == null && streak2 == null) {
//					result = 0;
//				} else if (streak1 != null && streak2 == null) {
//					result = -1;
//				} else if (streak1 == null && streak2 != null) {
//					result = 1;
//				} else {
//					result = -Integer.compare(streak1.getCount(), streak2.getCount());
//					if (result == 0) {
//						result = -streak1.getFromDate().compareTo(streak2.getFromDate());
//					}
//					/*
//					 * TODO sort on credit on draw
//					 */
//				}
				result = -Integer.compare(streak1.getCount(), streak2.getCount());
				if (result == 0) {
					result = -streak1.getFromDate().compareTo(streak2.getFromDate());
				}
				/*
				 * TODO sort on credit on draw
				 */

				return result;
			}
		});

		String[] columnNames = {"nickname", "gplusID", "streak", "fromDate", "toDate", "id", "cardinal",
				"hasGplus", "gplusCount"};

		/*
		 * Cells can be modified in place. Unlike other feeds, cells are not directly added nor deleted.
		 * They are fixed in place, based on the dimensions of a given worksheet.
		 * To add or remove cells from a worksheet, use the worksheets feed to change the dimension of the worksheet.
		 * To empty a cell, simply update its value to be an empty string.
		 *
		 * cell batch update
		 *
		 * Important: Only the cells feed supports batching requests.
		 * https://developers.google.com/google-apps/spreadsheets/#updating_multiple_cells_with_a_batch_request
		 */

		String url = DevelopersSharedModule.getMessage("streakRanking");
		WorksheetEntry sheet = spreadsheetManager.getWorksheet(url);

		sheet.setRowCount(Math.min(activitiesWithStreak.size(), MAX_CELLS / sheet.getColCount()));
		sheet = sheet.update();

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();

		final List<CellEntry> entries = batchRequest.getEntries();

		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

			int activitiesIndex = 0;

			Map<String, CellEntry> cellMap = new HashMap<>();
			Map<String, Integer> columnMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				ParticipantStatistics p = activitiesWithStreak.get(activitiesIndex);

				/*
				 * it's guaranteed not to be null
				 */
				Streak latestStreak = p.getLatestStreak();

				/*
				 * it's guaranteed not to be greater than zero
				 */
				int count = latestStreak.getCount();

				String nickname = p.getNickname();
				if (nickname != null) {
					updateCell(entries, cellMap.get("nickname"), nickname);
				}

				String gplusID = p.getGplusID();
				if (gplusID != null) {
					if (gplusID.startsWith("+")) {
						gplusID = "'" + gplusID;
					}
				}
				updateCell(entries, cellMap.get("gplusID"), gplusID);

				updateCell(entries, cellMap.get("streak"), count + "");

				updateCell(entries, cellMap.get("fromDate"), DATE_FORMAT.format(latestStreak.getFromDate()));
				updateCell(entries, cellMap.get("toDate"), DATE_FORMAT.format(latestStreak.getToDate()));

				updateCell(entries, cellMap.get("id"), p.getContactID());
				updateCell(entries, cellMap.get("cardinal"), getRow() + "");

				/*
				 * the input value will be converted to the following
				 * =if(R[0]C[-6]<>"","Yes","")
				 * =R[-1]C[0]+if(R[0]C[-7]<>"",1,0)
				 *
				 * the API always returns cell addresses for formulas on cells in R1C1 notation,
				 * even if the formula was set with A1 notation.
				 * https://developers.google.com/google-apps/spreadsheets/data#work_with_cell-based_feeds
				 */
				{
//				updateCell(entries, cellMap.get("hasGplus"),
//						"=if(B" + (getRow() + 1) + "<>\"\",\"Yes\",\"\")");
					int columnOffset = columnMap.get("gplusID") - columnMap.get("hasGplus");
					updateCell(entries, cellMap.get("hasGplus"),
							"=if(R[0]C[" + columnOffset + "]<>\"\",\"1\",\"\")");
				}
				{
//				updateCell(entries, cellMap.get("gplusCount"),
//						getRow() == 1 ? "1" : "=I" + getRow() + "+if(B" + (getRow() + 1) + "<>\"\",1,0)");
					int columnOffset = columnMap.get("hasGplus") - columnMap.get("gplusCount");
					updateCell(entries, cellMap.get("gplusCount"),
							getRow() == 1 ? "1" : "=R[-1]C[0]+R[0]C[" + columnOffset + "]");
				}

				logger.debug("updating streak: " + activitiesIndex + ", " + p);

				activitiesIndex++;

				cellMap = new HashMap<>();

				return true;
			}

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				cellMap.put(columnName, cell);
			}

			@Override
			protected void processHeaderColumn(int column, String columnName) {
				columnMap.put(columnName, column);
			}
		};
		processor.process(sheet, columnNames);

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

//		sheet.setRowCount(processor.getRow());
//		sheet.update();
		logger.info("streak ranking rows updated: " + processor.getRow());
	}

	public void updateCreditRanking(List<ParticipantStatistics> activities)
			throws IOException, ServiceException {

		final List<ParticipantStatistics> activitiesWithCredit = new ArrayList<>();
		for (ParticipantStatistics p : activities) {
			/*
			 * it's just for distinguishing people attended at least one event from those who didn't at all,
			 * in the last 90 days
			 */
			if (p.getCredit() > 0) {
				activitiesWithCredit.add(p);
			}
		}

		Collections.sort(activitiesWithCredit, new Comparator<ParticipantStatistics>() {
			@Override
			public int compare(ParticipantStatistics o1, ParticipantStatistics o2) {

				int result = -Integer.compare(o1.getCredit(), o2.getCredit());
				if (result == 0) {
					result = -o1.getFromDate().compareTo(o2.getFromDate());
					/*
					 * TODO sort on streak on draw
					 */
				}

				return result;
			}
		});

		String[] columnNames = {"nickname", "gplusID", "credit", "fromDate", "toDate", "id", "cardinal",
				"hasGplus", "gplusCount"};

		String url = DevelopersSharedModule.getMessage("creditRanking");
		WorksheetEntry sheet = spreadsheetManager.getWorksheet(url);

		sheet.setRowCount(Math.min(activitiesWithCredit.size(), MAX_CELLS / sheet.getColCount()));
		sheet = sheet.update();

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();

		final List<CellEntry> entries = batchRequest.getEntries();

		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

			int activitiesIndex = 0;

			Map<String, CellEntry> cellMap = new HashMap<>();
			Map<String, Integer> columnMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				ParticipantStatistics p = activitiesWithCredit.get(activitiesIndex);

				int credit = p.getCredit();
				String nickname = p.getNickname();
				if (nickname != null) {
					if (nickname.startsWith("+")) {
						nickname = "'" + nickname;
					}
					updateCell(entries, cellMap.get("nickname"), nickname);
				}

				String gplusID = p.getGplusID();
				if (gplusID != null) {
					if (gplusID.startsWith("+")) {
						gplusID = "'" + gplusID;
					}
				}
				updateCell(entries, cellMap.get("gplusID"), gplusID);

				updateCell(entries, cellMap.get("credit"), credit + "");
				updateCell(entries, cellMap.get("fromDate"), DATE_FORMAT.format(p.getFromDate()));
				updateCell(entries, cellMap.get("toDate"), DATE_FORMAT.format(p.getToDate()));

				updateCell(entries, cellMap.get("id"), p.getContactID());
				updateCell(entries, cellMap.get("cardinal"), getRow() + "");

				/*
				 * the input value will be converted to the following
				 * =if(R[0]C[-6]<>"","Yes","")
				 * =R[-1]C[0]+if(R[0]C[-7]<>"",1,0)
				 *
				 * the API always returns cell addresses for formulas on cells in R1C1 notation,
				 * even if the formula was set with A1 notation.
				 * https://developers.google.com/google-apps/spreadsheets/data#work_with_cell-based_feeds
				 */
				{
//				updateCell(entries, cellMap.get("hasGplus"),
//						"=if(B" + (getRow() + 1) + "<>\"\",\"Yes\",\"\")");
					int columnOffset = columnMap.get("gplusID") - columnMap.get("hasGplus");
					updateCell(entries, cellMap.get("hasGplus"),
							"=if(R[0]C[" + columnOffset + "]<>\"\",\"1\",\"\")");
				}
				{
//				updateCell(entries, cellMap.get("gplusCount"),
//						getRow() == 1 ? "1" : "=I" + getRow() + "+if(B" + (getRow() + 1) + "<>\"\",1,0)");
					int columnOffset = columnMap.get("hasGplus") - columnMap.get("gplusCount");
					updateCell(entries, cellMap.get("gplusCount"),
							getRow() == 1 ? "1" : "=R[-1]C[0]+R[0]C[" + columnOffset + "]");
				}

				logger.debug("updating credit: " + activitiesIndex + ", " + p);

				activitiesIndex++;

				cellMap = new HashMap<>();

				return true;
			}

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				cellMap.put(columnName, cell);
			}

			@Override
			protected void processHeaderColumn(int column, String columnName) {
				columnMap.put(columnName, column);
			}
		};
		processor.processForBatchUpdate(sheet, columnNames);

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

//		sheet.setRowCount(processor.getRow());
//		sheet.update();
		logger.info("credit ranking rows updated: " + processor.getRow());
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

		return;
	}

	public void updateEventScore() throws IOException, ServiceException {

		/*
		 * cutoff date: a year ago
		 *
		 * TODO what about 180 days ago? to match pulse
		 */
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.YEAR, -1);
		final Map<String, EventScore> scoreMap = contactManager.listEventScore(calendar.getTime());

		final ArrayList<Map.Entry<String, EventScore>> scoreRanking = new ArrayList<>(scoreMap.entrySet());
		Collections.sort(scoreRanking, new Comparator<Map.Entry<String, EventScore>>() {
			@Override
			public int compare(Map.Entry<String, EventScore> o1, Map.Entry<String, EventScore> o2) {
				return -o1.getKey().compareTo(o2.getKey());
//				return -Integer.compare(o1.getValue().getValue(), o2.getValue().getValue());
			}
		});

		String url = DevelopersSharedModule.getMessage("event");
		WorksheetEntry sheet = spreadsheetManager.getWorksheet(url);

		sheet.setRowCount(Math.min(scoreMap.size(), MAX_CELLS / sheet.getColCount()));
		sheet = sheet.update();

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();

		final List<CellEntry> entries = batchRequest.getEntries();

		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

			int eventIndex = 0;

			Map<String, CellEntry> cellMap = new HashMap<>();
			Map<String, Integer> columnMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				Map.Entry<String, EventScore> s = scoreRanking.get(eventIndex);

				updateCell(entries, cellMap.get("Name"), s.getKey());
				updateCell(entries, cellMap.get("Score"), s.getValue().getValue() + "");

				logger.debug("updating event score: " + eventIndex + ", " + s);

				eventIndex++;

				cellMap = new HashMap<>();

				return true;
			}

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				cellMap.put(columnName, cell);
			}

			@Override
			protected void processHeaderColumn(int column, String columnName) {
				columnMap.put(columnName, column);
			}
		};
		processor.process(sheet, "Name", "Score", "Organizer");

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

//		sheet.setRowCount(processor.getRow());
//		sheet.update();
		logger.info("event score rows updated: " + processor.getRow());
	}
}
