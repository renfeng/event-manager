package com.google.developers.event.http;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.ActivityFeed;
import com.google.developers.api.GPlusManager;
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
import java.util.List;

/**
 * Created by renfeng on 7/20/15.
 */
@Singleton
public class GPlusServlet extends HttpServlet {

	private final GPlusManager gplusManager;
	private final JsonFactory jsonFactory;

	@Inject
	public GPlusServlet(GPlusManager gplusManager, JsonFactory jsonFactory) {
		this.gplusManager = gplusManager;
		this.jsonFactory = jsonFactory;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		Plus plus = gplusManager.getClient();

		Plus.Activities.List listActivities = plus.activities().list("me", "public");
		listActivities.setMaxResults(5L);

		// Execute the request for the first page
		ActivityFeed activityFeed = listActivities.execute();

		// Unwrap the request and extract the pieces we want
		List<Activity> activities = activityFeed.getItems();

		resp.setContentType("text/javascript");
		JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(resp.getWriter());
		jsonGenerator.writeStartArray();
		// Loop through until we arrive at an empty page
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

				jsonGenerator.writeStartObject();

				Activity.PlusObject.Actor actor = activity.getObject().getActor();
				jsonGenerator.writeFieldName("actorID");
				jsonGenerator.writeString(actor.getId());
				jsonGenerator.writeFieldName("actorURL");
				jsonGenerator.writeString(actor.getUrl());
				jsonGenerator.writeFieldName("actorImage");
				jsonGenerator.writeString(actor.getImage().getUrl());

				/*
				 * TODO parse hash tags from content
				 *
				 * TODO replace +xxx (g+ id), and hash tag with links
				 *
				 * "<b>Launching Google beacon platform</b><br /><br />Helping your apps work smarter: Introducing the <a rel=\"nofollow\" class=\"ot-hashtag\" href=\"https://plus.google.com/s/%23GoogleBeaconPlatform\">#GoogleBeaconPlatform</a> and the <a rel=\"nofollow\" class=\"ot-hashtag\" href=\"https://plus.google.com/s/%23Eddystone\">#Eddystone</a> BLE beacon format.\ufeff"
				 */
				String content = activity.getObject().getContent();

				{
					/*
					 * extract hash tags
					 */
					jsonGenerator.writeFieldName("hashTags");
					jsonGenerator.writeStartArray();
					Document document = Jsoup.parse(content);
					Elements hashTagAnchor = document.select("a[class=ot-hashtag]");
					for (Element t : hashTagAnchor) {
						String hashTag = t.text();
						jsonGenerator.writeString(hashTag);
					}
					jsonGenerator.writeEndArray();
				}

				jsonGenerator.writeFieldName("content");
				jsonGenerator.writeString(content);

				List<Activity.PlusObject.Attachments> attachments = activity.getObject().getAttachments();
				if (attachments != null) {
					Activity.PlusObject.Attachments attachment = attachments.get(0);
					jsonGenerator.writeFieldName("attachmentName");
					jsonGenerator.writeString(attachment.getDisplayName());
					jsonGenerator.writeFieldName("attachmentURL");
					jsonGenerator.writeString(attachment.getUrl());
					if (attachment.getImage() != null) {
						jsonGenerator.writeFieldName("attachmentImage");
						jsonGenerator.writeString(attachment.getImage().getUrl());
					}
				}

				jsonGenerator.writeEndObject();
			}

			// We will know we are on the last page when the next page token is null.
			// If this is the case, break.
			if (activityFeed.getNextPageToken() == null) {
				break;
			}

			/*
			 * TODO uncomment to load all activities
			 */
//			// Prepare to request the next page of activities
//			listActivities.setPageToken(activityFeed.getNextPageToken());
//
//			// Execute and process the next page request
//			activityFeed = listActivities.execute();
//			activities = activityFeed.getItems();
			activities = null;
		}
		jsonGenerator.writeEndArray();
		jsonGenerator.flush();
	}
}
