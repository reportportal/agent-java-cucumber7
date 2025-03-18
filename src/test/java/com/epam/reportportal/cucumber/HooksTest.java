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
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

public class HooksTest {
	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.step" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class StepHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.scenario.one" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ScenarioSingleHookReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.scenario.two" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ScenarioTwoHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.all" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class AllHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class NoHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = stepIds.stream()
			.flatMap(s -> Stream.generate(() -> CommonUtils.namedId("nested_step_")).limit(2).map(ns -> Pair.of(s, ns)))
			.collect(Collectors.toList());
	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, scenarioId, stepIds);
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_step_reported_in_steps() {
		TestUtils.runTests(StepHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(scenarioId), any());
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), any());
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), any());
		verify(client, times(10)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_single_before_after_scenario_reported_in_steps() {
		TestUtils.runTests(ScenarioSingleHookReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioId), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(0)), beforeScenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(3)), afterScenarioCaptor.capture());
		verify(client, times(6)).log(any(List.class));

		List<StartTestItemRQ> steps = stepCaptor.getAllValues();
		assertThat(steps.get(0).getName(), equalTo("Before hooks"));
		assertThat(steps.get(steps.size() - 1).getName(), equalTo("After hooks"));

		StartTestItemRQ beforeStep = beforeScenarioCaptor.getValue();
		assertThat(
				beforeStep.getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.one.EmptySteps.my_before_hook()")
		);

		StartTestItemRQ afterStep = afterScenarioCaptor.getValue();
		assertThat(
				afterStep.getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.one.EmptySteps.my_after_hook()")
		);

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(8)).finishTestItem(anyString(), finishCaptor.capture());
		List<FinishTestItemRQ> finishSteps = finishCaptor.getAllValues();
		finishSteps.subList(0, finishSteps.size() - 2).forEach(step -> assertThat(step.getStatus(), equalTo(ItemStatus.PASSED.name())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_two_before_after_scenario_reported_in_steps() {
		TestUtils.runTests(ScenarioTwoHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioId), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), beforeScenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(3)), afterScenarioCaptor.capture());
		verify(client, times(10)).log(any(List.class));

		List<StartTestItemRQ> steps = stepCaptor.getAllValues();
		assertThat(steps.get(0).getName(), equalTo("Before hooks"));
		assertThat(steps.get(steps.size() - 1).getName(), equalTo("After hooks"));

		List<StartTestItemRQ> beforeSteps = beforeScenarioCaptor.getAllValues();
		beforeSteps.forEach(beforeStep -> assertThat(
				beforeStep.getName(),
				anyOf(
						equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.two.EmptySteps.my_first_before_hook()"),
						equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.two.EmptySteps.my_second_before_hook()")
				)
		));

		List<StartTestItemRQ> afterSteps = afterScenarioCaptor.getAllValues();
		afterSteps.forEach(afterStep -> assertThat(
				afterStep.getName(),
				anyOf(
						equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.two.EmptySteps.my_first_after_hook()"),
						equalTo("com.epam.reportportal.cucumber.integration.hooks.scenario.two.EmptySteps.my_second_after_hook()")
				)
		));

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(10)).finishTestItem(anyString(), finishCaptor.capture());
		List<FinishTestItemRQ> finishSteps = finishCaptor.getAllValues();
		finishSteps.subList(0, finishSteps.size() - 2).forEach(step -> assertThat(step.getStatus(), equalTo(ItemStatus.PASSED.name())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_all_reported_in_steps() {
		TestUtils.runTests(AllHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());

		// @BeforeAll and @AfterAll hooks does not emit any events, see: https://github.com/cucumber/cucumber-jvm/issues/2422
		verify(client, times(2)).startTestItem(same(scenarioId), any());
		verify(client, times(3)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_not_reported_in_steps() {
		TestUtils.runTests(NoHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(scenarioId), any());
		verify(client, times(2)).log(any(List.class));
	}
}


