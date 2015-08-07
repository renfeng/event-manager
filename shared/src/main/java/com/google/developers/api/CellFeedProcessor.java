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
			row = 1;

			CellFeed batchRequest = new CellFeed();
			int colCount = sheet.getColCount();
			for (int c = 1; c <= colCount; c++) {
				String idString = "R" + row + "C" + c;
				CellEntry batchEntry = new CellEntry(row, c, "");
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
			row = 2;
			Map<String, String> valueMap = new HashMap<>();

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
					int row = Integer.parseInt(matcher.group(1));
					if (row != this.row) {
						/*
						 * now I've read all the data I need in a row
						 */
						if (!processDataRow(valueMap, cellFeedURL)) {
							stoppedOnDemand = true;
							break;
						}

						valueMap = new HashMap<>();
						this.row = row;
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
				processDataRow(valueMap, cellFeedURL);
			}
		}
	}

	public void process(WorksheetEntry sheet, String... columns) throws IOException, ServiceException {

		boolean stoppedOnDemand = false;

		List<String> columnNames = new ArrayList<>(Arrays.asList(columns));

		Map<Integer, String> columnNameMap = new HashMap<>();
		Map<String, String> valueMap = new HashMap<>();

		URL cellFeedURL = sheet.getCellFeedUrl();
		CellFeed feed = spreadsheetManager.getService().getFeed(cellFeedURL, CellFeed.class);
		List<CellEntry> cells = feed.getEntries();
		Iterator<CellEntry> iterator = cells.iterator();

		this.row = 1;
		while (iterator.hasNext()) {
			CellEntry cell = iterator.next();
			String cellId = cell.getId();
			Matcher matcher = cellIDPattern.matcher(cellId.substring(cellId.lastIndexOf('/') + 1));
			if (matcher.matches()) {
				int column = Integer.parseInt(matcher.group(2));
				int row = Integer.parseInt(matcher.group(1));

				if (row != 1) {
					break;
				}
				if (columnNames.size() == 0) {
					break;
				}

				String columnName = cell.getCell().getValue();
				if (columnNames.contains(columnName)) {
					columnNameMap.put(column, columnName);
					columnNames.remove(columnName);
					processHeaderColumn(column, columnName);
				}
			} else {
				/*
				 * won't be here
				 */
				throw new RuntimeException("invalid cell notation: " + cellId);
			}
		}

		this.row = 2;
		while (iterator.hasNext()) {
			CellEntry cell = iterator.next();
			String cellId = cell.getId();
			Matcher matcher = cellIDPattern.matcher(cellId.substring(cellId.lastIndexOf('/') + 1));
			if (matcher.matches()) {

				int column = Integer.parseInt(matcher.group(2));
				int row = Integer.parseInt(matcher.group(1));

				if (row != this.row) {
						/*
						 * now all the cells in a row is collected
						 */
					if (!processDataRow(valueMap, cellFeedURL)) {
						stoppedOnDemand = true;
						break;
					}

					valueMap = new HashMap<>();
					this.row = row;
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
			} else {
				/*
				 * won't be here
				 */
				throw new RuntimeException("invalid cell notation: " + cellId);
			}
		}

		if (!stoppedOnDemand && valueMap != null) {
			processDataRow(valueMap, cellFeedURL);
		}
	}

	/**
	 * @return current row number, starting from one
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
