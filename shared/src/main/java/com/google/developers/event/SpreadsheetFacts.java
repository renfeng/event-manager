package com.google.developers.event;

import java.text.SimpleDateFormat;

/**
 * Created by renfeng on 10/13/15.
 */
public interface SpreadsheetFacts {

	/*
	 * 2000000
	 * Size: Up to 2 million cells.
	 * https://support.google.com/docs/answer/37603?hl=en
	 */
	int MAX_CELLS = 2000000;

	/*
	 * The literal value of the cell element is the calculated value of the cell,
	 * without formatting applied. If the cell contains a formula, the calculated value is given here.
	 * The Sheets API has no concept of formatting, and thus cannot manipulate formatting of cells.
	 * https://developers.google.com/google-apps/spreadsheets/data#work_with_cell-based_feeds
	 */
	SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("M/d/yyyy");

}
