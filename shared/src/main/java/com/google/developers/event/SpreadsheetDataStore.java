package com.google.developers.event;

import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/**
 * Created by frren on 2015-08-03.
 */
public class SpreadsheetDataStore<V extends Serializable> extends AbstractDataStore<V> {

	/**
	 * @param dataStoreFactory data store factory
	 * @param id               data store ID
	 */
	protected SpreadsheetDataStore(DataStoreFactory dataStoreFactory, String id) {
		super(dataStoreFactory, id);
	}

	@Override
	public Set<String> keySet() throws IOException {
		return null;
	}

	@Override
	public Collection<V> values() throws IOException {
		return null;
	}

	@Override
	public V get(String key) throws IOException {
		return null;
	}

	@Override
	public DataStore<V> set(String key, V value) throws IOException {
		return null;
	}

	@Override
	public DataStore<V> clear() throws IOException {
		return null;
	}

	@Override
	public DataStore<V> delete(String key) throws IOException {
		return null;
	}
}
