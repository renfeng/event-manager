package com.google.developers.event.model;

import com.google.api.client.util.DateTime;
import com.google.api.client.util.Key;

import java.util.List;

/**
 * Created by renfeng on 8/9/15.
 */
public class TopekaQuiz implements Comparable<TopekaQuiz> {

	/**
	 * alpha-picker
	 * fill-blank
	 * fill-two-blanks
	 * four-quarter
	 * multi-select
	 * picker
	 * single-select-item
	 * single-select
	 * toggle-translate
	 * true-false
	 * gplus-post
	 */
	@Key
	private String type = "gplus-post";

	@Key
	private String question;

	/*
	 * optional
	 */
	@Key
	private List<String> options;
	@Key
	private Integer min;
	@Key
	private Integer max;
	@Key
	private String start;
	@Key
	private String end;

	/**
	 * String, String[], int, int[], boolean
	 */
	@Key
	private Object answer;

	@Key
	private int points = 1;

	private DateTime updated;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public List<String> getOptions() {
		return options;
	}

	public void setOptions(List<String> options) {
		this.options = options;
	}

	public Integer getMin() {
		return min;
	}

	public void setMin(Integer min) {
		this.min = min;
	}

	public Integer getMax() {
		return max;
	}

	public void setMax(Integer max) {
		this.max = max;
	}

	public String getStart() {
		return start;
	}

	public void setStart(String start) {
		this.start = start;
	}

	public String getEnd() {
		return end;
	}

	public void setEnd(String end) {
		this.end = end;
	}

	public Object getAnswer() {
		return answer;
	}

	public void setAnswer(Object answer) {
		this.answer = answer;
	}

	public int getPoints() {
		return points;
	}

	public void setPoints(int points) {
		this.points = points;
	}

	public DateTime getUpdated() {
		return updated;
	}

	public void setUpdated(DateTime updated) {
		this.updated = updated;
	}

	@Override
	public int compareTo(TopekaQuiz o) {
		return -Long.compare(getUpdated().getValue(), o.getUpdated().getValue());
	}
}
