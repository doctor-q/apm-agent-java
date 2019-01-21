package co.elastic.apm.grpc;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.internal.ServerStream;
import io.grpc.internal.ServerTransportListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;

public class ServerTransportListenerInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodEnter
    private static void onBeforeExecute(@Advice.This ServerTransportListener serverTransportListener,
                                        @Advice.Argument(0) final ServerStream stream,
                                        @Advice.Argument(1) final String methodName,
                                        @Advice.Argument(2) final Metadata headers,
                                        @Advice.Local("span") Span span) {
        if (tracer == null || tracer.getActive() == null) {
            return;
        }
        final AbstractSpan<?> parent = tracer.getActive();
        span = GrpcHelper.startGrpcServerSpan(parent, methodName, headers);
        Metadata.Key<String> key = Metadata.Key.of(TraceContext.TRACE_PARENT_HEADER, Metadata.ASCII_STRING_MARSHALLER);
        if (span != null) {
            headers.put(key, span.getTraceContext().getOutgoingTraceParentHeader().toString());
        } else if (!headers.containsKey(key)) {
            // re-adds the header on redirects
            headers.put(key, parent.getTraceContext().getOutgoingTraceParentHeader().toString());
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(is(ServerTransportListener.class))
            .and(hasSuperType(is(ServerTransportListener.class)));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("streamCreated")
            .and(isPublic());
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("grpc");
    }
}
