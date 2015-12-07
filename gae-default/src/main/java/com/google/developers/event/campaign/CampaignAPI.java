package com.google.developers.event.campaign;

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
import com.google.developers.event.http.OAuth2EntryPage;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
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
import java.util.List;
import java.util.Map;

/**
 * Created by renfeng on 10/13/15.
 */
@Singleton
public class CampaignAPI extends OAuth2EntryPage
		implements Path, CampaignSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(CampaignAPI.class);

	@Inject
	public CampaignAPI(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

		// Get the stored credentials using the Authorization Flow
//		AuthorizationCodeFlow authFlow = initializeFlow();
//		Credential credential = authFlow.loadCredential(getUserId(req));
		Credential credential = getCredential();

		ContactManager contactManager = new ContactManager(credential);
		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);

		try {
			ActiveEvent activeEvent = DefaultServletModule.getActiveEvent(req, spreadsheetManager, CAMPAIGN_PAGE_URL);
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

			CellFeedProcessor processor = new CellFeedProcessor(spreadsheetManager.getService()) {

				int i;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL) throws IOException, ServiceException {

					ParticipantStatistics participant = activities.get(i);
					updateCell(NICK, participant.getNickname());
					updateCell(EMAIL, participant.getEmail());

					String gplusID = participant.getGplusID();
					if (gplusID != null) {
						updateCell(GPLUS, gplusID.startsWith("+") ? "'" + gplusID : gplusID);
					}

					i++;

					return true;
				}

			};
			processor.processForUpdate(sheet, NICK, EMAIL, EMAIL_SENT, EMAIL_REPLIED, EMAIL_BOUNCED, GPLUS,
					GPLUS_EVENT_INVITED, GPLUS_EVENT_ACCEPTED, GPLUS_EVENT_MAYBE, GPLUS_EVENT_DENIED);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}
}
