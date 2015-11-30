package com.google.developers.event.mail;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.inject.Singleton;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

@Singleton
public class MailReceiverServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(MailReceiverServlet.class);

	private static final String PREFIX = "/_ah/mail/";

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String path = req.getServletPath();
		int beginIndex = path.indexOf(PREFIX) + PREFIX.length();
		String address = path.substring(beginIndex);
		logger.info(address);

		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);
		try {
			MimeMessage message = new MimeMessage(session, req.getInputStream());

			/*
			 * from
			 */
			Address[] from = message.getFrom();
			logger.info("from: " + Arrays.toString(from));

			/*
			 * to
			 */
			logger.info("to: "
					+ Arrays.toString(message.getRecipients(RecipientType.TO)));
			logger.info("cc: "
					+ Arrays.toString(message.getRecipients(RecipientType.CC)));
			logger.info("bcc: "
					+ Arrays.toString(message.getRecipients(RecipientType.BCC)));

			/*
			 * subject
			 */
			logger.info("subject: " + message.getSubject());

			/*
			 * content type
			 */
			logger.info("content type: " + message.getContentType());
			if (message.getContentType().startsWith("text/plain")) {
			}else if (message.getContentType().startsWith("multipart/related")){
				/*
				 * contains embedded image
				 */
			} else {
			}

			/*
			 * content
			 */
			Object content = message.getContent();
			logger.info("content: " + content);
			if (content instanceof MimeMultipart) {
				MimeMultipart parts = (MimeMultipart) content;
				for (int i = 0; i < parts.getCount(); i++) {
					BodyPart bodyPart = parts.getBodyPart(i);
					logger.info(bodyPart.toString());
					logger.info("part " + i + ", content type: " + bodyPart.getContentType());
					logger.info("part " + i + ", content: " + bodyPart.getContent());
					if (bodyPart instanceof MimeBodyPart) {
						MimeBodyPart p = (MimeBodyPart) bodyPart;
						if (p.getContentType().startsWith("text/plain")) {

							/*
							 * FIXME parse the command
							 */

							break;
						} else if (p.getContentType().startsWith("multipart/alternative")) {
						} else if (p.getContentType().startsWith("image/jpeg")) {
							/*
							 * TODO save image to g+
							 */
						}
					}
				}
			} else {
				/*
				 * FIXME if content type = text/plain
				 */
			}

			logger.info("content id: " + message.getContentID());
			logger.info("content languages: "
					+ Arrays.toString(message.getContentLanguage()));
			logger.info("content md5: " + message.getContentMD5());

			logger.info("description: " + message.getDescription());
			logger.info("disposition: " + message.getDisposition());
			logger.info("encoding: " + message.getEncoding());
			logger.info("filename: " + message.getFileName());
			logger.info("message id: " + message.getMessageID());

			/*
			 * TODO enqueue a task
			 */
//			for (Address a : from) {
//				Queue q = QueueFactory.getQueue("password-queue");
//				q.add(TaskOptions.Builder.withMethod(TaskOptions.Method.PULL)
//						.payload(a.toString()));
//			}

		} catch (MessagingException e) {
			throw new ServletException("Error processing inbound message", e);
		} catch (IOException e) {
			throw new ServletException("Error processing inbound message", e);
		}

		return;
	}

}
