package com.google.developers.setup;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import com.google.developers.PropertiesContant;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by renfeng on 7/19/15.
 */
public class Setup implements PropertiesContant {

	private static final Logger logger = LoggerFactory
			.getLogger(Setup.class);

	private static final String PEOPLE_PROPERTIES = "src/main/resources/peopleHaveyou.properties";

	private static final String PEOPLE_HTML = "src/site/resources/peopleHaveyou.html";
	private static final String PEOPLE_HTML_2 = "src/site/resources/peopleHaveyou2.html";

	private static final HttpTransport transport = new NetHttpTransport();
	private static final Scheduler scheduler = new Scheduler();
//	private static final JsonFactory jsonFactory = new JacksonFactory();

	public static void main(String... args) throws IOException, InterruptedException, JSONException {
		refreshPeopleHaveYou();
	}

	/**
	 * requires merging emails manually (because some email may also be of a
	 * page instead of a user profile)
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	static void refreshPeopleHaveYou() throws IOException,
			JSONException, InterruptedException {

		Map<String, String> incoming = new HashMap<>();
		extract(FileUtils.readFileToString(new File(PEOPLE_HTML)), incoming);
		extract(FileUtils.readFileToString(new File(PEOPLE_HTML_2)), incoming);
		incoming = filterCustomUrl(incoming);

		/*
		 * merge incoming with existing
		 */
		Map<String, String> existing = new HashMap<>();
		File file = new File(PEOPLE_PROPERTIES);
		if (file.isFile()) {
			List<String> lines = FileUtils.readLines(file);
			for (String line : lines) {
				int index = line.indexOf(KEY_VALUE_DELIMITER);
				/*
				 * java - How to convert a string with Unicode encoding to a string of letters - Stack Overflow
				 * http://stackoverflow.com/a/14368185/333033
				 */
				String existingId = StringEscapeUtils.unescapeJava(line.substring(0, index));

				String existingEmail = line.substring(index + KEY_VALUE_DELIMITER.length());

					/*
					 * merge and resolve conflicts (properties file, html)
					 *
					 * 0. #email, email = #email 1. id, #id = id 2. #id, id = id
					 */

					/*
					 * email prefix, #, indicates manually removed user
					 */
					/*
					 * a manually enabled row must start with an id different
					 * from GPlus id
					 */
				if (existingId.startsWith(COMMENT_LINE_START)) {
					String incomingEmail = incoming.remove(existingId);
					if (incomingEmail == null) {
						String idWithCustomURL = existingId.substring(1);
						incomingEmail = incoming.remove(idWithCustomURL);
						if (incomingEmail != null) {
							existingId = idWithCustomURL;
						}
					}
				} else {
					String incomingEmail = incoming.remove(existingId);
					if (incomingEmail == null) {
						String idWithoutCustomURL = COMMENT_LINE_START + existingId;
						incomingEmail = incoming.remove(idWithoutCustomURL);
						if (incomingEmail != null) {
							existingId = idWithoutCustomURL;
						}
					}
				}

				existing.put(existingId, existingEmail);
			}
		}

		existing.putAll(incoming);

