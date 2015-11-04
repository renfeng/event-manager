package com.google.developers.event.mail;

import com.google.appengine.api.mail.BounceNotification;
import com.google.appengine.api.mail.BounceNotificationParser;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
public class BounceHandlerServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(BounceHandlerServlet.class);

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		try {
			BounceNotification bounce = BounceNotificationParser.parse(req);

			/*
			 * The following data is available in a BounceNotification object
			 */
			logger.info(bounce.getOriginal().getFrom());
			logger.info(bounce.getOriginal().getTo());
			logger.info(bounce.getOriginal().getSubject());
			logger.info(bounce.getOriginal().getText());
			logger.info(bounce.getNotification().getFrom());
			logger.info(bounce.getNotification().getTo());
			logger.info(bounce.getNotification().getSubject());
			logger.info(bounce.getNotification().getText());

		} catch (MessagingException ex) {
			throw new ServletException(ex);
		}

		return;
	}
}