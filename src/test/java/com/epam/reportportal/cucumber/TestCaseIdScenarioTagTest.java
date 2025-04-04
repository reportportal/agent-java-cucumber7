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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestCaseIdScenarioTagTest {

	@CucumberOptions(features = "src/test/resources/features/TestCaseIdOnScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunTestCaseIdScenarioTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(1).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> pairs = Stream.of(Pair.of(testId, stepIds)).collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, pairs);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	public void verify_test_case_id_from_scenario_tag() {
		TestUtils.runTests(RunTestCaseIdScenarioTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());

		StartTestItemRQ rq = captor.getValue();
		// Verify that Test Case ID equals "JIRA-1234"
		assertThat(rq.getTestCaseId(), equalTo("JIRA-1234"));

		// Verify that there is no attribute with name "tc_id" in the request
		Set<String> attributeKeys = rq.getAttributes().stream()
				.map(ItemAttributesRQ::getKey)
				.collect(Collectors.toSet());
		assertThat(attributeKeys, not(hasItem("tc_id")));
	}
}
