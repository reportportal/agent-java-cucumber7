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
import com.epam.reportportal.cucumber.integration.feature.FailedSteps;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MultipartBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

public class FailedTest {

	private static final String EXPECTED_ERROR = "java.lang.IllegalStateException: " + FailedSteps.ERROR_MESSAGE;
	private static final String EXPECTED_STACK_TRACE = EXPECTED_ERROR
			+ "\n\tat com.epam.reportportal.cucumber.integration.feature.FailedSteps.i_have_a_failed_step(FailedSteps.java:32)"
			+ "\n\tat ✽.I have a failed step(file://" + System.getProperty("user.dir")
			+ "/src/test/resources/features/FailedScenario.feature:4)\n";
	private static final String ERROR_LOG_TEXT = "Error:\n" + EXPECTED_STACK_TRACE;

	@CucumberOptions(features = "src/test/resources/features/FailedScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = { "pretty",
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class FailedScenarioReporterTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final String stepId = CommonUtils.namedId("step_");

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, testId, stepId);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void verify_failed_step_reporting_scenario_reporter() {
		TestUtils.runTests(FailedScenarioReporterTest.class);

		verify(client).startTestItem(any());
		verify(client).startTestItem(same(suiteId), any());
		verify(client).startTestItem(same(testId), any());
		ArgumentCaptor<FinishTestItemRQ> stepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepId), stepCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(testId), scenarioCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> featureCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(suiteId), featureCaptor.capture());

		FinishTestItemRQ finishStepRequest = stepCaptor.getValue();
		assertThat(finishStepRequest.getStatus(), equalTo(ItemStatus.FAILED.name()));

		FinishTestItemRQ finishScenarioRequest = scenarioCaptor.getValue();
		assertThat(finishScenarioRequest.getStatus(), equalTo(ItemStatus.FAILED.name()));

		FinishTestItemRQ finishFeatureRequest = featureCaptor.getValue();
		assertThat(finishFeatureRequest.getStatus(), nullValue());

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());

		List<SaveLogRQ> expectedErrorList = filterLogs(logCaptor, l -> l.getMessage() != null && l.getMessage().startsWith(EXPECTED_ERROR));
		assertThat(expectedErrorList, hasSize(1));
		SaveLogRQ expectedError = expectedErrorList.get(0);
		assertThat(expectedError.getItemUuid(), equalTo(stepId));
	}

	@Test
	public void verify_failed_nested_step_description_scenario_reporter() {
		TestUtils.runTests(FailedScenarioReporterTest.class);

		verify(client).startTestItem(any());
		verify(client).startTestItem(same(suiteId), any());
		verify(client).startTestItem(same(testId), any());
		ArgumentCaptor<FinishTestItemRQ> stepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepId), stepCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(testId), scenarioCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> featureCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(suiteId), featureCaptor.capture());

		FinishTestItemRQ finishStepRequest = stepCaptor.getValue();
		assertThat(finishStepRequest.getDescription(), nullValue());
		assertThat(finishStepRequest.getStatus(), equalTo(ItemStatus.FAILED.name()));

		FinishTestItemRQ finishScenarioRequest = scenarioCaptor.getValue();
		assertThat(finishScenarioRequest.getDescription(), equalTo(ERROR_LOG_TEXT));
		assertThat(finishScenarioRequest.getStatus(), equalTo(ItemStatus.FAILED.name()));

		FinishTestItemRQ finishFeatureRequest = featureCaptor.getValue();
		assertThat(finishFeatureRequest.getDescription(), nullValue());
		assertThat(finishFeatureRequest.getStatus(), nullValue());
	}
}
