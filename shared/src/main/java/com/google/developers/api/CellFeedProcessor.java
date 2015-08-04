package com.google.developers.api;

import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by renfeng on 5/24/15.
 */
public abstract class CellFeedProcessor {

	private static final Pattern cellIDPattern = Pattern.compile("R([0-9]+)C([0-9]+)");
//		Pattern titlePattern = Pattern.compile("([A-Z]+)([0-9]+)");

	private final SpreadsheetManager spreadsheetManager;

	private int row;

	public CellFeedProcessor(SpreadsheetManager spreadsheetManager) {
		this.spreadsheetManager = spreadsheetManager;
	}

	public void processForBatchUpdate(WorksheetEntry sheet, String... columns) throws IOException, ServiceException {

		boolean stoppedOnDemand = false;

		URL cellFeedURL = sheet.getCellFeedUrl();

		List<String> columnNames = new ArrayList<>(Arrays.asList(columns));

		Map<Integer, String> columnNameMap = new HashMap<>();

		int rowCount = sheet.getRowCount();
		/*
		 * TODO got problem when there was too many entities
		 */
//		int rowCount = 1954;
//		int rowCount = 1954 / 2;
//		rowCount = (rowCount + 1954) / 2;
//		rowCount = (rowCount + 1954) / 2;
		if (rowCount > 1) {
			CellFeed batchRequest = new CellFeed();
			row = 1;
			int r = 1;
			int colCount = sheet.getColCount();
			for (int c = 1; c <= colCount; c++) {
				String idString = "R" + r + "C" + c;
				CellEntry batchEntry = new CellEntry(r, c, "");
				batchEntry.setId(cellFeedURL.toString() + "/" + idString);
				BatchUtils.setBatchId(batchEntry, idString);
				BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.QUERY);
				batchRequest.getEntries().add(batchEntry);
			}

			CellFeed cellFeed = spreadsheetManager.getService().getFeed(cellFeedURL, CellFeed.class);
			CellFeed queryBatchResponse = spreadsheetManager.getService().batch(
					new URL(cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM).getHref()),
					batchRequest);
			List<CellEntry> cells = queryBatchResponse.getEntries();
			for (CellEntry cellEntry : cells) {
				Matcher matcher = cellIDPattern.matcher(
						cellEntry.getId().substring(cellEntry.getId().lastIndexOf('/') + 1));
				if (matcher.matches()) {
					int column = Integer.parseInt(matcher.group(2));

					String columnName = cellEntry.getCell().getValue();
					if (columnNames.contains(columnName)) {
						columnNameMap.put(column, columnName);
						columnNames.remove(columnName);
					}

					processHeaderColumn(column, columnName);

					if (columnNames.size() == 0) {
						break;
					}
				} else {
				/*
				 * won't be here
				 */
				}
			}
		}

		if (columnNameMap.size() > 0) {
			CellFeed batchRequest = new CellFeed();
			for (int r = 2; r <= rowCount; r++) {
				for (int c : columnNameMap.keySet()) {
					String idString = "R" + r + "C" + c;
					CellEntry batchEntry = new CellEntry(r, c, "");
					batchEntry.setId(cellFeedURL.toString() + "/" + idString);
					BatchUtils.setBatchId(batchEntry, idString);
					BatchUtils.setBatchOperationType(batchEntry, BatchOperationType.QUERY);
					batchRequest.getEntries().add(batchEntry);
				}
			}

			Map<String, String> valueMap = new HashMap<>();
			String lastRow = "1";

			CellFeed cellFeed = spreadsheetManager.getService().getFeed(cellFeedURL, CellFeed.class);
			CellFeed queryBatchResponse = spreadsheetManager.getService().batch(
					new URL(cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM).getHref()),
					batchRequest);
			List<CellEntry> cells = queryBatchResponse.getEntries();
			for (CellEntry cellEntry : cells) {
				Matcher matcher = cellIDPattern.matcher(
						cellEntry.getId().substring(cellEntry.getId().lastIndexOf('/') + 1));
				if (matcher.matches()) {
					int column = Integer.parseInt(matcher.group(2));
					String row = matcher.group(1);
					if (!row.equals(lastRow)) {
						this.row++;

							/*
							 * now I've read all the data I need in a row
							 */
						if (!processDataRow(valueMap, cellFeedURL)) {
							stoppedOnDemand = true;
							break;
						}

						valueMap = new HashMap<>();
						lastRow = row;
					}

					String columnName = columnNameMap.get(column);
					if (columnName != null) {
						processDataColumn(cellEntry, columnName);

						String value = cellEntry.getCell().getValue();
						if (value == null) {
							continue;
						}

						valueMap.put(columnName, value);
					}
				} else {
				/*
				 * won't be here
				 */
				}
			}

			if (!stoppedOnDemand && valueMap != null) {
				this.row++;
				processDataRow(valueMap, cellFeedURL);
			}
		}
	}

	public void process(WorksheetEntry sheet, String... columns) throws IOException, ServiceException {

		boolean stoppedOnDemand = false;

		List<String> columnNames = new ArrayList<>(Arrays.asList(columns));

		Map<Integer, String> columnNameMap = new HashMap<>();
		Map<String, String> valueMap = null;
		String lastRow = null;

		URL cellFeedURL = sheet.getCellFeedUrl();
		CellFeed feed = spreadsheetManager.getService().getFeed(cellFeedURL, CellFeed.class);
		List<CellEntry> cells = feed.getEntries();
		for (CellEntry cell : cells) {
			Matcher matcher = cellIDPattern.matcher(cell.getId().substring(cell.getId().lastIndexOf('/') + 1));
			if (matcher.matches()) {

				int column = Integer.parseInt(matcher.group(2));
				String row = matcher.group(1);
				if ("1".equals(row)) {
					if (columnNames.size() == 0) {
						continue;
					}

					String columnName = cell.getCell().getValue();
					if (columnNames.contains(columnName)) {
						columnNameMap.put(column, columnName);
						columnNames.remove(columnName);
					}

					this.row = 0;
					processHeaderColumn(column, columnName);

				} else {
					if (!row.equals(lastRow)) {
						/*
						 * now all the cells in a row is collected
						 */

						if (valueMap != null) {
							/*
							 * this is not the header row
							 */

							this.row++;

							if (!processDataRow(valueMap, cellFeedURL)) {
								stoppedOnDemand = true;
								break;
							}
						}

						valueMap = new HashMap<>();
						lastRow = row;
					}

					String columnName = columnNameMap.get(column);
					if (columnName != null) {
						processDataColumn(cell, columnName);

						String value = cell.getCell().getValue();
						if (value == null) {
							continue;
						}

						valueMap.put(columnName, value);
					}
				}
			} else {
				/*
				 * won't be here
				 */
			}
		}

		if (!stoppedOnDemand && valueMap != null) {
			this.row++;
			processDataRow(valueMap, cellFeedURL);
		}
	}

	/**
	 * @return zero-based row number
	 */
	public int getRow() {
		return row;
	}

	protected void processDataColumn(CellEntry cell, String columnName) {
	}

	protected void processHeaderColumn(int column, String columnName) {
	}

	protected abstract boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
			throws IOException, ServiceException;
}
