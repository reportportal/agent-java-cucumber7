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

package com.epam.reportportal.cucumber.integration;

import com.epam.reportportal.cucumber.ScenarioReporter;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.core.gherkin.Feature;

import javax.annotation.Nonnull;
import java.net.URI;

public class TestScenarioReporterWithPause extends ScenarioReporter {
	public static final ThreadLocal<ReportPortal> RP = new ThreadLocal<>();

	@Override
	protected ReportPortal buildReportPortal() {
		return RP.get();
	}

	@Override
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRequest(@Nonnull Feature feature, @Nonnull URI uri) {
		StartTestItemRQ result = super.buildStartFeatureRequest(feature, uri);
		try {
			Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		} catch (InterruptedException ignore) {
		}
		return result;
	}
}
