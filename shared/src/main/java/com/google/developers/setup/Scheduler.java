package com.google.developers.setup;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class Scheduler extends ScheduledThreadPoolExecutor {

	public Scheduler() {
		super(Integer.MAX_VALUE, new DaemonThreadFactory("Developers"));
	}
}
