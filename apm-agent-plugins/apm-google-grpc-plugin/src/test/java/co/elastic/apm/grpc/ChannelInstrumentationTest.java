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

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelInstrumentationTest extends AbstractInstrumentationTest {
    private static final Logger log = LoggerFactory.getLogger(ChannelInstrumentationTest.class);

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5011;
    private Server server;
    private Channel channel;
    private TestServiceGrpc.TestServiceBlockingStub stub;

    @Before
    public void setUp() throws InterruptedException {
        new Thread(() -> {
            try {
                server = ServerBuilder.forPort(PORT).addService(new TestServiceGrpc.TestServiceImplBase() {
                    @Override
                    public void invoke(Request request, StreamObserver<Response> responseObserver) {
                        Response response = Response.newBuilder().build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                }).build().start();
                server.awaitTermination();
            } catch (Exception e) {
                log.error("", e);
            }
        }).start();
        Thread.sleep(1000);
        channel = ManagedChannelBuilder.forAddress(HOST, PORT).usePlaintext()
            .build();
        stub = TestServiceGrpc.newBlockingStub(channel);
        tracer.startTransaction().activate();
    }

    @Test
    public void test() {
        Response response = stub.invoke(Request.newBuilder().setInput("in").build());
        System.out.println(response);
        Transaction transaction = tracer.currentTransaction();
        System.out.println(transaction);
        Span span = tracer.currentSpan();
        System.out.println(span);
    }

    @After
    public void tearDown() {
        server.shutdownNow();
    }
}
