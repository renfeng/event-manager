package com.google.developers.event.http;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;
import com.google.developers.api.*;
import com.google.developers.event.DevelopersSharedModule;
import com.google.developers.event.EventActivities;
import com.google.developers.event.model.TopekaCategory;
import com.google.developers.event.model.TopekaQuiz;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Created by renfeng on 7/20/15.
 */
@Singleton
public class ActivitiesServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(ActivitiesServlet.class);

	/*
	 * e.g. Wed, August 5, 8:00 PM
	 *
	 * http://docs.oracle.com/javase/6/docs/api/java/text/SimpleDateFormat.html
	 */
	private static final SimpleDateFormat GPLUS_EVENT_DATE_TIME_FORMAT =
			new SimpleDateFormat("EEE, MMMMM d, h:mm a");

	private final JsonFactory jsonFactory;
	private final HttpTransport transport;

	@Inject
	public ActivitiesServlet(HttpTransport transport, JsonFactory jsonFactory) {
		this.jsonFactory = jsonFactory;
		this.transport = transport;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		Credential credential = GoogleOAuth2.getGlobalCredential(transport, jsonFactory);

		final SpreadsheetManager spreadsheetManager = new SpreadsheetManager(credential);
		CalendarManager calendarManager = new CalendarManager(transport, jsonFactory, credential);
		GPlusManager gplusManager = new GPlusManager(transport, jsonFactory, credential);

		List<Event> publishedEvents = new ArrayList<>();
		List<String> eventsNotOnCalendar = new ArrayList<>();

		Map<String, TopekaCategory> categoryMap = new HashMap<>();

		/*
		 * maps G+ event url to objects
		 */
		Map<String, TopekaQuiz> quizMap = new HashMap<>();
		Map<String, Event> eventMap = calendarManager.listEvents();

		Plus plus = gplusManager.getClient();
		Plus.Activities.List listActivities = plus.activities().list("me", "public");
//		listActivities.setMaxResults(5L);

		// Execute the request for the first page
		ActivityFeed activityFeed = listActivities.execute();

		// Unwrap the request and extract the pieces we want
		List<Activity> activities = activityFeed.getItems();
		while (activities != null) {
			for (Activity activity : activities) {
//				System.out.println("ID " + activity.getId() + " Content: " +
//						activity.getObject().getContent());

				/*
				 * TODO separate events from posts
				 */
				if (!"share".equals(activity.getVerb())) {
					continue;
				}

				DateTime updated = activity.getUpdated();

				Activity.PlusObject object = activity.getObject();
				String objectUrl = object.getUrl();
				String objectContent = object.getContent();
				String objectActorId = object.getActor().getId();
				String objectActorDisplayName = object.getActor().getDisplayName();

				/*
				 * TODO parse hash tags from content
				 *
				 * TODO replace +xxx (g+ id), and hash tag with links
				 */

				/*
				 * assemble G+ like post
				 */
				String answer;
				if (object.getAttachments() != null) {
					Activity.PlusObject.Attachments attachments = object.getAttachments().get(0);
					Activity.PlusObject.Attachments.Image image = attachments.getImage();
					String url = attachments.getUrl();
					String objectType = attachments.getObjectType();
					String displayName = attachments.getDisplayName();

					if ("photo".equals(objectType)) {
						String template = DevelopersSharedModule.getMessage("gplus.photo");
						answer = String.format(template,
								objectActorId,
								objectActorDisplayName,
								objectUrl,
								objectContent,
								url,
								image.getUrl()
						);
					} else if ("article".equals(objectType)) {
						if (image != null) {
							String template = DevelopersSharedModule.getMessage("gplus.article");
							answer = String.format(template,
									objectActorId,
									objectActorDisplayName,
									objectUrl,
									objectContent,
									url,
									image.getUrl(),
									displayName
							);
						} else {
							String template = DevelopersSharedModule.getMessage("gplus.article.noimage");
							answer = String.format(template,
									objectActorId,
									objectActorDisplayName,
									objectUrl,
									objectContent,
									url,
									attachments.getContent(),
									displayName,
									new URL(url).getHost()
							);
						}
					} else if ("event".equals(objectType)) {
						/*
						 * G+ alone doesn't provide start/end date and location of an event.
						 * While Calendar doesn't distinguish G+ events from other non-G+ events quite well,
						 * except for the url. Combined together, a closer look to G+ web page can be assembled.
						 *
						 * TODO read from Calendar API start/end dates and location
						 */
						Event event = eventMap.get(url);
						if (event == null) {
							eventsNotOnCalendar.add(url);
							continue;
						}

						publishedEvents.add(event);

						Date date = new Date(event.getStart().getDateTime().getValue());

						String template = DevelopersSharedModule.getMessage("gplus.event");
						answer = String.format(template,
								objectActorId,
								objectActorDisplayName,
								objectUrl,
								objectContent,
								url,
								displayName,
								GPLUS_EVENT_DATE_TIME_FORMAT.format(date),
								event.getLocation()
						);

						TopekaQuiz quiz = new TopekaQuiz();
						quiz.setType("gplus-post");
						quiz.setQuestion(activity.getTitle());
						quiz.setAnswer(answer);
						quiz.setUpdated(updated);

						quizMap.put(url, quiz);

						continue;
					} else if ("album".equals(objectType)) {
							/*
							 * TODO thumbnail layout
							 */
						String template = DevelopersSharedModule.getMessage("gplus.album");
						answer = String.format(template,
								objectActorId,
								objectActorDisplayName,
								objectUrl,
								objectContent,
								url,
								attachments.getThumbnails().get(0).getImage().getUrl(),
								displayName
						);
					} else {
						answer = objectContent;
					}
				} else {
					String template = DevelopersSharedModule.getMessage("gplus.none");
					answer = String.format(template,
							objectActorId,
							objectActorDisplayName,
							objectUrl,
							objectContent
					);
				}

				TopekaQuiz quiz = new TopekaQuiz();
				quiz.setType("gplus-post");
				quiz.setQuestion(activity.getTitle());
				quiz.setAnswer(answer);
				quiz.setUpdated(updated);

				/*
				 * extract hash tags
				 */
				Document document = Jsoup.parse(objectContent);
				Elements hashTagAnchor = document.select("a[class=ot-hashtag]");
				if (hashTagAnchor.size() > 0) {
					for (Element t : hashTagAnchor) {
						String hashTag = t.text();
						updateCategoryMap(categoryMap, updated, quiz, hashTag);
					}
				} else {
					String hashTag = "";
					updateCategoryMap(categoryMap, updated, quiz, hashTag);
				}
			}

			// We will know we are on the last page when the next page token is null.
			// If this is the case, break.
			if (activityFeed.getNextPageToken() == null) {
				break;
			}

			// Prepare to request the next page of activities
			listActivities.setPageToken(activityFeed.getNextPageToken());

			// Execute and process the next page request
			activityFeed = listActivities.execute();
			activities = activityFeed.getItems();
		}

		for (String e : eventsNotOnCalendar) {
			logger.info("not found on Calendar: " + e);
			/*
			 * https://plus.google.com/+GDGSuzhou/posts/VjNdgbvdCov
			 * https://plus.google.com/+GDGSuzhou/posts/EpfV8jXBwL6
			 * https://plus.google.com/+GDGSuzhou/posts/5ceHFSNpcnE
			 * https://plus.google.com/+GDGSuzhou/posts/GR8fjLu4GHA
			 * https://plus.google.com/+GDGSuzhou/posts/JFtB12eHFPC
			 * https://plus.google.com/+GDGSuzhou/posts/DNkULAjaKcK
			 */
		}

		for (Event event : publishedEvents) {
			/*
			 * Calendar event creator is GDG event organizer, c.f. others are contributor
			 */
			String creatorId = event.getCreator().getId();
			String gplusEvent = event.getHtmlLink();


		}

		/*
		 * TODO update Activities.index with events and organizers
		 *
		 * TODO determine start/end time for the active event, and events in the past and in the future
		 *
		 * an event is a past event if the time is after its feedback cutoff, or after check-in cutoff when
		 * there is no feedback cutoff, or after register cutoff when there is neither feedback or check-in cutoff
		 */
		TopekaCategory pastEvent = new TopekaCategory();

		/*
		 * the event that follows the last past event is active event, or the first event before
		 * it becomes a past event. there may be no active event.
		 */
		TopekaCategory activeEvent = new TopekaCategory();

		/*
		 * the next event after the active event. there may be no future event.
		 * TODO reverse order
		 */
		TopekaCategory futureEvent = new TopekaCategory();

		List<String> eventsNotOnSpreadsheet = new ArrayList<>();

		final Map<String, EventActivities> eventGplusMap = new HashMap<>();
		CellFeedProcessor processor = new CellFeedProcessor(
				spreadsheetManager.getService()) {

			Map<String, EventActivities> eventLabelMap = new HashMap<>();

			@Override
			protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL) throws IOException, ServiceException {

				String gplusEvent = valueMap.get("Google+ Event");
				String activityLabel = valueMap.get("Activity");
				String date = valueMap.get("Date");
				String timestampDateFormat = valueMap.get("timestamp.dateFormat");
				String timestampDateFormatLocale = valueMap.get("timestamp.dateFormat.locale");

				if (activityLabel == null) {
					return true;
				}

				Matcher matcher = ContactManager.EVENT_PATTERN.matcher(activityLabel);
				if (!matcher.matches()) {
					return true;
				}

				String dateString = matcher.group(1);
				String activity = matcher.group(2);

				Date cutoffDate;
				try {
					if (date != null) {
						DateFormat dateFormat;
						if (timestampDateFormatLocale != null) {
							dateFormat = new SimpleDateFormat(timestampDateFormat,
									Locale.forLanguageTag(timestampDateFormatLocale));
						} else {
							dateFormat = new SimpleDateFormat(timestampDateFormat);
						}

						cutoffDate = dateFormat.parse(date);
					} else {
						cutoffDate = ContactManager.DATE_FORMAT.parse(dateString);
					}
				} catch (ParseException e) {
					logger.debug("failed to parse activity date from event label", e);
					cutoffDate = null;
				}

				EventActivities eventActivities = eventLabelMap.get(dateString);
				if (eventActivities == null) {
					eventActivities = new EventActivities();
					eventLabelMap.put(gplusEvent, eventActivities);
				}
				if ("Register".equals(activity)) {
					eventActivities.setRegister(cutoffDate);
				} else if ("Check-in".equals(activity)) {
					eventActivities.setCheckIn(cutoffDate);
				} else if ("Feedback".equals(activity)) {
					eventActivities.setFeedback(cutoffDate);
//			} else if ("Sponsor".equals(activity)) {
//				eventActivities.setSponsor(dateString);
				}

				if (gplusEvent != null && eventGplusMap.get(gplusEvent) == null) {
					eventGplusMap.put(gplusEvent, eventActivities);
				}

				return true;
			}
		};
		try {
			processor.process(spreadsheetManager
							.getWorksheet(DevelopersSharedModule.getMessage("metaSpreadsheet")),
					"Google+ Event", "Activity", "Date",
					"timestamp.dateFormat", "timestamp.dateFormat.locale");
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		Date now = new Date();
		for (Event e : publishedEvents) {
			/*
			 * Calendar event creator is GDG event organizer, c.f. others are contributor
			 */
//			String creatorId = e.getCreator().getId();
			//String displayName = plus.people().get(creatorId).execute().getDisplayName();

			String gplusEvent = e.getHtmlLink();
			TopekaQuiz quiz = quizMap.get(gplusEvent);

			EventActivities eventActivities = eventGplusMap.get(gplusEvent);
			if (eventActivities != null) {
				if (eventActivities.getFeedback() != null && eventActivities.getFeedback().before(now)) {
					pastEvent.getQuizzes().add(quiz);
				} else if (eventActivities.getCheckIn() != null && eventActivities.getCheckIn().before(now)) {
					pastEvent.getQuizzes().add(quiz);
				} else if (eventActivities.getRegister() != null && eventActivities.getRegister().before(now)) {
					pastEvent.getQuizzes().add(quiz);
				} else {
					futureEvent.getQuizzes().add(quiz);
				}
			} else {
				eventsNotOnSpreadsheet.add(gplusEvent);
			}
		}

		for (String e : eventsNotOnSpreadsheet) {
			logger.info("not found on Spreadsheet: " + e);
			/*
			 * x https://plus.google.com/events/cqui1i677tauhvuipovfvavfc0s
			 * x https://plus.google.com/events/cmr0k2kdiihgvt2uoboollh8av0
			 * https://plus.google.com/events/cq0r3kjadlm2eep5v1qip26i9t8
			 * https://plus.google.com/events/c1lk51knk3pvso4hl677jihrbh0
			 * x https://plus.google.com/events/ctruh5edssc1nouplpje05b9e94
			 * x https://plus.google.com/events/cj07jkvt34go4ckjtcktqvmc6fg
			 * x https://plus.google.com/events/c56lv64cm013epeiu3as6m69rlc
			 */
		}

		if (futureEvent.getQuizzes().size() > 0) {
			TopekaQuiz last = futureEvent.getQuizzes().last();
			activeEvent.getQuizzes().add(last);
			futureEvent.getQuizzes().remove(last);
		}

		TopekaCategory hottest = new TopekaCategory();
		TopekaCategory latest = new TopekaCategory();
		TopekaCategory other = new TopekaCategory();
		for (TopekaCategory category : categoryMap.values()) {
			if (latest.getUpdated() == null) {
				latest = category;
			} else if (latest.getUpdated().getValue() < category.getUpdated().getValue()) {
				latest = category;
			} else if (latest.getUpdated().getValue() == category.getUpdated().getValue()) {
				latest.getQuizzes().addAll(category.getQuizzes());
			} else if (category.getQuizzes().size() > hottest.getQuizzes().size()) {
				hottest = category;
			} else if (category.getQuizzes().size() == hottest.getQuizzes().size()) {
				hottest.getQuizzes().addAll(category.getQuizzes());
			}
			other.getQuizzes().addAll(category.getQuizzes());
		}
		other.getQuizzes().removeAll(hottest.getQuizzes());
		other.getQuizzes().removeAll(latest.getQuizzes());

		List<TopekaCategory> categories = new ArrayList<>();
		{
			TopekaCategory category = new TopekaCategory();
			category.setName("积分");
			category.setId("profile");
			category.setTheme("red");
			categories.add(category);
		}
		{
			TopekaCategory category = new TopekaCategory();
			category.setName("幕后英雄");
			category.setId("entertainment");
			category.setTheme("purple");
			categories.add(category);
		}
		{
			TopekaCategory category = activeEvent;
//			category.setName("最近的活动");
			category.setId("tvmovies");
			category.setTheme("red");
			categories.add(category);
		}
		{
			TopekaCategory category = futureEvent;
			category.setName("活动预告");
			category.setId("food");
			category.setTheme("green");
			categories.add(category);
		}
		{
			TopekaCategory category = pastEvent;
			category.setName("精彩瞬间");
			category.setId("history");
			category.setTheme("yellow");
			categories.add(category);
		}
		{
			TopekaCategory category = latest;
			category.setName("新闻");
			category.setId("music");
			category.setTheme("blue");
			categories.add(category);
		}
		{
			TopekaCategory category = hottest;
			category.setName("热点");
			category.setId("sports");
			category.setTheme("green");
			categories.add(category);
		}
		{
			TopekaCategory category = other;
			category.setName("其他");
			category.setId("science");
			category.setTheme("purple");
			categories.add(category);
		}
		{
			TopekaCategory category = new TopekaCategory();
//			category.setName("知识库");
			category.setId("knowledge");
			category.setTheme("blue");
			categories.add(category);
		}

		/*
		 * headers must be set before body
		 *
		 * java - Response encoding of Google App Engine (can not change response encoding) - Stack Overflow
		 * http://stackoverflow.com/a/9447068/333033
		 */
		resp.setContentType("text/javascript; charset=utf-8");

		JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(resp.getWriter());
		jsonGenerator.serialize(categories);
		jsonGenerator.flush();
	}

	private void updateCategoryMap(Map<String, TopekaCategory> categoryMap, DateTime updated, TopekaQuiz quiz, String hashTag) {
		TopekaCategory category = categoryMap.get(hashTag);
		if (category == null) {
			category = new TopekaCategory();
			categoryMap.put(hashTag, category);
		}
		category.getQuizzes().add(quiz);
		if (category.getUpdated() == null || category.getUpdated().getValue() < updated.getValue()) {
			category.setUpdated(updated);
		}
	}
}
