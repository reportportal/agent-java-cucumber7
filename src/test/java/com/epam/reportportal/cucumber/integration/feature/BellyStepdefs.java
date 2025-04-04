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

import com.epam.reportportal.cucumber.integration.service.Belly;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("unused")
public class BellyStepdefs {

	private final Belly belly = new Belly();

	@Given("^I have (\\d+) cukes in my belly$")
	public void I_have_cukes_in_my_belly(int cukes) {
		belly.eat(cukes);
	}

	@When("^I wait (\\d+) hour$")
	public void I_wait(int hours) {
		belly.wait(hours);
	}

	@Then("^my belly should growl$")
	public void my_belly_should_growl() {
		assertThat(belly.growl(), equalTo(Boolean.TRUE));
	}
}
