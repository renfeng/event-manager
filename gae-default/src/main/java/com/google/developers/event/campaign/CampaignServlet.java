package com.google.developers.event.campaign;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.ContactManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.CampaignSpreadsheet;
import com.google.developers.event.ParticipantStatistics;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.OAuth2EntryServlet;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by renfeng on 10/13/15.
 */
@Singleton
public class CampaignServlet extends OAuth2EntryServlet
		implements Path, CampaignSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(CampaignServlet.class);

	@Inject
	public CampaignServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		req.getRequestDispatcher(CAMPAIGN_URL + "index.html").forward(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

		// Get the stored credentials using the Authorization Flow
		AuthorizationCodeFlow authFlow = initializeFlow();
		Credential credential = authFlow.loadCredential(getUserId(req));

		ContactManager contactManager = new ContactManager(credential);
		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);

		try {
			ActiveEvent activeEvent = DefaultServletModule.getActiveEvent(req, spreadsheetManager, CAMPAIGN_URL);
			if (activeEvent == null) {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.YEAR, -2);
			final List<ParticipantStatistics> activities = contactManager.listActivities(calendar.getTime());

			WorksheetEntry sheet = spreadsheetManager.getWorksheet(activeEvent.getCampaignURL());
			sheet.setRowCount(Math.min(activities.size(), MAX_CELLS / sheet.getColCount()));
			sheet.update();

			long startTime = System.currentTimeMillis();
			CellFeed batchRequest = new CellFeed();
			final List<CellEntry> entries = batchRequest.getEntries();

			CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager.getService()) {

				Map<String, CellEntry> cellMap = new HashMap<>();
				int i;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL) throws IOException, ServiceException {

					ParticipantStatistics participant = activities.get(i);
					updateCell(entries, cellMap.get(NICK), participant.getNickname());
					updateCell(entries, cellMap.get(EMAIL), participant.getEmail());

					String gplusID = participant.getGplusID();
					if (gplusID != null) {
						updateCell(entries, cellMap.get(GPLUS), gplusID.startsWith("+") ? "'" + gplusID : gplusID);
					}

					i++;

					return true;
				}

				@Override
				protected void processDataColumn(CellEntry cell, String columnName) {
					cellMap.put(columnName, cell);
				}

			};
			processor.processForUpdate(sheet, NICK, EMAIL, EMAIL_SENT, EMAIL_REPLIED, EMAIL_BOUNCED, GPLUS,
					GPLUS_EVENT_INVITED, GPLUS_EVENT_ACCEPTED, GPLUS_EVENT_MAYBE, GPLUS_EVENT_DENIED);


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

			logger.info("contact rows updated: " + (processor.getRow() - 1));
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}
}
