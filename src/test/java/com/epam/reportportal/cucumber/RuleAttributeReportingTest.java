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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class RuleAttributeReportingTest {
	@CucumberOptions(features = "src/test/resources/features/RuleKeywordAttributes.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RuleTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> ruleIds = Stream.generate(() -> CommonUtils.namedId("rule_")).limit(2).collect(Collectors.toList());
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(3).collect(Collectors.toList());

	private final List<Pair<String, List<String>>> tests = Stream.of(
			Pair.of(ruleIds.get(0), testIds.subList(0, 2)),
			Pair.of(ruleIds.get(1), testIds.subList(2, 3))
	).collect(Collectors.toList());

	private final List<Pair<String, String>> steps = testIds.stream()
			.flatMap(t -> Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).map(s -> Pair.of(t, s)))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockNestedSteps(client, steps);
		TestUtils.mockLogging(client);
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

	private static final List<Pair<String, String>> RULE1_ATTRIBUTES = Arrays.asList(Pair.of(null, "first"), Pair.of("key", "value"));
	private static final List<Pair<String, String>> RULE2_ATTRIBUTES = Arrays.asList(Pair.of(null, "second"), Pair.of("key", "value"));

	@Test
	public void verify_scenario_reporter_attributes() {
		TestUtils.runTests(RuleTest.class);

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> ruleCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(suiteId), ruleCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(ruleIds.get(0)), scenarioCaptor1.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(ruleIds.get(1)), scenarioCaptor2.capture());
		testIds.forEach(t -> verify(client, times(2)).startTestItem(same(t), any(StartTestItemRQ.class)));

		verifyAttributes(featureCaptor.getValue().getAttributes(), FEATURE_ATTRIBUTES);
		verifyAttributes(ruleCaptor.getAllValues().get(0).getAttributes(), RULE1_ATTRIBUTES);
		verifyAttributes(ruleCaptor.getAllValues().get(1).getAttributes(), RULE2_ATTRIBUTES);
	}
}
