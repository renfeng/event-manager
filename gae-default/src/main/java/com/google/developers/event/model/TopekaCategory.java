package com.google.developers.event.model;

import com.google.api.client.util.DateTime;
import com.google.api.client.util.Key;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by renfeng on 8/9/15.
 */
public class TopekaCategory {

	@Key
	private String name;

	/**
	 * entertainment
	 * food
	 * geography
	 * history
	 * knowledge
	 * music
	 * science
	 * sports
	 * tvmovies
	 */
	@Key
	private String id;

	@Key
	private String theme;

	@Key
	private SortedSet<TopekaQuiz> quizzes = new TreeSet<>();

	private DateTime updated;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTheme() {
		return theme;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public SortedSet<TopekaQuiz> getQuizzes() {
		return quizzes;
	}

	public void setQuizzes(SortedSet<TopekaQuiz> quizzes) {
		this.quizzes = quizzes;
	}

	public DateTime getUpdated() {
		return updated;
	}

	public void setUpdated(DateTime updated) {
		this.updated = updated;
	}
}
