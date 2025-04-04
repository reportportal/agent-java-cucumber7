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
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

public class HooksTest {
	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.step.one" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class OneStepHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.step.two" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class TwoStepHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.step.fail" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class FailStepHooksReporterTest extends AbstractTestNGCucumberTests {

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

	@CucumberOptions(features = "src/test/resources/features/TwoDummyScenarios.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.scenario.two" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class TwoScenarioTwoHooksReporterTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/DummyScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.hooks.scenario.fail" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ScenarioTwoHooksOneBeforeFailedReporterTest extends AbstractTestNGCucumberTests {

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
	private final List<String> scenarioIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> steps = scenarioIds.stream()
			.map(s -> Pair.of(s, Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final List<String> stepIds = steps.stream().flatMap(s -> s.getValue().stream()).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = stepIds.stream()
			.flatMap(s -> Stream.generate(() -> CommonUtils.namedId("nested_step_")).limit(2).map(ns -> Pair.of(s, ns)))
			.flatMap(s -> Stream.concat(
					Stream.of(s),
					Stream.generate(() -> CommonUtils.namedId("nested_" + s.getValue())).limit(2).map(ns -> Pair.of(s.getValue(), ns))
			))
			.collect(Collectors.toList());
	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, steps);
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
		TestUtils.runTests(OneStepHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		// Capture step requests to verify
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());

		// Capture nested step requests to verify names
		ArgumentCaptor<StartTestItemRQ> nestedStepOneCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), nestedStepOneCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> nestedStepTwoCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), nestedStepTwoCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeHookStepOneCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(nestedSteps.get(0).getValue()), beforeHookStepOneCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterHookStepOneCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(nestedSteps.get(3).getValue()), afterHookStepOneCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeHookStepTwoCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(nestedSteps.get(6).getValue()), beforeHookStepTwoCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterHookStepTwoCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(nestedSteps.get(9).getValue()), afterHookStepTwoCaptor.capture());

		verify(client, atLeast(10)).log(any(List.class));

		// Verify step names from the feature file
		List<StartTestItemRQ> mainSteps = stepCaptor.getAllValues();
		assertThat(mainSteps, hasSize(2));
		assertThat(
				mainSteps.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Given I have empty step", "Then I have another empty step")
		);

		// Verify hook group names
		List<StartTestItemRQ> stepRequests = nestedStepOneCaptor.getAllValues();
		assertThat(
				stepRequests.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Before step", "After step")
		);
		stepRequests = nestedStepTwoCaptor.getAllValues();
		assertThat(
				stepRequests.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Before step", "After step")
		);

		// Verify hook step names
		assertThat(
				beforeHookStepOneCaptor.getValue().getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.step.one.EmptySteps.my_before_step_hook()")
		);
		assertThat(
				afterHookStepOneCaptor.getValue().getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.step.one.EmptySteps.my_after_step_hook()")
		);
		assertThat(
				beforeHookStepTwoCaptor.getValue().getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.step.one.EmptySteps.my_before_step_hook()")
		);
		assertThat(
				afterHookStepTwoCaptor.getValue().getName(),
				equalTo("com.epam.reportportal.cucumber.integration.hooks.step.one.EmptySteps.my_after_step_hook()")
		);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_two_before_after_step_reported_in_steps() {
		TestUtils.runTests(TwoStepHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());

		// Capture step requests to verify
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());

		// Capture nested step requests to verify names
		ArgumentCaptor<StartTestItemRQ> nestedStepOneCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), nestedStepOneCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> nestedStepTwoCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), nestedStepTwoCaptor.capture());

		// Capture hook steps for first step
		ArgumentCaptor<StartTestItemRQ> beforeStepOneHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(0).getValue()), beforeStepOneHooksCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterStepOneHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(3).getValue()), afterStepOneHooksCaptor.capture());

		// Capture hook steps for second step
		ArgumentCaptor<StartTestItemRQ> beforeStepTwoHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(6).getValue()), beforeStepTwoHooksCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterStepTwoHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(9).getValue()), afterStepTwoHooksCaptor.capture());

		verify(client, atLeast(18)).log(any(List.class));

		// Verify step names from the feature file
		List<StartTestItemRQ> mainSteps = stepCaptor.getAllValues();
		assertThat(mainSteps, hasSize(2));
		assertThat(
				mainSteps.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Given I have empty step", "Then I have another empty step")
		);

		// Verify hook group names
		List<StartTestItemRQ> stepRequests = nestedStepOneCaptor.getAllValues();
		assertThat(
				stepRequests.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Before step", "After step")
		);
		stepRequests = nestedStepTwoCaptor.getAllValues();
		assertThat(
				stepRequests.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Before step", "After step")
		);

		// Verify before hook step names for first step
		List<StartTestItemRQ> beforeStepOneHooks = beforeStepOneHooksCaptor.getAllValues();
		assertThat(beforeStepOneHooks, hasSize(2));
		assertThat(
				beforeStepOneHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_first_before_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_second_before_step_hook()"
				)
		);

		// Verify after hook step names for first step
		List<StartTestItemRQ> afterStepOneHooks = afterStepOneHooksCaptor.getAllValues();
		assertThat(afterStepOneHooks, hasSize(2));
		assertThat(
				afterStepOneHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_first_after_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_second_after_step_hook()"
				)
		);

		// Verify before hook step names for second step
		List<StartTestItemRQ> beforeStepTwoHooks = beforeStepTwoHooksCaptor.getAllValues();
		assertThat(beforeStepTwoHooks, hasSize(2));
		assertThat(
				beforeStepTwoHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_first_before_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_second_before_step_hook()"
				)
		);

		// Verify after hook step names for second step
		List<StartTestItemRQ> afterStepTwoHooks = afterStepTwoHooksCaptor.getAllValues();
		assertThat(afterStepTwoHooks, hasSize(2));
		assertThat(
				afterStepTwoHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_first_after_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.two.EmptySteps.my_second_after_step_hook()"
				)
		);

		// Verify all steps are marked as passed
		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(16)).finishTestItem(anyString(), finishCaptor.capture());
		List<FinishTestItemRQ> finishSteps = finishCaptor.getAllValues();
		finishSteps.subList(0, finishSteps.size() - 1).forEach(step -> assertThat(step.getStatus(), equalTo(ItemStatus.PASSED.name())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_two_before_after_one_before_failed_step_reported_in_steps() {
		TestUtils.runTests(FailStepHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());

		// Capture step requests to verify
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());

		// Capture step hook group requests for the first step
		ArgumentCaptor<StartTestItemRQ> nestedStepOneCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), nestedStepOneCaptor.capture());

		// Capture step hook group requests for the second step
		ArgumentCaptor<StartTestItemRQ> nestedStepTwoCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), nestedStepTwoCaptor.capture());

		// Capture hook steps for first step (before)
		ArgumentCaptor<StartTestItemRQ> beforeStepOneHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(0).getValue()), beforeStepOneHooksCaptor.capture());

		// Capture hook steps for first step (after)
		ArgumentCaptor<StartTestItemRQ> afterStepOneHooksCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(nestedSteps.get(3).getValue()), afterStepOneHooksCaptor.capture());

		// For the second step we only care about the hook group items
		verify(client, times(2)).startTestItem(same(nestedSteps.get(6).getValue()), any(StartTestItemRQ.class));
		verify(client, times(2)).startTestItem(same(nestedSteps.get(9).getValue()), any(StartTestItemRQ.class));

		verify(client, atLeast(12)).log(any(List.class));

		// Verify step names from the feature file
		List<StartTestItemRQ> mainSteps = stepCaptor.getAllValues();
		assertThat(mainSteps, hasSize(2));
		assertThat(
				mainSteps.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Given I have empty step", "Then I have another empty step")
		);

		// Verify hook group names
		List<StartTestItemRQ> stepOneHooks = nestedStepOneCaptor.getAllValues();
		assertThat(stepOneHooks, hasSize(2));
		assertThat(
				stepOneHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()),
				containsInAnyOrder("Before step", "After step")
		);

		// Verify hook step names for first step (before hooks)
		List<StartTestItemRQ> beforeStepOneHooks = beforeStepOneHooksCaptor.getAllValues();
		assertThat(beforeStepOneHooks, hasSize(2));
		assertThat(
				beforeStepOneHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.fail.EmptySteps.my_first_before_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.fail.EmptySteps.my_second_before_step_hook()"
				)
		);

		// Verify after hook step names for first step
		List<StartTestItemRQ> afterStepOneHooks = afterStepOneHooksCaptor.getAllValues();
		assertThat(afterStepOneHooks, hasSize(2));
		assertThat(
				afterStepOneHooks.stream().map(StartTestItemRQ::getName).collect(Collectors.toList()), containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.step.fail.EmptySteps.my_first_after_step_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.step.fail.EmptySteps.my_second_after_step_hook()"
				)
		);

		// Verify step statuses
		ArgumentCaptor<FinishTestItemRQ> beforeHookStepsGroupFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(nestedSteps.get(0).getValue()), beforeHookStepsGroupFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> beforeHookStepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(nestedSteps.get(1).getValue()), beforeHookStepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(nestedSteps.get(2).getValue()), beforeHookStepsFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> stepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(stepIds.get(0)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(1)), stepsFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> afterHookStepsGroupFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(nestedSteps.get(3).getValue()), afterHookStepsGroupFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> afterHookStepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(nestedSteps.get(4).getValue()), afterHookStepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(nestedSteps.get(5).getValue()), afterHookStepsFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> scenarioFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(scenarioIds.get(0)), scenarioFinishCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> featureFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(suiteId), featureFinishCaptor.capture());

		assertThat(beforeHookStepsGroupFinishCaptor.getValue().getStatus(), equalTo(ItemStatus.FAILED.name()));

		assertThat(
				beforeHookStepsFinishCaptor.getAllValues().stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()),
				containsInAnyOrder(ItemStatus.FAILED.name(), ItemStatus.PASSED.name())
		);

		assertThat(
				stepsFinishCaptor.getAllValues().stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()),
				contains(ItemStatus.SKIPPED.name(), ItemStatus.SKIPPED.name())
		);

		assertThat(afterHookStepsGroupFinishCaptor.getValue().getStatus(), equalTo(ItemStatus.PASSED.name()));

		assertThat(
				afterHookStepsFinishCaptor.getAllValues().stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()),
				contains(ItemStatus.PASSED.name(), ItemStatus.PASSED.name())
		);

		assertThat(scenarioFinishCaptor.getValue().getStatus(), equalTo(ItemStatus.FAILED.name()));
		assertThat(featureFinishCaptor.getValue().getStatus(), nullValue());

		// Skip testing of the second step, count as the same as the first one
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_single_before_after_scenario_reported_in_steps() {
		TestUtils.runTests(ScenarioSingleHookReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(0)), beforeScenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(stepIds.get(3)), afterScenarioCaptor.capture());
		verify(client, atLeast(6)).log(any(List.class));

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
		verify(client, times(4)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> beforeScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), beforeScenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> afterScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(3)), afterScenarioCaptor.capture());
		verify(client, atLeast(10)).log(any(List.class));

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
		finishSteps.subList(0, finishSteps.size() - 1).forEach(step -> assertThat(step.getStatus(), equalTo(ItemStatus.PASSED.name())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_two_before_after_two_scenarios_reported_in_steps() {
		TestUtils.runTests(TwoScenarioTwoHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any(StartTestItemRQ.class));
		verify(client, times(2)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioIds.get(0)), firstStepCaptor.capture());
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), any(StartTestItemRQ.class));
		verify(client, times(2)).startTestItem(same(stepIds.get(3)), any(StartTestItemRQ.class));
		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioIds.get(1)), secondStepCaptor.capture());
		verify(client, times(2)).startTestItem(same(stepIds.get(0)), any(StartTestItemRQ.class));
		verify(client, times(2)).startTestItem(same(stepIds.get(3)), any(StartTestItemRQ.class));
		verify(client, atLeast(20)).log(any(List.class));

		List<StartTestItemRQ> steps = firstStepCaptor.getAllValues();
		assertThat(steps.get(0).getName(), equalTo("Before hooks"));
		assertThat(steps.get(steps.size() - 1).getName(), equalTo("After hooks"));

		steps = secondStepCaptor.getAllValues();
		assertThat(steps.get(0).getName(), equalTo("Before hooks"));
		assertThat(steps.get(steps.size() - 1).getName(), equalTo("After hooks"));

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(19)).finishTestItem(anyString(), finishCaptor.capture());
		List<FinishTestItemRQ> finishSteps = finishCaptor.getAllValues();
		finishSteps.subList(0, finishSteps.size() - 2).forEach(step -> assertThat(step.getStatus(), equalTo(ItemStatus.PASSED.name())));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_two_before_after_one_before_failed_scenarios_reported_in_steps() {
		TestUtils.runTests(ScenarioTwoHooksOneBeforeFailedReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> hooksScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(4)).startTestItem(ArgumentMatchers.startsWith("step_"), hooksScenarioCaptor.capture());
		verify(client, atLeast(8)).log(any(List.class));

		List<String> steps = stepCaptor.getAllValues().stream().map(StartTestItemRQ::getName).collect(Collectors.toList());
		assertThat(steps, containsInAnyOrder("Before hooks", "After hooks", "Given I have empty step", "Then I have another empty step"));

		List<String> beforeSteps = hooksScenarioCaptor.getAllValues()
				.stream()
				.map(StartTestItemRQ::getName)
				.filter(n -> n.contains("before"))
				.collect(Collectors.toList());
		assertThat(beforeSteps, hasSize(2));
		assertThat(
				beforeSteps, containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.scenario.fail.EmptySteps.my_first_before_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.scenario.fail.EmptySteps.my_second_before_hook()"
				)
		);

		List<String> afterSteps = hooksScenarioCaptor.getAllValues()
				.stream()
				.map(StartTestItemRQ::getName)
				.filter(n -> n.contains("after"))
				.collect(Collectors.toList());
		assertThat(afterSteps, hasSize(2));
		assertThat(
				afterSteps, containsInAnyOrder(
						"com.epam.reportportal.cucumber.integration.hooks.scenario.fail.EmptySteps.my_first_after_hook()",
						"com.epam.reportportal.cucumber.integration.hooks.scenario.fail.EmptySteps.my_second_after_hook()"
				)
		);

		ArgumentCaptor<FinishTestItemRQ> hookStepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(4)).finishTestItem(ArgumentMatchers.startsWith("nested_step_"), hookStepsFinishCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> stepsFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(stepIds.get(0)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(1)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(2)), stepsFinishCaptor.capture());
		verify(client).finishTestItem(eq(stepIds.get(3)), stepsFinishCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> otherFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(scenarioIds.get(0)), otherFinishCaptor.capture());
		verify(client).finishTestItem(eq(suiteId), otherFinishCaptor.capture());

		List<FinishTestItemRQ> hookFinishes = hookStepsFinishCaptor.getAllValues();
		assertThat(hookFinishes, hasSize(4));
		assertThat(
				hookFinishes.stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()),
				containsInAnyOrder(ItemStatus.FAILED.name(), ItemStatus.PASSED.name(), ItemStatus.PASSED.name(), ItemStatus.PASSED.name())
		);

		List<FinishTestItemRQ> finishSteps = stepsFinishCaptor.getAllValues();
		assertThat(
				finishSteps.stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList()),
				containsInAnyOrder(ItemStatus.FAILED.name(), ItemStatus.SKIPPED.name(), ItemStatus.SKIPPED.name(), ItemStatus.PASSED.name())
		);

		List<FinishTestItemRQ> suiteFinish = otherFinishCaptor.getAllValues();
		assertThat(suiteFinish.get(0).getStatus(), equalTo(ItemStatus.FAILED.name()));
		assertThat(suiteFinish.get(1).getStatus(), nullValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_all_reported_in_steps() {
		TestUtils.runTests(AllHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());

		// @BeforeAll and @AfterAll hooks does not emit any events, see: https://github.com/cucumber/cucumber-jvm/issues/2422
		verify(client, times(2)).startTestItem(same(scenarioIds.get(0)), any());
		verify(client, atLeast(3)).log(any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_before_after_not_reported_in_steps() {
		TestUtils.runTests(NoHooksReporterTest.class);

		verify(client, times(1)).startTestItem(any());
		verify(client, times(1)).startTestItem(same(suiteId), any());
		verify(client, times(2)).startTestItem(same(scenarioIds.get(0)), any());
		verify(client, times(2)).log(any(List.class));
	}
}
