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

import com.epam.reportportal.cucumber.util.HookSuite;
import io.cucumber.plugin.event.Node;
import io.cucumber.plugin.event.TestCase;
import io.reactivex.Maybe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Represents the context of a Cucumber scenario during test execution.
 * This class holds all the necessary information about a scenario, including its parent rule (if any), the actual scenario or scenario
 * outline node, the associated test case, and references to Report Portal test items.
 * <p>
 * The context is used by the {@link ScenarioReporter} to track and report the execution of scenarios, steps, and hooks to Report Portal.
 */
public class ScenarioContext {

	/**
	 * The line number of the scenario or scenario outline in the feature file.
	 */
	private final int line;
	
	/**
	 * The parent rule context, if this scenario is defined within a rule.
	 */
	private final RuleContext rule;
	
	/**
	 * The Cucumber scenario node for regular scenarios.
	 */
	private final Node.Scenario scenario;
	
	/**
	 * The Cucumber scenario outline node for scenario outlines.
	 */
	private final Node.ScenarioOutline scenarioOutline;

	/**
	 * The Cucumber test case associated with this scenario context.
	 */
	private TestCase testCase;
	
	/**
	 * The Report Portal ID for this scenario.
	 */
	private Maybe<String> id = Maybe.empty();
	
	/**
	 * The Report Portal ID for the current hook.
	 */
	private Maybe<String> hookId = Maybe.empty();
	
	/**
	 * The current step information including Report Portal ID and step type.
	 */
	private Step step;
	
	/**
	 * The previous step information including Report Portal ID and step type.
	 * Used to track the step that was just finished.
	 */
	private Step previousStep;
	
	/**
	 * The hook suite (collection of hooks of the same type) for this scenario.
	 */
	private HookSuite hookSuite;

	/**
	 * Creates a new scenario context for a regular scenario.
	 *
	 * @param ruleNode    the parent rule context, may be null
	 * @param scenarioNode the Cucumber scenario node
	 */
	public ScenarioContext(@Nullable RuleContext ruleNode, @Nonnull Node.Scenario scenarioNode) {
		rule = ruleNode;
		scenario = scenarioNode;
		scenarioOutline = null;
		line = scenario.getLocation().getLine();
	}

	/**
	 * Creates a new scenario context for a scenario outline.
	 *
	 * @param ruleNode           the parent rule context, may be null
	 * @param scenarioOutlineNode the Cucumber scenario outline node
	 */
	public ScenarioContext(@Nullable RuleContext ruleNode, @Nonnull Node.ScenarioOutline scenarioOutlineNode) {
		rule = ruleNode;
		scenario = null;
		scenarioOutline = scenarioOutlineNode;
		line = scenarioOutline.getLocation().getLine();
	}

	/**
	 * Returns the line number of the scenario or scenario outline in the feature file.
	 *
	 * @return the line number
	 */
	public int getLine() {
		return line;
	}

	/**
	 * Returns the Cucumber test case associated with this scenario context, if available.
	 *
	 * @return an Optional containing the test case, or empty if not set
	 */
	@Nonnull
	@SuppressWarnings("unused")
	public Optional<TestCase> getTestCase() {
		return ofNullable(testCase);
	}

	/**
	 * Sets the Cucumber test case for this scenario context.
	 *
	 * @param testCase the test case to associate with this context
	 */
	public void setTestCase(@Nullable TestCase testCase) {
		this.testCase = testCase;
	}

	/**
	 * Returns the parent rule context, if this scenario is defined within a rule.
	 *
	 * @return an Optional containing the rule context, or empty if not within a rule
	 */
	@Nonnull
	public Optional<RuleContext> getRule() {
		return ofNullable(rule);
	}

	/**
	 * Returns the Report Portal ID for this scenario.
	 *
	 * @return the Report Portal ID
	 */
	@Nonnull
	public Maybe<String> getId() {
		return id;
	}

	/**
	 * Sets the Report Portal ID for this scenario.
	 *
	 * @param id the Report Portal ID
	 */
	public void setId(@Nonnull Maybe<String> id) {
		this.id = id;
	}

	/**
	 * Returns the hook suite for this scenario.
	 * The hook suite represents a collection of hooks of the same type (before, after, etc.).
	 *
	 * @return an Optional containing the hook suite, or empty if not set
	 */
	@Nonnull
	public Optional<HookSuite> getHookSuite() {
		return ofNullable(hookSuite);
	}

	/**
	 * Sets the hook suite for this scenario.
	 *
	 * @param hookSuite the hook suite to set
	 */
	public void setHookSuite(@Nullable HookSuite hookSuite) {
		this.hookSuite = hookSuite;
	}

	/**
	 * Sets the Report Portal ID for the current hook.
	 *
	 * @param hookStepId the Report Portal ID for the hook
	 */
	public void setHookId(@Nonnull Maybe<String> hookStepId) {
		hookId = hookStepId;
	}

	/**
	 * Returns the Report Portal ID for the current hook.
	 *
	 * @return the hook ID
	 */
	@Nonnull
	public Maybe<String> getHookId() {
		return hookId;
	}

	/**
	 * Sets the current step.
	 *
	 * @param step the step object
	 */
	public void setStep(@Nullable Step step) {
		this.step = step;
	}

	/**
	 * Returns the current step.
	 *
	 * @return the current step
	 */
	@Nonnull
	public Optional<Step> getStep() {
		return ofNullable(step);
	}

	/**
	 * Sets the previous step.
	 *
	 * @param previousStep the step object that was previously executed
	 */
	public void setPreviousStep(@Nullable Step previousStep) {
		this.previousStep = previousStep;
	}

	/**
	 * Returns the previous step that was executed.
	 *
	 * @return an Optional containing the previous step, or empty if not set
	 */
	@Nonnull
	public Optional<Step> getPreviousStep() {
		return ofNullable(previousStep);
	}
}
