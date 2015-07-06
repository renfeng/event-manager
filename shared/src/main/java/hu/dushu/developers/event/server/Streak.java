package hu.dushu.developers.event.server;

import java.util.Date;

/**
 * Created by renfeng on 5/20/15.
 */
public class Streak {

	/*
	 * TODO change back to 90 - with a large number, credit should be equal to streak.credit
	 */
	public static final int STREAK_WINDOW_IN_DAYS = 90;
//	public static final int STREAK_WINDOW_IN_MILLISECONDS = STREAK_WINDOW_IN_DAYS * 24 * 60 * 60 * 1000;

	private int count;
	private Date fromDate;
	private Date toDate;

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
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

	public boolean inRange(Streak o) {
//        long differenceInMilliseconds = o.getFromDate().getTime() - getFromDate().getTime();
//        return (int) (differenceInMilliseconds / STREAK_WINDOW_IN_MILLISECONDS) == 0;
		return o.getFromDate().before(getToDate()) && o.getToDate().after(getFromDate());
	}

	@Override
	public String toString() {
		return "Streak: " + getCount() + ", from: " + getFromDate() + ", to: " + getToDate();
	}
}
