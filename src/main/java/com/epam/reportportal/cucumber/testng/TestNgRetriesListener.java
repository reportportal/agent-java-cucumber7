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
	private static final Map<String, Boolean> RETRIES = new ConcurrentHashMap<>();
	private static final Class<?> WRAPPER_CLASS;

	static {
		Class<?> wrapperClass = null;
		try {
			wrapperClass = Class.forName("io.cucumber.testng.PickleWrapper");
		} catch (Throwable ignore) {
		}
		WRAPPER_CLASS = wrapperClass;
	}

	@Override
	public void onExecutionFinish() {
		if (WRAPPER_CLASS == null) {
			return;
		}
		RETRIES.clear();
	}

	private static void setRetryFlag(ITestResult result) {
		if (WRAPPER_CLASS == null) {
			return;
		}

		ofNullable(result.getParameters()).filter(params -> params.length > 0 && params[0] != null && WRAPPER_CLASS.isInstance(params[0]))
				.map(params -> (io.cucumber.testng.PickleWrapper) params[0])
				.map(io.cucumber.testng.PickleWrapper::getPickle)
				.map(pickle -> pickle.getUri().toString() + Utils.KEY_VALUE_SEPARATOR + pickle.getLine())
				.ifPresent(uniqueId -> RETRIES.put(uniqueId, result.wasRetried()));
	}

	@Override
	public void onTestSuccess(ITestResult result) {
		setRetryFlag(result);
	}

	@Override
	public void onTestFailure(ITestResult result) {
		setRetryFlag(result);
	}

	@Override
	public void onTestSkipped(ITestResult result) {
		setRetryFlag(result);
	}

	@Override
	public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
		setRetryFlag(result);
	}

	public static boolean isRetry(String id) {
		return RETRIES.getOrDefault(id, false);
	}
}
