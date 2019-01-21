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
package co.elastic.apm.grpc;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ClientCallInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @Advice.OnMethodEnter
    private static Span onBeforeExecute(@Advice.This ClientCall clientCall,
                                        @Advice.Argument(0) ClientCall.Listener responseListener,
                                        @Advice.Argument(1) Metadata headers) {
        if (tracer != null) {
            final AbstractSpan<?> parent = tracer.getActive();
            return GrpcHelper.startGrpcSpan(parent, clientCall, responseListener, headers);
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Enter @Nullable Span span,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(is(ClientCall.class))
            .and(hasSuperType(is(ClientCall.class)));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("start")
            .and(isPublic());
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("grpc");
    }
}
