package com.google.developers.event;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;

/**
 * Created by frren on 2015-07-04.
 */
@Singleton
public class CheckInServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory.getLogger(CheckInServlet.class);
}
