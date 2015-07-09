package hu.dushu.developers.event.server.http;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.dushu.developers.event.server.ActiveEvent;
import hu.dushu.developers.event.server.DevelopersSharedModule;
import hu.dushu.developers.event.server.google.CellFeedProcessor;
import hu.dushu.developers.event.server.google.SpreadsheetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by renfeng on 6/17/15.
 */
@Singleton
public class CheckInServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(CheckInServlet.class);

	public static final String QR_CODE_COLUMN = "QR code";

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;
	private final Provider<ActiveEvent> activeEventProvider;

	@Inject
	public CheckInServlet(HttpTransport transport, JsonFactory jsonFactory,
	                      SpreadsheetManager spreadsheetManager, Provider<ActiveEvent> activeEventProvider) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
		this.activeEventProvider = activeEventProvider;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		final String uuid = req.getParameter("uuid");
		final ThreadLocal<String> emailThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> numberThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> errorThreadLocal = new ThreadLocal<>();

		/*
		 * retrieve the urls of register and check-in for the latest event
		 */

		ActiveEvent activeEvent = activeEventProvider.get();

		try {
			Date eventDate = new Date();
			logger.info("Event date (now): " + eventDate);
			activeEvent.refresh(eventDate);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		/*
		 * check if the id is registered
		 */
		{
			String registerURL = activeEvent.getRegisterResponsesURL();
			if (registerURL == null) {
				throw new ServletException(
						"Missing URL to the register form responses of event, " + activeEvent.getEvent());
			}

			final String registerEmailColumn = activeEvent.getRegisterEmailColumn();
			if (registerEmailColumn == null) {
				throw new ServletException(
						"Missing emailAddress column mapping for the register form responses of event, " +
								activeEvent.getEvent());
			}

			final String registerNameColumn = activeEvent.getRegisterNameColumn();
			if (registerNameColumn == null) {
				throw new ServletException(
						"Missing nickname column mapping for the register form responses of event, " +
								activeEvent.getEvent());
			}

			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

				private int number = 1;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
						throws IOException, ServiceException {
					String qrCode = valueMap.get(QR_CODE_COLUMN);
					if (qrCode != null && qrCode.equals(uuid)) {
						emailThreadLocal.set(valueMap.get(registerEmailColumn));
						nameThreadLocal.set(valueMap.get(registerNameColumn));
						numberThreadLocal.set(String.format("%03d", number));
						return false;
					}

					number++;

					return true;
				}
			};
			try {
				cellFeedProcessor.process(registerURL, registerEmailColumn, QR_CODE_COLUMN, registerNameColumn);
			} catch (ServiceException e) {
				throw new ServletException(e);
			}
		}

		final String email = emailThreadLocal.get();
		if (email != null) {
			/*
			 * check if the id is available for check in
			 */
			String checkInResponsesURL = activeEvent.getCheckInResponsesURL();
			if (checkInResponsesURL == null) {
				throw new ServletException(
						"Missing URL to the check-in form responses of event, " + activeEvent.getEvent());
			}

			final String checkInEmailColumn = activeEvent.getCheckInEmailColumn();
			if (checkInEmailColumn == null) {
				throw new ServletException(
						"Missing emailAddress column mapping for the check-in form responses of event, " +
								activeEvent.getEvent());
			}

			String checkInTimestampColumn = activeEvent.getCheckInTimestampColumn();
			if (checkInTimestampColumn == null) {
				throw new ServletException(
						"Missing timestamp column mapping for the check-in form responses of event, " +
								activeEvent.getEvent());
			}

//			numberThreadLocal.set("001");
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

//				private int number = 1;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, String rowNotation, URL cellFeedURL)
						throws IOException, ServiceException {
					if (valueMap.containsKey(checkInEmailColumn) &&
							valueMap.get(checkInEmailColumn).equals(email)) {
						errorThreadLocal.set("Already checked in.");
						return false;
					}

//					number++;
//					numberThreadLocal.set(String.format("%03d", number));

					return true;
				}
			};
			try {
			    /*
			     * timestamp column here is to ensure the number won't duplicate when the email is removed to
                 * allow check-in again.
                 */
				cellFeedProcessor.process(checkInResponsesURL, checkInEmailColumn, checkInTimestampColumn);
			} catch (ServiceException e) {
				throw new ServletException(e);
			}
		} else {
			errorThreadLocal.set("Invalid QR code");
		}

		String error = errorThreadLocal.get();
		if (error == null) {
			String clientIp = req.getRemoteAddr();

			/*
			 * http post to formResponse with email entry parameter
			 */
			HttpRequestFactory factory = transport.createRequestFactory();

			GenericUrl url = new GenericUrl(activeEvent.getCheckInFormURL());

			Map<String, Object> params = new HashMap<>();
			params.put(activeEvent.getCheckInEmailEntry(), email);
			params.put(activeEvent.getCheckInClientIp(), clientIp);

			HttpContent content = new UrlEncodedContent(params);
			HttpRequest request = factory.buildPostRequest(url, content);

			HttpResponse response = request.execute();

			int statusCode = response.getStatusCode();
			if (statusCode != 200) {
				resp.setStatus(statusCode);
				return;
			}
		}

		/*
		 * headers must be set before body
		 *
		 * java - Response encoding of Google App Engine (can not change response encoding) - Stack Overflow
		 * http://stackoverflow.com/a/9447068/333033
		 */
		resp.setContentType("text/javascript; charset=utf-8");

		/*
		 * return nick name for label printer
		 */
		String name = nameThreadLocal.get();
		String number = numberThreadLocal.get();

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("number");
		generator.writeString(number);
		generator.writeFieldName("name");
		generator.writeString(name);
		generator.writeFieldName("error");
		generator.writeString(error);
		generator.writeEndObject();
		generator.flush();
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		MemcacheServiceFactory.getMemcacheService().delete(DevelopersSharedModule.ACTIVE_EVENT_MEMCACHE);
	}
}
