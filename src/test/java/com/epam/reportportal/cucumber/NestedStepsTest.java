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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class NestedStepsTest {

	@CucumberOptions(features = "src/test/resources/features/NestedStepsFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());
	private final List<String> nestedStepIds = Stream.generate(() -> CommonUtils.namedId("nested_step_"))
			.limit(3)
			.collect(Collectors.toList());
	private final List<Pair<String, String>> firstLevelNestedStepIds = Stream.of(
			Pair.of(stepIds.get(0), nestedStepIds.get(0)),
			Pair.of(stepIds.get(1), nestedStepIds.get(1)),
			Pair.of(stepIds.get(1), nestedStepIds.get(2))
	).collect(Collectors.toList());

	private final String nestedNestedStepId = CommonUtils.namedId("double_nested_step_");
	private final List<Pair<String, String>> secondLevelNestedStepIds = Collections.singletonList(Pair.of(
			nestedStepIds.get(0),
			nestedNestedStepId
	));

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockNestedSteps(client, firstLevelNestedStepIds);
		TestUtils.mockNestedSteps(client, secondLevelNestedStepIds);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	public static final List<String> STEP_NAMES = Arrays.asList("Given I have a step", "When I have one more step");

	public static final List<String> FIRST_LEVEL_NAMES = Arrays.asList(
			"A step inside step",
			"A step with parameters",
			"A step with attributes"
	);

	@Test
	public void test_scenario_reporter_nested_steps() {
		TestUtils.runTests(ReporterTest.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(captor.capture());
		verify(client).startTestItem(same(suiteId), captor.capture());
		List<StartTestItemRQ> parentItems = captor.getAllValues();
		parentItems.forEach(i -> assertThat(i.isHasStats(), anyOf(equalTo(Boolean.TRUE))));

		ArgumentCaptor<StartTestItemRQ> stepLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testId), stepLevelCaptor.capture());

		List<StartTestItemRQ> stepLevelRqs = stepLevelCaptor.getAllValues();
		IntStream.range(0, stepLevelRqs.size()).forEach(i -> {
			StartTestItemRQ rq = stepLevelRqs.get(i);
			assertThat(rq.isHasStats(), equalTo(Boolean.FALSE));
			assertThat(rq.getName(), equalTo(STEP_NAMES.get(i)));
		});

		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(stepIds.get(0)), firstLevelCaptor1.capture());

		StartTestItemRQ firstLevelRq1 = firstLevelCaptor1.getValue();
		assertThat(firstLevelRq1.getName(), equalTo(FIRST_LEVEL_NAMES.get(0)));
		assertThat(firstLevelRq1.isHasStats(), equalTo(Boolean.FALSE));

		ArgumentCaptor<StartTestItemRQ> firstLevelCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), firstLevelCaptor2.capture());

		List<StartTestItemRQ> firstLevelRqs2 = firstLevelCaptor2.getAllValues();
		IntStream.range(1, FIRST_LEVEL_NAMES.size()).forEach(i -> {
			assertThat(firstLevelRqs2.get(i - 1).getName(), equalTo(FIRST_LEVEL_NAMES.get(i)));
			assertThat(firstLevelRqs2.get(i - 1).isHasStats(), equalTo(Boolean.FALSE));
		});

		StartTestItemRQ stepWithAttributes = firstLevelRqs2.get(1);
		Set<ItemAttributesRQ> attributes = stepWithAttributes.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(2)));
		List<Pair<String, String>> kvAttributes = attributes.stream()
				.map(a -> Pair.of(a.getKey(), a.getValue()))
				.collect(Collectors.toList());
		List<Pair<String, String>> keyAndValueList = kvAttributes.stream().filter(kv -> kv.getKey() != null).collect(Collectors.toList());
		assertThat(keyAndValueList, hasSize(1));
		assertThat(keyAndValueList.get(0).getKey(), equalTo("key"));
		assertThat(keyAndValueList.get(0).getValue(), equalTo("value"));

		List<Pair<String, String>> tagList = kvAttributes.stream().filter(kv -> kv.getKey() == null).collect(Collectors.toList());
		assertThat(tagList, hasSize(1));
		assertThat(tagList.get(0).getValue(), equalTo("tag"));

		ArgumentCaptor<StartTestItemRQ> secondLevelCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(nestedStepIds.get(0)), secondLevelCaptor.capture());

		StartTestItemRQ secondLevelRq = secondLevelCaptor.getValue();
		assertThat(secondLevelRq.getName(), equalTo("A step inside nested step"));
		assertThat(secondLevelRq.isHasStats(), equalTo(Boolean.FALSE));
	}
}
