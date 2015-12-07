package com.google.developers.event.qrcode;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.DriveManager;
import com.google.developers.api.GmailManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.OAuth2Utils;
import com.google.developers.event.http.Path;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Created by frren on 2015-09-29.
 */
@Singleton
public class TicketAPI extends HttpServlet
		implements Path, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(TicketAPI.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final OAuth2Utils oauth2Utils;

	@Inject
	public TicketAPI(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.oauth2Utils = oauth2Utils;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		final String qrCode = req.getParameter("qrCode");

		final Credential credential = oauth2Utils.initializeFlow().loadCredential(
				UserServiceFactory.getUserService().getCurrentUser().getEmail());

		/*
		 * TODO https://developers.google.com/api-client-library/java/google-oauth-java-client/oauth2?hl=en#detecting_an_expired_access_token
		 */

		/*
		 * http://stackoverflow.com/questions/10827920/google-oauth-refresh-token-is-not-being-received
		 */
		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);
		final GmailManager gmailManager = new GmailManager(transport, jsonFactory, credential);
		final DriveManager driveManager = new DriveManager(transport, jsonFactory, credential);

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, TICKET_PAGE_URL);
			if (activeEvent == null) {
				throw new ServletException("missing active event");
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

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

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			CellEntry qrCodeCellEntry;

			@Override
			protected void processDataColumn(CellEntry cell, String columnName) {
				if (QR_CODE_COLUMN.equals(columnName)) {
					qrCodeCellEntry = cell;
				}
			}

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				if (qrCode.equals(valueMap.get(QR_CODE_COLUMN))) {
					/*
					 * removed cc and bcc
					 *
					 * send as the user (oauth2), c.f. the system
					 * TODO add a column to track sender
					 */

					String nick = valueMap.get(registerNameColumn);

					String subject = activeEvent.getTicketEmailSubject();
					subject = subject.replaceAll("[$][{]nickname[}]", nick);

					String uuid = Float.toHexString(new Random().nextFloat());

					try {
						/*
						 * inline images
						 */
						String body = activeEvent.getTemplateCache(
								driveManager, transport, credential.getAccessToken());
//						String body = "<H1>Hello</H1><img src=\"cid:logoBlob\">";
						body = body.replaceAll("[$][{]Logo[}]", "<img src='cid:logoBlob'/>");
						body = body.replaceAll("[$][{]nickname[}]", nick);
						body = body.replaceAll("[$][{]QR code[}]", "<img src='cid:qrCodeBlob'/>");
						String from = "suzhou.gdg@gmail.com";
						String to = valueMap.get(registerEmailColumn);

						Properties props = new Properties();
						Session session = Session.getDefaultInstance(props, null);

						MimeMessage email = new MimeMessage(session);
						InternetAddress tAddress = new InternetAddress(to);
						InternetAddress fAddress = new InternetAddress(from);

						email.setFrom(fAddress);
						email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
						email.setSubject(subject, "UTF-8");

						Multipart multipart = new MimeMultipart("related");
						{
							MimeBodyPart mimeBodyPart = new MimeBodyPart();
							mimeBodyPart.setContent(body, "text/html; charset=UTF-8");
							multipart.addBodyPart(mimeBodyPart);
						}
						{
							/*
							 * TODO determine the mine type through drive api
							 */
							MimeBodyPart mimeBodyPart = new MimeBodyPart();
							mimeBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(
									activeEvent.getLogoCache(driveManager), "image/png")));
							mimeBodyPart.setContentID("<logoBlob>");
							mimeBodyPart.setDisposition(MimeBodyPart.INLINE);

							multipart.addBodyPart(mimeBodyPart);
						}
						{
							HttpRequestFactory factory = transport.createRequestFactory();
							HttpRequest request = factory.buildGetRequest(new GenericUrl(
									"http://chart.apis.google.com/chart?cht=qr&chs=300x300&chld=H|0&chl=" + uuid));
							HttpResponse response = request.execute();

							MimeBodyPart mimeBodyPart = new MimeBodyPart();
							mimeBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(
									response.getContent(), "image/png")));
							mimeBodyPart.setContentID("<qrCodeBlob>");
							mimeBodyPart.setDisposition(MimeBodyPart.INLINE);

							multipart.addBodyPart(mimeBodyPart);
						}
						email.setContent(multipart);

						gmailManager.sendMessage("me", email);

						qrCodeCellEntry.changeInputValueLocal(uuid);
					} catch (MessagingException e) {
						qrCodeCellEntry.changeInputValueLocal(e.getMessage());
					}
					qrCodeCellEntry.update();
				}

				return true;
			}
		};
		try {
			cellFeedProcessor.processForUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException ex) {
			/*
			 * jsoup to extract text
			 *
			 * TODO show the html to user (insert into <head> tag, <base href="https://www.google.com"/>)
			 */
			String message;
			if (ex.getResponseBody() != null &&
					ex.getResponseContentType().match(new ContentType("text/html"))) {
				Document document = Jsoup.parse(ex.getResponseBody());

				String s = ex.getClass().getName();
				String localizedMessage = ex.getLocalizedMessage();
				message = (localizedMessage != null ? (s + ": " + localizedMessage) : s) + "\n" +
						document.text();
			} else {
				message = ex.toString();
			}
			logger.debug("error: " + message);
			throw new ServletException(message);
		}
	}
}
