package com.google.developers.setup;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class Scheduler extends ScheduledThreadPoolExecutor {

//	private static final int POOL_SIZE = Integer.MAX_VALUE;
	private static final int POOL_SIZE = 8;

	public Scheduler() {
		super(POOL_SIZE, new DaemonThreadFactory("Developers"));
	}
}
