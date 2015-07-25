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
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
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

			CellEntry statusCell = null;
			String statusColumn = null;

			@Override
			public boolean processDataColumn(CellEntry cell, String columnName) {
				if ("Status".equals(columnName)) {
					statusCell = cell;
				}
				return true;
			}

			@Override
			public void processHeaderColumn(String column, String columnName) {
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
				if (importContactsFromSpreadsheet(valueMap)) {
					String status = valueMap.get("Status");
					if (statusCell != null) {
						statusCell.changeInputValueLocal(status);
						statusCell.update();
					} else {
						/*
						 * google.com#q=google spreadsheet +api insert cell
						 * http://stackoverflow.com/a/12936664/333033
						 * http://stackoverflow.com/a/30187245/333033
						 */
						statusCell = new CellEntry(getRow(), Integer.parseInt(statusColumn), status);
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

	private boolean importContactsFromSpreadsheet(Map<String, String> valueMap)
			throws IOException, ServiceException {

		boolean dirty = false;

		String status = valueMap.get("Status");
		if (status == null) {
			String event = valueMap.get("Group");
			String url = valueMap.get("URL");
			if (url == null) {
				valueMap.put("Status", "missing url");
				dirty = true;
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
						participants = contactManager.importContactsFromSpreadsheet(
								participants, eventGroup, activity);
					}

					logger.debug("done: " + event);
					valueMap.put("Status", "Done");
					dirty = true;
				} catch (Exception ex) {
					String message = ex.getMessage();
					if (message == null) {
						message = ex.getClass().getName();
					}
					logger.debug("error: " + message);
					valueMap.put("Status", message);
					dirty = true;
				}
			}
		}

		return dirty;
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

		sheet.setRowCount(Math.min(activitiesWithStreak.size(), 2000000 / sheet.getColCount()));
		sheet = sheet.update();

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();

		final List<CellEntry> entries = batchRequest.getEntries();

		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

			int activitiesIndex = 0;

			Map<String, CellEntry> cellMap = new HashMap<>();

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
					entries.add(updateCell(cellMap.get("nickname"), nickname));
				}

				String gplusID = p.getGplusID();
				if (gplusID != null) {
					if (gplusID.startsWith("+")) {
						gplusID = "'" + gplusID;
					}
					entries.add(updateCell(cellMap.get("gplusID"), gplusID));
				}

				entries.add(updateCell(cellMap.get("streak"), count + ""));
				entries.add(updateCell(cellMap.get("fromDate"),
						ContactManager.DATE_FORMAT.format(latestStreak.getFromDate())));
				entries.add(updateCell(cellMap.get("toDate"),
						ContactManager.DATE_FORMAT.format(latestStreak.getToDate())));

				entries.add(updateCell(cellMap.get("id"), p.getContactID()));
				entries.add(updateCell(cellMap.get("cardinal"), getRow() + ""));

				entries.add(updateCell(cellMap.get("hasGplus"),
						"=if(B" + (getRow() + 1) + "<>\"\",\"Yes\",\"\")"));
				entries.add(updateCell(cellMap.get("gplusCount"),
						getRow() == 1 ? "1" : "=I" + getRow() + "+if(B" + (getRow() + 1) + "<>\"\",1,0)"));

				logger.debug("updating streak: " + activitiesIndex + ", " + p);

				activitiesIndex++;

				cellMap = new HashMap<>();

				return true;
			}

			@Override
			protected boolean processDataColumn(CellEntry cell, String columnName) {
				cellMap.put(columnName, cell);
				return true;
			}

			private CellEntry updateCell(CellEntry cellEntry, String value) {

				CellEntry batchEntry = new CellEntry(cellEntry);
				batchEntry.changeInputValueLocal(value);

				BatchUtils.setBatchId(batchEntry, batchEntry.getId());
				BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.UPDATE);

				return batchEntry;
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

		sheet.setRowCount(processor.getRow());
		sheet.update();
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

		sheet.setRowCount(Math.min(activitiesWithCredit.size(), 2000000 / sheet.getColCount()));
		sheet = sheet.update();

		long startTime = System.currentTimeMillis();
		CellFeed batchRequest = new CellFeed();

		final List<CellEntry> entries = batchRequest.getEntries();

		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager) {

			int activitiesIndex = 0;

			Map<String, CellEntry> cellMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL) throws IOException, ServiceException {

				ParticipantStatistics p = activitiesWithCredit.get(activitiesIndex);

				int credit = p.getCredit();
				String nickname = p.getNickname();
				if (nickname != null) {
					entries.add(updateCell(cellMap.get("nickname"), nickname));
				}

				String gplusID = p.getGplusID();
				if (gplusID != null) {
					if (gplusID.startsWith("+")) {
						gplusID = "'" + gplusID;
					}
					entries.add(updateCell(cellMap.get("gplusID"), gplusID));
				}

				entries.add(updateCell(cellMap.get("credit"), credit + ""));
				entries.add(updateCell(cellMap.get("fromDate"),
						ContactManager.DATE_FORMAT.format(p.getFromDate())));
				entries.add(updateCell(cellMap.get("toDate"),
						ContactManager.DATE_FORMAT.format(p.getToDate())));

				entries.add(updateCell(cellMap.get("id"), p.getContactID()));
				entries.add(updateCell(cellMap.get("cardinal"), getRow() + ""));

				entries.add(updateCell(cellMap.get("hasGplus"),
						"=if(B" + (getRow() + 1) + "<>\"\",\"Yes\",\"\")"));
				entries.add(updateCell(cellMap.get("gplusCount"),
						getRow() == 1 ? "1" : "=I" + getRow() + "+if(B" + (getRow() + 1) + "<>\"\",1,0)"));

				logger.debug("updating credit: " + activitiesIndex + ", " + p);

				activitiesIndex++;

				cellMap = new HashMap<>();

				return true;
			}

			@Override
			protected boolean processDataColumn(CellEntry cell, String columnName) {
				cellMap.put(columnName, cell);
				return true;
			}

			private CellEntry updateCell(CellEntry cellEntry, String value) {

				CellEntry batchEntry = new CellEntry(cellEntry);
				batchEntry.changeInputValueLocal(value);

				BatchUtils.setBatchId(batchEntry, batchEntry.getId());
				BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.UPDATE);

				return batchEntry;
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

		sheet.setRowCount(processor.getRow());
		sheet.update();
	}
}
