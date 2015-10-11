package com.google.developers.event;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by renfeng on 5/22/15.
 */
public class EventManagerTest {

	private final Injector injector = Guice.createInjector(Stage.DEVELOPMENT,
			new DevelopersSharedModule());

	@Test
	public void test() throws IOException, ServiceException {

		EventManager eventManager = injector.getInstance(EventManager.class);
		eventManager.importContactsFromSpreadsheets();
		eventManager.updateRanking();
		eventManager.updateEventScore();

//		SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yyyy HH:mm:ss");
//		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+08"));
//		try {
//			Date date = dateFormat.parse("6/26/2015 20:00:00");
//			System.out.println(date);
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}

	}

	@Test
	public void averageCostPerParticipant() throws IOException, ServiceException {

		final ThreadLocal<Integer> participantsThreadLocal = new ThreadLocal<>();
		final ThreadLocal<Float> costThreadLocal = new ThreadLocal<>();

		SpreadsheetManager spreadsheetManager = injector.getInstance(SpreadsheetManager.class);
		CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager.getService()) {

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				String participantsString = valueMap.get("参加人数");
				if (participantsString != null) {
					try {
						participantsThreadLocal.set(participantsThreadLocal.get() +
								Integer.parseInt(participantsString));
					} catch (NumberFormatException ex) {
						/*
						 * ignore
						 */
					}
				}

				String costString = valueMap.get("报销总数");
				if (costString == null) {
					costString = valueMap.get("发票总数");
				}
				if (costString != null) {
					try {
						costThreadLocal.set(costThreadLocal.get() +
								Float.parseFloat(costString));
					} catch (NumberFormatException ex) {
						/*
						 * ignore
						 */
					}
				}

				return true;
			}
		};

		int participants = 0;
		float cost = 0;
		Map<String, Float> map = new HashMap<>();

		for (WorksheetEntry sheet : spreadsheetManager.listWorksheets("1xZ4synqj-jHFWuISxgePtRybz1o3b51FmTn7knfVbyI")) {
			participantsThreadLocal.set(0);
			costThreadLocal.set(0F);

			String city = sheet.getTitle().getPlainText();
			System.out.print(city + ",");
			processor.process(sheet, "参加人数", "报销总数", "发票总数");

			float average = costThreadLocal.get() / participantsThreadLocal.get();
			System.out.println(String.format("%1$.2f", average));

			if (!Float.isNaN(average)) {
				map.put(city, average);
			}

			participants += participantsThreadLocal.get();
			cost += costThreadLocal.get();
		}
		System.out.println(String.format("%1$s, %2$.2f", "all", cost / participants));

		/*
		 * collections - How to sort a Map<Key, Value> on the values in Java? - Stack Overflow
		 * http://stackoverflow.com/a/3420912/333033
		 */
		Ordering<String> valueComparator = Ordering.natural().onResultOf(Functions.forMap(map));
		map = ImmutableSortedMap.copyOf(map, valueComparator);
		for (Map.Entry<String, Float> entry : map.entrySet()) {
			String city = entry.getKey();
			Float average = entry.getValue();
			System.out.println(String.format("%1$s, %2$.2f", city, average));
		}

		/*
		 * TODO china summit measurements
		 */
	}
}
