package com.google.developers.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gdata.client.photos.PicasawebService;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.media.MediaStreamSource;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * https://developers.google.com/picasa-web/docs/2.0/developers_guide_java
 *
 * Created by renfeng on 12/4/15.
 */
public class PicasawebManager extends ServiceManager<PicasawebService> {

	public PicasawebManager(Credential credential) {

		PicasawebService service = new PicasawebService(GoogleOAuth2.APPLICATION_NAME);
		service.setProtocolVersion(PicasawebService.DEFAULT_VERSION);
		service.setOAuth2Credentials(credential);

		setService(service);
	}

	public PhotoEntry upload(InputStream stream, String type, String title) throws IOException, ServiceException {

//		URL albumPostUrl = new URL("https://picasaweb.google.com/data/feed/api/user/username/albumid/albumid");
		URL albumPostUrl = new URL("https://picasaweb.google.com/data/feed/api/user/suzhougdg/albumid/EventManager");

		PhotoEntry myPhoto = new PhotoEntry();
		myPhoto.setTitle(new PlainTextConstruct(title));
//		myPhoto.setDescription(new PlainTextConstruct("Puppies are the greatest."));
		myPhoto.setClient(GoogleOAuth2.APPLICATION_NAME);

		MediaStreamSource myMedia = new MediaStreamSource(stream, type);
		myPhoto.setMediaSource(myMedia);

		return getService().insert(albumPostUrl, myPhoto);
	}
}
