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

import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;

public class GrpcHelper {
    private static final Logger logger = LoggerFactory.getLogger(GrpcHelper.class);

    private static Span createSpan(AbstractSpan<?> parent, MethodDescriptor methodDescriptor, CallOptions callOptions) {
        String fullMethodName = methodDescriptor.getFullMethodName();
        Span span = parent
            .createSpan()
            .withType("grpc.channel")
            .appendToName(fullMethodName)
            .activate();

        // set grpc tags
        // authority
        if (callOptions.getAuthority() != null) {
            span.addTag("grpc.authority", callOptions.getAuthority());
        }
        // compressor
        if (callOptions.getCompressor() != null) {
            span.addTag("grpc.compressor", callOptions.getCompressor());
        }
        // deadline
        if (callOptions.getDeadline() != null) {
            span.addTag("grpc.deadline_millis", String.valueOf(callOptions.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)));
        }
        // methodType
        if (methodDescriptor.getType() != null) {
            span.addTag("grpc.method_type", methodDescriptor.getType().toString());
        }
        return span;
    }

    /*
     * typically, more than one ClientExecChain implementation is invoked during an HTTP request
     */
    private static boolean isAlreadyMonitored(AbstractSpan<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a http client span can't be the child of another http client span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().equalsIgnoreCase("grpc.channel");
    }

    @Nullable
    @VisibleForAdvice
    public static Span startGrpcServerSpan(AbstractSpan<?> parent, String methodName, Metadata headers) {
        Span span = null;
        if (!isAlreadyMonitored(parent)) {
            span = parent
                .createSpan()
                .withType("grpc.channel")
                .appendToName(methodName)
                .activate();
        }
        return span;
    }

    /**
     * for client, just violently wrapper {@link ClientCall}
     * to get more information from the final class {@link ClientCallImpl}
     */
    @Nullable
    @VisibleForAdvice
    public static Span startGrpcSpan(AbstractSpan<?> parent, ClientCall clientCall,
                                           ClientCall.Listener listener, Metadata headers) {
        Span span = null;
        if (!isAlreadyMonitored(parent)) {
            if (clientCall.getClass().getSimpleName().equalsIgnoreCase("ClientCallImpl")) {
                try {
                    Field method = clientCall.getClass().getDeclaredField("method");
                    method.setAccessible(true);
                    MethodDescriptor methodDescriptor = (MethodDescriptor) method.get(clientCall);

                    Field callOptions = clientCall.getClass().getDeclaredField("callOptions");
                    callOptions.setAccessible(true);
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(callOptions, callOptions.getModifiers() & ~Modifier.FINAL);
                    CallOptions options = (CallOptions) callOptions.get(clientCall);

                    span = createSpan(parent, methodDescriptor, options);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.error("", e);
                }
            }
        }
        return span;
    }
}
