package com.google.developers.event;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by frren on 2015-08-03.
 */
public class SpreadsheetDataStoreFactory extends AbstractDataStoreFactory {
	@Override
	protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
		return new SpreadsheetDataStore<>(this, id);
	}
}
