package com.google.developers.event;

import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by frren on 2015-08-03.
 */
public class SpreadsheetDataStoreFactory implements DataStoreFactory {
	@Override
	public <V extends Serializable> DataStore<V> getDataStore(String id) throws IOException {
		return null;
	}
}
