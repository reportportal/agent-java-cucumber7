/*
 * Copyright 2026 EPAM Systems
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

package com.epam.reportportal.cucumber.testng;

import com.epam.reportportal.cucumber.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IExecutionListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

/**
 * The listener is supposed to be used with TestNG Cucumber runner and retry listeners to allow
 * {@link com.epam.reportportal.cucumber.ScenarioReporter} determine if a Scenario is a retry.
 */
public class TestNgRetriesListener implements IExecutionListener, ITestListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestNgRetriesListener.class);
	private static final Map<String, Boolean> RETRIES = new ConcurrentHashMap<>();
	private static final Boolean ENABLED;

	static {
		boolean hasTestNG = false;
		try {
			Class.forName("io.cucumber.testng.PickleWrapper");
			hasTestNG = true;
		} catch (ClassNotFoundException ignore) {
		}
		ENABLED = hasTestNG;
	}

	@Override
	public void onExecutionFinish() {
		if (!ENABLED) {
			return;
		}
		RETRIES.clear();
	}

	@Override
	public void onTestStart(ITestResult result) {
		if (!ENABLED) {
			return;
		}
		ofNullable(result.getParameters()).map(params -> (io.cucumber.testng.PickleWrapper) params[0])
				.map(io.cucumber.testng.PickleWrapper::getPickle)
				.map(pickle -> pickle.getUri().toString() + Utils.KEY_VALUE_SEPARATOR + pickle.getLine())
				.ifPresent(uniqueId -> {
					RETRIES.put(uniqueId, result.wasRetried());
					LOGGER.warn("Test result started: {}; retry: {}", uniqueId, result.wasRetried());
				});
	}

	public static boolean isRetry(String id) {
		return RETRIES.getOrDefault(id, false);
	}
}
