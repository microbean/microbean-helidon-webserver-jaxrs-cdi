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

import java.util.Collections;
import java.util.Objects;

import javax.enterprise.inject.Instance;

import javax.inject.Inject;

import io.helidon.common.http.Http;

import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

// Note: not a bean, but used as a bean template
class ResourceClassService implements Service {

  private final Instance<ResourceMethodHandler<?>> resourceMethodHandlers;

  @Inject
  private ResourceClassService(final Instance<ResourceMethodHandler<?>> resourceMethodHandlers) {    
    super();
    this.resourceMethodHandlers = Objects.requireNonNull(resourceMethodHandlers);
  }

  @Override
  @Inject
  public void update(final Routing.Rules rules) {
    Objects.requireNonNull(rules);    
    for (final ResourceMethodHandler<?> handler : this.resourceMethodHandlers) {
      if (handler != null) {
        final ResourceMethodDescriptor<?> descriptor = handler.getResourceMethodDescriptor();
        assert descriptor != null;
        final Http.RequestMethod httpMethod = descriptor.getHttpMethod();
        assert httpMethod != null;
        rules.anyOf(Collections.singleton(httpMethod), descriptor.getPath(), handler);
      }
    }
  }

}
