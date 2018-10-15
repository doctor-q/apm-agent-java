/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.configuration.validation;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class RegexValidatorTest {

    @Test
    void testRegexValidator() {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid("foo")).doesNotThrowAnyException();
            // checking for nullness is not the responsibility of the validator, but it must be null safe
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid(null)).doesNotThrowAnyException();
            softly.assertThatCode(() -> RegexValidator.of("foo").assertValid("bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value \"bar\" does not match regex foo");
            softly.assertThatCode(() -> RegexValidator.of("foo", "{0} is not {1}").assertValid("bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bar is not foo");
        });
    }
}