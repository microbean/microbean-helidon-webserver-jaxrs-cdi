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

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.Any;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.Unmanaged;

import javax.enterprise.event.Observes;

import javax.enterprise.util.AnnotationLiteral;

import javax.inject.Singleton;

import javax.ws.rs.Path;

import javax.ws.rs.core.Application;
import javax.ws.rs.ApplicationPath;

public class HelidonJAXRSExtension implements Extension {

  private static final ApplicationPath DEFAULT_APPLICATION_PATH = new ApplicationPathLiteral();

  private static final Map<Class<?>, Unmanaged<?>> unmanageds = new HashMap<>();

  public HelidonJAXRSExtension() {
    super();
  }

  private final <T extends Application> void processApplication(@Observes final ProcessAnnotatedType<T> event) {
    if (event != null) {
      final AnnotatedType<T> application = event.getAnnotatedType();
      if (application != null) {
        final ApplicationPath applicationPath = application.getAnnotation(ApplicationPath.class);
        if (applicationPath == null) {
          event.configureAnnotatedType()
            .add(DEFAULT_APPLICATION_PATH);
        } else {
          final String value = applicationPath.value();
          if (value == null || value.trim().isEmpty()) {
            event.configureAnnotatedType()
              .add(DEFAULT_APPLICATION_PATH);
          }          
        }
      }
    }
  }

  private final <T extends Application> void enforceApplicationScopeOnApplications(@Observes final ProcessBeanAttributes<T> event) {
    if (event != null) {
      final BeanAttributes<T> applicationBeanAttributes = event.getBeanAttributes();
      if (applicationBeanAttributes != null) {
        if (!ApplicationScoped.Literal.INSTANCE.equals(applicationBeanAttributes.getScope())) {
          event.configureBeanAttributes()
            .scope(ApplicationScoped.class);
        }
      }
    }
  }

  private final void registerClassesAndSingletons(@Observes final AfterBeanDiscovery event,
                                                  final BeanManager beanManager)
  {
    if (event != null && beanManager != null) {
      final Set<Bean<?>> beans = beanManager.getBeans(Application.class, Any.Literal.INSTANCE);
      if (beans != null && !beans.isEmpty()) {
        for (final Bean<?> bean : beans) {
          @SuppressWarnings("unchecked")
          final Bean<? extends Application> applicationBean = (Bean<? extends Application>)bean;
          registerClassesAndSingletons(event, applicationBean, beanManager);
        }
      }
    }
  }

  private static final <T extends Application> void registerClassesAndSingletons(final AfterBeanDiscovery event,
                                                                                 final Bean<T> bean,
                                                                                 final BeanManager beanManager)
  {
    Objects.requireNonNull(event);
    Objects.requireNonNull(bean);
    Objects.requireNonNull(beanManager);

    // The contexts aren't active yet, so we can't use Context's get()
    // methods.  Instead, we're going to ask the Bean to create an
    // instance of the Application (this also handles injection
    // *partially*; i.e. non-synthetic beans are available to be
    // injected but non-synthetic beans are not, at least in Weld).
    // We'll ask the new Application instance, which is effectively
    // unmanaged, for its classes and singletons.  We'll register all
    // those under the same qualifiers that they might have already
    // plus the ones present on their governing Application.  Then we
    // destroy this unmanaged Application instance.  We've already
    // registered it to become a "real bean" in our
    // ProcessAnnotatedType observer method.
    
    final CreationalContext<T> cc = beanManager.createCreationalContext(bean);
    final T application = bean.create(cc);
    if (application != null) {
      final Set<Class<?>> classes = application.getClasses();
      if (classes != null && !classes.isEmpty()) {
        for (final Class<?> cls : classes) {
          if (cls != null) {
            event.addBean()
              .scope(Dependent.class) // Dependent by default...
              .read(beanManager.createAnnotatedType(cls)) // ...but overridable
              .addQualifiers(bean.getQualifiers()); // TODO: maybe?
          }
        }
      }
      final Set<?> singletons = application.getSingletons();
      if (singletons != null && !singletons.isEmpty()) {
        for (final Object singleton : singletons) {
          if (singleton != null) {
            event.addBean()
              .scope(Singleton.class) // Singleton by default...
              .read(beanManager.createBeanAttributes(beanManager.createAnnotatedType(singleton.getClass()))) // ...but overridable
              .qualifiers(bean.getQualifiers()) // TODO: maybe?
              .createWith(ignored -> singleton);
          }
        }
      }
    }
    bean.destroy(application, cc);
    cc.release();
  }

  private static final class ApplicationPathLiteral extends AnnotationLiteral<ApplicationPath> implements ApplicationPath {

    private static final long serialVersionUID = 1L;
    
    private final String value;
    
    private ApplicationPathLiteral() {
      super();
      this.value = "/";
    }
    
    @Override
    public final String value() {
      return this.value;
    }
    
  }
  
}
