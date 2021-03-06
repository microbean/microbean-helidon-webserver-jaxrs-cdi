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
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ExecutionException;

import javax.annotation.Priority;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;

import javax.enterprise.context.spi.CreationalContext;

import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionTargetFactory;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import javax.enterprise.inject.spi.ProcessSyntheticBean;

import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;

import javax.enterprise.event.Observes;

import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;

import javax.inject.Qualifier;
import javax.inject.Singleton;

import javax.interceptor.Interceptor;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.PathParam;

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

import io.helidon.webserver.ServerRequest;

import org.microbean.helidon.webserver.cdi.HelidonWebServerExtension;

public class HelidonJAXRSExtension implements Extension {

  private static final ApplicationPath DEFAULT_APPLICATION_PATH = new ApplicationPathLiteral("");

  public HelidonJAXRSExtension() {
    super();
  }

  private final void makeCertainJaxRsAnnotationsQualifiers(@Observes final BeforeBeanDiscovery event) {
    event.addQualifier(PathParam.class); // TODO: and so on
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

  private final void registerClassesAndSingletons(@Observes @Priority(Interceptor.Priority.APPLICATION) final AfterBeanDiscovery event, final BeanManager beanManager)
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
        applicationPathString = ResourceMethodDescriptor.pattern.matcher(applicationPathString).replaceAll("/$2");
      }

      final Set<Annotation> qualifiers = bean.getQualifiers();
      beanManager.getExtension(HelidonWebServerExtension.class).addQualifiers(qualifiers);
      
      event.addBean()
        .types(ApplicationPath.class)
        .qualifiers(qualifiers)
        .scope(Singleton.class)
        .createWith(ignored -> applicationPath);
      
