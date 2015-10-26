package com.google.developers.event;

import com.csvreader.CsvReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HeaderValidatingCsvReader extends CsvReader {

	public HeaderValidatingCsvReader(
			InputStream inputStream, Charset charset,
			int skipLines, String... headers) throws IOException {

		super(inputStream, charset);

		for (int i = 0; i < skipLines; i++) {
			if (!skipLine()) {
				throw new IOException("invalid file");
			}
		}

		if (headers.length > 0) {
			List<String> expectedHeaders = new ArrayList<>(
					Arrays.asList(headers));
			if (readHeaders()) {
				List<String> actualHeaders = Arrays.asList(getHeaders());
				expectedHeaders.removeAll(actualHeaders);
				int size = expectedHeaders.size();
				if (size > 0) {
					throw new IOException("missing headers: "
							+ Arrays.toString(expectedHeaders
							.toArray(new String[size])));
				}
			} else {
				throw new IOException("missing headers");
			}
		}
	}
}