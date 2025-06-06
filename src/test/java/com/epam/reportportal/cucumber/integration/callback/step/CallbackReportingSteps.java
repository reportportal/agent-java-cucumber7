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

package com.epam.reportportal.cucumber.integration.callback.step;

import com.epam.reportportal.cucumber.ScenarioReporter;
import com.epam.reportportal.cucumber.util.ItemTreeUtils;
import com.epam.reportportal.service.tree.ItemTreeReporter;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;

import java.util.Calendar;

@SuppressWarnings("unused")
public class CallbackReportingSteps {

	public static final String STEP_TEXT = "I have a step for callback reporting";

	@Given(STEP_TEXT)
	public void a_step_for_callback_reporting() throws InterruptedException {
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
	}

	@AfterStep
	public void after(Scenario scenario) {
		ItemTreeUtils.retrieveLeaf(scenario.getUri(), scenario.getLine(), STEP_TEXT, ScenarioReporter.getCurrent().getItemTree())
				.ifPresent(itemLeaf -> {
					if (scenario.getName().contains("failure")) {
						itemLeaf.getItemId().blockingGet(); // trigger item creation to avoid async issues
						sendFinishRequest(itemLeaf, "FAILED", "secondTest");
						attachLog(itemLeaf);
					} else {
						sendFinishRequest(itemLeaf, "PASSED", "firstTest");
					}
				});
	}

	private void sendFinishRequest(TestItemTree.TestItemLeaf testResultLeaf, String status, String description) {
		FinishTestItemRQ finishTestItemRQ = new FinishTestItemRQ();
		finishTestItemRQ.setDescription(description);
		finishTestItemRQ.setStatus(status);
		finishTestItemRQ.setEndTime(Calendar.getInstance().getTime());
		ItemTreeReporter.finishItem(
				ScenarioReporter.getCurrent().getReportPortal().getClient(),
				finishTestItemRQ,
				ScenarioReporter.getCurrent().getItemTree().getLaunchId(),
				testResultLeaf
		).cache().blockingGet();
	}

	private void attachLog(TestItemTree.TestItemLeaf testItemLeaf) {
		ItemTreeReporter.sendLog(
				ScenarioReporter.getCurrent().getReportPortal().getClient(),
				"ERROR",
				"Error message",
				Calendar.getInstance().getTime(),
				ScenarioReporter.getCurrent().getItemTree().getLaunchId(),
				testItemLeaf
		);
	}
}
