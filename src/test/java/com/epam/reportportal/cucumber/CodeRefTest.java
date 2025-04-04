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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

public class CodeRefTest {

	@CucumberOptions(features = "src/test/resources/features/belly.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class BellyTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/TwoScenarioInOne.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class TwoFeaturesTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/BasicScenarioOutlineParameters.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ScenarioOutlineTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> tests = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ListenerParameters parameters = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, parameters, executorService);

	@BeforeEach
	public void initLaunch() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
	}

	private static final String SCENARIO_CODE_REFERENCES = "src/test/resources/features/belly.feature/[SCENARIO:a few cukes]";

	@Test
	public void verify_code_reference() {
		TestUtils.runTests(BellyTest.class);

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(suiteId), scenarioCaptor.capture());
		verify(client, times(3)).startTestItem(same(testIds.get(0)), any());

		StartTestItemRQ feature = featureCaptor.getValue();
		StartTestItemRQ scenario = scenarioCaptor.getValue();

		assertThat(feature.getCodeRef(), nullValue());
		assertThat(scenario.getCodeRef(), allOf(notNullValue(), equalTo(SCENARIO_CODE_REFERENCES)));
	}

	private static final List<String> TWO_SCENARIOS_FEATURE_CODE_REFERENCES = Arrays.asList(
			"src/test/resources/features/TwoScenarioInOne.feature/[SCENARIO:The first scenario]",
			"src/test/resources/features/TwoScenarioInOne.feature/[SCENARIO:The second scenario]"
	);

	@Test
	public void verify_code_reference_two_features() {
		TestUtils.runTests(TwoFeaturesTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(suiteId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testIds.get(0)), stepCaptor1.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(testIds.get(1)), stepCaptor2.capture());

		List<StartTestItemRQ> scenarios = scenarioCaptor.getAllValues();

		IntStream.range(0, TWO_SCENARIOS_FEATURE_CODE_REFERENCES.size())
				.forEach(i -> assertThat(
						scenarios.get(i).getCodeRef(),
						allOf(notNullValue(), equalTo(TWO_SCENARIOS_FEATURE_CODE_REFERENCES.get(i)))
				));

		List<StartTestItemRQ> steps1 = stepCaptor1.getAllValues();
		IntStream.range(0, steps1.size()).forEach(i -> assertThat(steps1.get(i).getCodeRef(), nullValue()));
		List<StartTestItemRQ> steps2 = stepCaptor2.getAllValues();
		IntStream.range(0, steps2.size()).forEach(i -> assertThat(steps2.get(i).getCodeRef(), nullValue()));
	}

	private static final List<String> SCENARIO_OUTLINE_CODE_REFERENCES = Arrays.asList(
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:123;str:\"first\"]]",
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:12345;str:\"second\"]]",
			"src/test/resources/features/BasicScenarioOutlineParameters.feature/[EXAMPLE:Test with different parameters[parameters:12345678;str:\"third\"]]"
	);

	@Test
	public void verify_code_reference_scenario_outline() {
		TestUtils.runTests(ScenarioOutlineTest.class);

		verify(client, times(1)).startTestItem(any());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(suiteId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor1 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(0)), stepCaptor1.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor2 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(1)), stepCaptor2.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor3 = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(testIds.get(1)), stepCaptor3.capture());

		List<StartTestItemRQ> scenarios = scenarioCaptor.getAllValues();

		IntStream.range(0, SCENARIO_OUTLINE_CODE_REFERENCES.size())
				.forEach(i -> assertThat(
						scenarios.get(i).getCodeRef(),
						allOf(notNullValue(), equalTo(SCENARIO_OUTLINE_CODE_REFERENCES.get(i)))
				));

		List<StartTestItemRQ> steps1 = stepCaptor1.getAllValues();
		IntStream.range(0, steps1.size()).forEach(i -> assertThat(steps1.get(i).getCodeRef(), nullValue()));
		List<StartTestItemRQ> steps2 = stepCaptor2.getAllValues();
		IntStream.range(0, steps2.size()).forEach(i -> assertThat(steps2.get(i).getCodeRef(), nullValue()));
		List<StartTestItemRQ> steps3 = stepCaptor3.getAllValues();
		IntStream.range(0, steps3.size()).forEach(i -> assertThat(steps3.get(i).getCodeRef(), nullValue()));
	}
}
