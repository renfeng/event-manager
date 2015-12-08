package com.google.developers.event.mail;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.GoogleOAuth2;
import com.google.developers.api.PicasawebManager;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.DevelopersSharedModule;
import com.google.developers.event.MetaSpreadsheet;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.common.util.Base64;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
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
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class MailReceiverServlet extends HttpServlet implements MetaSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(MailReceiverServlet.class);

	private static final String PREFIX = "/_ah/mail/";
	/*
	 * image/jpeg; name="IMG_20151128_182644.jpg"
	 */
	private static final Pattern IMAGE_MIME_PATTERN = Pattern.compile("(image/[^;]+); name=\"([^\"]+)\"");

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
	public MailReceiverServlet(HttpTransport transport, JsonFactory jsonFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		PicasawebManager picasawebManager = new PicasawebManager(
				GoogleOAuth2.getGlobalCredential(transport, jsonFactory));
		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(
				GoogleOAuth2.getGlobalCredential(transport, jsonFactory));

		final String gplusEventUrl;
		final String subject;
		final Address[] to;
		final Address[] cc;
		final Address[] bcc;

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

			to = message.getRecipients(RecipientType.TO);
			generator.writeFieldName("to");
			generator.writeString(Arrays.toString(to));

			cc = message.getRecipients(RecipientType.CC);
			generator.writeFieldName("cc");
			generator.writeString(Arrays.toString(cc));

			/*
			 * won't see other bcc recipients
			 */
			bcc = message.getRecipients(RecipientType.BCC);
			generator.writeFieldName("bcc");
			generator.writeString(Arrays.toString(bcc));

			/*
			 * only read the first to address
			 *
			 * TODO what if the first is not the intended?
			 * TODO support multiple events?
			 */
			if (to == null || to.length == 0) {
				throw new ServletException("missing email address");
			}
			if (to.length > 1) {
				throw new ServletException("unsupported multiple email addresses");
			}

			String target = to[0].toString();

			String appid = getServletContext().getInitParameter("appengine.app.id");
			if (!target.endsWith("@" + appid + ".appspotmail.com")) {
				/*
				 * ignore the to address as it must comes from bcc
				 */
				target = bcc[0].toString();
			}

			gplusEventUrl = "https://plus.google.com/events/" + target.substring(0, target.indexOf("@"));

			/*
			 * subject
			 */
			subject = message.getSubject();
			generator.writeFieldName("subject");
			generator.writeString(subject);

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
		} catch (ServiceException e) {
			logger.error("Error processing inbound message", e);
			throw new ServletException("Error processing inbound message", e);
		}

		generator.writeEndObject();
		generator.close();

		/*
		 * FIXME persisnt the email as json
		 */
		final String json = writer.toString();
		logger.info("email received: " + json);

		CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager.getService()) {

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
					throws IOException, ServiceException {

				if (gplusEventUrl.equals(valueMap.get(GPLUS_EVENT))) {
					updateCell(TICKET_EMAIL_TEMPLATE, json);
					updateCell(TICKET_EMAIL_SUBJECT, subject);

					/*
					 * TODO join the strings
					 */
					updateCell(TICKET_EMAIL_CC, Arrays.toString(cc));

					/*
					 * TODO how to define bcc
					 */
//					updateCell(TICKET_EMAIL_BCC, "");

					return false;
				}

				return true;
			}
		};
		try {
			cellFeedProcessor.processForUpdate(
					spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
					GPLUS_EVENT, TICKET_EMAIL_TEMPLATE, TICKET_EMAIL_SUBJECT, TICKET_EMAIL_CC, TICKET_EMAIL_BCC);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

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
				 * While all photos appearing on the Picasa Web Albums site are in the JPEG format,
				 * photos of any of the following types can be uploaded using the API:
				 * image/bmp
				 * image/gif
				 * image/jpeg
				 * image/png
				 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_protocol#PostPhotos
				 */
				/*
				 * save to picasaweb, and save a link here for recover the photo later,
				 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_java#UploadPhotos
				 */
				Matcher matcher = IMAGE_MIME_PATTERN.matcher(contentType);
				if (matcher.matches()) {
					String type = matcher.group(1);
					String filename = matcher.group(2);
					PhotoEntry photo = picasawebManager.upload((InputStream) content, type, filename);
//					System.out.println("Title: " + photo.getTitle().getPlainText());
//					System.out.println("Description: " + photo.getDescription().getPlainText());
//					System.out.println("ID: " + photo.getId());
//					System.out.println("Camera Model: " + photo.getExifTags().getCameraModel());
//					System.out.println("Geo Location: " + photo.getGeoLocation());
//					System.out.println("Media Thumbnail: " + photo.getMediaThumbnails().get(0).getUrl());

					generator.writeString(photo.getGphotoId());
				} else {
					generator.writeString("TODO");
				}

			} else if (contentType.startsWith("video/")) {
				/*
				 * TODO upload video to youtube
				 *
				 * Maximum file size: 64GB
				 * Accepted Media MIME types: video/*, application/octet-stream
				 * https://developers.google.com/youtube/v3/docs/videos/insert
				 *
				 * A video is posted in the same way as a photo with metadata. Instead of an image MIME type you have to
				 * use an appropriate video MIME type. The recognized video MIME types are:
				 * video/3gpp
				 * video/avi
				 * video/quicktime
				 * video/mp4
				 * video/mpeg
				 * video/mpeg4
				 * video/msvideo
				 * video/x-ms-asf
				 * video/x-ms-wmv
				 * video/x-msvideo
				 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_protocol#PostVideo
				 */
				generator.writeString("Video attachment is not supported at the moment.");
				logger.warn("Video attachment is not supported at the moment.");
			} else {
				generator.writeString(Base64.encode(IOUtils.toByteArray((InputStream) content)));
			}
		} else {
			generator.writeString(content.toString());
			logger.warn("content: " + contentType);
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
