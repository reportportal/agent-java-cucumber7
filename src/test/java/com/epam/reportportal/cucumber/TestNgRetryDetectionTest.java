/*
 * Copyright 2026 EPAM Systems
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
import com.epam.reportportal.cucumber.integration.feature.RetrySteps;
import com.epam.reportportal.cucumber.integration.testng.RetryAnalyzer;
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
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestNgRetryDetectionTest {
	public static class AnnotationTransformer implements IAnnotationTransformer {
		@Override
		public void transform(ITestAnnotation annotation, Class testClass, Constructor constructor, Method method) {
			annotation.setRetryAnalyzer(RetryAnalyzer.class);
		}
	}

	@CucumberOptions(features = "src/test/resources/features/TestNgRetry.feature", glue = {
			"com.epam.reportportal.cucumber.integration.feature" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class RetryCucumberTest extends AbstractTestNGCucumberTests {
	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(2).collect(Collectors.toList());
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> steps = IntStream.range(0, testIds.size())
			.mapToObj(i -> Pair.of(testIds.get(i), stepIds.subList(i * 2, i * 2 + 2)))
			.collect(Collectors.toList());

	private final ListenerParameters params = TestUtils.standardParameters();
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params, executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, steps);
		TestUtils.mockLogging(client);
		TestScenarioReporter.RP.set(reportPortal);
		RetrySteps.reset();
	}

	@AfterEach
	public void tearDown() {
		CommonUtils.shutdownExecutorService(executorService);
	}

	@Test
	public void verify_retry_flags_and_links() {
		TestUtils.runTestsWithListener(AnnotationTransformer.class, RetryCucumberTest.class);

		ArgumentCaptor<StartTestItemRQ> startScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(suiteId), startScenarioCaptor.capture());

		List<StartTestItemRQ> scenarioStarts = startScenarioCaptor.getAllValues();
		assertThat(scenarioStarts, hasSize(2));

		StartTestItemRQ firstAttempt = scenarioStarts.get(0);
		assertThat(firstAttempt.isRetry(), nullValue());
		assertThat(firstAttempt.getRetryOf(), nullValue());

		StartTestItemRQ secondAttempt = scenarioStarts.get(1);
		assertThat(secondAttempt.isRetry(), equalTo(true));
		assertThat(secondAttempt.getRetryOf(), equalTo(testIds.get(0)));
	}
}
