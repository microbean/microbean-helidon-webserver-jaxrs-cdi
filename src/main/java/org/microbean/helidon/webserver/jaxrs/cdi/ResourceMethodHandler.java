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

import java.lang.reflect.Method;

import java.util.Objects;

import javax.inject.Provider;

import io.helidon.common.http.Http;

import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

public class ResourceMethodHandler implements Handler {

  private final ResourceMethodDescriptor descriptor;

  private final Provider<?> resourceInstanceProvider;
  
  public ResourceMethodHandler(final Provider<?> resourceInstanceProvider, final ResourceMethodDescriptor descriptor) {
    super();
    this.descriptor = Objects.requireNonNull(descriptor);
    this.resourceInstanceProvider = Objects.requireNonNull(resourceInstanceProvider);
  }

  public void accept(final ServerRequest request, final ServerResponse response) {
    if (request != null && response != null && this.resourceInstanceProvider != null) {
      final Object resourceInstance = this.resourceInstanceProvider.get();
      if (resourceInstance != null) {
        final Method resourceMethod = this.descriptor.getResourceMethod();
        if (resourceMethod == null) {
          throw new IllegalStateException();
        }
        final Class<?> returnType = resourceMethod.getReturnType();
        assert returnType != null;

      }
    }
  }
  
}
