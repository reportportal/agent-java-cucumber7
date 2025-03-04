/*
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class AttributeReportingTest {
	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("suite_");
	private final String testId = CommonUtils.namedId("test_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = nestedStepIds.stream()
			.map(s -> Pair.of(stepIds.get(0), s))
			.collect(Collectors.toList());

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

	private static void verifyAnnotationAttributes(List<StartTestItemRQ> testSteps) {
		Set<ItemAttributesRQ> stepAttributes = testSteps.get(0).getAttributes();
		verifyAttributes(stepAttributes, Collections.singleton(Pair.of("key", "value")));

		stepAttributes = testSteps.get(1).getAttributes();
		verifyAttributes(
				stepAttributes,
				new HashSet<>(Arrays.asList(Pair.of("key1", "value1"), Pair.of("key2", "value2"), Pair.of("k1", "v"), Pair.of("k2", "v")))
		);

		stepAttributes = testSteps.get(2).getAttributes();
		verifyAttributes(stepAttributes, new HashSet<>(Arrays.asList(Pair.of(null, "v1"), Pair.of(null, "v2"))));
	}

	private static final List<Pair<String, String>> FEATURE_ATTRIBUTES = Arrays.asList(Pair.of(null, "@smoke"), Pair.of(null, "@test"));

	@Test
	public void verify_scenario_reporter_attributes() {
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.runTests(SimpleTest.class);

		ArgumentCaptor<StartTestItemRQ> mainSuiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(mainSuiteCaptor.capture());
		assertThat(mainSuiteCaptor.getValue().getAttributes(), anyOf(emptyIterable(), nullValue()));

		ArgumentCaptor<StartTestItemRQ> suiteCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(suiteId), suiteCaptor.capture());
		verifyAttributes(suiteCaptor.getValue().getAttributes(), FEATURE_ATTRIBUTES);

		ArgumentCaptor<StartTestItemRQ> testCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(testId), testCaptor.capture());
		verifyAttributes(testCaptor.getValue().getAttributes(), Collections.singleton(Pair.of(null, "@ok")));

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(stepIds.get(0)), stepCaptor.capture());

		verifyAnnotationAttributes(stepCaptor.getAllValues());
	}
}
