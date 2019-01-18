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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

import io.helidon.common.http.MediaType;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

@ApplicationScoped
public class ResourceClassSelector implements Service {

  private static final Pattern pattern = Pattern.compile("^(\\s*/*\\s*)(.+)(\\s*/*\\s*)$");
  
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
        final Matcher matcher = pattern.matcher(prefix);
        prefix = matcher.replaceAll("$2");
      }
      for (final Bean<?> resourceBean : resourceBeans) {
        if (resourceBean != null) {
          addHandlers(rules, prefix, resourceBean);
        }
      }
    }
  }

  private static final void addHandlers(final Routing.Rules rules, String prefix, final Bean<?> resourceBean) {
    Objects.requireNonNull(rules);
    Objects.requireNonNull(resourceBean);

  }

  static final String getPath(final String prefix, final Class<?> resourceClass, final Method resourceMethod) {
    final String returnValue;
    final String pathPrefix = getPath(prefix, resourceClass);
    if (resourceMethod == null) {
      returnValue = pathPrefix;
    } else if (resourceMethod.isAnnotationPresent(Path.class)) {
      returnValue = getPath(pathPrefix, resourceMethod);
    } else {
      final Class<?> declaringClass = resourceMethod.getDeclaringClass();
      assert declaringClass != null;
      if (!declaringClass.isAssignableFrom(resourceClass)) {
        throw new IllegalArgumentException("Method " + declaringClass + " must be a superclass of " + resourceClass);
      }
      // TODO: check superclass method for the annotation, then check interface declaration for it
      // For now just punt
      returnValue = pathPrefix;
    }
    return returnValue;
  }

  private static final String getPath(String prefix, final AnnotatedElement element) {
    final String returnValue;
    if (element == null) {
      returnValue = prefix;
    } else if (element instanceof Method) {
      
    }
    return getPath(prefix, element.getAnnotation(Path.class));
  }

  private static final String getPath(String prefix, final Path path) {
    final String returnValue;
    if (path == null) {
      returnValue = getPath(prefix, (String)null);
    } else {
      returnValue = getPath(prefix, path.value());
    }
    return returnValue;
  }

  private static final String getPath(String prefix, String path) {
    final String returnValue;
    if (path == null || path.isEmpty()) {
      if (prefix == null || prefix.isEmpty()) {
        returnValue = "";
      } else {
        returnValue = prefix;
      }
    } else if (prefix == null || prefix.isEmpty()) {
      returnValue = "";
    } else {
      final Matcher matcher = pattern.matcher(path);
      path = matcher.replaceAll("$2");
      if (path == null || path.isEmpty()) {
        returnValue = prefix;
      } else {
        returnValue = prefix + "/" + path;
      }
    }
    return returnValue;
  }

  private static final String getHttpMethod(final Method m) {
    final String returnValue;
    if (m == null) {
      returnValue = null;
    } else {
      final Annotation[] annotations = m.getAnnotations();
      if (annotations == null || annotations.length <= 0) {
        returnValue = null;
      } else {
        String temp = null;
        for (final Annotation annotation : annotations) {
          temp = getHttpMethod(annotation);          
          if (temp != null) {
            break;
          }
        }
        returnValue = temp;
      }
    }
    return returnValue;
  }
  
  private static final String getHttpMethod(final Annotation annotation) {
    final String returnValue;
    if (annotation == null) {
      returnValue = null;
    } else {
      final Class<?> annotationType = annotation.annotationType();
      if (annotationType == null) {
        returnValue = null;
      } else {
        final HttpMethod httpMethod = annotationType.getAnnotation(HttpMethod.class);
        if (httpMethod == null) {
          returnValue = null;
        } else {
          returnValue = httpMethod.value();
        }
      }
    }
    return returnValue;
  }

  /**
   * Finds a certain object given a starting {@link Method} and a
   * {@link Function} that produces the desired object if possible,
   * following the rules of the JAX-RS specification's section 3.6
   * concerning annotation inheritance.
   */
  public static final <T> T find(final Method source, Function<? super Method, T> tester) throws ReflectiveOperationException {
    Objects.requireNonNull(source);
    Objects.requireNonNull(tester);
    T returnValue = tester.apply(source);
    if (returnValue == null) {
      final Set<Class<?>> interfaces = new LinkedHashSet<>();
      Class<?> c = source.getDeclaringClass().getSuperclass();
      Method m = null;
      while (c != null) {
        try {
          m = c.getDeclaredMethod(source.getName(), source.getParameterTypes());
          assert m != null;
          returnValue = tester.apply(m);
        } catch (final NoSuchMethodException canHappen) {
          
        }
        if (returnValue != null) {
          break;
        }
        // Keep track of, but don't yet process, interfaces as we go
        // up the inheritance hierarchy.
        interfaces.addAll(Arrays.asList(c.getInterfaces()));
        c = c.getSuperclass();
      }
      if (returnValue == null && !interfaces.isEmpty()) {
        for (final Class<?> iface : interfaces) {
          assert iface != null;
          try {
            m = iface.getDeclaredMethod(source.getName(), source.getParameterTypes());
            assert m != null;
            returnValue = tester.apply(m);
          } catch (final NoSuchMethodException canHappen) {
            
          }
          if (returnValue != null) {
            break;
          }
        }
      }
      interfaces.clear();
    }
    return returnValue;
  }

  private static final class ResourceMethodDescriptor {
    
    private final Set<? extends MediaType> consumedMediaTypes;
    
    private final Set<? extends MediaType> producedMediaTypes;
    
    private final String path;
    
    private final Method resourceMethod;
    
    private ResourceMethodDescriptor(final Method resourceMethod, final String path, final Set<? extends MediaType> consumedMediaTypes, final Set<? extends MediaType> producedMediaTypes) {
      super();
      this.resourceMethod = Objects.requireNonNull(resourceMethod);
      this.path = Objects.requireNonNull(path);
      if (consumedMediaTypes == null || consumedMediaTypes.isEmpty()) {
        this.consumedMediaTypes = Collections.singleton(MediaType.WILDCARD);
      } else {
        this.consumedMediaTypes = Collections.unmodifiableSet(new HashSet<>(consumedMediaTypes));
      }
      if (producedMediaTypes == null || producedMediaTypes.isEmpty()) {
        this.producedMediaTypes = Collections.singleton(MediaType.WILDCARD);
      } else {
        this.producedMediaTypes = Collections.unmodifiableSet(new HashSet<>(producedMediaTypes));
      }
    }

    public Method getResourceMethod() {
      return this.resourceMethod;
    }
    
    public String getPath() {
      return this.path;
    }

    public Set<? extends MediaType> getConsumedMediaTypes() {
      return this.consumedMediaTypes;
    }

    public Set<? extends MediaType> getProducedMediaTypes() {
      return this.producedMediaTypes;
    }

    public static ResourceMethodDescriptor from(final String applicationPath, final Class<?> resourceClass, final Method resourceMethod) {      
      Objects.requireNonNull(resourceClass);
      Objects.requireNonNull(resourceMethod);
      if (!resourceMethod.getDeclaringClass().isAssignableFrom(resourceClass)) {
        throw new IllegalArgumentException("Method " + resourceMethod.getDeclaringClass() + " must be a superclass of " + resourceClass);
      }
      ResourceMethodDescriptor returnValue = null;
      final String path = ResourceClassSelector.getPath(applicationPath, resourceClass, resourceMethod);
      assert path != null;
      final Annotation methodConsumes = resourceMethod.getDeclaredAnnotation(Consumes.class);
      final Annotation methodProduces = resourceMethod.getDeclaredAnnotation(Produces.class);
      if (methodConsumes == null) {
        if (methodProduces == null) {
          // No method-level JAX-RS annotations (other than perhaps Path).
          // Check interfaces?
        }
      }
      throw new UnsupportedOperationException("TODO IMPLEMENT");
    }
    
  }
  
}
