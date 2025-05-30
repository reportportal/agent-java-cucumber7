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
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.files.ByteSource;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.reportportal.utils.http.ContentType;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.epam.reportportal.cucumber.Utils.*;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.createKey;
import static com.epam.reportportal.cucumber.util.ItemTreeUtils.retrieveLeaf;
import static com.epam.reportportal.utils.formatting.ExceptionUtils.getStackTrace;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Abstract Cucumber 6.x - 7.x formatter for ReportPortal
 */
public class ScenarioReporter implements ConcurrentEventListener {
	public static final String BACKGROUND_PREFIX = "BACKGROUND: ";
	protected static final URI WORKING_DIRECTORY = new File(System.getProperty("user.dir")).toURI();
	protected static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
	private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioReporter.class);
	private static final ThreadLocal<ScenarioReporter> INSTANCES = new InheritableThreadLocal<>();
	private static final String AGENT_PROPERTIES_FILE = "agent.properties";
	private static final String COLON_INFIX = ": ";
	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";
	private static final String DOC_STRING_PARAM = "DocString";
	private static final String DATA_TABLE_PARAM = "DataTable";
	private static final String UNKNOWN_PARAM = "arg";
	private static final String TEST_CASE_ID_PREFIX = "@tc_id:";
	private static final String ERROR_FORMAT = "Error:\n%s";

	private final Map<URI, FeatureContext> featureContextMap = new ConcurrentHashMap<>();
	private final TestItemTree itemTree = new TestItemTree();
	private final ReportPortal rp = buildReportPortal();

	// There is no event for recognizing end of feature in Cucumber.
	// This map is used to record the last scenario time and its feature uri.
	// End of feature occurs once launch is finished.
	private final Map<URI, Date> featureEndTime = new ConcurrentHashMap<>();

	/**
	 * This map uses to record the description of the scenario and the step to append the error to the description.
	 */
	private final Map<Maybe<String>, String> descriptionsMap = new ConcurrentHashMap<>();
	/**
	 * This map uses to record errors to append to the description.
	 */
	private final Map<Maybe<String>, Throwable> errorMap = new ConcurrentHashMap<>();
	private final Supplier<Launch> launch = new MemoizingSupplier<>(new Supplier<>() {

		/* should not be lazy */
		private final Date startTime = Calendar.getInstance().getTime();

		@Override
		public Launch get() {
			StartLaunchRQ rq = buildStartLaunchRq(startTime, getReportPortal().getParameters());
			Launch myLaunch = getReportPortal().newLaunch(rq);
			itemTree.setLaunchId(myLaunch.start());
			return myLaunch;
		}
	});

	public ScenarioReporter() {
		INSTANCES.set(this);
	}

	/**
	 * Returns a reporter instance for the current thread.
	 *
	 * @return reporter instance for the current thread
	 */
	@Nonnull
	public static ScenarioReporter getCurrent() {
		return INSTANCES.get();
	}

	/**
	 * A method for creation a Start Launch request which will be sent to ReportPortal. You can customize it by overriding the method.
	 *
	 * @param startTime  launch start time, which will be set into the result request
	 * @param parameters ReportPortal client parameters
	 * @return a Start Launch request instance
	 */
	protected StartLaunchRQ buildStartLaunchRq(Date startTime, ListenerParameters parameters) {
		StartLaunchRQ rq = new StartLaunchRQ();
		rq.setName(parameters.getLaunchName());
		rq.setStartTime(startTime);
		rq.setMode(parameters.getLaunchRunningMode());
		Set<ItemAttributesRQ> attributes = new HashSet<>(parameters.getAttributes());
		rq.setAttributes(attributes);
		attributes.addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, ScenarioReporter.class.getClassLoader()));
		rq.setDescription(parameters.getDescription());
		rq.setRerun(parameters.isRerun());
		if (isNotBlank(parameters.getRerunOf())) {
			rq.setRerunOf(parameters.getRerunOf());
		}

		if (null != parameters.getSkippedAnIssue()) {
			ItemAttributesRQ skippedIssueAttribute = new ItemAttributesRQ();
			skippedIssueAttribute.setKey(SKIPPED_ISSUE_KEY);
			skippedIssueAttribute.setValue(parameters.getSkippedAnIssue().toString());
			skippedIssueAttribute.setSystem(true);
			attributes.add(skippedIssueAttribute);
		}
		return rq;
	}

	/**
	 * @return a full Test Item Tree with attributes
	 */
	@Nonnull
	public TestItemTree getItemTree() {
		return itemTree;
	}

	/**
	 * @return a {@link ReportPortal} class instance which is used to communicate with the portal
	 */
	@Nonnull
	public ReportPortal getReportPortal() {
		return rp;
	}

	/**
	 * @return a ReportPortal {@link Launch} class instance which is used in test item reporting
	 */
	@Nonnull
	public Launch getLaunch() {
		return launch.get();
	}

	/**
	 * Manipulations before the launch starts
	 */
	protected void beforeLaunch() {
		getLaunch();
	}

	/**
	 * Extension point to customize ReportPortal instance
	 *
	 * @return ReportPortal
	 */
	protected ReportPortal buildReportPortal() {
		return ReportPortal.builder().build();
	}

	/**
	 * Finish RP launch
	 */
	protected void afterLaunch() {
		FinishExecutionRQ finishLaunchRq = new FinishExecutionRQ();
		finishLaunchRq.setEndTime(Calendar.getInstance().getTime());
		getLaunch().finish(finishLaunchRq);
	}

	private void addToTree(Feature feature, TestCase testCase, Maybe<String> scenarioId) {
		retrieveLeaf(feature.getUri(), itemTree).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.put(createKey(testCase.getLocation().getLine()), TestItemTree.createTestItemLeaf(scenarioId)));
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	@Nonnull
	protected Set<ItemAttributesRQ> extractAttributes(@Nonnull Collection<?> tags) {
		return tags.stream().map(Object::toString).map(Utils::toAttribute).collect(Collectors.toSet());
	}

	private void execute(@Nonnull URI uri, @Nonnull FeatureContextAware context) {
		Optional<FeatureContext> feature = ofNullable(featureContextMap.get(uri));
		if (feature.isPresent()) {
			context.executeWithContext(feature.get());
		} else {
			LOGGER.warn("Unable to locate corresponding Feature for URI: {}", uri);
		}
	}

	private boolean isLineInExamplesTable(String line) {
		return line.startsWith("|") && line.endsWith("|");
	}

	/**
	 * Check if the given line is within an Examples table
	 */
	private boolean isLineInExamplesTable(List<String> fileLines, int lineNumber) {
		// Line numbers in Cucumber are 1-based, list indices are 0-based
		if (lineNumber <= 0 || lineNumber > fileLines.size()) {
			return false;
		}

		String line = fileLines.get(lineNumber - 1).trim();
		// Check if the line is a table row (starts and ends with pipe)
		return isLineInExamplesTable(line);
	}

	/**
	 * Find the header row of the Examples table containing the given line
	 */
	private int findHeaderRowIndex(List<String> fileLines, int lineNumber) {
		int previousLine = lineNumber - 2;
		// Search upward from the current line to find the header row
		for (int i = previousLine; i >= 0; i--) {
			String line = fileLines.get(i).trim();
			if (StringUtils.isNotBlank(line) && !isLineInExamplesTable(line)) {
				return previousLine;
			}

			if (StringUtils.isNotBlank(line)) {
				previousLine = i;
			}
		}
		return -1;
	}

	/**
	 * Extract cells from a table row
	 */
	private List<String> extractTableCells(String tableRow) {
		return Arrays.stream(tableRow.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
	}

	/**
	 * Extract parameters from Examples table for the current Scenario Outline
	 *
	 * @param testCase Cucumber's TestCase object
	 * @return a list of parameter pairs or null if failed to extract
	 */
	@Nullable
	protected List<Pair<String, String>> getParameters(@Nonnull TestCase testCase) {
		URI uri = testCase.getUri();
		int lineNumber = testCase.getLocation().getLine();

		// Read the feature file to extract the parameters for the current example
		List<String> fileLines;
		try {
			fileLines = Arrays.asList(new String(
					com.epam.reportportal.utils.files.Utils.getFile(uri).read(),
					Charset.defaultCharset()
			).split("\r?\n"));
		} catch (IOException e) {
			LOGGER.error("Failed to read feature file: {}", uri, e);
			return null;
		}

		// Check if this is a scenario from a scenario outline by checking the location line
		if (!isLineInExamplesTable(fileLines, lineNumber)) {
			return null;
		}

		// Get header row (parameter names)
		int headerRowIndex = findHeaderRowIndex(fileLines, lineNumber);
		if (headerRowIndex < 0) {
			return null;
		}

		String headerRow = fileLines.get(headerRowIndex).trim();
		List<String> paramNames = extractTableCells(headerRow);

		// Get value row (current example)
		String valueRow = fileLines.get(lineNumber - 1).trim();
		List<String> paramValues = extractTableCells(valueRow);

		// Check if we got everything correctly
		if (paramValues.isEmpty() || paramNames.size() != paramValues.size()) {
			return null;
		}

		// Form parameter list and return
		return IntStream.range(0, paramNames.size())
				.mapToObj(i -> Pair.of(paramNames.get(i), paramValues.get(i)))
				.collect(Collectors.toList());
	}

	/**
	 * Returns code reference for feature files by URI and text line number
	 *
	 * @param testCase   Cucumber's TestCase object
	 * @param parameters a scenario parameters
	 * @return a code reference
	 */
	@Nonnull
	protected String getCodeRef(@Nonnull TestCase testCase, @Nullable List<Pair<String, String>> parameters) {
		URI uri = testCase.getUri();
		String relativePath = WORKING_DIRECTORY.relativize(uri).toString();
		String baseCodeRef = relativePath + "/[SCENARIO:" + testCase.getName() + "]";
		if (parameters == null) {
			return baseCodeRef;
		}
		return relativePath + "/[EXAMPLE:" + testCase.getName() + formatParameters(parameters) + "]";
	}

	protected Set<ItemAttributesRQ> getAttributes(TestCase testCase) {
		Set<String> tags = testCase.getTags().stream().filter(t -> !t.startsWith(TEST_CASE_ID_PREFIX)).collect(Collectors.toSet());
		execute(testCase.getUri(), f -> tags.removeAll(f.getTags()));
		return extractAttributes(tags);
	}

	/**
	 * Return a Test Case ID for a scenario in a feature file
	 *
	 * @param testCase   Cucumber's TestCase object
	 * @param parameters a scenario parameters
	 * @return Test Case ID
	 */
	@Nonnull
	protected String getTestCaseId(@Nonnull TestCase testCase, @Nullable List<Pair<String, String>> parameters) {
		List<String> tags = testCase.getTags().stream().filter(t -> t.startsWith(TEST_CASE_ID_PREFIX)).collect(Collectors.toList());
		return tags.isEmpty() ?
				getCodeRef(testCase, parameters) :
				tags.get(0).substring(TEST_CASE_ID_PREFIX.length()) + (parameters == null || parameters.isEmpty() ?
						"" :
						formatParameters(parameters));
	}

	/**
	 * Extension point to customize scenario creation event/request
	 *
	 * @param testCase Cucumber's TestCase object
	 * @return start test item request ready to send on RP
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRequest(@Nonnull TestCase testCase) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(buildName(testCase.getKeyword(), ScenarioReporter.COLON_INFIX, testCase.getName()));
		List<Pair<String, String>> parameters = getParameters(testCase);
		rq.setParameters(ParameterUtils.getParameters((String) null, parameters));
		String codeRef = getCodeRef(testCase, parameters);
		rq.setCodeRef(codeRef);
		rq.setAttributes(getAttributes(testCase));
		rq.setStartTime(Calendar.getInstance().getTime());
		String type = ItemType.STEP.name();
		rq.setType(type);
		rq.setTestCaseId(getTestCaseId(testCase, parameters));
		return rq;
	}

	/**
	 * Start Cucumber Scenario
	 *
	 * @param featureId       parent feature item id
	 * @param startScenarioRq scenario start request
	 * @return scenario item id
	 */
	@Nonnull
	protected Maybe<String> startScenario(@Nonnull Maybe<String> featureId, @Nonnull StartTestItemRQ startScenarioRq) {
		return getLaunch().startTestItem(featureId, startScenarioRq);
	}

	private void execute(@Nonnull TestCase testCase, @Nonnull ScenarioContextAware context) {
		URI uri = testCase.getUri();
		int line = testCase.getLocation().getLine();
		execute(
				uri, f -> {
					Optional<ScenarioContext> scenario = f.getScenario(line);
					if (scenario.isPresent()) {
						context.executeWithContext(f, scenario.get());
					} else {
						LOGGER.warn("Unable to locate corresponding Feature or Scenario context for URI: {}; line: {}", uri, line);
					}
				}
		);
	}

	/**
	 * Finish a test item with specified status
	 *
	 * @param itemId   an ID of the item
	 * @param status   the status of the item
	 * @param dateTime a date and time object to use as feature end time
	 * @return a date and time object of the finish event
	 */
	protected Date finishTestItem(@Nullable Maybe<String> itemId, @Nullable ItemStatus status, @Nullable Date dateTime) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return null;
		}
		Date endTime = ofNullable(dateTime).orElse(Calendar.getInstance().getTime());
		FinishTestItemRQ rq = buildFinishTestItemRequest(itemId, endTime, status);
		//noinspection ReactiveStreamsUnusedPublisher
		getLaunch().finishTestItem(itemId, rq);
		return endTime;
	}

	/**
	 * Finish a test item with specified status
	 *
	 * @param itemId an ID of the item
	 * @param status the status of the item
	 */
	protected void finishTestItem(@Nullable Maybe<String> itemId, @Nullable ItemStatus status) {
		finishTestItem(itemId, status, null);
	}

	/**
	 * Finish a test item with no specific status
	 *
	 * @param itemId an ID of the item
	 */
	protected void finishTestItem(@Nullable Maybe<String> itemId) {
		finishTestItem(itemId, null);
	}

	private void removeFromTree(Feature featureContext, TestCase scenarioContext) {
		retrieveLeaf(featureContext.getUri(), itemTree).ifPresent(suiteLeaf -> suiteLeaf.getChildItems()
				.remove(createKey(scenarioContext.getLocation().getLine())));
	}

	/**
	 * Start before/after-hook item on ReportPortal
	 *
	 * @param parentId parent item id
	 * @param rq       hook start request
	 * @return hook item id
	 */
	@Nonnull
	protected Maybe<String> startHook(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return getLaunch().startTestItem(parentId, rq);
	}

	protected void beforeHooksSuite(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		execute(
				testCase, (f, s) -> {
					HookType hookType = testStep.getHookType();
					Optional<HookSuite> hookSuiteOptional = s.getHookSuite();
					if (hookType == hookSuiteOptional.map(HookSuite::getType).orElse(null)) {
						// if we have the same hook type, we should operate with the same hook suite
						return;
					}

					final Maybe<String> parentId;
					// Special handling for BEFORE_STEP hooks
					if (hookType == HookType.BEFORE_STEP) {
						// Create a virtual step for BEFORE_STEP hooks
						Maybe<String> virtualStepId = getLaunch().createVirtualItem();
						// Set the virtual step in the scenario context
						s.setStep(new Step(virtualStepId, Step.Type.VIRTUAL));
						// Use virtual step as parent for hook suite
						parentId = virtualStepId;
					} else if (hookType == HookType.AFTER_STEP) {
						parentId = s.getPreviousStep().map(Step::getId).orElseGet(() -> {
							LOGGER.warn("Unable to locate step ID for AFTER_STEP hook. Using scenario ID as parent.");
							return s.getId();
						});
					} else {
						parentId = s.getId();
					}

					hookSuiteOptional.map(hookSuite -> {
						// if we have a new hook type, we need to finish the previous suite and create new one
						finishTestItem(hookSuite.getId(), ofNullable(hookSuite.getStatus()).orElse(ItemStatus.PASSED));
						StartTestItemRQ hookSuiteRq = buildStartHookSuiteRequest(testStep);
						Maybe<String> hookSuiteId = startHook(parentId, hookSuiteRq);
						s.setHookSuite(new HookSuite(hookSuiteId, hookType, null));
						return true;
					}).orElseGet(() -> {
						// if we don't have a hook suite, we need to create one
						StartTestItemRQ hookSuiteRq = buildStartHookSuiteRequest(testStep);
						Maybe<String> hookSuiteId = startHook(parentId, hookSuiteRq);
						s.setHookSuite(new HookSuite(hookSuiteId, hookType, null));
						return false;
					});
				}
		);
	}

	protected void afterHooksSuite(@Nonnull TestCase testCase) {
		execute(
				testCase, (f, s) -> {
					Optional<HookSuite> hookSuite = s.getHookSuite();
					hookSuite.ifPresent(suite -> {
						finishTestItem(suite.getId(), ofNullable(suite.getStatus()).orElse(ItemStatus.PASSED));
						s.setHookSuite(null);
					});
				}
		);
	}

	/**
	 * Finish Cucumber scenario
	 * Put scenario end time in a map to check last scenario end time per feature
	 *
	 * @param event Cucumber's TestCaseFinished object
	 */
	protected void afterScenario(TestCaseFinished event) {
		TestCase testCase = event.getTestCase();
		afterHooksSuite(testCase);
		execute(
				testCase, (f, s) -> {
					URI featureUri = f.getUri();
					if (mapItemStatus(event.getResult().getStatus()) == ItemStatus.FAILED) {
						Optional.ofNullable(event.getResult().getError()).ifPresent(error -> errorMap.put(s.getId(), error));
					}
					Date endTime = finishTestItem(s.getId(), mapItemStatus(event.getResult().getStatus()), null);
					featureEndTime.put(featureUri, endTime);
					removeFromTree(f.getFeature(), testCase);
				}
		);
	}

	/**
	 * Generate a step name.
	 *
	 * @param testStep Cucumber's TestStep object
	 * @return a step name
	 */
	@Nullable
	protected String getStepName(@Nonnull PickleStepTestStep testStep) {
		return testStep.getStep().getText();
	}

	/**
	 * Returns a list of parameters for a step
	 *
	 * @param testStep Cucumber's Step object
	 * @return a list of parameters or empty list if none
	 */
	@Nonnull
	protected List<ParameterResource> getParameters(@Nonnull TestStep testStep) {
		if (!(testStep instanceof PickleStepTestStep)) {
			return Collections.emptyList();
		}

		PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
		List<Argument> arguments = pickleStepTestStep.getDefinitionArgument();
		List<Pair<String, String>> params = ofNullable(arguments).map(a -> a.stream()
				.map(arg -> Pair.of(arg.getParameterTypeName(), arg.getValue()))
				.collect(Collectors.toList())).orElse(new ArrayList<>());
		ofNullable(pickleStepTestStep.getStep().getArgument()).ifPresent(a -> {
			String value;
			if (a instanceof DocStringArgument) {
				value = ((DocStringArgument) a).getContent();
				params.add(Pair.of(DOC_STRING_PARAM, value));
			} else if (a instanceof DataTableArgument) {
				params.add(Pair.of(DATA_TABLE_PARAM, formatDataTable(((DataTableArgument) a).cells())));
			} else {
				params.add(Pair.of(UNKNOWN_PARAM, a.toString()));
			}
		});
		return ParameterUtils.getParameters((String) null, params);
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param testStep   a cucumber step object
	 * @param stepPrefix a prefix of the step (e.g. 'Background')
	 * @param keyword    a step keyword (e.g. 'Given')
	 * @return a Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRequest(@Nonnull PickleStepTestStep testStep, @Nullable String stepPrefix,
			@Nullable String keyword) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(buildName(stepPrefix, keyword, getStepName(testStep)));
		rq.setDescription(buildMultilineArgument(testStep));
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType("STEP");
		rq.setParameters(getParameters(testStep));
		rq.setHasStats(false);
		return rq;
	}

	/**
	 * Start virtual step item on ReportPortal
	 *
	 * @param scenarioId    parent scenario item id
	 * @param virtualStepId virtual step item id
	 * @param startStepRq   step start request
	 * @return step item id
	 */
	@Nonnull
	protected Maybe<String> startVirtualStep(@Nonnull Maybe<String> scenarioId, @Nonnull Maybe<String> virtualStepId,
			@Nonnull StartTestItemRQ startStepRq) {
		return getLaunch().startVirtualTestItem(scenarioId, virtualStepId, startStepRq);
	}

	/**
	 * Start Step item on ReportPortal
	 *
	 * @param scenarioId  parent scenario item id
	 * @param startStepRq step start request
	 * @return step item id
	 */
	@Nonnull
	protected Maybe<String> startStep(@Nonnull Maybe<String> scenarioId, @Nonnull StartTestItemRQ startStepRq) {
		return getLaunch().startTestItem(scenarioId, startStepRq);
	}

	private void addToTree(@Nonnull TestCase scenario, @Nullable String text, @Nullable Maybe<String> stepId) {
		retrieveLeaf(scenario.getUri(), scenario.getLocation().getLine(), itemTree).ifPresent(scenarioLeaf -> scenarioLeaf.getChildItems()
				.put(createKey(text), TestItemTree.createTestItemLeaf(stepId)));
	}

	/**
	 * Start Cucumber step
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param step     a cucumber step object
	 */
	protected void beforeStep(@Nonnull TestCase testCase, @Nonnull PickleStepTestStep step) {
		execute(
				testCase, (f, s) -> {
					afterHooksSuite(testCase);
					String stepPrefix = step.getStep().getLocation().getLine() < s.getLine() ? BACKGROUND_PREFIX : null;
					StartTestItemRQ rq = buildStartStepRequest(step, stepPrefix, step.getStep().getKeyword());

					Optional<Step> currentStepOptional = s.getStep();
					Maybe<String> stepId = currentStepOptional.map(currentStep -> {
						final Maybe<String> sId;
						if (currentStep.getType() == Step.Type.VIRTUAL) {
							// For VIRTUAL step override timestamp and use startVirtualTestItem
							rq.setStartTime(currentStep.getTimestamp());
							sId = startVirtualStep(s.getId(), currentStep.getId(), rq);
						} else {
							// For NORMAL step, log a warning about potential unfinished step
							LOGGER.warn(
									"Unexpected state: starting a step when another step of type NORMAL is active. "
											+ "This might indicate an unfinished step. Step: {}: {}",
									step.getStep().getKeyword(),
									step.getStep().getText()
							);
							sId = startStep(s.getId(), rq);
						}
						s.setStep(new Step(sId, Step.Type.NORMAL));
						return sId;
					}).orElseGet(() -> {
						// No existing step, proceed with normal flow
						Maybe<String> sId = startStep(s.getId(), rq);
						s.setStep(new Step(sId, Step.Type.NORMAL));
						return sId;
					});

					String stepText = step.getStep().getText();
					if (getLaunch().getParameters().isCallbackReportingEnabled()) {
						addToTree(testCase, stepText, stepId);
					}
				}
		);
		String description = buildMultilineArgument(step).trim();
		if (!description.isEmpty()) {
			sendLog(description);
		}
	}

	/**
	 * Finish Cucumber step
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param testStep a cucumber step object
	 * @param result   Step result
	 */
	@SuppressWarnings("unused")
	protected void afterStep(@Nonnull TestCase testCase, @Nonnull PickleStepTestStep testStep, @Nonnull Result result) {
		execute(
				testCase, (f, s) -> {
					reportResult(result);
					Optional<Step> optionalStep = s.getStep();
					if (optionalStep.isPresent()) {
						Step step = optionalStep.get();
						if (step.getType() == Step.Type.NORMAL) {
							if (mapItemStatus(result.getStatus()) == ItemStatus.FAILED) {
								Optional.ofNullable(result.getError()).ifPresent(error -> errorMap.put(step.getId(), error));
							}
							finishTestItem(step.getId(), mapItemStatus(result.getStatus()), null);
							// Store current step as previous step before clearing the current step
							s.setPreviousStep(step);
						} else {
							LOGGER.error(
									"BUG: Trying to finish virtual step item: {}: {}",
									testStep.getStep().getKeyword(),
									testStep.getStep().getText()
							);
						}
						s.setStep(null);
					} else {
						LOGGER.error(
								"BUG: Trying to finish unspecified step item: {}: {}",
								testStep.getStep().getKeyword(),
								testStep.getStep().getText()
						);
					}
				}
		);
	}

	/**
	 * Returns hook type and name as a <code>Pair</code>
	 *
	 * @param hookType Cucumber's hoo type
	 * @return a pair of type and name
	 */
	@Nonnull
	protected String getHookName(@Nonnull HookType hookType) {
		switch (hookType) {
			case BEFORE:
				return "Before hooks";
			case AFTER:
				return "After hooks";
			case AFTER_STEP:
				return "After step";
			case BEFORE_STEP:
				return "Before step";
			default:
				return "Hook";
		}
	}

	protected StartTestItemRQ buildStartHookSuiteRequest(@Nonnull HookTestStep testStep) {
		StartTestItemRQ rq = new StartTestItemRQ();
		String name = getHookName(testStep.getHookType());
		rq.setName(name);
		rq.setType(ItemType.STEP.name());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setHasStats(false);
		return rq;
	}

	/**
	 * Extension point to customize test creation event/request
	 *
	 * @param testCase Cucumber's TestCase object
	 * @param testStep a cucumber step object
	 * @return Request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected StartTestItemRQ buildStartHookRequest(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(testStep.getCodeLocation());
		rq.setType(ItemType.STEP.name());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setHasStats(false);
		return rq;
	}

	/**
	 * Called when before/after-hooks are started
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param testStep Cucumber's TestStep object
	 */
	protected void beforeHooks(@Nonnull TestCase testCase, @Nonnull HookTestStep testStep) {
		execute(
				testCase, (f, s) -> {
					beforeHooksSuite(testCase, testStep);
					StartTestItemRQ rq = buildStartHookRequest(testCase, testStep);
					Optional<HookSuite> hookSuite = s.getHookSuite();
					s.setHookId(startHook(hookSuite.map(HookSuite::getId).orElseGet(s::getId), rq));
				}
		);
	}

	/**
	 * Called when before/after-hooks are finished
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param step     a cucumber step object
	 * @param result   a cucumber result object
	 */
	@SuppressWarnings("unused")
	protected void afterHooks(@Nonnull TestCase testCase, @Nonnull HookTestStep step, Result result) {
		execute(
				testCase, (f, s) -> {
					reportResult(result);
					ItemStatus hookStatus = mapItemStatus(result.getStatus());
					finishTestItem(s.getHookId(), hookStatus);
					s.setHookId(Maybe.empty());
					Optional<HookSuite> hookSuite = s.getHookSuite();
					if (hookSuite.isEmpty() || hookStatus == null) {
						return;
					}
					hookSuite.get().updateStatus(hookStatus);
				}
		);
	}

	/**
	 * Report test item result and error (if present)
	 *
	 * @param result - Cucumber result object
	 */
	protected void reportResult(@Nonnull Result result) {
		ofNullable(result.getError()).ifPresent(ReportPortal::sendStackTraceToRP);
	}

	/**
	 * Send a log with data attached.
	 *
	 * @param name     attachment name
	 * @param mimeType attachment type
	 * @param data     data to attach
	 */
	protected void embedding(@Nullable String name, @Nullable String mimeType, @Nonnull byte[] data) {
		String type = ofNullable(mimeType).filter(ContentType::isValidType).orElseGet(() -> getDataType(data, name));
		String attachmentName = ofNullable(name).filter(m -> !m.isEmpty())
				.orElseGet(() -> ofNullable(type).map(t -> t.substring(0, t.indexOf("/"))).orElse(""));
		ReportPortal.emitLog(
				new ReportPortalMessage(ByteSource.wrap(data), type, attachmentName),
				LogLevel.INFO.name(),
				Calendar.getInstance().getTime()
		);
	}

	/**
	 * Send a text log entry to ReportPortal with 'INFO' level, using current datetime as timestamp
	 *
	 * @param message a text message
	 */
	protected void sendLog(@Nullable String message) {
		ReportPortal.emitLog(message, LogLevel.INFO.name(), Calendar.getInstance().getTime());
	}

	/**
	 * Extension point to customize scenario creation event/request
	 *
	 * @param rule the rule node
	 * @return start test item request ready to send on RP
	 */
	@Nonnull
	protected StartTestItemRQ buildStartRuleRequest(@Nonnull Node.Rule rule) {
		String ruleKeyword = rule.getKeyword().orElse("Rule");
		String ruleName = rule.getName().orElse(null);
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(ruleName != null ? buildName(ruleKeyword, COLON_INFIX, ruleName) : ruleKeyword);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setAttributes(extractAttributes(Utils.getTags(rule)));
		rq.setType("SUITE");
		return rq;
	}

	/**
	 * Start Rule item on ReportPortal
	 *
	 * @param featureId parent item id
	 * @param ruleRq    Rule start request
	 * @return hook item id
	 */
	@Nonnull
	protected Maybe<String> startRule(@Nonnull Maybe<String> featureId, @Nonnull StartTestItemRQ ruleRq) {
		return getLaunch().startTestItem(featureId, ruleRq);
	}

	/**
	 * Start Cucumber scenario
	 *
	 * @param feature  current feature object
	 * @param scenario current scenario object
	 */
	protected void beforeScenario(@Nonnull Feature feature, @Nonnull TestCase scenario) {
		execute(
				scenario, (f, s) -> {
					Optional<RuleContext> rule = s.getRule();
					Optional<RuleContext> currentRule = f.getCurrentRule();
					if (!currentRule.equals(rule)) {
						if (currentRule.isEmpty()) {
							rule.ifPresent(r -> {
								r.setId(startRule(f.getId(), buildStartRuleRequest(r.getRule())));
								f.setCurrentRule(r);
							});
						} else {
							finishTestItem(currentRule.get().getId());
							rule.ifPresent(r -> {
								r.setId(startRule(f.getId(), buildStartRuleRequest(r.getRule())));
								f.setCurrentRule(r);
							});
						}
					}
					Maybe<String> rootId = rule.map(RuleContext::getId).orElseGet(f::getId);

					// If it's a ScenarioOutline use Example's line number as code reference to detach one Test Item from another
					StartTestItemRQ startTestItemRQ = buildStartScenarioRequest(scenario);
					s.setId(startScenario(rootId, startTestItemRQ));
					descriptionsMap.put(s.getId(), ofNullable(startTestItemRQ.getDescription()).orElse(StringUtils.EMPTY));
					if (getLaunch().getParameters().isCallbackReportingEnabled()) {
						addToTree(feature, scenario, s.getId());
					}
				}
		);
	}

	/**
	 * Returns code reference for feature files by URI and text line number
	 *
	 * @param feature a Cucumber's Feature object
	 * @return a code reference
	 */
	@Nonnull
	protected String getCodeRef(@Nonnull Feature feature) {
		return WORKING_DIRECTORY.relativize(feature.getUri()).toString();
	}

	/**
	 * Extension point to customize feature creation event/request
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a path to the feature
	 * @return Request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRequest(@Nonnull Feature feature, @Nonnull URI uri) {
		String featureKeyword = feature.getKeyword().orElse("");
		String featureName = feature.getName().orElse(getCodeRef(feature));
		StartTestItemRQ startFeatureRq = new StartTestItemRQ();
		startFeatureRq.setDescription(getDescription(feature, uri));
		startFeatureRq.setName(buildName(featureKeyword, ScenarioReporter.COLON_INFIX, featureName));
		execute(feature.getUri(), f -> startFeatureRq.setAttributes(extractAttributes(f.getTags())));
		startFeatureRq.setStartTime(Calendar.getInstance().getTime());
		startFeatureRq.setType(ItemType.STORY.name());
		return startFeatureRq;
	}

	/**
	 * Start Cucumber Feature
	 *
	 * @param startFeatureRq feature start request
	 * @return feature item id
	 */
	@Nonnull
	protected Maybe<String> startFeature(@Nonnull StartTestItemRQ startFeatureRq) {
		return getLaunch().startTestItem(startFeatureRq);
	}

	private void addToTree(Feature feature, Maybe<String> featureId) {
		getItemTree().getTestItems().put(createKey(feature.getUri()), TestItemTree.createTestItemLeaf(featureId));
	}

	/**
	 * Starts a Cucumber Test Case start, also starts corresponding Feature if is not started already.
	 *
	 * @param event Cucumber's Test Case started event object
	 */
	protected void handleStartOfTestCase(@Nonnull TestCaseStarted event) {
		TestCase testCase = event.getTestCase();
		URI uri = testCase.getUri();
		execute(
				uri, f -> {
					//noinspection ReactiveStreamsUnusedPublisher
					if (f.getId().equals(Maybe.empty())) {
						StartTestItemRQ featureRq = buildStartFeatureRequest(f.getFeature(), uri);
						f.setId(startFeature(featureRq));
						if (getLaunch().getParameters().isCallbackReportingEnabled()) {
							addToTree(f.getFeature(), f.getId());
						}
					}
				}
		);
		execute(
				testCase, (f, s) -> {
					s.setTestCase(testCase);
					beforeScenario(f.getFeature(), testCase);
				}
		);
	}

	protected void handleSourceEvents(TestSourceParsed parseEvent) {
		parseEvent.getNodes().forEach(n -> {
			if (n instanceof Feature) {
				Feature feature = (Feature) n;
				featureContextMap.put(feature.getUri(), new FeatureContext(feature));
			} else {
				LOGGER.warn("Unknown node type: {}", n.getClass().getSimpleName());
			}
		});
	}

	protected void handleTestStepStarted(@Nonnull TestStepStarted event) {
		TestStep testStep = event.getTestStep();
		TestCase testCase = event.getTestCase();
		if (testStep instanceof HookTestStep) {
			beforeHooks(testCase, (HookTestStep) testStep);
		} else if (testStep instanceof PickleStepTestStep) {
			afterHooksSuite(testCase);
			beforeStep(testCase, (PickleStepTestStep) testStep);
		} else {
			LOGGER.warn("Unable to start unknown step type: {}", testStep.getClass().getSimpleName());
		}
	}

	protected void handleTestStepFinished(@Nonnull TestStepFinished event) {
		TestStep testStep = event.getTestStep();
		TestCase testCase = event.getTestCase();
		if (testStep instanceof HookTestStep) {
			afterHooks(testCase, (HookTestStep) testStep, event.getResult());
		} else if (testStep instanceof PickleStepTestStep) {
			afterStep(testCase, (PickleStepTestStep) testStep, event.getResult());
		} else {
			LOGGER.warn("Unable to finish unknown step type: {}", testStep.getClass().getSimpleName());
		}
	}

	private void removeFromTree(Feature feature) {
		itemTree.getTestItems().remove(createKey(feature.getUri()));
	}

	protected void handleEndOfFeature() {
		featureContextMap.values().forEach(f -> {
			Date featureCompletionDateTime = featureEndTime.get(f.getUri());
			f.getCurrentRule().ifPresent(r -> finishTestItem(r.getId(), null, featureCompletionDateTime));
			finishTestItem(f.getId(), null, featureCompletionDateTime);
			removeFromTree(f.getFeature());
		});
		featureContextMap.clear();
	}

	protected EventHandler<TestRunStarted> getTestRunStartedHandler() {
		return event -> beforeLaunch();
	}

	protected EventHandler<TestSourceParsed> getTestSourceParsedHandler() {
		return this::handleSourceEvents;
	}

	protected EventHandler<TestCaseStarted> getTestCaseStartedHandler() {
		return this::handleStartOfTestCase;
	}

	protected EventHandler<TestStepStarted> getTestStepStartedHandler() {
		return this::handleTestStepStarted;
	}

	protected EventHandler<TestStepFinished> getTestStepFinishedHandler() {
		return this::handleTestStepFinished;
	}

	protected EventHandler<TestCaseFinished> getTestCaseFinishedHandler() {
		return this::afterScenario;
	}

	protected EventHandler<TestRunFinished> getTestRunFinishedHandler() {
		return event -> {
			handleEndOfFeature();
			afterLaunch();
		};
	}

	protected EventHandler<EmbedEvent> getEmbedEventHandler() {
		return event -> embedding(event.getName(), event.getMediaType(), event.getData());
	}

	protected EventHandler<WriteEvent> getWriteEventHandler() {
		return event -> sendLog(event.getText());
	}

	/**
	 * Registers an event handler for a specific event.
	 * <p>
	 * The available events types are:
	 * <ul>
	 * <li>{@link TestRunStarted} - the first event sent.
	 * <li>{@link TestSourceRead} - sent for each feature file read, contains the feature file source.
	 * <li>{@link TestCaseStarted} - sent before starting the execution of a Test Case(/Pickle/Scenario), contains the Test Case
	 * <li>{@link TestStepStarted} - sent before starting the execution of a Test Step, contains the Test Step
	 * <li>{@link TestStepFinished} - sent after the execution of a Test Step, contains the Test Step and its Result.
	 * <li>{@link TestCaseFinished} - sent after the execution of a Test Case(/Pickle/Scenario), contains the Test Case and its Result.
	 * <li>{@link TestRunFinished} - the last event sent.
	 * <li>{@link EmbedEvent} - calling scenario.embed in a hook triggers this event.
	 * <li>{@link WriteEvent} - calling scenario.write in a hook triggers this event.
	 * </ul>
	 */
	@Override
	public void setEventPublisher(EventPublisher publisher) {
		publisher.registerHandlerFor(TestRunStarted.class, getTestRunStartedHandler());
		publisher.registerHandlerFor(TestRunFinished.class, getTestRunFinishedHandler());
		publisher.registerHandlerFor(TestSourceParsed.class, getTestSourceParsedHandler());
		publisher.registerHandlerFor(TestCaseStarted.class, getTestCaseStartedHandler());
		publisher.registerHandlerFor(TestCaseFinished.class, getTestCaseFinishedHandler());
		publisher.registerHandlerFor(TestStepStarted.class, getTestStepStartedHandler());
		publisher.registerHandlerFor(TestStepFinished.class, getTestStepFinishedHandler());
		publisher.registerHandlerFor(EmbedEvent.class, getEmbedEventHandler());
		publisher.registerHandlerFor(WriteEvent.class, getWriteEventHandler());
	}

	/**
	 * Build finish test item request object
	 *
	 * @param itemId     item ID reference
	 * @param finishTime a datetime object to use as item end time
	 * @param status     item result status
	 * @return finish request
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishTestItemRequest(@Nonnull Maybe<String> itemId, @Nullable Date finishTime,
			@Nullable ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		if (status == ItemStatus.FAILED) {
			Optional<String> currentDescription = Optional.ofNullable(descriptionsMap.remove(itemId));
			Optional<Throwable> currentError = Optional.ofNullable(errorMap.remove(itemId));
			currentDescription.flatMap(description -> currentError.map(errorMessage -> resolveDescriptionErrorMessage(
					description,
					errorMessage
			))).ifPresent(rq::setDescription);
		}
		ofNullable(status).ifPresent(s -> rq.setStatus(s.name()));
		rq.setEndTime(finishTime);
		return rq;
	}

	/**
	 * Resolve description
	 *
	 * @param currentDescription Current description
	 * @param error              Error message
	 * @return Description with error
	 */
	private String resolveDescriptionErrorMessage(String currentDescription, Throwable error) {
		String errorStr = getReportPortal().getParameters().isExceptionTruncate() ?
				format(ERROR_FORMAT, getStackTrace(error, new Throwable())) :
				format(ERROR_FORMAT, ExceptionUtils.getStackTrace(error));
		return Optional.ofNullable(currentDescription)
				.filter(StringUtils::isNotBlank)
				.map(description -> MarkdownUtils.asTwoParts(currentDescription, errorStr))
				.orElse(errorStr);
	}

	/**
	 * Map Cucumber statuses to RP item statuses
	 *
	 * @param status - Cucumber status
	 * @return RP test item status and null if status is null
	 */
	@Nullable
	protected ItemStatus mapItemStatus(@Nullable Status status) {
		if (status == null) {
			return null;
		} else {
			if (STATUS_MAPPING.get(status) == null) {
				LOGGER.error("Unable to find direct mapping between Cucumber and ReportPortal for TestItem with status: '{}'.", status);
				return ItemStatus.SKIPPED;
			}
			return STATUS_MAPPING.get(status);
		}
	}

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	protected String formatDataTable(@Nonnull final List<List<String>> table) {
		return MarkdownUtils.formatDataTable(table);
	}

	/**
	 * Generate multiline argument (DataTable or DocString) representation
	 *
	 * @param step - Cucumber step object
	 * @return - transformed multiline argument (or empty string if there is
	 * none)
	 */
	@Nonnull
	protected String buildMultilineArgument(@Nonnull TestStep step) {
		List<List<String>> table = null;
		String docString = null;
		PickleStepTestStep pickleStep = (PickleStepTestStep) step;
		if (pickleStep.getStep().getArgument() != null) {
			StepArgument argument = pickleStep.getStep().getArgument();
			if (argument instanceof DocStringArgument) {
				docString = ((DocStringArgument) argument).getContent();
			} else if (argument instanceof DataTableArgument) {
				table = ((DataTableArgument) argument).cells();
			}
		}

		StringBuilder marg = new StringBuilder();
		if (table != null) {
			marg.append(formatDataTable(table));
		}

		if (docString != null) {
			marg.append(DOCSTRING_DECORATOR).append(docString).append(DOCSTRING_DECORATOR);
		}
		return marg.toString();
	}

	/**
	 * Build an item description for a feature
	 *
	 * @param feature a Cucumber's Feature object
	 * @param uri     a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(Feature feature, @Nonnull URI uri) {
		return uri.toString();
	}

	/**
	 * Build an item description for a scenario
	 *
	 * @param testCase a Cucumber's TestCase object
	 * @param uri      a feature URI
	 * @return item description
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected String getDescription(@Nonnull TestCase testCase, @Nonnull URI uri) {
		return uri.toString();
	}

	@FunctionalInterface
	private interface FeatureContextAware {
		void executeWithContext(@Nonnull FeatureContext featureContext);
	}

	@FunctionalInterface
	private interface ScenarioContextAware {

		void executeWithContext(@Nonnull FeatureContext featureContext, @Nonnull ScenarioContext scenarioContext);
	}
}
