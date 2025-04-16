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
import com.epam.reportportal.cucumber.integration.embed.image.EmbeddingStepdefs;
import com.epam.reportportal.cucumber.integration.util.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.Constants;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.cucumber.integration.util.TestUtils.filterLogs;
import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class EmbeddingTest {
	@CucumberOptions(features = "src/test/resources/features/embedding/ImageEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ImageTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/TextEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.text" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class TextTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/PdfEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.pdf" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class PdfTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/ArchiveEmbeddingFeature.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.zip" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ZipTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/EmbedImageWithoutName.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ImageNoNameTest extends AbstractTestNGCucumberTests {

	}

	@CucumberOptions(features = "src/test/resources/features/embedding/EmbedImageWithEmptyName.feature", glue = {
			"com.epam.reportportal.cucumber.integration.embed.image" }, plugin = {
			"com.epam.reportportal.cucumber.integration.TestScenarioReporter" })
	public static class ImageEmptyNameTest extends AbstractTestNGCucumberTests {

	}

	private final String launchId = CommonUtils.namedId("launch_");
	private final String suiteId = CommonUtils.namedId("feature_");
	private final List<String> testIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> steps = testIds.stream()
			.map(id -> Pair.of(id, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = steps.stream()
			.flatMap(s -> s.getValue().stream())
			.flatMap(s -> Stream.generate(() -> CommonUtils.namedId("nested_step_")).limit(2).map(ns -> Pair.of(s, ns)))
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

	private static List<SaveLogRQ> getLogsWithFiles(ArgumentCaptor<List<MultipartBody.Part>> logCaptor) {
		return filterLogs(logCaptor, l -> Objects.nonNull(l.getFile()));
	}

	private static List<MultipartBody.Part> getLogFiles(String name, ArgumentCaptor<List<MultipartBody.Part>> logCaptor) {
		return logCaptor.getAllValues()
				.stream()
				.flatMap(Collection::stream)
				.filter(p -> ofNullable(p.headers()).map(headers -> headers.get("Content-Disposition"))
						.map(h -> h.contains(Constants.LOG_REQUEST_BINARY_PART) && h.contains(name))
						.orElse(false))
				.collect(Collectors.toList());
	}

	@Test
	public void verify_image_embedding() {
		TestUtils.runTests(ImageTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);
		logs.forEach(l -> assertThat(l.getMessage(), equalTo(EmbeddingStepdefs.IMAGE_NAME)));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("image/jpeg", "image/png", "image/jpeg"));
	}

	@Test
	public void verify_text_embedding() {
		TestUtils.runTests(TextTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());

		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("text/plain", "image/png", "text/plain"));
	}

	@Test
	public void verify_pfd_embedding() {
		TestUtils.runTests(PdfTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		assertThat(logs, hasSize(3));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("application/pdf", "image/png", "application/pdf"));
	}

	@Test
	public void verify_archive_embedding() {
		TestUtils.runTests(ZipTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(6)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());
		assertThat(types, hasSize(3));
		assertThat(types, containsInAnyOrder("application/zip", "image/png", "application/zip"));
	}

	@Test
	public void verify_image_no_name_embedding() {
		TestUtils.runTests(ImageNoNameTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(2)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		logs.forEach(l -> assertThat(l.getMessage(), equalTo("image")));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());
		assertThat(types, hasSize(1));
		assertThat(types, containsInAnyOrder("image/jpeg"));
	}

	@Test
	public void verify_image_empty_name_embedding() {
		TestUtils.runTests(ImageEmptyNameTest.class);
		CommonUtils.shutdownExecutorService(executorService); // Ensure everything is finished

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, timeout(10000).times(2)).log(logCaptor.capture());
		List<SaveLogRQ> logs = getLogsWithFiles(logCaptor);

		logs.forEach(l -> assertThat(l.getMessage(), equalTo("image")));

		List<String> types = logs.stream()
				.flatMap(l -> getLogFiles(l.getFile().getName(), logCaptor).stream())
				.flatMap(f -> ofNullable(f.body().contentType()).map(MediaType::toString).stream())
				.collect(Collectors.toList());
		assertThat(types, hasSize(1));
		assertThat(types, containsInAnyOrder("image/jpeg"));
	}
}
