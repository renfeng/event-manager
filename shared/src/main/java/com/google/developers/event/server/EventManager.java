package com.google.developers.event.server;

import com.google.developers.event.server.google.CellFeedProcessor;
import com.google.developers.event.server.google.ContactManager;
import com.google.developers.event.server.google.SpreadsheetManager;
import com.google.gdata.data.contacts.ContactGroupEntry;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.Namespaces;
import com.google.gdata.util.InvalidEntryException;
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

	public void importContactsFromSpreadsheet(String spreadsheet, String worksheet)
			throws IOException, ServiceException, ParseException {

		List<EventParticipant> participants = spreadsheetManager.getGoogleGroupsMember(spreadsheet, worksheet);

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
			String statusColumnNotation = null;

			@Override
			public boolean processDataColumn(CellEntry cell, String columnName) {
				if ("Status".equals(columnName)) {
					statusCell = cell;
				}
				return true;
			}

			@Override
			public void processHeaderColumn(String columnNotation, String columnName) {
				if ("Status".equals(columnName)) {
					statusColumnNotation = columnNotation;
				}
			}

			@Override
			public boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
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
						statusCell = new CellEntry(
								Integer.parseInt(rowNotation), Integer.parseInt(statusColumnNotation),
								status);
						spreadsheetManager.getService().insert(cellFeedURL, statusCell);
					}
				}

				statusCell = null;
				return true;
			}
		};
		cellFeedProcessor.process(
				DevelopersSharedModule.getMessage("metaSpreadsheet"),
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

		Collections.sort(activities, new Comparator<ParticipantStatistics>() {
			@Override
			public int compare(ParticipantStatistics o1, ParticipantStatistics o2) {

				int result;

				Streak streak1 = o1.getLatestStreak();
				Streak streak2 = o2.getLatestStreak();
				if (streak1 == null && streak2 == null) {
					result = 0;
				} else if (streak1 != null && streak2 == null) {
					result = -1;
				} else if (streak1 == null && streak2 != null) {
					result = 1;
				} else {
					result = -Integer.compare(streak1.getCount(), streak2.getCount());
					if (result == 0) {
						result = -streak1.getFromDate().compareTo(streak2.getFromDate());
					}
					/*
					 * TODO sort on credit on draw
					 */
				}

				return result;
			}
		});
		updateStreakRanking(activities);

		Collections.sort(activities, new Comparator<ParticipantStatistics>() {
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

	public void updateStreakRanking(List<ParticipantStatistics> activities) throws IOException, ServiceException {

		String url = DevelopersSharedModule.getMessage("streakRanking");
		URL listFeedUrl = spreadsheetManager.getFeedURL(url, null, Namespaces.LIST_LINK_REL);

		/*
		 * TODO cell batch update
		 *
		 * batchLink will be null
		 *
		 * Important: Only the cells feed supports batching requests.
		 * https://developers.google.com/google-apps/spreadsheets/#updating_multiple_cells_with_a_batch_request
		 */
//		long startTime = System.currentTimeMillis();
//		ListFeed batchRequest = spreadsheetManager.getService().getFeed(listFeedUrl, ListFeed.class);
//		Link batchLink = batchRequest.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);

		List<ListEntry> rows = spreadsheetManager.getListEntries(url, null);
		int rowIndex = 0;
		int activitiesIndex = 0;
		while (rowIndex < rows.size() && activitiesIndex < activities.size()) {
			ListEntry row = rows.get(rowIndex);
			ParticipantStatistics p = activities.get(activitiesIndex);
			if (updateStreakRanking(p, row)) {
				logger.debug("updating streak: " + rowIndex + ", " + p);
				row.update();
//				BatchUtils.setBatchId(row, p.toString());
//				BatchUtils.setBatchOperationType(row, BatchOperationType.UPDATE);
//				batchRequest.getEntries().add(row);
				rowIndex++;
			} else {
				logger.debug("no streak to update: " + rowIndex + ", " + p);
			}
			activitiesIndex++;
		}

		if (rowIndex < rows.size()) {
			ListEntry row = rows.get(rowIndex);
			do {
				try {
					logger.debug("deleting streak row: " + rowIndex);
					row.delete();
				} catch (InvalidEntryException ex) {
					logger.warn("failed to delete streak row: " + rowIndex, ex);
				}
//				BatchUtils.setBatchId(row, i + "");
//				BatchUtils.setBatchOperationType(row, BatchOperationType.DELETE);
//				batchRequest.getEntries().add(row);
				rowIndex++;
			} while (rowIndex < rows.size());
		}

		while (activitiesIndex < activities.size()) {
			ParticipantStatistics p = activities.get(activitiesIndex);
			ListEntry row = new ListEntry();
			if (updateStreakRanking(p, row)) {
				logger.debug("inserting streak: " + activitiesIndex + ", " + p);
				// Send the new row to the API for insertion.
				spreadsheetManager.getService().insert(listFeedUrl, row);
//				BatchUtils.setBatchId(row, p.toString());
//				BatchUtils.setBatchOperationType(row, BatchOperationType.INSERT);
//				batchRequest.getEntries().add(row);
			} else {
				logger.debug("no streak to insert: " + activitiesIndex + ", " + p);
			}
			activitiesIndex++;
		}

//		ListFeed batchResponse = spreadsheetManager.getService().batch(new URL(batchLink.getHref()), batchRequest);
//
//		// Check the results
//		boolean isSuccess = true;
//		for (ListEntry entry : batchResponse.getEntries()) {
//			String batchId = BatchUtils.getBatchId(entry);
//			if (!BatchUtils.isSuccess(entry)) {
//				isSuccess = false;
//				BatchStatus status = BatchUtils.getBatchStatus(entry);
//				System.out.printf("%s failed (%s) %s", batchId, status.getReason(), status.getContent());
//			}
//		}
//
//		logger.debug(isSuccess ? "\nBatch operations successful." : "\nBatch operations failed");
//		logger.debug(String.format("\n%s ms elapsed\n", System.currentTimeMillis() - startTime));
	}

	public void updateCreditRanking(List<ParticipantStatistics> activities) throws IOException, ServiceException {

		String url = DevelopersSharedModule.getMessage("creditRanking");
		URL listFeedUrl = spreadsheetManager.getFeedURL(url, null, Namespaces.LIST_LINK_REL);

		List<ListEntry> rows = spreadsheetManager.getListEntries(url, null);
		int rowIndex = 0;
		int activitiesIndex = 0;
		while (rowIndex < rows.size() && activitiesIndex < activities.size()) {
			ListEntry row = rows.get(rowIndex);
			ParticipantStatistics p = activities.get(activitiesIndex);
			if (updateCreditRanking(p, row)) {
				logger.debug("updating credit: " + rowIndex + ", " + p);
				row.update();
				rowIndex++;
			} else {
				logger.debug("no credit to update: " + rowIndex + ", " + p);
			}
			activitiesIndex++;
		}

		if (rowIndex < rows.size()) {
			ListEntry row = rows.get(rowIndex);
			do {
				try {
					logger.debug("deleting credit row: " + rowIndex);
					row.delete();
				} catch (InvalidEntryException ex) {
					logger.warn("failed to delete credit row: " + rowIndex, ex);
				}
				rowIndex++;
			} while (rowIndex < rows.size());
		}

		while (activitiesIndex < activities.size()) {
			ParticipantStatistics p = activities.get(activitiesIndex);
			ListEntry row = new ListEntry();
			if (updateCreditRanking(p, row)) {
				logger.debug("inserting credit: " + activitiesIndex + ", " + p);
				// Send the new row to the API for insertion.
				spreadsheetManager.getService().insert(listFeedUrl, row);
			} else {
				logger.debug("no credit to insert: " + activitiesIndex + ", " + p);
			}
			activitiesIndex++;
		}
	}

	private boolean updateStreakRanking(ParticipantStatistics p, ListEntry row)
			throws IOException, ServiceException {

		boolean dirty = false;

		Streak latestStreak = p.getLatestStreak();
		if (latestStreak != null) {
			int count = latestStreak.getCount();
			/*
			 * it's just for distinguishing people attended at least one event from those who didn't at all,
			 * in the last 90 days
			 * TODO should handle single streak specially, at least on ui
			 */
			if (count > 0) {
				String nickname = p.getNickname();
				if (nickname != null) {
					row.getCustomElements().setValueLocal("nickname", nickname);
				}

				String gplusID = p.getGplusID();
				if (gplusID != null) {
					row.getCustomElements().setValueLocal("gplusID", gplusID);
				}

				row.getCustomElements().setValueLocal("streak", count + "");
				row.getCustomElements().setValueLocal("fromDate",
						ContactManager.DATE_FORMAT.format(latestStreak.getFromDate()));
				row.getCustomElements().setValueLocal("toDate",
						ContactManager.DATE_FORMAT.format(latestStreak.getToDate()));

				row.getCustomElements().setValueLocal("id", p.getContactID());

				dirty = true;
			}
		}

		return dirty;
	}

	private boolean updateCreditRanking(ParticipantStatistics p, ListEntry row)
			throws IOException, ServiceException {

		boolean dirty = false;

		int credit = p.getCredit();
		if (credit > 0) {
			String nickname = p.getNickname();
			if (nickname != null) {
				row.getCustomElements().setValueLocal("nickname", nickname);
			}

			String gplusID = p.getGplusID();
			if (gplusID != null) {
				row.getCustomElements().setValueLocal("gplusID", gplusID);
			}

			row.getCustomElements().setValueLocal("credit", credit + "");

			Date fromDate = p.getFromDate();
			row.getCustomElements().setValueLocal("fromDate",
					ContactManager.DATE_FORMAT.format(fromDate));

			Date toDate = p.getToDate();
			row.getCustomElements().setValueLocal("toDate",
					ContactManager.DATE_FORMAT.format(toDate));

			row.getCustomElements().setValueLocal("id", p.getContactID());

			dirty = true;
		}

		return dirty;
	}
}
