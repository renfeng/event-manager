package com.google.developers.event.model;

import com.google.api.client.util.Key;

import java.util.List;

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
	private List<TopekaQuizz> quizzes;

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

	public List<TopekaQuizz> getQuizzes() {
		return quizzes;
	}

	public void setQuizzes(List<TopekaQuizz> quizzes) {
		this.quizzes = quizzes;
	}
}
