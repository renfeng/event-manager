package com.google.developers.event.qrcode;

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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by frren on 2015-12-11.
 */
public class EmailManager implements MetaSpreadsheet {

	private static final Logger logger = LoggerFactory.getLogger(EmailManager.class);

	/*
	 * image/jpeg; name="IMG_20151128_182644.jpg"
	 */
	private static final Pattern IMAGE_MIME_PATTERN = Pattern.compile("(image/[^;]+); name=\"([^\"]+)\"");

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;

	@Inject
	public EmailManager(HttpTransport transport, JsonFactory jsonFactory) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
	}

	public void receive(String requestBody, String appId)
			throws IOException, MessagingException, ServiceException {

		/*
		 * this is for debugging
		 */
		MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
		cache.put(this.getClass().getCanonicalName(), requestBody);

		PicasawebManager picasawebManager = new PicasawebManager(
				GoogleOAuth2.getGlobalCredential(transport, jsonFactory));
		SpreadsheetManager spreadsheetManager = new SpreadsheetManager(
				GoogleOAuth2.getGlobalCredential(transport, jsonFactory));

		final List<String> urlList = new ArrayList<>();

		StringWriter writer = new StringWriter();
		JsonGenerator generator = jsonFactory.createJsonGenerator(writer);
		generator.writeStartObject();

		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		//logger.info("request body: " + requestBody);
		MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(requestBody.getBytes("UTF-8")));

		/*
		 * TODO check if from address is privileged
		 */
		Address[] from = message.getFrom();

		Address[] to = message.getRecipients(Message.RecipientType.TO);
		Address[] cc = message.getRecipients(Message.RecipientType.CC);
		Address[] bcc = message.getRecipients(Message.RecipientType.BCC);
		String messageId = message.getMessageID();

		/*
		 * collect the event id
		 */
		String suffix = "@" + appId + ".appspotmail.com";

		String toList = StringUtils.join(collect(urlList, suffix, to), ",");
		String ccList = StringUtils.join(collect(urlList, suffix, cc), ",");
		String bccList = StringUtils.join(collect(urlList, suffix, bcc), ",");

		generator.writeFieldName("size");
		generator.writeNumber(message.getSize());

		generator.writeFieldName("from");
		generator.writeString(StringUtils.join(from, ","));

		if (!toList.isEmpty()) {
			generator.writeFieldName("to");
			generator.writeString(toList);
		}

		if (!ccList.isEmpty()) {
			generator.writeFieldName("cc");
			generator.writeString(ccList);
		}

		/*
		 * won't see other bcc recipients
		 */
		if (!bccList.isEmpty()) {
			generator.writeFieldName("bcc");
			generator.writeString(StringUtils.join(bcc, ","));
		}

		/*
		 * subject
		 */
		String subject = message.getSubject();
		generator.writeFieldName("subject");
		generator.writeString(subject);

		/*
		 * content
		 */
		extract(null, message.getContentType(), message.getContent(), generator, picasawebManager, messageId);

		/*
		 * these are null. always?
		 */
