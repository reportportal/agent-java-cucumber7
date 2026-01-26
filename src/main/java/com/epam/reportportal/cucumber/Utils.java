/*
 * Copyright 2025 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.utils.MimeTypeDetector;
import com.epam.reportportal.utils.files.ByteSource;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.plugin.event.Node;
import io.cucumber.plugin.event.Status;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for static methods
 *
 * @author Vadzim Hushchanskou
 */
public class Utils {
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	private static final String EMPTY = "";
	public static final String TAG_KEY = "@";
	public static final String KEY_VALUE_SEPARATOR = ":";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	//@formatter:off
	public static final Map<Status, ItemStatus> STATUS_MAPPING = Map.of(
			Status.PASSED, ItemStatus.PASSED,
			Status.FAILED, ItemStatus.FAILED,
			Status.SKIPPED, ItemStatus.SKIPPED,
			Status.PENDING, ItemStatus.SKIPPED,
			Status.AMBIGUOUS, ItemStatus.SKIPPED,
			Status.UNDEFINED, ItemStatus.SKIPPED,
			Status.UNUSED, ItemStatus.SKIPPED
	);
	//@formatter:on

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @return transformed string
	 */
	public static String buildName(@Nullable String prefix, @Nullable String infix, @Nullable String argument) {
		return (prefix == null ? EMPTY : prefix) + infix + argument;
	}

	/**
	 * Parses a feature source and return all declared tags before the feature.
	 *
	 * @param feature Cucumber's Feature object
	 * @return tags set
	 */
	@Nonnull
	public static Set<String> getTags(@Nonnull Feature feature) {
		return feature.getKeyword().map(k -> {
			Set<String> tags = new HashSet<>();
			for (String line : feature.getSource().split("\\r?\\n")) {
				String bareLine = line.trim();
				if (bareLine.startsWith(k)) {
					return tags;
				}
				if (!line.startsWith(TAG_KEY)) {
					continue;
				}
				tags.addAll(Arrays.asList(line.split("\\s+")));
			}
			return tags;
		}).orElse(Collections.emptySet());
	}

	/**
	 * Parses a rule source and return all declared tags before the rule.
	 *
	 * @param rule Cucumber's Rule object
	 * @return tags set
	 */
	@Nonnull
	public static Set<String> getTags(@Nonnull Node.Rule rule) {
		return rule.getParent().map(p -> {
			if (!(p instanceof Feature)) {
				return Collections.<String>emptySet();
			}
			Feature feature = (Feature) p;
			List<Node> featureChildren = feature.elements()
					.stream()
					.sorted(Comparator.comparing(n -> n.getLocation().getLine()))
					.collect(Collectors.toList());
			int ruleIndex = IntStream.range(0, featureChildren.size())
					.filter(i -> featureChildren.get(i).equals(rule))
					.findFirst()
					.orElse(-1);
			if (ruleIndex < 0) {
				return Collections.<String>emptySet();
			}
			int lastLine = ruleIndex > 0 ? featureChildren.get(ruleIndex - 1).getLocation().getLine() : feature.getLocation().getLine();
			String[] lines = feature.getSource().split("\\r?\\n");
			Set<String> tags = new HashSet<>();
			for (int i = rule.getLocation().getLine() - 1; i > lastLine; i--) {
				String line = lines[i].trim();
				if (!line.startsWith(TAG_KEY)) {
					continue;
				}
				tags.addAll(Arrays.asList(line.split("\\s+")));
			}
			return tags;
		}).orElse(Collections.emptySet());
	}

	/**
	 * Convert a tag string to a ReportPortal attribute, split by key-value separator ":" if present.
	 *
	 * @param tag tag string
	 * @return attribute object
	 */
	public static ItemAttributesRQ toAttribute(String tag) {
		String tagStr = tag.trim();
		tagStr = tagStr.startsWith(TAG_KEY) ? tagStr.substring(TAG_KEY.length()) : tagStr; // strip leading '@'
		if (tagStr.contains(KEY_VALUE_SEPARATOR)) {
			String[] parts = tagStr.split(KEY_VALUE_SEPARATOR, 2);
			return new ItemAttributesRQ(parts[0], parts[1]);
		} else {
			return new ItemAttributesRQ(null, tagStr);
		}
	}

	/**
	 * Format a list of parameters into a string representation to use in code reference and Test Case ID.
	 *
	 * @param parameters list of parameters as key-value pairs
	 * @return formatted string representation of parameters
	 */
	@Nonnull
	public static String formatParameters(@Nonnull List<Pair<String, String>> parameters) {
		String paramString = parameters.stream()
				.sorted()
				.map(entry -> entry.getKey() + ":" + entry.getValue())
				.collect(Collectors.joining(";"));
		return "[" + paramString + "]";
	}

	/**
	 * Detects the MIME type of the given byte array using the MimeTypeDetector.
	 *
	 * @param data the byte array to analyze
	 * @param name an optional name to help with detection (can be null)
	 * @return the detected MIME type as a String, or null if detection fails
	 */
	@Nullable
	public static String getDataType(@Nonnull byte[] data, @Nullable String name) {
		try {
			return MimeTypeDetector.detect(ByteSource.wrap(data), name);
		} catch (IOException e) {
			LOGGER.warn("Unable to detect MIME type", e);
		}
		return null;
	}
}
