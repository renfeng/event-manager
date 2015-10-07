package com.google.developers.event.qrcode;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.DriveManager;
import com.google.developers.api.GmailManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.developers.event.RegisterFormResponseSpreadsheet;
import com.google.developers.event.http.DefaultServletModule;
import com.google.developers.event.http.Path;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.common.util.Base64;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
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
public class SendQrServlet extends HttpServlet
		implements Path, RegisterFormResponseSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(SendQrServlet.class);

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;
	private final GmailManager gmailManager;
	private final DriveManager driveManager;

	@Inject
	public SendQrServlet(
			HttpTransport transport, JsonFactory jsonFactory,
			SpreadsheetManager spreadsheetManager, GmailManager gmailManager,
			DriveManager driveManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
		this.gmailManager = gmailManager;
		this.driveManager = driveManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.getRequestDispatcher("/send-qr/index.html").forward(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		final String qrCode = req.getParameter("qrCode");

		final ActiveEvent activeEvent;
		try {
			activeEvent = DefaultServletModule.getActiveEvent(
					req, spreadsheetManager, SEND_QR_URL);
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

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

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
					 * TODO send as the user (oauth2), c.f. the system
					 * TODO add a column to track sender
					 */

					String nick = valueMap.get(registerNameColumn);
					String subject = activeEvent.getTicketEmailSubject() + nick;
					String uuid = Float.toHexString(new Random().nextFloat());

					try {
						/*
						 * inline images
						 */
						String body = activeEvent.getTemplateCache(driveManager, transport);
//						String body = "<H1>Hello</H1><img src=\"cid:logoBlob\">";
						body = body.replaceAll("[$][{]Logo[}]", "<img src='cid:logoBlob'/>");
						body = body.replaceAll("[$][{]nickname[}]", nick);
						body = body.replaceAll("[$][{]QR code[}]", "<img src='cid:qrCodeBlob'/>");
						String from = "suzhou.gdg@gmail.com";
						String to = valueMap.get(registerEmailColumn);

//						MimeMessage email = GmailManager.createEmail(
//								to, from, subject, body);
						Properties props = new Properties();
						Session session = Session.getDefaultInstance(props, null);

						MimeMessage email = new MimeMessage(session);
						InternetAddress tAddress = new InternetAddress(to);
						InternetAddress fAddress = new InternetAddress(from);

						email.setFrom(fAddress);
						email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
						email.setSubject(subject);

						Multipart multipart = new MimeMultipart("related");
						{
							MimeBodyPart mimeBodyPart = new MimeBodyPart();
							mimeBodyPart.setContent(body, "text/html; charset=\"UTF-8\"");
//							mimeBodyPart.setHeader("Content-Type", "text/html; charset=\"UTF-8\"");
							multipart.addBodyPart(mimeBodyPart);
						}
						{
							/*
							 * TODO determine the mine type through drive api
							 */
							MimeBodyPart mimeBodyPart = new MimeBodyPart();
							mimeBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(
									activeEvent.getLogoCache(driveManager), "image/png")));
//							mimeBodyPart.setDataHandler(new DataHandler(new URLDataSource(new URL(
//									"data:image/png;base64," + Base64.encode(activeEvent.getLogoCache(driveManager))))));
//							mimeBodyPart.setHeader("Content-ID", "<logoBlob>");
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
//							mimeBodyPart.setHeader("Content-ID", "<qrCodeBlob>");
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
			cellFeedProcessor.processForBatchUpdate(spreadsheetManager.getWorksheet(registerURL),
					registerNameColumn, registerEmailColumn, QR_CODE_COLUMN);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}
	}
}
