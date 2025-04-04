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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestCaseIdExamplesTest {

	@CucumberOptions(features = "src/test/resources/features/TestCaseIdOnExamples.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunTestCaseIdExamplesTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TestCaseIdOnScenarioOutline.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunTestCaseIdScenarioOutlineTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TestCaseIdOnScenarioOutlineAndExamples.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RunTestCaseIdScenarioOutlineExamplesTest extends AbstractTestNGCucumberTests {

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

	private static final String[] EXPECTED_TEST_CASE_IDS = new String[] { "JIRA-4321[parameters:123;str:\"first\"]",
			"JIRA-4321[parameters:12345;str:\"second\"]", "JIRA-4321[parameters:12345678;str:\"third\"]" };

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

	@ParameterizedTest
	@ValueSource(classes = { RunTestCaseIdExamplesTest.class, RunTestCaseIdScenarioOutlineTest.class,
			RunTestCaseIdScenarioOutlineExamplesTest.class })
	public void verify_test_case_id_from_examples_tag(Class<?> clazz) {
		TestUtils.runTests(clazz);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(suiteId), captor.capture());

		List<StartTestItemRQ> requests = captor.getAllValues();

		// Verify that Test Case IDs start with "JIRA-4321" and contain parameters
		IntStream.range(0, requests.size()).forEach(i -> {
			assertThat(requests.get(i).getTestCaseId(), equalTo(EXPECTED_TEST_CASE_IDS[i]));

			// Verify that there is no attribute with name "tc_id" in the request
			Set<String> attributeKeys = requests.get(i).getAttributes().stream().map(ItemAttributesRQ::getKey).collect(Collectors.toSet());
			assertThat(attributeKeys, not(hasItem("tc_id")));
		});
	}
}
