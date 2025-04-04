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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

public class TestCaseIdTest {

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunBellyTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/BasicScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ScenarioOutlineTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, stepIds);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	public void verify_test_case_id_simple_scenario() {
		TestUtils.runTests(RunBellyTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), captor.capture());

		StartTestItemRQ rq = captor.getValue();
		assertThat(rq.getTestCaseId(), equalTo("src/test/resources/features/belly.feature/[SCENARIO:a few cukes]"));
	}

	private static final String[] TEST_CASE_IDS = new String[] {
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:123;str:\"first\"]]",
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:12345;str:\"second\"]]",
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:12345678;str:\"third\"]]" };

	@Test
	public void verify_test_case_id_scenario_outline() {
		TestUtils.runTests(ScenarioOutlineTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(suiteId), captor.capture());

		List<StartTestItemRQ> requests = captor.getAllValues();
		IntStream.range(0, requests.size()).forEach(i -> assertThat(requests.get(i).getTestCaseId(), equalTo(TEST_CASE_IDS[i])));
	}
}
