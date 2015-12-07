package com.google.developers.event.mail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.api.PicasawebManager;
import com.google.developers.event.http.OAuth2EntryPage;
import com.google.developers.event.http.OAuth2Utils;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class MailReceiverServlet extends OAuth2EntryPage {

	private static final Logger logger = LoggerFactory
			.getLogger(MailReceiverServlet.class);

	private static final String PREFIX = "/_ah/mail/";

	/*
	 * image/jpeg; name="IMG_20151128_182644.jpg"
	 */
	private static final Pattern IMAGE_MIME_PATTERN = Pattern.compile("(image/[^;]+); name=\"([^\"]+)\"");

	@Inject
	public MailReceiverServlet(HttpTransport transport, JsonFactory jsonFactory, OAuth2Utils oauth2Utils) {
		super(transport, jsonFactory, oauth2Utils);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		final Credential credential = getCredential();
		PicasawebManager picasawebManager = new PicasawebManager(credential);

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
			String requestBody = IOUtils.toString(req.getInputStream());
			MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
			cache.put(this.getClass().getCanonicalName(), requestBody);

			//logger.info("request body: " + requestBody);
			MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(requestBody.getBytes("UTF-8")));

			generator.writeFieldName("size");
			generator.writeNumber(message.getSize());

			generator.writeFieldName("from");
			generator.writeString(Arrays.toString(message.getFrom()));

			/*
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
			extract(message.getContentType(), message.getContent(), generator, picasawebManager);

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
		} catch (ServiceException e) {
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

	private void extract(String contentType, Object content, JsonGenerator generator, PicasawebManager picasawebManager)
			throws MessagingException, IOException, ServiceException {

		generator.writeFieldName(contentType);
		if (content instanceof MimeMultipart) {
			/*
			 * multipart/related
			 * multipart/alternative
			 */
			generator.writeStartArray();
			MimeMultipart parts = (MimeMultipart) content;
			for (int i = 0; i < parts.getCount(); i++) {
				BodyPart bodyPart = parts.getBodyPart(i);
				logger.info(bodyPart.toString());
				if (bodyPart instanceof MimeBodyPart) {
					MimeBodyPart p = (MimeBodyPart) bodyPart;
					logger.info("part " + i + ", content type: " + p.getContentType());
					logger.info("part " + i + ", content: " + p.getContent());
					generator.writeStartObject();
					extract(p.getContentType(), p.getContent(), generator, picasawebManager);
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
				 * save to picasaweb, and save a link here for recover the photo later,
				 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_java#UploadPhotos
				 */
				Matcher matcher = IMAGE_MIME_PATTERN.matcher(contentType);
				if (matcher.matches()) {
					String type = matcher.group(1);
					String filename = matcher.group(2);
					PhotoEntry entry = picasawebManager.upload((InputStream) content, type, filename);
					generator.writeString(entry.getId());
				} else {
					generator.writeString("TODO");
				}

			} else {
				generator.writeString(Base64.encode(IOUtils.toByteArray((InputStream) content)));
			}
		} else {
			generator.writeString(content.toString());
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
		String requestBody = (String) cache.get(this.getClass().getCanonicalName());
		if (requestBody != null) {
			resp.getWriter().write(requestBody);
		}
	}
}
