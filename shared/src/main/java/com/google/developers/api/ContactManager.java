package com.google.developers.api;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.developers.PropertiesContant;
import com.google.developers.event.EventActivities;
import com.google.developers.event.EventParticipant;
import com.google.developers.event.ParticipantStatistics;
import com.google.developers.event.Streak;
import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactQuery;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.data.contacts.*;
import com.google.gdata.data.extensions.*;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContactManager extends ServiceManager<ContactsService> implements PropertiesContant {

	private static final Logger logger = LoggerFactory
			.getLogger(ContactManager.class);

	private static final String GROUPS_URL = "https://www.google.com/m8/feeds/groups/default/full";
	private static final String CONTACTS_URL = "https://www.google.com/m8/feeds/contacts/default/full";
	private static final String CONTACT_BATCH_URL = "https://www.google.com/m8/feeds/contacts/default/full/batch";

	public static final Pattern ACTIVITY_PATTERN = Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2}) (Register|Check-in|Feedback)");
	public static final Pattern GROUP_PATTERN = Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2})(?: .+)?");
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	@Inject
	public ContactManager(@Named("refreshToken") String refreshToken,
						  @Named("clientId") String clientId,
						  @Named("clientSecret") String clientSecret,
						  HttpTransport transport, JsonFactory jsonFactory) {

		super(refreshToken, clientId, clientSecret, transport, jsonFactory);

		ContactsService service = new ContactsService("GDG Event Management");
		service.setProtocolVersion(ContactsService.Versions.V3_1);
		setService(service);
	}

	public ContactGroupEntry findGroupByName(String groupName)
			throws IOException, ServiceException {

		logger.trace("finding group: " + groupName);

		ContactGroupEntry entry;

		Query groupQuery = createAllContactsQuery(new URL(GROUPS_URL));
		ContactGroupFeed groupFeed = getService().query(groupQuery, ContactGroupFeed.class);

		entry = null;
		for (ContactGroupEntry groupEntry : groupFeed.getEntries()) {

			logger.trace(groupEntry.getTitle().getPlainText());

			if (groupEntry.hasSystemGroup()) {
				logger.trace("skipping system group");
				continue;
			}

			if (groupEntry.getTitle().getPlainText().equals(groupName)) {
				entry = groupEntry;
				break;
			}
		}

		if (entry == null) {
			logger.trace("group not found");
		} else {
			logger.trace("group found");
		}

		return entry;
	}

	public ContactGroupEntry createGroup(String groupName) throws IOException,
			ServiceException {

		URL url = new URL(GROUPS_URL);

		ContactGroupEntry entry = new ContactGroupEntry();
		entry.setTitle(new PlainTextConstruct(groupName));

		// ExtendedProperty additionalInfo = new ExtendedProperty();
		// additionalInfo.setName("more info about the group");
		// additionalInfo.setValue("Nice people.");
		// result.addExtendedProperty(additionalInfo);

		// Ask the service to insert the new entry
		entry = getService().insert(url, entry);

		return entry;
	}

	public List<EventParticipant> importContactsFromSpreadsheet(
			List<EventParticipant> participants, ContactGroupEntry eventGroup, String eventName)
			throws IOException, ServiceException {

		logger.debug("importing " + participants.size() + " participants");

		List<EventParticipant> failedParticipants = new ArrayList<>();

		URL url = new URL(CONTACTS_URL);

		/*
		 * FIXME indexing contacts - won't merge
		 */
		// Map<String, List<ContactEntry>> contactMap = new HashMap<String,
		// List<ContactEntry>>();
		Map<String, ContactEntry> contactMap = new HashMap<>();
		{
			ContactQuery contactQuery = createAllContactsQuery(url);

			/*
			 * active since 90 days ago - bad idea to have duplicate contacts,
			 * and rely on a last updated date not visible on Google Contacts
			 */
//			if (cutoffDate != null) {
//				Calendar calendar = Calendar.getInstance();
//				calendar.setTime(cutoffDate);
//				calendar.add(Calendar.DATE, -Streak.STREAK_WINDOW_IN_DAYS);
//				DateTime nintyDaysAgo = new DateTime(calendar.getTimeInMillis());
//				contactQuery.setUpdatedMin(nintyDaysAgo);
//			}

			ContactFeed contactFeed = getService().query(contactQuery,
					ContactFeed.class);
			for (ContactEntry entry : contactFeed.getEntries()) {
				for (Email email : entry.getEmailAddresses()) {
					// List<ContactEntry> list = contactMap
					// .get(email.getAddress());
					// if (list == null) {
					// list = new ArrayList<ContactEntry>();
					// contactMap.put(email.getAddress(), list);
					// }
					// list.add(entry);
					contactMap.put(email.getAddress(), entry);
				}
				for (PhoneNumber phone : entry.getPhoneNumbers()) {
					// List<ContactEntry> list = contactMap.get(phone
					// .getPhoneNumber());
					// if (list == null) {
					// list = new ArrayList<ContactEntry>();
					// contactMap.put(phone.getPhoneNumber(), list);
					// }
					// list.add(entry);
					contactMap.put(phone.getPhoneNumber(), entry);
				}
			}
		}

		// Pattern emailPattern = Pattern
		// .compile("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])");
		//
		// /*
		// * http://regexlib.com/REDetails.aspx?regexp_id=3313
		// */
		// Pattern phonePattern = Pattern
		// .compile("^(\\+86)?(13[0-9]|145|147|15[0-3,5-9]|18[0,2,5-9])(\\d{8})$");

		// Set<ContactEntry> dirtyContacts = new HashSet<ContactEntry>();

		/*
		 *Feed that holds all the batch request entries.
		 */
		ContactFeed requestFeed = new ContactFeed();

		Map<String, EventParticipant> participantMap = new HashMap<>();
		URL batchUrl = new URL(CONTACT_BATCH_URL);

		String groupAtomId = eventGroup.getId();

		for (EventParticipant participant : participants) {
			String nickname = participant.getNickname();
			String emailAddress = participant.getEmailAddress();
			String phoneNumber = participant.getPhoneNumber();
			Date timestamp = participant.getTimestamp();
			logger.debug("row: " + participant);
			if (emailAddress == null && phoneNumber == null) {
				logger.debug("skipped attendee who left no email nor phone number");
				continue;
			}

			// if (contactMap.containsKey(emailAddress)) {
			// List<ContactEntry> contactEntryList = contactMap
			// .get(emailAddress);
			// for (ContactEntry contactEntry : contactEntryList) {
			// boolean hasNumber = false;
			// for (PhoneNumber n : contactEntry.getPhoneNumbers()) {
			// if (n.getPhoneNumber().equals(phoneNumber)) {
			// hasNumber = true;
			// break;
			// }
			// }
			// if (!hasNumber) {
			// PhoneNumber number = new PhoneNumber();
			// number.setPhoneNumber(phoneNumber);
			// contactEntry.addPhoneNumber(number);
			// dirtyContacts.add(contactEntry);
			// }
			// }
			// }
			// if (contactMap.containsKey(phoneNumber)) {
			// List<ContactEntry> contactEntryList = contactMap
			// .get(phoneNumber);
			// for (ContactEntry contactEntry : contactEntryList) {
			// boolean hasEmail = false;
			// for (Email a : contactEntry.getEmailAddresses()) {
			// if (a.getAddress().equals(phoneNumber)) {
			// hasEmail = true;
			// break;
			// }
			// }
			// if (!hasEmail) {
			// Email email = new Email();
			// email.setAddress(emailAddress);
			// contactEntry.addEmailAddress(email);
			// dirtyContacts.add(contactEntry);
			// }
			// }
			// }

			boolean dirty;

			ContactEntry contactEntry = contactMap.get(emailAddress);
			if (contactEntry == null) {
				contactEntry = contactMap.get(phoneNumber);
			}
			try {
				if (contactEntry == null) {
					contactEntry = createContact(url, nickname, emailAddress, phoneNumber);

					if (emailAddress != null) {
						contactMap.put(emailAddress, contactEntry);
					}
					if (phoneNumber != null) {
						contactMap.put(phoneNumber, contactEntry);
					}

					joinGroup(groupAtomId, contactEntry);

					setEvent(eventName, timestamp, true, true, contactEntry);
					setEvent("first seen", timestamp, true, false, contactEntry);
					setEvent("last seen", timestamp, false, true, contactEntry);

					/*
					 * (always true)
					 */
					dirty = true;

				} else {
					dirty = modifyContact(eventGroup, eventName, timestamp, contactMap,
							nickname, emailAddress, phoneNumber, contactEntry);
				}

			/*
			 * this test code helped to find out the timestamp only restriction
			 */
//			for (Event event : contactEntry.getEvents()) {
//				if (!event.isImmutable()) {
//					event.setLabel(event.getLabel() + "x");
//					dirty = true;
//				}
//			}

				if (dirty) {
					/*
					 * batch update
					 */
//					contactEntry = contactEntry.update();
//					if (emailAddress != null) {
//						contactMap.put(emailAddress, contactEntry);
//					}
//					if (phoneNumber != null) {
//						contactMap.put(phoneNumber, contactEntry);
//					}
					/*
					 * Batch requests are limited to 100 operations at a time.
					 * https://developers.google.com/google-apps/contacts/v3/#batch_operations
					 */
					if (requestFeed.getEntries().size() < 100) {
						String batchId = participant.toString();
						BatchUtils.setBatchId(contactEntry, batchId);
						BatchUtils.setBatchOperationType(contactEntry, BatchOperationType.UPDATE);
						requestFeed.getEntries().add(contactEntry);
						participantMap.put(batchId, participant);
					}
					if (requestFeed.getEntries().size() == 100) {
						failedParticipants.addAll(submitBatch(batchUrl, requestFeed, participantMap));
					}
				}
			} catch (Exception ex) {
				logger.error("failed to update: " + participant, ex);
				failedParticipants.add(participant);
			}

			// ContactFeed resultFeed = null;
			// if (resultFeed == null && emailAddress != null
			// && emailPattern.matcher(emailAddress).matches()) {
			// logger.trace("search contact by email address");
			// ContactQuery contactQuery = new ContactQuery(url);
			// contactQuery.setFullTextQuery(emailAddress);
			// resultFeed = contacts.query(contactQuery, ContactFeed.class);
			// }
			// if (resultFeed == null && phoneNumber != null
			// && phonePattern.matcher(phoneNumber).matches()) {
			// logger.trace("search contact by phone number");
			// ContactQuery contactQuery = new ContactQuery(url);
			// contactQuery.setFullTextQuery(phoneNumber);
			// resultFeed = contacts.query(contactQuery, ContactFeed.class);
			// }
			// if (resultFeed != null) {
			// logger.trace("contact found");
			//
			// // Print the results
			// for (ContactEntry entry : resultFeed.getEntries()) {
			// System.out
			// .println(entry.getName().getFullName().getValue());
			// logger.trace("Updated on: "
			// + entry.getUpdated().toStringRfc822());
			//
			// entry = joinGroup(contacts, groupAtomId, entry);
			// }
			// } else {
			// logger.trace("new contact");
			//
			// createContact(contacts, url, groupAtomId, nickname,
			// emailAddress, phoneNumber);
			// }
		}

		if (requestFeed.getEntries().size() > 0) {
			failedParticipants.addAll(submitBatch(batchUrl, requestFeed, participantMap));
		}

		return failedParticipants;
	}

	private List<EventParticipant> submitBatch(
			URL batchUrl, ContactFeed requestFeed, Map<String, EventParticipant> participantMap)
			throws IOException, ServiceException {

		List<EventParticipant> failedParticipants = new ArrayList<>();

		// Submit the batch request to the server.
		ContactFeed responseFeed = getService().batch(batchUrl, requestFeed);
		// Check the status of each operation.
		for (ContactEntry entry : responseFeed.getEntries()) {
			String batchId = BatchUtils.getBatchId(entry);
			BatchStatus status = BatchUtils.getBatchStatus(entry);
			logger.debug(batchId + ": " + status.getCode() + " (" + status.getReason() + ")");
			if (status.getCode() != HttpURLConnection.HTTP_OK) {
				/*
				 * map participant
				 */
				logger.error("failed to update: " + participantMap.get(batchId));
				failedParticipants.add(participantMap.get(batchId));
			}
		}

		requestFeed.getEntries().clear();

		return failedParticipants;
	}

	private boolean modifyContact(
			ContactGroupEntry eventGroup, String eventName, Date timestamp,
			Map<String, ContactEntry> contactMap, String nickname,
			String emailAddress, String phoneNumber, ContactEntry contactEntry)
			throws IOException, ServiceException {

		logger.debug("update contact");

		boolean dirty = false;

		/*
		 * update contact
		 */
		if (contactMap.containsKey(emailAddress)
				&& !contactMap.containsKey(phoneNumber)) {
			/*
			 * add phone number
			 */
			if (addPhone(phoneNumber, contactEntry)) {
				dirty = true;
			}

			// contactEntry = contacts.update(new URL(contactEntry
			// .getEditLink().getHref()), contactEntry);
			//
			// contactMap.put(emailAddress, contactEntry);
			// contactMap.put(phoneNumber, contactEntry);

		} else if (!contactMap.containsKey(emailAddress)
				&& contactMap.containsKey(phoneNumber)) {
			/*
			 * add email address
			 */
			if (addEmail(nickname, emailAddress, contactEntry)) {
				dirty = true;
			}

			// contactEntry = contacts.update(new URL(contactEntry
			// .getEditLink().getHref()), contactEntry);
			//
			// contactMap.put(emailAddress, contactEntry);
			// contactMap.put(phoneNumber, contactEntry);

		} else if (contactMap.containsKey(emailAddress)
				&& contactMap.containsKey(phoneNumber)) {
			// /*
			// * there may be duplicate contacts with identical email
			// * address, phone number, or both
			// */
			//
			// // ContactEntry c1 = contactMap.get(emailAddress);
			// // ContactEntry c2 = contactMap.get(phoneNumber);
			//
			// /*
			// * add email address
			// */
			// {
			// boolean hasEmail = false;
			// for (Email a : contactEntry.getEmailAddresses()) {
			// if (a.getAddress().equals(emailAddress)) {
			// hasEmail = true;
			// break;
			// }
			// }
			// if (!hasEmail) {
			// addEmail(nickname, emailAddress, contactEntry);
			//
			// dirty = true;
			//
			// // contactEntry = contacts.update(new
			// // URL(contactEntry
			// // .getEditLink().getHref()), contactEntry);
			// //
			// // contactMap.put(emailAddress, contactEntry);
			// // contactMap.put(phoneNumber, contactEntry);
			// }
			// }
			//
			// /*
			// * add phone number
			// */
			// {
			// boolean hasNumber = false;
			// for (PhoneNumber n : contactEntry.getPhoneNumbers()) {
			// if (n.getPhoneNumber().equals(phoneNumber)) {
			// hasNumber = true;
			// break;
			// }
			// }
			// if (!hasNumber) {
			// addPhone(phoneNumber, contactEntry);
			//
			// dirty = true;
			//
			// // contactEntry = contacts.update(new
			// // URL(contactEntry
			// // .getEditLink().getHref()), contactEntry);
			// //
			// // contactMap.put(emailAddress, contactEntry);
			// // contactMap.put(phoneNumber, contactEntry);
			// }
			// }

		}

		/*
		 * update nickname
		 */
		if (nickname != null) {
			if (contactEntry.hasNickname()) {
				Nickname n = contactEntry.getNickname();
				String originalNickname = n.getValue();
				if (!nickname.equals(originalNickname)) {
					 /*
					 * backup and change nickname
					 */
					String propertyName = "nickname before "
							+ eventGroup.getTitle().getPlainText();

					ExtendedProperty extendedProperty = null;
					for (ExtendedProperty p : contactEntry
							.getExtendedProperties()) {
						if (p.getName().equals(propertyName)) {
							extendedProperty = p;
							break;
						}
					}
					if (extendedProperty == null) {
						extendedProperty = new ExtendedProperty();
						extendedProperty.setName(propertyName);
						extendedProperty.setValue(nickname);

						contactEntry
								.addExtendedProperty(extendedProperty);

					} else if (!extendedProperty.getValue().equals(
							nickname)) {
						extendedProperty.setValue(nickname);
					}

					n.setValue(nickname);
					dirty = true;
				}
			} else {
				contactEntry.setNickname(new Nickname(nickname));
				dirty = true;
			}
		}

		/*
		 * join group
		 */
		if (joinGroup(eventGroup.getId(), contactEntry)) {
			dirty = true;
		}

		if (timestamp != null && setEvent(eventName, timestamp, true, true, contactEntry)) {
			dirty = true;
		}

		if (timestamp != null && setEvent("first seen", timestamp, true, false, contactEntry)) {
			dirty = true;
		}

		if (timestamp != null && setEvent("last seen", timestamp, false, true, contactEntry)) {
			dirty = true;
		}

		return dirty;
	}

	private boolean joinGroup(String groupAtomId, ContactEntry entry)
			throws IOException, ServiceException {

		boolean isMember = false;
		for (GroupMembershipInfo g : entry.getGroupMembershipInfos()) {
			if (g.getHref().equals(groupAtomId)) {
				isMember = true;
				logger.debug("already in the group: " + groupAtomId);
				break;
			}
		}

		if (!isMember) {
			entry.addGroupMembershipInfo(new GroupMembershipInfo(false,
					groupAtomId));
			// URL editUrl = new URL(entry.getEditLink().getHref());
			// entry = contacts.update(editUrl, entry);
			logger.debug("joined the group: " + groupAtomId);
		}

		return !isMember;
	}

	private ContactEntry createContact(URL url, String nickname, String emailAddress, String phoneNumber)
			throws IOException, ServiceException {

		logger.debug("creating new contact: " + nickname + "");

		/*
		 * create contact
		 */
		ContactEntry entry = new ContactEntry();

		if (nickname != null) {
			entry.setNickname(new Nickname(nickname));
		}

		addEmail(nickname, emailAddress, entry);
		addPhone(phoneNumber, entry);

		ContactEntry newEntry = getService().insert(url, entry);

		return newEntry;
	}

	private boolean setEvent(String label, Date date, boolean acceptBefore, boolean acceptAfter, ContactEntry entry) {

		/*-
		 * https://developers.google.com/google-apps/contacts/v3/reference#gcEvent
		 */

		boolean dirty = false;

		ArrayList<Event> dupes = new ArrayList<>();
		for (Event e : entry.getEvents()) {
			/*
			 * TODO uncomment this block was for cleaning test data
			 * i.e. contacts events whose name don't match first seen, last seen, or * Register/Check-in/Feedback
			 */
//			if (e.hasLabel() &&
//					!e.getLabel().equals("first seen") &&
//					!e.getLabel().equals("last seen") &&
//					!e.getLabel().contains("Register") &&
//					!e.getLabel().contains("Check-in") &&
//					!e.getLabel().contains("Feedback")) {
//				entry.getEvents().remove(e);
//				dirty = true;
//			}
			if (e.hasLabel() && e.getLabel().equals(label)) {
				dupes.add(e);
			}
		}

		if (dupes.size() == 1) {
			Event event = dupes.get(0);
			When when = event.getWhen();
			if (acceptBefore && when.getStartTime().compareTo(date) > 0) {
				when.setStartTime(newDateOnly(date));
				dirty = true;
			}
			if (acceptAfter && when.getStartTime().compareTo(date) < 0) {
				when.setStartTime(newDateOnly(date));
				dirty = true;
			}
		} else {
			DateTime minDate = null;
			DateTime maxDate = null;
			for (Event e : dupes) {
				if (minDate == null || e.getWhen().getStartTime().compareTo(minDate) < 0) {
					minDate = e.getWhen().getStartTime();
				}
				if (maxDate == null || e.getWhen().getStartTime().compareTo(maxDate) > 0) {
					maxDate = e.getWhen().getStartTime();
				}
				entry.getEvents().remove(e);

				dirty = true;
			}

			if (acceptBefore || acceptAfter) {
				if (minDate == null || minDate.compareTo(date) > 0) {
					minDate = newDateOnly(date);
				}
				if (maxDate == null || maxDate.compareTo(date) < 0) {
					maxDate = newDateOnly(date);
				}

				Event event = new Event(label, null);

				When when = new When();
				if (acceptBefore && !acceptAfter) {
					when.setStartTime(minDate);
				} else if (!acceptBefore && acceptAfter) {
					when.setStartTime(maxDate);
				} else {
					when.setStartTime(newDateOnly(date));
				}

				event.setWhen(when);
				entry.addEvent(event);

				dirty = true;
			}

		}

		return dirty;
	}

	private DateTime newDateOnly(Date date) {

		DateTime dateTime = new DateTime(date);

		/*
		 * However, only the @startTime attribute is supported. Furthermore, its value must be a pure date, without the time component.
		 * See https://developers.google.com/google-apps/contacts/v3/reference#restrictionsOnGdwhen
		 */
		dateTime.setDateOnly(true);

		return dateTime;
	}

	private boolean addPhone(String phoneNumber, ContactEntry entry) {

		boolean dirty = false;

		if (phoneNumber != null && phoneNumber.trim().length() > 0) {
			PhoneNumber number = new PhoneNumber();
			number.setPhoneNumber(phoneNumber);
			number.setRel("http://schemas.google.com/g/2005#home");
			// primaryPhoneNumber.setPrimary(true);
			entry.addPhoneNumber(number);

			dirty = true;
		}


		return dirty;
	}

	private boolean addEmail(String nickname, String emailAddress,
							 ContactEntry entry) {

		boolean dirty = false;

		if (emailAddress != null && emailAddress.trim().length() > 0) {
			Email email = new Email();
			email.setAddress(emailAddress);
			email.setDisplayName(nickname);

			if (emailAddress.endsWith("@gmail.com") //
				/*
				 * aol
				 */
					|| emailAddress.endsWith("@aol.com")
					|| emailAddress.endsWith("@love.com")
					|| emailAddress.endsWith("@ygm.com")
					|| emailAddress.endsWith("@games.com")
					|| emailAddress.endsWith("@wow.com")
				/*
				 * yahoo
				 */
					|| emailAddress.endsWith("@yahoo.com")
				/*
				 * microsoft
				 */
					|| emailAddress.endsWith("@outlook.com")
				/*
				 * 163
				 */
					|| emailAddress.endsWith("@163.com")
					|| emailAddress.endsWith("@qq.com")) {
				email.setRel("http://schemas.google.com/g/2005#home");
			} else {
				email.setRel("http://schemas.google.com/g/2005#work");
			}
			// email.setPrimary(true);
			entry.addEmailAddress(email);

			dirty = true;
		}

		return dirty;
	}

	public void dedupeGroups() throws IOException, ServiceException {

		// Feed that holds all the batch request entries.
//		ContactFeed requestFeed = new ContactFeed();

		URL url = new URL(CONTACTS_URL);

		Query groupQuery = createAllContactsQuery(new URL(GROUPS_URL));
		ContactGroupFeed groupFeed = getService().query(groupQuery, ContactGroupFeed.class);

		Map<String, ContactGroupEntry> groupMap = new HashMap<>();
		for (ContactGroupEntry groupEntry : groupFeed.getEntries()) {

			if (groupEntry.hasSystemGroup()) {
				continue;
			}

			String groupName = groupEntry.getTitle().getPlainText();
			ContactGroupEntry firstMatchingGroup = groupMap.get(groupName);
			if (firstMatchingGroup == null) {
				groupMap.put(groupName, groupEntry);
			} else {
				/*
				 * iterate the contacts of groupEntry, add them to firstMatchingGroup, remove groupEntry from the contacts, and remove groupEntry
				 */

				String groupAtomId = groupEntry.getId();
				String firstMatchingGroupAtomId = firstMatchingGroup.getId();

				List<ContactEntry> contacts;
				do {
					contacts = removeContactFromGroup(url, groupAtomId, firstMatchingGroupAtomId);
				} while (contacts.size() > 0);

				groupEntry.delete();
			}
		}

		return;
	}

	public List<ContactEntry> removeContactFromGroup(
			URL contactURL, String groupAtomId, String firstMatchingGroupAtomId)
			throws IOException, ServiceException {

		List<ContactEntry> failedContacts = new ArrayList<>();

		ContactQuery contactQuery = createAllContactsQuery(contactURL);
		contactQuery.setGroup(groupAtomId);

		ContactFeed contactFeed = getService().query(contactQuery,
				ContactFeed.class);
		for (ContactEntry entry : contactFeed.getEntries()) {
			try {
				boolean dirty = false;

				if (joinGroup(firstMatchingGroupAtomId, entry)) {
					dirty = true;
				}

				List<GroupMembershipInfo> list = entry.getGroupMembershipInfos();
				for (GroupMembershipInfo g : list) {
					if (g.getHref().equals(groupAtomId)) {
//							g.setDeleted(true);
						list.remove(g);
						dirty = true;
						break;
					}
				}

				if (dirty) {
//						BatchUtils.setBatchId(entry, "update");
//						BatchUtils.setBatchOperationType(entry, BatchOperationType.UPDATE);
//						requestFeed.getEntries().add(entry);
					entry = entry.update();
				}
			} catch (Exception ex) {
				failedContacts.add(entry);
			}
		}

//				// Submit the batch request to the server.
//				ContactFeed responseFeed =
//						getService().batch(new URL(CONTACT_BATCH_URL),
//								requestFeed);
//				// Check the status of each operation.
//				for (ContactEntry entry : responseFeed.getEntries()) {
//					String batchId = BatchUtils.getBatchId(entry);
//					BatchStatus status = BatchUtils.getBatchStatus(entry);
//					logger.debug(batchId + ": " + status.getCode() + " (" + status.getReason() + ")");
//				}

		return failedContacts;
	}

	private ContactQuery createAllContactsQuery(URL url) {

		ContactQuery contactQuery = new ContactQuery(url);

		/*-
		 * If you want to receive all of the contacts, rather than only
		the default maximum, you can specify a very large number for
		max-results.
		 *
		 *
		https://developers.google.com/google-apps/contacts/v3/reference#Parameters
		 */
		contactQuery.setMaxResults(Integer.MAX_VALUE);

		return contactQuery;
	}

	public List<ParticipantStatistics> listActivities(Date cutoff) throws IOException, ServiceException {

		List<ParticipantStatistics> result = new ArrayList<>();

		Map<String, String> peopleHaveyouGplusIdMap = new HashMap<>();
		{
			List<String> lines = IOUtils.readLines(
					Thread.currentThread().getContextClassLoader().getResourceAsStream("peopleHaveyou.properties"));
			for (String line : lines) {
				int index = line.indexOf(KEY_VALUE_DELIMITER);
				/*
				 * java - How to convert a string with Unicode encoding to a string of letters - Stack Overflow
				 * http://stackoverflow.com/a/14368185/333033
				 */
				String id = StringEscapeUtils.unescapeJava(line.substring(0, index));

				String email = line.substring(index + KEY_VALUE_DELIMITER.length());

				if (id.startsWith(COMMENT_LINE_START)) {
					id = id.substring(1);
				}
				if (email.startsWith(COMMENT_LINE_START)) {
					email = email.substring(1);
				}

				String oldId = peopleHaveyouGplusIdMap.put(email, id);
				if (oldId != null && oldId.startsWith("+")) {
					/*
					 * custom url path is preferred over id
					 */
					peopleHaveyouGplusIdMap.put(email, oldId);
				}
			}
		}

		/*
		 * TODO fetch all groups
		 */
		Map<String, String> groupMap = new HashMap<>();
		Query groupQuery = createAllContactsQuery(new URL(GROUPS_URL));
		ContactGroupFeed groupFeed = getService().query(groupQuery, ContactGroupFeed.class);
		for (ContactGroupEntry groupEntry : groupFeed.getEntries()) {
			groupMap.put(groupEntry.getId(), groupEntry.getTitle().getPlainText());
		}

		URL contactURL = new URL(CONTACTS_URL);
		ContactQuery contactQuery = createAllContactsQuery(contactURL);
		ContactFeed contactFeed = getService().query(contactQuery,
				ContactFeed.class);
		for (ContactEntry entry : contactFeed.getEntries()) {
			ParticipantStatistics participantStatistics = getParticipantStatistics(
					cutoff, peopleHaveyouGplusIdMap, groupMap, entry);

			logger.debug(participantStatistics.toString());
			result.add(participantStatistics);
		}

		return result;
	}

	ParticipantStatistics getParticipantStatistics(
			Date cutoff, Map<String, String> peopleHaveyouGplusIdMap, Map<String, String> groupMap, ContactEntry entry)
			throws IOException, ServiceException {

		String nickname;
		{
			if (entry.hasNickname()) {
				nickname = entry.getNickname().getValue();
			} else {
				/*
				 * do not use full name which is maintained manually, and for internal view only
				 * TODO get gplus profile name
				 */
				nickname = "(anonymous)";
			}
//			logger.debug(nickname);
		}

		/*
		 * get gplus id
		 */
		String gplusId = null;
		for (Email email : entry.getEmailAddresses()) {
			String emailAddress = email.getAddress();
			if (emailAddress.endsWith("@gmail.com")) {
				/*
				 * nomalize gmail address, e.g. change renfeng.cn@gmail.com to renfengcn@gmail.com
				 */
				emailAddress = emailAddress.substring(0, emailAddress.indexOf("@gmail.com")).replaceAll("[.]", "") +
						"@gmail.com";
			}
			gplusId = peopleHaveyouGplusIdMap.get(emailAddress);
			if (gplusId != null) {
				break;
			}
		}
		if (gplusId == null) {
			logger.info("retrieving G+ ID from Google Contacts API");
			for (Website w : entry.getWebsites()) {
				String href = w.getHref();
				Website.Rel rel = w.getRel();
				if (href.startsWith("http://www.google.com/profiles/")) {
					gplusId = href.substring("http://www.google.com/profiles/".length());
					logger.info("G+ ID: " + gplusId);
					break;
				} else if (href.startsWith("https://www.google.com/profiles/")) {
					gplusId = href.substring("https://www.google.com/profiles/".length());
					logger.info("G+ ID: " + gplusId);
					break;
				} else if (href.startsWith("https://plus.google.com/")) {
					gplusId = href.substring("https://plus.google.com/".length());
					logger.info("G+ ID: " + gplusId);
					break;
				} else if (href.startsWith("http://plus.google.com/")) {
					gplusId = href.substring("http://plus.google.com/".length());
					logger.info("G+ ID: " + gplusId);
					break;
				}
			}
			if (gplusId == null) {
				logger.info("G+ ID: (missing)");
			}
		}

		/*
		 * TODO get gplus profile name (and photo), see PeopleHaveyou.java custom url
		 */

		int credit = 0;
		Date from = new Date();
		Date to = cutoff;
		Map<String, EventActivities> eventMap = new HashMap<>();
		for (Event event : entry.getEvents()) {
			String label = event.getLabel();
			if (label == null) {
				continue;
			}
			Matcher matcher = ACTIVITY_PATTERN.matcher(label);
			if (!matcher.matches()) {
				continue;
			}

			String dateString = matcher.group(1);
			String activity = matcher.group(2);

			Date date;
			try {
				date = DATE_FORMAT.parse(dateString);
			} catch (ParseException e) {
				logger.debug("failed to parse activity date from event label", e);
				date = new Date(event.getWhen().getStartTime().getValue());
			}
			if (date.before(cutoff)) {
				continue;
			}

			EventActivities eventActivities = eventMap.get(label);
			if (eventActivities == null) {
				eventActivities = new EventActivities();
				eventMap.put(label, eventActivities);
			}
			if ("Register".equals(activity)) {
				credit++;
				eventActivities.setRegister(dateString);
			} else if ("Check-in".equals(activity)) {
				credit++;
				eventActivities.setCheckIn(dateString);
			} else if ("Feedback".equals(activity)) {
				credit++;
				eventActivities.setFeedback(dateString);
//			} else if ("Sponsor".equals(activity)) {
//				credit++;
//				eventActivities.setSponsor(dateString);
			} else {
				continue;
			}

			if (from.after(date)) {
				from = date;
			}
			if (to.before(date)) {
				to = date;
			}
		}
		for (EventActivities eventActivities : eventMap.values()) {
			if (eventActivities.getRegister() != null) {
				if (eventActivities.getCheckIn() != null) {
					if (eventActivities.getFeedback() != null) {
						credit += 1 + 2 + 4;
					} else {
						credit += 1 + 2;
					}
				}
			}
		}

		List<Streak> streaks = new ArrayList<>();
		for (GroupMembershipInfo groupMembershipInfo : entry.getGroupMembershipInfos()) {
			String href = groupMembershipInfo.getHref();
			String groupName = groupMap.get(href);
//			if (groupName == null) {
//				ContactGroupEntry group = getService().getEntry(new URL(href), ContactGroupEntry.class);
//
//				/*
//				 * do NOT skip caching system groups
//				 */
////				if (group.hasSystemGroup()) {
////					logger.trace("skipping system group");
////					continue;
////				}
//
//				groupName = group.getTitle().getPlainText();
//				groupMap.put(href, groupName);
//			}
			Matcher matcher = GROUP_PATTERN.matcher(groupName);
			if (matcher.matches()) {
				try {
					Streak streak = new Streak();

					Date date = DATE_FORMAT.parse(matcher.group(1));
					streak.setFromDate(date);
					{
						/*
						 * the effective date is in 90 days
						 */
						Calendar calendar = Calendar.getInstance();
						calendar.setTime(date);
						calendar.add(Calendar.DATE, Streak.STREAK_WINDOW_IN_DAYS);
						streak.setToDate(calendar.getTime());
					}

					streak.setCount(1);

					streaks.add(streak);
				} catch (ParseException e) {
					logger.error("activity parse error", e);
				}
			}
		}

		Streak latestStreak;
		if (streaks.size() > 0) {
			Collections.sort(streaks, new Comparator<Streak>() {
				@Override
				public int compare(Streak o1, Streak o2) {
					return -o1.getFromDate().compareTo(o2.getFromDate());
				}
			});
			latestStreak = streaks.get(0);
			for (int i = 1; i < streaks.size(); i++) {
				Streak streak = streaks.get(i);
				if (!latestStreak.inRange(streak)) {
					break;
				}
				if (latestStreak.getFromDate().after(streak.getFromDate())) {
					latestStreak.setFromDate(streak.getFromDate());
				}
				if (latestStreak.getToDate().before(streak.getToDate())) {
					latestStreak.setToDate(streak.getToDate());
				}
				latestStreak.setCount(latestStreak.getCount() + streak.getCount());
				streaks.set(i, null);
			}

			/*
			 * only count streaks not ended. i.e. those for people attended at least one event in the last 90 days.
			 */
			Calendar calendar = Calendar.getInstance();
			// calendar.add(Calendar.DATE, -Streak.STREAK_WINDOW_IN_DAYS);
			if (latestStreak.getToDate().before(calendar.getTime())) {
				latestStreak = null;
			}
		} else {
			latestStreak = null;
		}

		ParticipantStatistics participantStatistics = new ParticipantStatistics();
		participantStatistics.setCredit(credit);
		participantStatistics.setFromDate(from);
		participantStatistics.setToDate(to);
		participantStatistics.setLatestStreak(latestStreak);
		participantStatistics.setGplusID(gplusId);
		participantStatistics.setNickname(nickname);

		String id = entry.getId();
		participantStatistics.setContactID(id.substring(id.lastIndexOf("/") + "/".length()));

		return participantStatistics;
	}
}