		/*
		 * sort by email
		 */
		ArrayList<Map.Entry<String, String>> list;
		list = new ArrayList<>(existing.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<String, String>>() {

			@Override
			public int compare(Map.Entry<String, String> o1,
							   Map.Entry<String, String> o2) {

				int result;

				String email1 = o1.getValue();
				if (email1.startsWith(COMMENT_LINE_START)) {
					email1 = email1.substring(1);
				}

				String email2 = o2.getValue();
				if (email2.startsWith(COMMENT_LINE_START)) {
					email2 = email2.substring(1);
				}

				result = email1.compareTo(email2);
				if (result == 0) {
					if (o1.getKey().startsWith("+") && !o2.getKey().startsWith("+")) {
						result = -1;
					} else if (!o1.getKey().startsWith("+") && o2.getKey().startsWith("+")) {
						result = 1;
					} else {
						result = Integer.compare(o1.getKey().length(), o2.getKey().length());
					}
				}
				if (result == 0) {
					result = o1.getKey().compareTo(o2.getKey());
				}

				return result;
			}
		});

		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> e : list) {
			String id = e.getKey();
			String email = e.getValue();
			builder.append(StringEscapeUtils.escapeJava(id) + KEY_VALUE_DELIMITER + email + "\n");
		}

		FileUtils.write(new File("src/main/resources/peopleHaveyou.properties"), builder.toString());
	}

	static void extract(String html, Map<String, String> incoming) throws JSONException {

		int beginIndex = html.indexOf("ppv.psn");
		int i = html.lastIndexOf("AF_initDataCallback(", beginIndex);
		int j = html.indexOf(");", beginIndex);
		String json = html.substring(i + "AF_initDataCallback(".length(), j);

		/*
		 * TODO rewrite the following with json parser
		 */
//		JsonParser jsonParser = jsonFactory.createJsonParser(json);

		JSONObject rootObject = new JSONObject(json);
		JSONArray lprArray = rootObject.getJSONArray("data");
		JSONArray psnArray = lprArray.getJSONArray(1).getJSONArray(0)
				.getJSONArray(1);
		for (int p = 0; p < psnArray.length(); p++) {
			JSONArray attrArray = psnArray.getJSONArray(p);

			JSONArray a1 = attrArray.getJSONArray(1);

			JSONArray a1a0 = a1.getJSONArray(0);
			String email = a1a0.optString(0);
			String id = a1a0.getString(2);

			JSONArray a1a2 = a1.getJSONArray(2);
			String name = a1a2.getString(0);
			String portrait = a1a2.optString(8);
			String currentCity = a1a2.optString(11);
			String company = a1a2.optString(13);
			String occupation = a1a2.optString(14);

			String url;
			JSONArray a1a2a18 = a1a2.optJSONArray(18);
			if (a1a2a18 != null) {
				url = a1a2a18.optString(1);
			} else {
				url = null;
			}

			String tagline = a1a2.optString(21);

			logger.trace(id);
			logger.trace(email);
			logger.trace(name);
			logger.trace(company);
			logger.trace(portrait);

			if (email.length() > 0) {
				String oldEmail = incoming.put(id, email);
				if (oldEmail != null && !oldEmail.equals(email)) {
					logger.info("replaced email for gplus id: " + id);
					logger.info("old email: " + oldEmail);
					logger.info("email: " + email);
				}
			}
		}
	}

	private static Map<String, String> filterCustomUrl(
			Map<String, String> incoming) throws IOException,
			InterruptedException {

		final Map<String, String> filtered = new ConcurrentHashMap<>();

		final HttpRequestFactory factory = transport.createRequestFactory();

		final CountDownLatch latch = new CountDownLatch(incoming.size());
		for (Map.Entry<String, String> e : incoming.entrySet()) {
			final String id = e.getKey();
			final String email = e.getValue();

			// if (email.length() == 0) {
			// idEmailMap1.put(COMMENT_LINE_START + id, "");
			// continue;
			// }

			scheduler.submit(new Callable<Void>() {

				@Override
				public Void call() throws Exception {

					try {
						logger.trace("people: " + id + KEY_VALUE_DELIMITER + email);
						GenericUrl url = new GenericUrl("https://plus.google.com/" + id);

						HttpRequest request = factory.buildGetRequest(url)
								.setFollowRedirects(false)
								.setThrowExceptionOnExecuteError(false);

						HttpResponse response = request.execute();

						int statusCode = response.getStatusCode();
						if (statusCode == HttpServletResponse.SC_MOVED_TEMPORARILY) {
							/*
							 * save both id and custom url (path)
							 */

							filtered.put(id, email);

							String location = response.getHeaders().getLocation();
							logger.trace("custom url: " + location);
							if (location.startsWith("https://plus.google.com/+")) {
								String path = URLDecoder.decode(
										location.substring("https://plus.google.com/+".length()), "UTF-8");
								filtered.put("+" + path, email);
								logger.info("parsed g+ id: " + path);
							} else {
								logger.warn("failed to parse g+ id: " + location);
							}
						} else {
							filtered.put(id, email);
							logger.trace("(no custom url)");
						}
					} catch (Exception ex) {
						logger.error("failed to detect custom url", ex);
					} finally {
						latch.countDown();
					}

					return null;
				}

			});
		}

		latch.await();

		return filtered;
	}
}
