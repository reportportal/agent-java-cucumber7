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

import com.epam.reportportal.cucumber.integration.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class AttributeReportingTest {
	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestScenarioReporter.RP.set(reportPortal);
	}

	private static void verifyAttributes(Collection<ItemAttributesRQ> attributes, Collection<Pair<String, String>> values) {
		assertThat(attributes, hasSize(values.size()));
		Set<Pair<String, String>> attributePairs = attributes.stream()
				.map(a -> Pair.of(a.getKey(), a.getValue()))
				.collect(Collectors.toSet());
		values.forEach(v -> assertThat(attributePairs, hasItem(v)));
	}

	private static final List<Pair<String, String>> FEATURE_ATTRIBUTES = Arrays.asList(Pair.of(null, "smoke"), Pair.of(null, "test"));

	@Test
	public void verify_scenario_reporter_attributes() {
		TestUtils.runTests(SimpleTest.class);

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(featureCaptor.capture());
		verifyAttributes(featureCaptor.getValue().getAttributes(), FEATURE_ATTRIBUTES);

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(suiteId), testCaptor.capture());
		verifyAttributes(testCaptor.getValue().getAttributes(), Collections.singleton(Pair.of(null, "ok")));

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testId), stepCaptor.capture());
	}
}
