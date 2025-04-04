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
import com.epam.reportportal.cucumber.integration.feature.ManualStepReporterSteps;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

public class ManualStepReporterTest {
	@CucumberOptions(features = "src/test/resources/features/ManualStepReporter.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class SimpleTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());

	private final List<Pair<String, String>> nestedSteps = Stream.of(
			Pair.of(stepIds.get(0), CommonUtils.namedId("nested_step_")),
			Pair.of(stepIds.get(1), CommonUtils.namedId("nested_step_")),
			Pair.of(stepIds.get(1), CommonUtils.namedId("nested_step_"))
	).collect(Collectors.toList());

	private final Set<String> nestedStepIds = nestedSteps.stream().map(Pair::getValue).collect(Collectors.toSet());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepIds);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	private static void verifyStepStart(StartTestItemRQ step, String stepName) {
		assertThat(step.getName(), equalTo(stepName));
		assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
		assertThat(step.getType(), equalTo("STEP"));
	}

	private static void verifyLogEntry(SaveLogRQ firstStepLog, String stepId, String duringSecondNestedStepLog) {
		assertThat(firstStepLog.getItemUuid(), equalTo(stepId));
		assertThat(firstStepLog.getMessage(), containsString(duringSecondNestedStepLog));
		assertThat(firstStepLog.getFile(), nullValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_scenario_reporter_steps_integrity() {
		TestUtils.mockNestedSteps(client, nestedSteps);
		TestUtils.runTests(SimpleTest.class);

		verify(client, times(2)).startTestItem(same(testId), any());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(stepIds.get(0)), firstStepCaptor.capture());
		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, times(5)).log(logCaptor.capture());
		StartTestItemRQ firstStep = firstStepCaptor.getValue();
		List<SaveLogRQ> logs = filterLogs(logCaptor, l -> true);

		SaveLogRQ firstStepLog = logs.get(0);
		verifyStepStart(firstStep, ManualStepReporterSteps.FIRST_NAME);
		verifyLogEntry(firstStepLog, nestedSteps.get(0).getValue(), ManualStepReporterSteps.FIRST_NESTED_STEP_LOG);

		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(stepIds.get(1)), secondStepCaptor.capture());
		List<StartTestItemRQ> secondSteps = secondStepCaptor.getAllValues();
		List<SaveLogRQ> secondStepLogs = logs.subList(1, logs.size());

		StartTestItemRQ secondStep = secondSteps.get(0);
		verifyStepStart(secondStep, ManualStepReporterSteps.SECOND_NAME);
		verifyLogEntry(secondStepLogs.get(0), nestedSteps.get(1).getValue(), ManualStepReporterSteps.DURING_SECOND_NESTED_STEP_LOG);
		verifyLogEntry(secondStepLogs.get(1), nestedSteps.get(1).getValue(), ManualStepReporterSteps.SECOND_NESTED_STEP_LOG);

		StartTestItemRQ thirdStep = secondSteps.get(1);
		verifyStepStart(thirdStep, ManualStepReporterSteps.THIRD_NAME);

		SaveLogRQ pugLog = secondStepLogs.get(2);
		assertThat(pugLog.getItemUuid(), equalTo(nestedSteps.get(2).getValue()));
		assertThat(pugLog.getMessage(), emptyString());
		assertThat(pugLog.getFile(), notNullValue());

		verifyLogEntry(secondStepLogs.get(3), nestedSteps.get(2).getValue(), ManualStepReporterSteps.THIRD_NESTED_STEP_LOG);

		ArgumentCaptor<String> finishIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> finishRqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(7)).finishTestItem(finishIdCaptor.capture(), finishRqCaptor.capture());
		List<String> finishIds = finishIdCaptor.getAllValues();
		List<FinishTestItemRQ> finishRqs = finishRqCaptor.getAllValues();
		List<FinishTestItemRQ> nestedStepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> nestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(nestedStepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(1).getStatus(), equalTo("PASSED"));
		assertThat(nestedStepFinishes.get(2).getStatus(), equalTo("FAILED"));

		List<FinishTestItemRQ> stepFinishes = IntStream.range(0, finishIds.size())
				.filter(i -> !nestedStepIds.contains(finishIds.get(i)))
				.mapToObj(finishRqs::get)
				.collect(Collectors.toList());

		assertThat(stepFinishes.get(0).getStatus(), equalTo("PASSED"));
		assertThat(stepFinishes.get(1).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(2).getStatus(), equalTo("FAILED"));
		assertThat(stepFinishes.get(3).getStatus(), equalTo("FAILED"));
	}
}
