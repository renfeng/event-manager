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

					String subject = activeEvent.getTicketEmailSubject();
					subject = subject.replaceAll("[$][{]nickname[}]", nick);

					String uuid = Float.toHexString(new Random().nextFloat());

					try {
						Properties props = new Properties();
						Session session = Session.getDefaultInstance(props, null);
						MimeMessage email = new MimeMessage(session);

						InternetAddress fromAddress = new InternetAddress("suzhou.gdg@gmail.com");
						InternetAddress toAddress = new InternetAddress(valueMap.get(registerEmailColumn));
						InternetAddress ccAddress = new InternetAddress(activeEvent.getTicketEmailCc());

						email.setFrom(fromAddress);
						email.addRecipient(javax.mail.Message.RecipientType.TO, toAddress);
						email.addRecipient(javax.mail.Message.RecipientType.CC, ccAddress);
						email.setSubject(subject, "UTF-8");

						/*
						 * inline images
						 */
						String body = activeEvent.getTemplateCache(
								driveManager, transport, credential.getAccessToken());
						buildMimeMessage(nick, uuid, body, email);

						gmailManager.sendMessage("me", email);

						qrCodeCellEntry.changeInputValueLocal(uuid);
					} catch (MessagingException e) {
						qrCodeCellEntry.changeInputValueLocal(e.getMessage());
					}
					qrCodeCellEntry.update();
				}

				return true;
			}

			private void buildMimeMessage(String nick, String uuid, String body, MimeMessage email)
					throws MessagingException, IOException, ServiceException {

				if (body.startsWith("{")) {
					/*
					 * TODO parse content out of json
					 */
//					String from;
//					String subject;
//					String text;
//					String html;
					JsonParser parser = jsonFactory.createJsonParser(body);
//					JsonToken token = parser.nextToken();
//					if (token == JsonToken.START_OBJECT) {
//						token = parser.nextToken();
//						while (token != JsonToken.END_OBJECT) {
//							/*
//							 * token must be a field name
//							 */
//							if (token != JsonToken.FIELD_NAME) {
//								throw new RuntimeException("field name expected");
//							}
//
//							String field = parser.getText();
//							if (field.equals("size")) {
//								token = parser.nextToken();
//								if (token == JsonToken.VALUE_NUMBER_INT) {
//									/*
//									 * ignore
//									 */
//								}
//							} else if (field.equals("from")) {
//								token = parser.nextToken();
//								if (token == JsonToken.VALUE_STRING) {
//									from = parser.getText();
//								}
//							} else if (field.equals("subject")) {
//								token = parser.nextToken();
//								if (token == JsonToken.VALUE_STRING) {
//									from = parser.getText();
//								}
//							} else if (field.equals("multipart/related")) {
//								token = parser.nextToken();
//								if (token != JsonToken.START_ARRAY) {
//									throw new RuntimeException("array expected for multipart/related");
//								}
//								token = parser.nextToken();
//								while (token != JsonToken.END_ARRAY) {
//									if (token != JsonToken.START_OBJECT) {
//										throw new RuntimeException("object expected for multipart/related");
//									}
//
//								}
//							} else if (field.equals("message-id")) {
//								token = parser.nextToken();
//								if (token == JsonToken.VALUE_STRING) {
//									/*
//									 * ignore
//									 */
//								}
//							}
//
//							token = parser.nextToken();
//						}
//					}
					EmailJson json = parser.parse(EmailJson.class);
					email.setSubject(json.getSubject());
					email.setFrom(new InternetAddress("suzhou.gdg@gmail.com"));

					List<MimePartJson> relatedJson = json.getMultipartRelated();
					if (relatedJson != null) {
						Multipart multipartRelated = new MimeMultipart("related");
						for (MimePartJson r : relatedJson) {
							MimePartJson alternativeJson = r.getMultipartAlternative();
							if (alternativeJson != null) {
								MimeMultipart multipartAlternative = new MimeMultipart("alternative");
								String text = alternativeJson.getText();
								if (text != null) {
									MimeBodyPart part = new MimeBodyPart();
									//part.setText(text, "text/plain; charset=UTF-8");
									part.setContent(text, "text/plain; charset=UTF-8");
									multipartAlternative.addBodyPart(part);
								}

								String html = alternativeJson.getHtml();
								if (html != null) {
									MimeBodyPart part = new MimeBodyPart();
									part.setContent(html, "text/html; charset=UTF-8");
									multipartAlternative.addBodyPart(part);
								}

								MimeBodyPart alternativeBodyPart = new MimeBodyPart();
								alternativeBodyPart.setContent(multipartAlternative);
								multipartRelated.addBodyPart(alternativeBodyPart);
							}

							InlineImage jpg = r.getJpg();
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
								multipartRelated.addBodyPart(part);
							}
						}
						email.setContent(multipartRelated);
					}

					email.setContent("TODO", "text/plain");
				} else {
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
			}
		};
		try {
			cellFeedProcessor.processForUpdate(spreadsheetManager.getWorksheet(registerResponsesURL),
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
