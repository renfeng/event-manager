package com.google.developers.event.qrcode;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.developers.api.*;
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
import java.util.List;
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
			activeEvent = DefaultServletModule.getActiveEvent(req, spreadsheetManager, TICKET_PAGE_URL);
			if (activeEvent == null) {
				throw new ServletException("missing active event");
			}
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		String registerResponsesURL = activeEvent.getRegisterResponsesURL();
		if (registerResponsesURL == null) {
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

					String uuid = Float.toHexString(new Random().nextFloat());

					try {
						Properties props = new Properties();
						Session session = Session.getDefaultInstance(props, null);
						MimeMessage email = new MimeMessage(session);

						String subject = activeEvent.getTicketEmailSubject();
						email.setSubject(subject.replaceAll("[$][{]nickname[}]", nick), "UTF-8");

						email.setFrom(new InternetAddress("suzhou.gdg@gmail.com"));

						String to = valueMap.get(registerEmailColumn);
						email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));

						String cc = activeEvent.getTicketEmailCc();
						if (cc != null) {
							email.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(cc));
						}

						String bcc = activeEvent.getTicketEmailBcc();
						if (bcc != null) {
							email.addRecipient(javax.mail.Message.RecipientType.BCC, new InternetAddress(bcc));
						}

						/*
						 * inline images
						 */
						String body = activeEvent.getTemplateCache(
								driveManager, transport, credential.getAccessToken());
						if (body.startsWith("{")) {
							buildMimeMessage(to, nick, body, email);
						} else {
							buildMimeMessageLegacy(nick, uuid, body, email);
						}

						gmailManager.sendMessage("me", email);

						qrCodeCellEntry.changeInputValueLocal(uuid);
					} catch (MessagingException e) {
						qrCodeCellEntry.changeInputValueLocal(e.getMessage());
					}
					qrCodeCellEntry.update();
				}

				return true;
			}

			private void buildMimeMessageLegacy(String nick, String uuid, String body,
												MimeMessage email) throws MessagingException, IOException {

				/*
				 * set subject internally
				 */
//				String subject = activeEvent.getTicketEmailSubject();
//				email.setSubject(subject.replaceAll("[$][{]nickname[}]", nick), "UTF-8");

				/*
				 * set cc internally
				 */
//				InternetAddress ccAddress = new InternetAddress(activeEvent.getTicketEmailCc());
//				email.addRecipient(javax.mail.Message.RecipientType.CC, ccAddress);

				MimeMultipart multipart = new MimeMultipart("related");
				{
					MimeBodyPart mimeBodyPart = new MimeBodyPart();
					//String body = "<H1>Hello</H1><img src=\"cid:logoBlob\">";
					body = body.replaceAll("[$][{]Logo[}]", "<img src='cid:logoBlob'/>");
					body = body.replaceAll("[$][{]nickname[}]", nick);
					body = body.replaceAll("[$][{]QR code[}]", "<img src='cid:qrCodeBlob'/>");
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
			}

			private void buildMimeMessage(String to, String nick, String body, MimeMessage email)
					throws MessagingException, IOException, ServiceException {

				JsonParser parser = jsonFactory.createJsonParser(body);
				EmailJson json = parser.parse(EmailJson.class);

				/*
				 * don't worry about the subject set by an external editor
				 */
//				String subject = json.getSubject();
//				email.setSubject(subject.replaceAll("[$][{]nickname[}]", nick));

				/*
				 * usually, won't be able to set cc
				 */
//				InternetAddress ccAddress = new InternetAddress(json.getCc());
//				email.addRecipient(javax.mail.Message.RecipientType.CC, ccAddress);

				List<MimePartJson> relatedJson = json.getMultipartRelated();
				if (relatedJson != null && relatedJson.size() > 0) {
					Multipart multipart = new MimeMultipart("related");
					for (MimePartJson r : relatedJson) {
						buildMultipart(to, nick, multipart, r);
					}
					email.setContent(multipart);
				}

				MimePartJson alternativeJson = json.getMultipartAlternative();
				if (alternativeJson != null) {
					Multipart multipart = new MimeMultipart("alternative");
					buildMultipart(to, nick, multipart, alternativeJson);
					email.setContent(multipart);
				}
			}

			private void buildMultipart(String to, String nick, Multipart multipart, MimePartJson json)
					throws MessagingException, IOException, ServiceException {

				MimePartJson alternativeJson = json.getMultipartAlternative();
				if (alternativeJson != null) {
					MimeBodyPart part = new MimeBodyPart();

					MimeMultipart multipartAlternative = new MimeMultipart("alternative");
					buildMultipart(to, nick, multipartAlternative, alternativeJson);
					part.setContent(multipartAlternative);

					multipart.addBodyPart(part);
				}

				String text = json.getText();
				if (text != null) {
					MimeBodyPart part = new MimeBodyPart();
					//part.setText(text, "text/plain; charset=UTF-8");
					text = text.replaceAll("[$][{]nickname[}]", nick);
					text = text.replaceAll("[$][{]to[}]", to);
					part.setContent(text, "text/plain; charset=UTF-8");
					multipart.addBodyPart(part);
				}

				String html = json.getHtml();
				if (html != null) {
					MimeBodyPart part = new MimeBodyPart();
					html = html.replaceAll("[$][{]nickname[}]", nick);
					html = html.replaceAll("[$][{]to[}]", to);
					part.setContent(html, "text/html; charset=UTF-8");
					multipart.addBodyPart(part);
				}

				InlineImage jpg = json.getJpg();
				if (jpg != null) {
					PicasawebManager picasawebManager = new PicasawebManager(
							GoogleOAuth2.getGlobalCredential(transport, jsonFactory));
					String url = picasawebManager.getPhotoUrl(jpg.getgPhotoId());

					HttpRequestFactory factory = transport.createRequestFactory();
					HttpRequest request = factory.buildGetRequest(new GenericUrl(url));
					HttpResponse response = request.execute();

					MimeBodyPart part = new MimeBodyPart();
					part.setContentID(jpg.getCid());
					part.setDataHandler(new DataHandler(new ByteArrayDataSource(
							response.getContent(), response.getContentType())));
					multipart.addBodyPart(part);
				}

				/*
				 * TODO other image formats
				 *
				 * TODO Email Tracking - Measurement Protocol | Analytics Measurement Protocol | Google Developers
				 * https://developers.google.com/analytics/devguides/collection/protocol/v1/email
				 */
			}
		};
		try {
			cellFeedProcessor.processForUpdate(spreadsheetManager.getWorksheet(registerResponsesURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException ex) {
			/*
			 * jsoup to extract text
			 *
			 * TODO show the html to user (also, insert into <head> tag, <base href="https://www.google.com"/>)
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
