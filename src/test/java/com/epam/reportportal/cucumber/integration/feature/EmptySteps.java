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

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class EmptySteps {
	private static final Logger LOGGER = LoggerFactory.getLogger(EmptySteps.class);

	@Given("I have empty step")
	public void i_have_empty_step() {
		LOGGER.info("Inside 'I have empty step'");
	}

	@Then("I have another empty step")
	public void i_have_another_empty_step() {
		LOGGER.info("Inside 'I have another empty step'");
	}

	@When("I have one more empty step")
	public void i_have_one_more_empty_step() {
		LOGGER.info("I have one more empty step'");
	}

	@And("I have one more else empty step")
	public void i_have_one_more_else_empty_step() {
		LOGGER.info("I have one more else empty step'");
	}

	@Given("A very long step. Seriously, this step is ridiculously long. Our users require such a long step and here it is. This is not even a half of the step, it will last really long. I believe you will get tired before you reach the end of the step. Why? Because our users send here a megabyte-length JSONs or things like that, despite our words that this is not good for user experience, since it's hard to read such a long lines on UI and for backend performance, since we use this field in full-text search. OK I'm out of thought what else I can write here, but the step must go on. Probably you will see some random typing soon to get the step longer than it's right now. Because only now we crossed a half of the length we need. Alright, here we go: 39248cht42 3x,r093mhxr0,3hr c089r3423 xk309,,r3 k302uk032 3249xul398u3 at 34r9k39489 aumvi xkr293jed0 i 93u9f32u smhf 09ktc903 a tu09r328 ef5u0fu3v 0k8utf2 u59du9v u423kuc 9f5kv 39kd3uf39 -3940u5kfu5 b3-90485l-k3f. Are you tired? I'm too, but we still need a few words at the end.")
	public void very_long_step() {
		LOGGER.info("Inside very long step");
	}
}
