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
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.*;

public class TagFilterSystemPropertyTest {
	private static final String TAGS_PROPERTY = "cucumber.filter.tags";

	@CucumberOptions(features = "src/test/resources/features/tag_filter", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class TagFilterRunnerTest extends AbstractTestNGCucumberTests {
	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final String testId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());

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

	@AfterEach
	public void tearDown() {
		System.clearProperty(TAGS_PROPERTY);
		CommonUtils.shutdownExecutorService(executorService);
	}

	private void verifyOnlyNoTagsScenarioReported() {
		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		StartTestItemRQ featureRq = featureCaptor.getValue();
		assertThat(
				featureRq.getDescription(), allOf(
						notNullValue(),
						containsString("file:///"),
						endsWith("/agent-java-cucumber7/src/test/resources/features/tag_filter/TaggedDummyScenario.feature")
				)
		);

		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), scenarioCaptor.capture());
		StartTestItemRQ scenarioRq = scenarioCaptor.getValue();
		assertThat(scenarioRq.getType(), equalTo("STEP"));
		assertThat(scenarioRq.getName(), allOf(notNullValue(), containsString("Scenario:")));

		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testId), stepCaptor.capture());
		stepCaptor.getAllValues().forEach(rq -> assertThat(rq.getType(), equalTo("STEP")));
	}

	@Test
	public void runs_only_no_tag_scenario_when_filter_is_smoke() {
		System.setProperty(TAGS_PROPERTY, "@smoke");
		TestUtils.runTests(TagFilterRunnerTest.class);
		verifyOnlyNoTagsScenarioReported();
	}

	@Test
	public void runs_only_no_tag_scenario_when_filter_is_scenario_tag() {
		System.setProperty(TAGS_PROPERTY, "@scenario_tag");
		TestUtils.runTests(TagFilterRunnerTest.class);
		verifyOnlyNoTagsScenarioReported();
	}
}


