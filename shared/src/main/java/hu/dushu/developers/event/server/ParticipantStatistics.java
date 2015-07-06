package hu.dushu.developers.event.server;

import java.util.Date;

/**
 * Created by renfeng on 5/19/15.
 */
public class ParticipantStatistics {

	/*
	 * contact id
	 *
	 * https://mail.google.com/mail/b/319/u/0/#contacts
	 * https://mail.google.com/mail/b/319/u/0/#contact/2c6f2aa189f5503a
	 *
	 * https://www.google.com/contacts/u/1/?cplus=1#contacts
	 * https://www.google.com/contacts/u/1/?cplus=1#contact/44adf308283f47
	 *
	 * com.google.gdata.data.BaseEntry.getId()
	 * http://www.google.com/m8/feeds/contacts/suzhou.gdg%40gmail.com/base/44adf308283f47
	 */
	private String contactID;

	private String nickname;
	private String gplusID;

	/*
	 * credit is modeled after Contribution in the last year
	 * streak, the latest, a variation toDate Longest and Current streak
	 * https://github.com/renfeng/
	 */

	private int credit;
	private Date fromDate;
	private Date toDate;

	private Streak latestStreak;

	public String getContactID() {
		return contactID;
	}

	public void setContactID(String contactID) {
		this.contactID = contactID;
	}

	public Streak getLatestStreak() {
		return latestStreak;
	}

	public void setLatestStreak(Streak latestStreak) {
		this.latestStreak = latestStreak;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getGplusID() {
		return gplusID;
	}

	public void setGplusID(String gplusID) {
		this.gplusID = gplusID;
	}

	public int getCredit() {
		return credit;
	}

	public void setCredit(int credit) {
		this.credit = credit;
	}

	public Date getFromDate() {
		return fromDate;
	}

	public void setFromDate(Date fromDate) {
		this.fromDate = fromDate;
	}

	public Date getToDate() {
		return toDate;
	}

	public void setToDate(Date toDate) {
		this.toDate = toDate;
	}

	@Override
	public String toString() {
		/*
		 * %[argument_index$][flags][width][.precision]conversion
		 */
//		return String.format("Credit: %d, from: %t", getCredit(), getFromDate(), getToDate());
		return getNickname() + ", credit: " + getCredit() + ", from: " + getFromDate() + ", to: " + getToDate() +
				", streak: " + getLatestStreak();
	}
}
