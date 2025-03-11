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

package com.epam.reportportal.cucumber.integration.feature;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.step.StepReporter;
import com.epam.reportportal.util.test.CommonUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ManualStepReporterSteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(ManualStepReporterSteps.class);
	public static final String FIRST_NAME = "I am the first nested step";
	public static final String SECOND_NAME = "I am the second nested step";
	public static final String THIRD_NAME = "I am the third nested step";
	public static final String FIRST_NESTED_STEP_LOG = "Inside first nested step";
	public static final String SECOND_NESTED_STEP_LOG = "Inside second nested step";
	public static final String THIRD_NESTED_STEP_LOG = "Third error log of the second step";
	public static final String DURING_SECOND_NESTED_STEP_LOG = "A log entry during the first nested step report";

	@Given("A step with a manual step")
	@SuppressWarnings("unused")
	public void i_have_a_step_with_a_manual_step() throws InterruptedException {
		StepReporter stepReporter = Launch.currentLaunch().getStepReporter();

		stepReporter.sendStep(FIRST_NAME);
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		LOGGER.info(FIRST_NESTED_STEP_LOG);
	}

	@Then("A step with two manual steps")
	@SuppressWarnings("unused")
	public void i_have_a_step_with_two_manual_steps() throws InterruptedException {
		StepReporter stepReporter = Launch.currentLaunch().getStepReporter();

		stepReporter.sendStep(SECOND_NAME, DURING_SECOND_NESTED_STEP_LOG);
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		LOGGER.info(SECOND_NESTED_STEP_LOG);

		stepReporter.sendStep(ItemStatus.FAILED, THIRD_NAME, new File("files/unlucky.jpg"));
		Thread.sleep(CommonUtils.MINIMAL_TEST_PAUSE);
		LOGGER.error(THIRD_NESTED_STEP_LOG);
	}

}
