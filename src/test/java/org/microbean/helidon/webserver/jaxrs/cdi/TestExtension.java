/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helidon.webserver.jaxrs.cdi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.util.Objects;
import java.util.Collections;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import javax.annotation.Priority;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.Produces;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import javax.inject.Inject;
import javax.inject.Singleton;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import javax.ws.rs.core.Application;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;

import io.helidon.common.reactive.Flow;

import io.helidon.config.Config;

import io.helidon.webserver.BareRequest;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ApplicationScoped
public class TestExtension {

  
  /*
   * Test boilerplate.
   */

  
  private SeContainer cdiContainer;

  public TestExtension() {
    super();
  }

  @Before
  public void startCdiContainer() {
    final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
    assertNotNull(initializer);    
    this.cdiContainer = initializer.initialize();
  }

  @After
  public void shutDownCdiContainer() {
    if (this.cdiContainer != null) {
      this.cdiContainer.close();
    }
  }

  @Test
  public void test() {

  }


  /*
   * Actual test code.
   */
  
  
  private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                         final Application application,
                         WebServer webServer)
    throws ExecutionException, IOException, InterruptedException
  {
    assertNotNull(application);
    assertNotNull(webServer);
    webServer = webServer.start().toCompletableFuture().get();
    assertNotNull(webServer);
    final Class<?> c = application.getClass();
    assertNotNull(c);
    assertTrue(c.isSynthetic()); // proves @Dependent got replaced with @ApplicationScoped
    final Class<?> superclass = c.getSuperclass();
    assertEquals(Application.class, superclass);
    assertNotNull(application.toString());
    final URL url = new URL("http://127.0.0.1:" + webServer.port() + "/foo/frob/foo");
    try (final InputStream stream = new BufferedInputStream(url.openStream())) {
      assertNotNull(stream);
    }
    webServer.shutdown();
  }


  /*
   * Example user code exercised by test.
   */


  /**
   * An {@link Application} that implements {@link Serializable} for
   * no good reason other than to help test the {@link
   * HelidonJAXRSExtension.ClassDepthComparator} algorithm.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see Application
   *
   * @see HelidonJAXRSExtension.ClassDepthComparator
   */
  @RequestScoped
  @Priority(1)
  @ApplicationPath("foo")
  private static final class MyApplication extends Application implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    public MyApplication(final TestExtension injectee) {
      super();
      assertNotNull(injectee);
      assertNotNull(injectee.toString());
    }

    @Override
    public final Set<Class<?>> getClasses() {
      return Collections.singleton(StupidResource.class);
    }

  }

  @Path("/frob")
  private static final class StupidResource {

    public StupidResource() {
      super();
    }

    @Path("/foo")
    @GET
    public Object gorp(final Gorp input) {
      // return input.toString();
      return null;
    }
    
  }

  private static class Gorp {

  }

  @ApplicationScoped
  @Entity // TODO: This is kind of irritating
  private static class GorpReader implements Reader<Gorp> {

    @Override
    public CompletionStage<? extends Gorp> apply(final Flow.Publisher<DataChunk> publisher,
                                                 final Class<? super Gorp> type) {
      final CompletableFuture<Gorp> returnValue = new CompletableFuture<>();
      returnValue.complete(new Gorp());
      return returnValue;
    }

  }
  
}
