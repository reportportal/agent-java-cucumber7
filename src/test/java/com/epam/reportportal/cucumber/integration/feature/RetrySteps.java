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

package com.epam.reportportal.cucumber.integration.feature;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.concurrent.atomic.AtomicInteger;

public class RetrySteps {
	private static final AtomicInteger ATTEMPTS = new AtomicInteger();

	public static void reset() {
		ATTEMPTS.set(0);
	}

	@Given("I fail {int} times")
	public void i_fail_times(int times) {
		int attempt = ATTEMPTS.incrementAndGet();
		if (attempt <= times) {
			throw new IllegalStateException("Flaky failure on attempt " + attempt);
		}
	}

	@Then("I pass")
	public void i_pass() {
		// Step intentionally left blank.
	}
}
