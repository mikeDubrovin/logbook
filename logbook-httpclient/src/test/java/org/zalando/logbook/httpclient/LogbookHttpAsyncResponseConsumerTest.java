package org.zalando.logbook.httpclient;

/*
 * #%L
 * Logbook: HTTP Client
 * %%
 * Copyright (C) 2015 Zalando SE
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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.BasicHttpContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.zalando.logbook.Correlator;
import org.zalando.logbook.JsonHttpLogFormatter;
import org.zalando.logbook.Logbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.restdriver.clientdriver.RestClientDriver.giveResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.onRequestTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.nio.client.methods.HttpAsyncMethods.create;
import static org.apache.http.nio.client.methods.HttpAsyncMethods.createConsumer;
import static org.apache.http.util.EntityUtils.toByteArray;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class LogbookHttpAsyncResponseConsumerTest extends AbstractHttpTest {

    private final Logbook logbook = Logbook.builder()
            .writer(writer)
            .build();

    private final CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
            .addInterceptorFirst(new LogbookHttpRequestInterceptor(logbook))
            .build();

    @Mock
    private FutureCallback<HttpResponse> callback;

    @Mock
    private Correlator correlator;

    @Override
    public void start() {
        client.start();
    }

    @Override
    public void stop() throws IOException {
        client.close();
    }

    @Override
    protected void sendAndReceive() throws IOException, ExecutionException, InterruptedException {
        driver.addExpectation(onRequestTo("/"),
                giveResponse("Hello, world!", "text/plain"));

        final HttpGet request = new HttpGet(driver.getBaseUrl());

        final HttpResponse response = client.execute(create(request),
                new LogbookHttpAsyncResponseConsumer<>(createConsumer()), callback).get();

        assertThat(response.getStatusLine().getStatusCode(), is(200));
        assertThat(new String(toByteArray(response.getEntity()), UTF_8), is("Hello, world!"));
    }

    @Test(expected = UncheckedIOException.class)
    public void shouldWrapIOException() throws IOException {
        final HttpAsyncResponseConsumer<HttpResponse> unit = new LogbookHttpAsyncResponseConsumer<>(createConsumer());

        final BasicHttpContext context = new BasicHttpContext();
        context.setAttribute(Attributes.CORRELATOR, correlator);

        doThrow(new IOException()).when(correlator).write(any());

        unit.responseCompleted(context);
    }

}
