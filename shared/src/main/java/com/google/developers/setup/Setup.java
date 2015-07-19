package com.google.developers.setup;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by renfeng on 7/19/15.
 */
public class Setup {

	private static final Logger logger = LoggerFactory
			.getLogger(Setup.class);

	private static final String PEOPLE = "src/main/resources/peopleHaveyou.properties";

	private static final String PEOPLE2_HTML = "src/site/resources/People.htm";
	private static final String PEOPLE_HTML = "src/site/resources/peopleHaveyou.html";

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
		extract(FileUtils.readFileToString(new File(PEOPLE2_HTML)), incoming);
		incoming = filterCustomUrl(incoming);

		{
			Map<String, String> existing = new HashMap<>();
			File file = new File(PEOPLE);
			if (file.isFile()) {
				List<String> lines = FileUtils.readLines(file);
				for (String line : lines) {
					int index = line.indexOf("=");
					String existingId = line.substring(0, index);
					String existingEmail = line.substring(index + "=".length());

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
					if (existingId.startsWith("#")) {
						String incomingEmail = incoming.remove(existingId);
						if (incomingEmail == null) {
							incomingEmail = incoming.remove(existingId
									.substring(1));
							if (incomingEmail != null) {
								existingId = existingId.substring(1);
							}
						}
					} else {
						String incomingEmail = incoming.remove(existingId);
						if (incomingEmail == null) {
							incomingEmail = incoming.remove("#" + existingId);
							if (incomingEmail != null) {
								existingId = "#" + existingId;
							}
						}
					}

					existing.put(existingId, existingEmail);
				}
			}

			existing.putAll(incoming);

			ArrayList<Map.Entry<String, String>> list;
			list = new ArrayList<>(existing.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<String, String>>() {

				@Override
				public int compare(Map.Entry<String, String> o1,
								   Map.Entry<String, String> o2) {

					int result;

					String email1 = o1.getValue();
					if (email1.startsWith("#")) {
						email1 = email1.substring(1);
					}

					String email2 = o2.getValue();
					if (email2.startsWith("#")) {
						email2 = email2.substring(1);
					}

					result = email1.compareTo(email2);
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
				builder.append(id + "=" + email + "\n");
			}

			FileUtils.write(new File(
					"src/main/resources/peopleHaveyou.properties"), builder
					.toString());
		}
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

			if (email.length() == 0 && !"".equals(incoming.get(id))) {
				continue;
			}
			incoming.put(id, email);
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
			// idEmailMap1.put("#" + id, "");
			// continue;
			// }

			scheduler.submit(new Callable<Void>() {

				@Override
				public Void call() throws Exception {

					try {
						GenericUrl url = new GenericUrl(
								"https://plus.google.com/" + id);

						HttpRequest request = factory.buildGetRequest(url)
								.setFollowRedirects(false)
								.setThrowExceptionOnExecuteError(false);

						HttpResponse response = request.execute();
						// String msg = IOUtils.toString(response.getContent());
						// logger.debug(msg);

						int statusCode = response.getStatusCode();
						if (statusCode == HttpServletResponse.SC_MOVED_TEMPORARILY) {
							/*
							 * with custom url
							 */
							filtered.put(id, email);
							// List<String> location = (List<String>)
							// response.getHeaders()
							// .get("Location");
						} else {
							filtered.put("#" + id, email);
						}
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
