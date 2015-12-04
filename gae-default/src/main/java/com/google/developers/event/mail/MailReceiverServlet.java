package com.google.developers.event.mail;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.gdata.util.common.util.Base64;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;

@Singleton
public class MailReceiverServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(MailReceiverServlet.class);

	private static final String PREFIX = "/_ah/mail/";

	private final JsonFactory jsonFactory;

	String requestBody;

	@Inject
	public MailReceiverServlet(JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		/*
		 * the address is not available in the path. use to address, instead.
		 */
		String path = req.getServletPath();
		logger.info("path: " + path);
		int beginIndex = path.indexOf(PREFIX) + PREFIX.length();
		String address = path.substring(beginIndex);
		logger.info("address: " + address);

		StringWriter writer = new StringWriter();
		JsonGenerator generator = jsonFactory.createJsonGenerator(writer);
		generator.writeStartObject();

		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			requestBody = IOUtils.toString(req.getInputStream());
			//logger.info("request body: " + requestBody);
			MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(requestBody.getBytes("UTF-8")));

			/*
			 * from
			 */
			generator.writeFieldName("from");
			generator.writeString(Arrays.toString(message.getFrom()));

			/*
			 * to
			 *
			 * TODO only read the first to address
			 */
			generator.writeFieldName("to");
			generator.writeString(Arrays.toString(message.getRecipients(RecipientType.TO)));
			generator.writeFieldName("cc");
			generator.writeString(Arrays.toString(message.getRecipients(RecipientType.CC)));
			generator.writeFieldName("bcc");
			generator.writeString(Arrays.toString(message.getRecipients(RecipientType.BCC)));

			/*
			 * subject
			 */
			generator.writeFieldName("subject");
			generator.writeString(message.getSubject());

			/*
			 * content
			 */
			extract(message.getContentType(), message.getContent(), generator);

			/*
			 * these are null. always?
			 */
			generator.writeFieldName("content-id");
			generator.writeString(message.getContentID());
			generator.writeFieldName("content-language");
			generator.writeString(Arrays.toString(message.getContentLanguage()));
			generator.writeFieldName("content-md5");
			generator.writeString(message.getContentMD5());
			generator.writeFieldName("description");
			generator.writeString(message.getDescription());
			generator.writeFieldName("disposition");
			generator.writeString(message.getDisposition());
			generator.writeFieldName("encoding");
			generator.writeString(message.getEncoding());
			generator.writeFieldName("filename");
			generator.writeString(message.getFileName());

			generator.writeFieldName("message-id");
			generator.writeString(message.getMessageID());

			/*
			 * TODO enqueue a task
			 */
//			for (Address a : from) {
//				Queue q = QueueFactory.getQueue("password-queue");
//				q.add(TaskOptions.Builder.withMethod(TaskOptions.Method.PULL)
//						.payload(a.toString()));
//			}

		} catch (MessagingException e) {
			logger.error("Error processing inbound message", e);
			throw new ServletException("Error processing inbound message", e);
		} catch (IOException e) {
			logger.error("Error processing inbound message", e);
			throw new ServletException("Error processing inbound message", e);
		}

		generator.writeEndObject();
		generator.close();

		/*
		 * FIXME persisnt the email as json
		 */
		logger.info("email received: " + writer.toString());

		return;
	}

	private void extract(String contentType, Object content, JsonGenerator generator) throws MessagingException, IOException {

		generator.writeFieldName(contentType);
		if (content instanceof MimeMultipart) {
			generator.writeStartArray();
			MimeMultipart parts = (MimeMultipart) content;
			for (int i = 0; i < parts.getCount(); i++) {
				BodyPart bodyPart = parts.getBodyPart(i);
				logger.info(bodyPart.toString());
				if (bodyPart instanceof MimeBodyPart) {
					MimeBodyPart p = (MimeBodyPart) bodyPart;
					/*
					 * multipart/alternative
					 * image/jpeg; name="IMG_20151128_182644.jpg"
					 */
					logger.info("part " + i + ", content type: " + p.getContentType());
					logger.info("part " + i + ", content: " + p.getContent());
					generator.writeStartObject();
					extract(p.getContentType(), p.getContent(), generator);
					generator.writeEndObject();
//					if (p.getContentType().equals("multipart/alternative")) {
//
//					} else if (p.getContentType().startsWith("text/plain")) {
//
//					/*
//					 * FIXME parse the content
//					 */
//
//						//break;
//					}
				} else {
					logger.warn("unhandled {}", bodyPart);
				}
			}
			generator.writeEndArray();
		} else if (content instanceof String) {
			generator.writeString(content.toString());
		} else if (content instanceof InputStream) {
			if (contentType.startsWith("image/")) {
				/*
				 * save to g+
				 */
			} else {
				generator.writeString(Base64.encode(IOUtils.toByteArray((InputStream) content)));
			}
		} else {
			generator.writeString(content.toString());
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if (requestBody != null) {
			resp.getWriter().write(requestBody);
		}
	}
}
