package com.google.developers.event;

import com.google.api.client.util.Key;

/**
 * Created by renfeng on 10/27/15.
 */
public class EventBritePagination {

	@Key("object_count")
	private int objectCount;

	@Key("page_number")
	private int pageNumber;

	@Key("page_size")
	private int pageSize;

	@Key("page_count")
	private int pageCount;

	public int getObjectCount() {
		return objectCount;
	}

	public void setObjectCount(int objectCount) {
		this.objectCount = objectCount;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getPageCount() {
		return pageCount;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}
}