      final Set<Class<?>> applicationClasses = application.getClasses();
      if (applicationClasses != null && !applicationClasses.isEmpty()) {
        for (final Class<?> resourceOrProviderClass : applicationClasses) {
          if (resourceOrProviderClass != null) {
            @SuppressWarnings("unchecked")
            final AnnotatedType<U> resourceOrProviderAnnotatedType = beanManager.createAnnotatedType((Class<U>)resourceOrProviderClass);
            assert resourceOrProviderAnnotatedType != null;

            final BeanConfigurator<U> bc = event.addBean();
            assert bc != null;
            bc.read(resourceOrProviderAnnotatedType)
              .addQualifiers(qualifiers);
            
            if (isProviderClass(resourceOrProviderClass)) {
              // TODO: Features aren't supported, nor is it clear they should be
              bc.addQualifier(ProviderLiteral.INSTANCE);

            } else {
              final Set<? extends ResourceMethodDescriptor<U>> resourceMethodDescriptors =
                ResourceClasses.getResourceMethodDescriptors(beanManager, applicationPathString, resourceOrProviderAnnotatedType, qualifiers);
              if (resourceMethodDescriptors != null && !resourceMethodDescriptors.isEmpty()) {
                bc.addQualifier(new ResourceClass.Literal(resourceOrProviderClass));
                
                for (final ResourceMethodDescriptor<U> descriptor : resourceMethodDescriptors) {
                  if (descriptor != null) {

                    final AnnotatedMethod<? super U> resourceMethod = descriptor.getResourceMethod();
                    assert resourceMethod != null;

                    final List<? extends AnnotatedParameter<?>> parameters = resourceMethod.getParameters();
                    if (parameters != null && !parameters.isEmpty()) {
                      for (final AnnotatedParameter<?> parameter : parameters) {
                        assert parameter != null;
                        final Type baseType = parameter.getBaseType();
                        assert baseType != null;
                        final Collection<? extends Annotation> parameterAnnotations = parameter.getAnnotations();
                        if (parameterAnnotations == null || parameterAnnotations.isEmpty()) {
                          // Entity parameter.
                          if (baseType instanceof Class) {
                            // Helidon doesn't support generics here :-(
                            final Class<?> entityType = (Class<?>)baseType;
                            event.addBean()
                              .addTransitiveTypeClosure(baseType)
                              .addQualifiers(qualifiers)
                              .addQualifiers(Entity.Literal.INSTANCE)
                              .scope(RequestScoped.class)
                              .produceWith(instance -> {
                                  final ServerRequest request = instance.select(ServerRequest.class, qualifiers.toArray(new Annotation[qualifiers.size()])).get();
                                  try {
                                    // TODO: there's probably a better
                                    // non-blocking way to do this in
                                    // conjunction with
                                    // RequestMethodHandler....
                                    return request.content().as(entityType).toCompletableFuture().get();
                                  } catch (final InterruptedException interruptedException) {
                                    Thread.currentThread().interrupt();
                                    throw new CreationException(interruptedException.getMessage(), interruptedException);
                                  } catch (final ExecutionException executionException) {
                                    throw new CreationException(executionException.getMessage(), executionException.getCause());
                                  }
                                });
                          }

                        } else {
                          for (final Annotation parameterAnnotation : parameterAnnotations) {
                            if (parameterAnnotation != null && beanManager.isQualifier(parameterAnnotation.annotationType())) {
                              // Parameter with qualifiers (e.g. @PathParam, @QueryParam, etc)
                              // TODO: add bean for it
                            }
                          }
                        }
                      }
                    }
                    
                    event.addBean()
                      .addTransitiveTypeClosure(descriptor.getClass())
                      .qualifiers(qualifiers)
                      .scope(ApplicationScoped.class)
                      .createWith(ignored -> descriptor);

                    // Create a Handler bean that wraps the JAX-RSish
                    // resource method.
                    event.addBean()
                      .addTransitiveTypeClosure(new TypeLiteral<ResourceMethodHandler<U>>() {
                          private static final long serialVersionUID = 1L;
                        }.getType())
                      .qualifiers(qualifiers)
                      .scope(Dependent.class) // TODO: don't like it
                      .createWith(ignored -> {
                          try {
                            return new ResourceMethodHandler<>(beanManager, descriptor);
                          } catch (final ReflectiveOperationException reflectiveOperationException) {
                            throw new CreationException(reflectiveOperationException.getMessage(),
                                                        reflectiveOperationException);
                          }
                        });
                    
                  }
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
              .addQualifiers(qualifiers) // TODO: maybe?
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
          .addQualifiers(qualifiers) // TODO: maybe?
          .addQualifier(ApplicationPropertiesLiteral.INSTANCE)
          .createWith(ignored -> properties);
      }

      // OK, we've added beans for all the resource and provider
      // classes and singletons and properties.  Now add one for the
      // relevant Service.
      final AnnotatedType<ResourceClassService> resourceClassServiceAnnotatedType = beanManager.createAnnotatedType(ResourceClassService.class);

      final InjectionTargetFactory<ResourceClassService> itf = beanManager.getInjectionTargetFactory(resourceClassServiceAnnotatedType);
      assert itf != null;

      // Add the right qualifiers to the @Inject-annotated update() method.
      final AnnotatedMethodConfigurator<? super ResourceClassService> amc = itf.configure()
        .filterMethods(am -> am.getJavaMember().getName().equals("update"))
        .findFirst()
        .get();
      AnnotatedParameterConfigurator<? super ResourceClassService> apc = amc.filterParams(ap -> ap != null)
        .findFirst()
        .get();
      for (final Annotation qualifier : qualifiers) {
        apc.add(qualifier);
      }

      // Add the right qualifiers to the @Inject-annotated constructor.
      apc = itf.configure()
        .constructors()
        .stream()
        .findFirst()
        .get()
        .params()
        .get(0);
        for (final Annotation qualifier : qualifiers) {
          apc.add(qualifier);
        }

      final BeanAttributes<ResourceClassService> beanAttributes = beanManager.createBeanAttributes(resourceClassServiceAnnotatedType);      
      final BeanAttributes<ResourceClassService> resourceClassServiceBeanAttributes = new DelegatingBeanAttributes<ResourceClassService>(beanAttributes) {

          @Override
          public final Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
          }

          @Override
          public final Set<Annotation> getQualifiers() {
            return qualifiers;
          }
          
        };

      final Bean<ResourceClassService> resourceClassServiceBean = beanManager.createBean(resourceClassServiceBeanAttributes, resourceClassServiceAnnotatedType.getJavaClass(), itf);
      event.addBean(resourceClassServiceBean);

      // Add a producer for Content#as()
      
      
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

  private static class DelegatingBeanAttributes<T> implements BeanAttributes<T> {

    private final BeanAttributes<T> delegate;
    
    private DelegatingBeanAttributes(final BeanAttributes<T> delegate) {
      super();
      this.delegate = Objects.requireNonNull(delegate);
    }
    
    @Override
    public Set<Type> getTypes() {
      return this.delegate.getTypes();
    }

    @Override
    public Set<Annotation> getQualifiers() {
      return this.delegate.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
      return this.delegate.getScope();
    }

    @Override
    public String getName() {
      return this.delegate.getName();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
      return this.delegate.getStereotypes();
    }

    @Override
    public boolean isAlternative() {
      return this.delegate.isAlternative();
    }

  }

}
