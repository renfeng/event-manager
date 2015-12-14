package com.google.developers.event.mail;

import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.developers.event.EmailManager;
import com.google.developers.event.MetaSpreadsheet;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
public class MailReceiverServlet extends HttpServlet implements MetaSpreadsheet {

	private static final Logger logger = LoggerFactory
			.getLogger(MailReceiverServlet.class);

	private static final String PREFIX = "/_ah/mail/";

	private final EmailManager emailManager;

	@Inject
	public MailReceiverServlet(EmailManager emailManager) {
		this.emailManager = emailManager;
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

		try {
			emailManager.receive(IOUtils.toString(req.getInputStream()),
					getServletContext().getInitParameter("appengine.app.id"));
		} catch (MessagingException e) {
			logger.error("Error processing inbound message", e);
			throw new ServletException("Error processing inbound message", e);
		} catch (ServiceException e) {
			logger.error("Error processing inbound message", e);
			throw new ServletException("Error processing inbound message", e);
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
