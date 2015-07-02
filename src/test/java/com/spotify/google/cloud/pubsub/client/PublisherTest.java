/*
 * Copyright (c) 2011-2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spotify.google.cloud.pubsub.client;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PublisherTest {

  @Mock Pubsub pubsub;

  final ConcurrentMap<String, BlockingQueue<CompletableFuture<List<String>>>> topics = new ConcurrentHashMap<>();

  private Publisher publisher;

  @Before
  public void setUp() {
    publisher = Publisher.builder()
        .project("test")
        .pubsub(pubsub)
        .build();

    when(pubsub.publish(anyString(), anyString(), anyListOf(Message.class)))
        .thenAnswer(invocation -> {
          final String topic = (String) invocation.getArguments()[1];
          final CompletableFuture<List<String>> future = new CompletableFuture<>();
          final BlockingQueue<CompletableFuture<List<String>>> queue = topics.get(topic);
          queue.add(future);
          return future;
        });
  }

  @Test
  public void testConfigurationGetters() {
    final Publisher publisher = Publisher.builder()
        .pubsub(pubsub)
        .project("test")
        .concurrency(11)
        .batchSize(12)
        .queueSize(13)
        .build();

    assertThat(publisher.project(), is("test"));
    assertThat(publisher.concurrency(), is(11));
    assertThat(publisher.batchSize(), is(12));
    assertThat(publisher.queueSize(), is(13));
  }

  @Test
  public void testOutstandingRequests() throws InterruptedException, ExecutionException {
    final LinkedBlockingQueue<CompletableFuture<List<String>>> t1 = new LinkedBlockingQueue<>();
    final LinkedBlockingQueue<CompletableFuture<List<String>>> t2 = new LinkedBlockingQueue<>();
    topics.put("t1", t1);
    topics.put("t2", t2);

    final Message m1 = Message.builder().data("1").build();
    final Message m2 = Message.builder().data("2").build();

    // Verify that the outstanding requests before publishing anything is 0
    assertThat(publisher.outstandingRequests(), is(0));

    // Publish a message and verify that the outstanding request counter rises to 1
    final CompletableFuture<String> f1 = publisher.publish("t1", m1);
    assertThat(publisher.outstandingRequests(), is(1));

    // Publish another message and verify that the outstanding request counter rises to 2
    final CompletableFuture<String> f2 = publisher.publish("t2", m2);
    assertThat(publisher.outstandingRequests(), is(2));

    // Respond to the first request and verify that the outstanding request counter falls to 1
    t1.take().complete(singletonList("id1"));
    f1.get();
    assertThat(publisher.outstandingRequests(), is(1));

    // Respond to the second request and verify that the outstanding request counter falls to 0
    t2.take().complete(singletonList("id2"));
    f2.get();
    assertThat(publisher.outstandingRequests(), is(0));
  }

}
