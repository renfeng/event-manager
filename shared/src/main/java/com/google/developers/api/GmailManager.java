package com.google.developers.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Created by renfeng on 6/22/15.
 */
public class GmailManager extends ClientManager<Gmail> {

	private static GmailManager gmailManager;

	public static GmailManager getGlobalInstance(HttpTransport transport, JsonFactory jsonFactory)
			throws IOException {

		if (gmailManager == null) {
			synchronized (GmailManager.class) {
				if (gmailManager == null) {
					gmailManager = new GmailManager(transport, jsonFactory,
							GoogleOAuthManager.createCredentialWithRefreshToken(transport, jsonFactory));
				}
			}
		}
		return gmailManager;
	}

	public GmailManager(HttpTransport transport, JsonFactory jsonFactory,
						Credential credential) {

		Gmail gmail = new Gmail.Builder(transport, jsonFactory, credential)
				.setApplicationName(GoogleOAuthManager.APPLICATION_NAME)
				.build();

		setClient(gmail);
	}

	/*
	 * https://developers.google.com/gmail/api/guides/sending
	 */

	/**
	 * Create a MimeMessage using the parameters provided.
	 *
	 * @param to       Email address of the receiver.
	 * @param from     Email address of the sender, the mailbox account.
	 * @param subject  Subject of the email.
	 * @param bodyText Body text of the email.
	 * @return MimeMessage to be used to send email.
	 * @throws MessagingException
	 */
	public static MimeMessage createEmail(String to, String from, String subject,
										  String bodyText) throws MessagingException {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		MimeMessage email = new MimeMessage(session);

		email.setFrom(new InternetAddress(from));
		email.addRecipient(javax.mail.Message.RecipientType.TO,
				new InternetAddress(to));
		email.setSubject(subject);
		email.setText(bodyText);
		return email;
	}

	/**
	 * Create a Message from an email
	 *
	 * @param email Email to be set to raw of message
	 * @return Message containing base64url encoded email.
	 * @throws IOException
	 * @throws MessagingException
	 */
	public static Message createMessageWithEmail(MimeMessage email)
			throws MessagingException, IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		email.writeTo(bytes);
		String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
		Message message = new Message();
		message.setRaw(encodedEmail);
		return message;
	}

	/**
	 * Create a MimeMessage using the parameters provided.
	 *
	 * @param to       Email address of the receiver.
	 * @param from     Email address of the sender, the mailbox account.
	 * @param subject  Subject of the email.
	 * @param bodyText Body text of the email.
	 * @param fileDir  Path to the directory containing attachment.
	 * @param filename Name of file to be attached.
	 * @return MimeMessage to be used to send email.
	 * @throws MessagingException
	 */
	public static MimeMessage createEmailWithAttachment(
			String to, String from, String subject,
			String bodyText, String fileDir, String filename) throws MessagingException, IOException {

		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		MimeMessage email = new MimeMessage(session);
		InternetAddress tAddress = new InternetAddress(to);
		InternetAddress fAddress = new InternetAddress(from);

		email.setFrom(fAddress);
		email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
		email.setSubject(subject);

		MimeBodyPart mimeBodyPart = new MimeBodyPart();
		mimeBodyPart.setContent(bodyText, "text/plain");
		mimeBodyPart.setHeader("Content-Type", "text/plain; charset=\"UTF-8\"");

		Multipart multipart = new MimeMultipart();
		multipart.addBodyPart(mimeBodyPart);

		mimeBodyPart = new MimeBodyPart();
		DataSource source = new FileDataSource(fileDir + filename);

		mimeBodyPart.setDataHandler(new DataHandler(source));
		mimeBodyPart.setFileName(filename);
		String contentType = Files.probeContentType(FileSystems.getDefault()
				.getPath(fileDir, filename));
		mimeBodyPart.setHeader("Content-Type", contentType + "; name=\"" + filename + "\"");
		mimeBodyPart.setHeader("Content-Transfer-Encoding", "base64");

		multipart.addBodyPart(mimeBodyPart);

		email.setContent(multipart);

		return email;
	}

	/**
	 * Send an email from the user's mailbox to its recipient.
	 *
	 * @param userId User's email address. The special value "me"
	 *               can be used to indicate the authenticated user.
	 * @param email  Email to be sent.
	 * @throws MessagingException
	 * @throws IOException
	 */
	public void sendMessage(String userId, MimeMessage email)
			throws MessagingException, IOException {
		Message message = createMessageWithEmail(email);
		message = getClient().users().messages().send(userId, message).execute();

		System.out.println("Message id: " + message.getId());
		System.out.println(message.toPrettyString());
	}

	/*
	 * https://developers.google.com/gmail/api/guides/drafts
	 */

	/**
	 * Create draft email.
	 *
	 * @param userId user's email address. The special value "me"
	 *               can be used to indicate the authenticated user
	 * @param email  the MimeMessage used as email within the draft
	 * @return the created draft
	 * @throws MessagingException
	 * @throws IOException
	 */
	public Draft createDraft(String userId, MimeMessage email)
			throws MessagingException, IOException {
		Message message = createMessageWithEmail(email);
		Draft draft = new Draft();
		draft.setMessage(message);
		draft = getClient().users().drafts().create(userId, draft).execute();

		System.out.println("draft id: " + draft.getId());
		System.out.println(draft.toPrettyString());
		return draft;
	}

}