//		generator.writeFieldName("content-id");
//		generator.writeString(message.getContentID());
//		generator.writeFieldName("content-language");
//		generator.writeString(Arrays.toString(message.getContentLanguage()));
//		generator.writeFieldName("content-md5");
//		generator.writeString(message.getContentMD5());
//		generator.writeFieldName("description");
//		generator.writeString(message.getDescription());
//		generator.writeFieldName("disposition");
//		generator.writeString(message.getDisposition());
//		generator.writeFieldName("encoding");
//		generator.writeString(message.getEncoding());
//		generator.writeFieldName("filename");
//		generator.writeString(message.getFileName());

		/*
		 * TODO put message id to picasaweb photo description
		 */
		generator.writeFieldName("message-id");
		generator.writeString(messageId);

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

				String url = valueMap.get(GPLUS_EVENT);
				if (urlList.contains(url)) {
					updateCell(TICKET_EMAIL_TEMPLATE, json);

					urlList.remove(url);
					if (urlList.isEmpty()) {
						return false;
					}
				}

				return true;
			}
		};
		cellFeedProcessor.processForUpdate(
				spreadsheetManager.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
				GPLUS_EVENT, TICKET_EMAIL_TEMPLATE);
	}

	private ArrayList<Address> collect(List<String> gplusEventUrl, String suffix, Address[] source) {
		ArrayList<Address> filter = new ArrayList<>();
		if (source != null) {
			for (Address a : source) {
				String email = a.toString();
				if (email.endsWith(suffix)) {
					gplusEventUrl.add("https://plus.google.com/events/" + email.substring(0, email.indexOf("@")));
				} else {
					filter.add(a);
				}
			}
		}
		return filter;
	}

	private void extract(String contentId, String contentType, Object content, JsonGenerator generator,
						 PicasawebManager picasawebManager, String messageId)
			throws MessagingException, IOException, ServiceException {

		/*
		 * multipart/related; boundary=001a1143e186e868c6052607022d
		 * multipart/alternative; boundary=001a1143e186e868c1052607022c
		 * text/plain; charset=UTF-8
		 * text/html; charset=UTF-8
		 * image/jpeg; name="IMG_20151128_182644.jpg"
		 * TODO trim boundary, charset, and name (recoverable from picasaweb) off
		 */
		String field = contentType.split(";")[0];

		generator.writeFieldName(field);
		if (content instanceof MimeMultipart) {
			if ("multipart/related".equals(field)) {
				generator.writeStartArray();
				MimeMultipart parts = (MimeMultipart) content;
				for (int i = 0; i < parts.getCount(); i++) {
					BodyPart bodyPart = parts.getBodyPart(i);
					logger.info(bodyPart.toString());
					if (bodyPart instanceof MimeBodyPart) {
						MimeBodyPart p = (MimeBodyPart) bodyPart;
						logger.info("part " + i + ", content id: " + p.getContentID());
						logger.info("part " + i + ", content type: " + p.getContentType());
						logger.info("part " + i + ", content: " + p.getContent());
						generator.writeStartObject();
						extract(p.getContentID(), p.getContentType(), p.getContent(), generator,
								picasawebManager, messageId);
						generator.writeEndObject();
					} else {
						logger.warn("unhandled {}", bodyPart);
					}
				}
				generator.writeEndArray();
			} else if ("multipart/alternative".equals(field)) {
				generator.writeStartObject();
				MimeMultipart parts = (MimeMultipart) content;
				for (int i = 0; i < parts.getCount(); i++) {
					BodyPart bodyPart = parts.getBodyPart(i);
					logger.info(bodyPart.toString());
					if (bodyPart instanceof MimeBodyPart) {
						MimeBodyPart p = (MimeBodyPart) bodyPart;
						logger.info("part " + i + ", content id: " + p.getContentID());
						logger.info("part " + i + ", content type: " + p.getContentType());
						logger.info("part " + i + ", content: " + p.getContent());
						extract(p.getContentID(), p.getContentType(), p.getContent(), generator,
								picasawebManager, messageId);
					} else {
						logger.warn("unhandled {}", bodyPart);
					}
				}
				generator.writeEndObject();
			} else {
				generator.writeString("unsupported multipart");
			}
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
					PhotoEntry photo = picasawebManager.upload((InputStream) content, type, filename, messageId);
//					System.out.println("Title: " + photo.getTitle().getPlainText());
//					System.out.println("Description: " + photo.getDescription().getPlainText());
//					System.out.println("ID: " + photo.getId());
//					System.out.println("Camera Model: " + photo.getExifTags().getCameraModel());
//					System.out.println("Geo Location: " + photo.getGeoLocation());
//					System.out.println("Media Thumbnail: " + photo.getMediaThumbnails().get(0).getUrl());

					/*
					 * assumes all inline objects are image stored on picacaweb
					 */
					generator.writeStartObject();
					generator.writeFieldName("cid");
					generator.writeString(contentId);
					generator.writeFieldName("gPhotoId");
					generator.writeString(photo.getGphotoId());
					generator.writeEndObject();
				} else {
					generator.writeString("unsupported image type");
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
}
