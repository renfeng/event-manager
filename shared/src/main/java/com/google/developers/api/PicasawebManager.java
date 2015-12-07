package com.google.developers.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gdata.client.photos.PicasawebService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.media.MediaStreamSource;
import com.google.gdata.data.photos.AlbumEntry;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.data.photos.UserFeed;
import com.google.gdata.util.ServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_java
 * <p/>
 * Created by renfeng on 12/4/15.
 */
public class PicasawebManager extends ServiceManager<PicasawebService> {

	public PicasawebManager(Credential credential) {

		PicasawebService service = new PicasawebService(GoogleOAuth2.APPLICATION_NAME);
		service.setProtocolVersion(PicasawebService.DEFAULT_VERSION);
		service.setOAuth2Credentials(credential);

		/*
		 * Unable to process batch request for spread sheet. - Google Groups
		 * https://groups.google.com/d/msg/google-appengine/PVqNF8AumdY/gZNMJKpObowJ
		 *
		 * Timeouts and Errors | API Client Library for Java | Google Developers
		 * https://developers.google.com/api-client-library/java/google-api-java-client/errors
		 */
		service.setConnectTimeout(3 * 60000);
		service.setReadTimeout(3 * 60000);

		setService(service);
	}

	public PhotoEntry upload(InputStream stream, String type, String title) throws IOException, ServiceException {

		{
			URL feedUrl = new URL("https://picasaweb.google.com/data/feed/api/" +
					"user/100160462017014431473" +
					"?kind=album");

			UserFeed myUserFeed = getService().getFeed(feedUrl, UserFeed.class);

			for (AlbumEntry album : myUserFeed.getAlbumEntries()) {
				System.out.println(album.getTitle().getPlainText());
			}
		}

		PhotoEntry photo = new PhotoEntry();
		photo.setTitle(new PlainTextConstruct(title));
		photo.setClient(GoogleOAuth2.APPLICATION_NAME);

		MediaStreamSource myMedia = new MediaStreamSource(stream, type);
		photo.setMediaSource(myMedia);

		return getService().insert(new URL("https://picasaweb.google.com/data/feed/api/" +
				"user/100160462017014431473/" +
				"albumid/6224940472205310529"), photo);
	}

	public String getPhotoUrl(String photoId) throws IOException, ServiceException {
		/*
		 * https://developers.google.com/picasa-web/docs/2.0/reference#Parameters
		 */
		PhotoEntry photoEntry = getService().getEntry(new URL("https://picasaweb.google.com/data/entry/api/" +
				"user/100160462017014431473/" +
				"albumid/6224940472205310529/" +
				"photoid/" + photoId + "?imgmax=d"), PhotoEntry.class);
		return photoEntry.getMediaContents().get(0).getUrl();
	}
}
