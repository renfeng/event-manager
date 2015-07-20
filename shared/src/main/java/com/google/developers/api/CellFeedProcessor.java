package com.google.developers.api;

import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.Namespaces;
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

	public CellFeedProcessor(SpreadsheetManager spreadsheetManager) {
		this.spreadsheetManager = spreadsheetManager;
	}

	public void process(String url, String... columns) throws IOException, ServiceException {

		boolean done = false;

		List<String> columnNames = new ArrayList<>(Arrays.asList(columns));

		Map<String, String> columnNameMap = new HashMap<>();
		Map<String, String> valueMap = null;
		String lastRowNotation = "";
		String rowNotation = null;
		URL cellFeedURL = spreadsheetManager.getFeedURL(url, null, Namespaces.CELLS_LINK_REL);
		if (cellFeedURL == null) {
			throw new IllegalArgumentException("invalid Spreadsheet url: " + url);
		}

		CellFeed feed = spreadsheetManager.getService().getFeed(cellFeedURL, CellFeed.class);
		List<CellEntry> cells = feed.getEntries();
		for (CellEntry cell : cells) {
			Matcher matcher = cellIDPattern.matcher(cell.getId().substring(cell.getId().lastIndexOf('/') + 1));
			if (matcher.matches()) {

				String columnNotation = matcher.group(2);
				rowNotation = matcher.group(1);
				if ("1".equals(rowNotation)) {
					if (columnNames.size() == 0) {
						continue;
					}

					String columnName = cell.getCell().getValue();
					if (columnNames.contains(columnName)) {
						columnNameMap.put(columnNotation, columnName);
						columnNames.remove(columnName);
					}

					processHeaderColumn(columnNotation, columnName);

				} else {
					if (!rowNotation.equals(lastRowNotation)) {
						if (valueMap != null) {

							/*
							 * now I've read all the data I need in a row
							 */
							if (!processDataRow(valueMap, lastRowNotation, cellFeedURL)) {
								done = true;
								break;
							}

							valueMap = null;
						}

						lastRowNotation = rowNotation;
					}

					String columnName = columnNameMap.get(columnNotation);
					if (columnName != null) {
						if (!processDataColumn(cell, columnName)) {
							break;
						}

						String value = cell.getCell().getValue();

						if (valueMap == null) {
							valueMap = new HashMap<>();
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

		if (!done && rowNotation != null && valueMap != null) {
			processDataRow(valueMap, lastRowNotation, cellFeedURL);
		}
	}

	protected boolean processDataColumn(CellEntry cell, String columnName) {
		return true;
	}

	protected void processHeaderColumn(String columnNotation, String columnName) {
	}

	protected abstract boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
			throws IOException, ServiceException;
}
