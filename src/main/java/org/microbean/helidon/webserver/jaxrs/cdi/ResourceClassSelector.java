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

import java.lang.annotation.Annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;

import java.util.function.Function;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.inject.Instance;

import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import javax.inject.Inject;

import javax.ws.rs.core.Application;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Consumes;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

@ApplicationScoped
public class ResourceClassSelector implements Service {

  private final BeanManager beanManager;
  
  private final ApplicationPath applicationPath;
  
  @Inject
  public ResourceClassSelector(final BeanManager beanManager,
                               final ApplicationPath applicationPath) {
    super();
    this.beanManager = beanManager;
    this.applicationPath = Objects.requireNonNull(applicationPath);
  }

  @Override
  public final void update(final Routing.Rules rules) {
    Objects.requireNonNull(rules);    
    final Set<Bean<?>> resourceBeans = this.beanManager.getBeans(Object.class, new ResourceClass.Literal());
    if (resourceBeans != null && !resourceBeans.isEmpty()) {
      String prefix = this.applicationPath.value();
      assert prefix != null;
      if (!prefix.isEmpty()) {
        final Matcher matcher = ResourceMethodDescriptor.pattern.matcher(prefix);
        prefix = matcher.replaceAll("$2");
      }
      for (final Bean<?> resourceBean : resourceBeans) {
        if (resourceBean != null) {
          try {
            addHandlers(this.beanManager, rules, prefix, resourceBean);
          } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(reflectiveOperationException.getMessage(), reflectiveOperationException);
          }
        }
      }
    }
  }

  private static final <X> void addHandlers(final BeanManager beanManager, final Routing.Rules rules, String prefix, final Bean<?> resourceBean) throws ReflectiveOperationException {
    Objects.requireNonNull(rules);
    Objects.requireNonNull(resourceBean);
    final Set<Annotation> qualifiers = resourceBean.getQualifiers();
    assert qualifiers != null;
    assert !qualifiers.isEmpty();
    ResourceClass resourceClassAnnotation = null;
    for (final Annotation qualifier : qualifiers) {
      if (qualifier instanceof ResourceClass) {
        resourceClassAnnotation = (ResourceClass)qualifier;
        break;
      }
    }
    if (resourceClassAnnotation == null) {
      throw new IllegalArgumentException("resourceBean had no ResourceClass annotation on it");
    }
    @SuppressWarnings("unchecked")
    final Class<X> resourceClass = (Class<X>)resourceClassAnnotation.resourceClass();
    assert resourceClass != null;
    final AnnotatedType<X> resourceAnnotatedType = beanManager.createAnnotatedType(resourceClass);
    assert resourceAnnotatedType != null;
    resourceAnnotatedType.getMethods()
      .stream()
      .filter(am -> !am.isStatic() && Modifier.isPublic(am.getJavaMember().getModifiers()))
      .map(am -> {
          try {
            return ResourceMethodDescriptor.from(beanManager, prefix, resourceAnnotatedType, am);
          } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(reflectiveOperationException.getMessage(), reflectiveOperationException);
          }
        })
      .filter(am -> am != null)
      .forEach(d -> System.out.println("*** descriptor: " + d));
  }

  private static final class ResourceMethodHandler implements Handler {

    private final ResourceMethodDescriptor descriptor;
    
    private ResourceMethodHandler(final ResourceMethodDescriptor descriptor) {
      super();
      this.descriptor = Objects.requireNonNull(descriptor);
    }

    @Override
    public final void accept(final ServerRequest request, final ServerResponse response) {
      
    }
    
  }

}
