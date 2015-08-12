package com.google.developers.event.http;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.util.DateTime;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;
import com.google.developers.api.GPlusManager;
import com.google.developers.event.DevelopersSharedModule;
import com.google.developers.event.model.TopekaCategory;
import com.google.developers.event.model.TopekaQuiz;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by renfeng on 7/20/15.
 */
@Singleton
public class ActivitiesServlet extends HttpServlet {

	private final GPlusManager gplusManager;
	private final JsonFactory jsonFactory;

	@Inject
	public ActivitiesServlet(GPlusManager gplusManager, JsonFactory jsonFactory) {
		this.gplusManager = gplusManager;
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		Map<String, TopekaCategory> categoryMap = new HashMap<>();

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
					String objectType = attachments.getObjectType();
					if ("photo".equals(objectType)) {
						String template = DevelopersSharedModule.getMessage("gplus.photo");
						answer = String.format(template,
								objectActorId,
								objectActorDisplayName,
								objectUrl,
								objectContent,
								attachments.getUrl(),
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
									attachments.getUrl(),
									image.getUrl(),
									attachments.getDisplayName()
							);
						} else {
							String template = DevelopersSharedModule.getMessage("gplus.article.noimage");
							answer = String.format(template,
									objectActorId,
									objectActorDisplayName,
									objectUrl,
									objectContent,
									attachments.getUrl(),
									attachments.getContent(),
									attachments.getDisplayName(),
									new URL(attachments.getUrl()).getHost()
							);
						}
					} else if ("event".equals(objectType)) {
						/*
						 * skips events published on G+, TODO read from Calendar API instead
						 */
//						String template = DevelopersSharedModule.getMessage("gplus.event");
//						answer = String.format(template,
//								objectActorId,
//								objectActorDisplayName,
//								objectUrl,
//								objectContent,
//								attachments.getUrl(),
//								attachments.getDisplayName()
//						);
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
								attachments.getUrl(),
								attachments.getThumbnails().get(0).getImage().getUrl(),
								attachments.getDisplayName()
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

				DateTime updated = activity.getUpdated();

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

		TopekaCategory hottest = new TopekaCategory();
		TopekaCategory latest = new TopekaCategory();
		TopekaCategory other = new TopekaCategory();
		for (TopekaCategory category : categoryMap.values()) {
			if (category.getQuizzes().size() > hottest.getQuizzes().size()) {
				hottest = category;
			} else if (category.getQuizzes().size() == hottest.getQuizzes().size()) {
				hottest.getQuizzes().addAll(category.getQuizzes());
			}
			if (latest.getUpdated() == null) {
				latest = category;
			} else if (latest.getUpdated().getValue() < category.getUpdated().getValue()) {
				latest = category;
			} else if (latest.getUpdated().getValue() == category.getUpdated().getValue()) {
				latest.getQuizzes().addAll(category.getQuizzes());
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
			TopekaCategory category = new TopekaCategory();
			category.setName("最近的活动");
			category.setId("tvmovies");
			category.setTheme("red");
			categories.add(category);
		}
		{
			TopekaCategory category = new TopekaCategory();
			category.setName("活动预告");
			category.setId("food");
			category.setTheme("green");
			categories.add(category);
		}
		{
			TopekaCategory category = new TopekaCategory();
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
			category.setName("知识库");
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
