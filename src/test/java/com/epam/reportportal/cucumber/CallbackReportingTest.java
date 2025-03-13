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

import com.epam.reportportal.cucumber.integration.callback.TestScenarioReporter;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.EntryCreatedAsyncRS;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;

public class CallbackReportingTest {

	@CucumberOptions(features = "src/test/resources/features/CallbackReportingScenario.feature", glue = {
			"com.epam.reportportal.cucumber.integration.callback.scenario" }, plugin = {
			"com.epam.reportportal.cucumber.integration.callback.TestScenarioReporter" })
	public static class TestScenarioReporterRunner extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(2).collect(Collectors.toList());
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(4).collect(Collectors.toList());

	private final List<Pair<String, List<String>>> tests = Arrays.asList(
			Pair.of(testIds.get(0), stepIds.subList(0, 2)),
			Pair.of(testIds.get(1), stepIds.subList(2, 4))
	);

	private final Supplier<ListenerParameters> params = () -> {
		ListenerParameters p = TestUtils.standardParameters();
		p.setCallbackReportingEnabled(true);
		return p;
	};
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final ReportPortal reportPortal = ReportPortal.create(client, params.get(), executorService);

	@BeforeEach
	public void setup() {
		TestUtils.mockLaunch(client, launchId, suiteId, tests);
		TestUtils.mockLogging(client);
		TestScenarioReporter.addReportPortal(reportPortal);
		TestScenarioReporter.RP.set(reportPortal);
		when(client.log(any(SaveLogRQ.class))).thenReturn(Maybe.just(new EntryCreatedAsyncRS()));
	}

	@Test
	public void callback_reporting_test_scenario_reporter() {
		TestUtils.runTests(TestScenarioReporterRunner.class);

		ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<FinishTestItemRQ> rqCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client, times(9)).finishTestItem(idCaptor.capture(), rqCaptor.capture()); // Start test class and test method

		ArgumentCaptor<SaveLogRQ> saveLogRQArgumentCaptor = ArgumentCaptor.forClass(SaveLogRQ.class);
		verify(client, times(1)).log(saveLogRQArgumentCaptor.capture());

		List<String> finishIds = idCaptor.getAllValues();
		List<FinishTestItemRQ> finishRqs = rqCaptor.getAllValues();

		List<Pair<String, FinishTestItemRQ>> idRqs = IntStream.range(0, finishIds.size())
				.mapToObj(i -> Pair.of(finishIds.get(i), finishRqs.get(i)))
				.collect(Collectors.toList());

		List<Pair<String, FinishTestItemRQ>> firstScenarioIds = idRqs.stream()
				.filter(e -> stepIds.subList(0, 2).contains(e.getKey()))
				.collect(Collectors.toList());

		List<Pair<String, FinishTestItemRQ>> secondScenarioIds = idRqs.stream()
				.filter(e -> stepIds.subList(2, 4).contains(e.getKey()))
				.collect(Collectors.toList());

		assertThat(firstScenarioIds, hasSize(3));
		assertThat(secondScenarioIds, hasSize(3));

		List<Pair<String, FinishTestItemRQ>> failureUpdates = firstScenarioIds.stream()
				.filter(r -> "FAILED".equals(r.getValue().getStatus()))
				.collect(Collectors.toList());
		assertThat(failureUpdates, hasSize(1));

		SaveLogRQ logRq = saveLogRQArgumentCaptor.getValue();
		assertThat(logRq.getItemUuid(), equalTo(failureUpdates.get(0).getKey()));

		secondScenarioIds.forEach(e -> assertThat(e.getValue().getStatus(), equalTo("PASSED")));
	}
}
