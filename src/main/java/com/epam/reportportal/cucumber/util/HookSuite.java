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

package com.epam.reportportal.cucumber.util;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.utils.StatusEvaluation;
import io.cucumber.plugin.event.HookType;
import io.reactivex.Maybe;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a suite of hook operations for Cucumber test execution.
 * This class maintains the state and grouping of hooks (Before, After, BeforeStep, AfterStep)
 * for reporting to Report Portal.
 *
 * <p>A hook suite is created for each group of hooks of the same type that are executed together,
 * allowing them to be represented as a logical group in the Report Portal UI.</p>
 */
public class HookSuite {
	/**
	 * The Report Portal ID of the hook suite test item.
	 */
	private final Maybe<String> id;

	/**
	 * The type of hooks in this suite (BEFORE, AFTER, BEFORE_STEP, AFTER_STEP).
	 */
	private final HookType type;

	/**
	 * The current aggregated status of all hooks in this suite.
	 */
	private ItemStatus status;

	/**
	 * Creates a new hook suite with the specified ID, type and initial status.
	 *
	 * @param id     The Report Portal ID of this hook suite test item
	 * @param type   The type of hooks in this suite
	 * @param status The initial status of the hook suite
	 */
	public HookSuite(@Nonnull Maybe<String> id, @Nonnull HookType type, @Nullable ItemStatus status) {
		this.id = id;
		this.type = type;
		this.status = status;
	}

	/**
	 * Updates the status of this hook suite based on the status of an individual hook.
	 * Uses status evaluation logic to determine the new aggregated status.
	 *
	 * @param status The status to evaluate against the current status
	 */
	public void updateStatus(@Nonnull ItemStatus status) {
		this.status = StatusEvaluation.evaluateStatus(this.status, status);
	}

	/**
	 * Gets the current aggregated status of this hook suite.
	 *
	 * @return The current status of the hook suite
	 */
	@Nullable
	public ItemStatus getStatus() {
		return status;
	}

	/**
	 * Gets the type of hooks in this suite.
	 *
	 * @return The hook type (BEFORE, AFTER, BEFORE_STEP, or AFTER_STEP)
	 */
	@Nonnull
	public HookType getType() {
		return type;
	}

	/**
	 * Gets the Report Portal ID of this hook suite test item.
	 *
	 * @return The Report Portal ID assigned to this hook suite
	 */
	@Nonnull
	public Maybe<String> getId() {
		return id;
	}
}
