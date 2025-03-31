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

import io.reactivex.Maybe;

import javax.annotation.Nonnull;

/**
 * Represents a step in a Cucumber scenario execution with its Report Portal ID and type.
 */
public class Step {

    /**
     * Enumeration of possible step types.
     */
    public enum Type {
        /**
         * Standard step type for steps that are created immediately with complete information.
         */
        NORMAL,
        
        /**
         * Step type for steps that will have their data filled later due to reporting order issues.
         * Used when step hooks are placed inside a step but executed before we have information about the step itself.
         */
        VIRTUAL
    }

    /**
     * The Report Portal ID for this step.
     */
    private final Maybe<String> id;

    /**
     * The type of this step.
     */
    private final Type type;

    /**
     * Creates a new step with the specified ID and type.
     *
     * @param id   the Report Portal ID
     * @param type the step type
     */
    public Step(@Nonnull Maybe<String> id, @Nonnull Type type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Returns the Report Portal ID for this step.
     *
     * @return the Report Portal ID
     */
    @Nonnull
    public Maybe<String> getId() {
        return id;
    }

    /**
     * Returns the type of this step.
     *
     * @return the step type
     */
    @Nonnull
    public Type getType() {
        return type;
    }
}
