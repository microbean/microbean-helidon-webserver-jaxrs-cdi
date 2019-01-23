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

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.regex.Matcher;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;

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
import javax.enterprise.inject.spi.ProcessSyntheticBean;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.Unmanaged;

import javax.enterprise.inject.spi.configurator.BeanConfigurator;

import javax.enterprise.event.Observes;

import javax.enterprise.util.AnnotationLiteral;

import javax.inject.Qualifier;
import javax.inject.Singleton;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;

import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.RxInvokerProvider;

import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.DynamicFeature;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Feature;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.WriterInterceptor;

public class HelidonJAXRSExtension implements Extension {

  private static final ApplicationPath DEFAULT_APPLICATION_PATH = new ApplicationPathLiteral("");

  private static final Map<Class<?>, Unmanaged<?>> unmanageds = new HashMap<>();

  public HelidonJAXRSExtension() {
    super();
  }

  private final <T extends Application> void ensureApplicationPathOnApplications(@Observes final ProcessAnnotatedType<T> event) {
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

  private final void registerClassesAndSingletons(@Observes final AfterBeanDiscovery event, final BeanManager beanManager)
    throws ReflectiveOperationException {
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

  private static final <T extends Application, U> void registerClassesAndSingletons(final AfterBeanDiscovery event,
                                                                                    final Bean<T> bean,
                                                                                    final BeanManager beanManager)
    throws ReflectiveOperationException {
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

      // application should not be a proxy because we've asked the
      // Bean to create it directly.  That's good; we can get
      // its @ApplicationPath directly.
      final ApplicationPath applicationPath;
      final Class<?> applicationClass = application.getClass();
      assert !applicationClass.isSynthetic();
      if (applicationClass.isAnnotationPresent(ApplicationPath.class)) {
        applicationPath = applicationClass.getAnnotation(ApplicationPath.class);
      } else {
        applicationPath = DEFAULT_APPLICATION_PATH;
      }
      assert applicationPath != null;

      String applicationPathString = applicationPath.value();
      assert applicationPathString != null;
      if (!applicationPathString.isEmpty()) {
        final Matcher matcher = ResourceMethodDescriptor.pattern.matcher(applicationPathString);
        applicationPathString = matcher.replaceAll("$2");
      }
      
      event.addBean()
        .types(ApplicationPath.class)
        .qualifiers(bean.getQualifiers())
        .scope(Singleton.class)
        .createWith(ignored -> applicationPath);
      
      final Set<Class<?>> classes = application.getClasses();
      if (classes != null && !classes.isEmpty()) {
        for (final Class<?> cls : classes) {
          if (cls != null) {
            @SuppressWarnings("unchecked")
            final AnnotatedType<U> annotatedType = beanManager.createAnnotatedType((Class<U>)cls);
            assert annotatedType != null;

            final BeanConfigurator<U> bc = event.addBean();
            assert bc != null;
            bc.read(annotatedType)
              .addQualifiers(bean.getQualifiers());
            
            final Set<? extends ResourceMethodDescriptor> resourceMethodDescriptors =
              ResourceClasses.getResourceMethodDescriptors(beanManager, applicationPathString, annotatedType);
            if (isProviderClass(cls)) {
              // TODO: Features aren't supported, nor is it clear they should be
              bc.addQualifier(ProviderLiteral.INSTANCE);
            } else if (resourceMethodDescriptors != null && !resourceMethodDescriptors.isEmpty()) {
              bc.addQualifier(new ResourceClass.Literal(cls));

              for (final ResourceMethodDescriptor descriptor : resourceMethodDescriptors) {
                if (descriptor != null) {
                  // TODO: add a bean for a Handler wrapping this descriptor
                    
                }
              }
            }
          }
        }
      }
      final Set<?> singletons = application.getSingletons();
      if (singletons != null && !singletons.isEmpty()) {
        for (final Object singleton : singletons) {
          if (singleton != null) {
            event.addBean()
              .read(beanManager.createBeanAttributes(beanManager.createAnnotatedType(singleton.getClass())))
              .scope(ApplicationScoped.class)
              .addQualifiers(bean.getQualifiers()) // TODO: maybe?
              .createWith(ignored -> singleton);
          }
        }
      }
      // Not sure about this block; may not be necessary.
      final Map<String, Object> properties = application.getProperties();
      if (properties != null && !properties.isEmpty()) {
        event.addBean()
          .read(beanManager.createBeanAttributes(beanManager.createAnnotatedType(properties.getClass())))
          .scope(Singleton.class)
          .addQualifiers(bean.getQualifiers()) // TODO: maybe?
          .addQualifier(ApplicationPropertiesLiteral.INSTANCE)
          .createWith(ignored -> properties);
      }
    }
    bean.destroy(application, cc);
    cc.release();
  }

  private static final boolean isProviderClass(final Class<?> cls) {
    return cls != null &&
      (
        ClientRequestFilter.class.isAssignableFrom(cls) ||
        ClientResponseFilter.class.isAssignableFrom(cls) ||
        ContainerRequestFilter.class.isAssignableFrom(cls) ||
        ContainerResponseFilter.class.isAssignableFrom(cls) ||
        ContextResolver.class.isAssignableFrom(cls) ||
        DynamicFeature.class.isAssignableFrom(cls) ||
        Feature.class.isAssignableFrom(cls) ||
        ExceptionMapper.class.isAssignableFrom(cls) ||
        MessageBodyReader.class.isAssignableFrom(cls) ||
        MessageBodyWriter.class.isAssignableFrom(cls) ||
        ParamConverterProvider.class.isAssignableFrom(cls) ||
        ReaderInterceptor.class.isAssignableFrom(cls) ||
        RxInvokerProvider.class.isAssignableFrom(cls) ||
        WriterInterceptor.class.isAssignableFrom(cls)
      );
  }
    
  private final <T> void processResources(@Observes final ProcessSyntheticBean<T> event) {
    if (event != null && (event.getSource() instanceof HelidonJAXRSExtension)) {
      // It's a synthetic bean we added, not one that somebody else added.
      
    }
  }

  private static final class ApplicationPathLiteral extends AnnotationLiteral<ApplicationPath> implements ApplicationPath {

    private static final long serialVersionUID = 1L;

    private final String value;
    
    private ApplicationPathLiteral(String value) {
      super();
      if (value == null) {
        value = "";
      } else {
        value = value.trim();
      }
      this.value = value;
    }
    
    @Override
    public final String value() {
      return this.value;
    }
    
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  private static @interface ApplicationProperties {

  }

  private static final class ApplicationPropertiesLiteral extends AnnotationLiteral<ApplicationProperties> implements ApplicationProperties {

    private static final long serialVersionUID = 1L;

    private static final ApplicationProperties INSTANCE = new ApplicationPropertiesLiteral();

    private ApplicationPropertiesLiteral() {
      super();
    }

  }

  private static final class ProviderLiteral extends AnnotationLiteral<Provider> implements Provider {

    private static final long serialVersionUID = 1L;

    private static final Provider INSTANCE = new ProviderLiteral();

    private ProviderLiteral() {
      super();
    }

  }
  
}
