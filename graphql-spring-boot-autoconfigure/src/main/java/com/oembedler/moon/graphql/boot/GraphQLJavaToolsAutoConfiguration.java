package com.oembedler.moon.graphql.boot;

import com.coxautodev.graphql.tools.GraphQLResolver;
import com.coxautodev.graphql.tools.PerFieldObjectMapperProvider;
import com.coxautodev.graphql.tools.SchemaParser;
import com.coxautodev.graphql.tools.SchemaParserBuilder;
import com.coxautodev.graphql.tools.SchemaParserDictionary;
import com.coxautodev.graphql.tools.SchemaParserOptions;
import com.coxautodev.graphql.tools.TypeDefinitionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.servlet.config.GraphQLSchemaProvider;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Andrew Potter
 */
@Configuration
@ConditionalOnClass(SchemaParser.class)
@AutoConfigureAfter({JacksonAutoConfiguration.class})
@EnableConfigurationProperties(GraphQLToolsProperties.class)
public class GraphQLJavaToolsAutoConfiguration {

  @Autowired(required = false)
  private SchemaParserDictionary dictionary;

  @Autowired(required = false)
  private GraphQLScalarType[] scalars;

  @Autowired(required = false)
  private List<SchemaDirective> directives;

  @Autowired(required = false)
  private List<SchemaDirectiveWiring> directiveWirings;

  @Autowired(required = false)
  private List<TypeDefinitionFactory> typeDefinitionFactories;

  @Autowired
  private GraphQLToolsProperties props;

  @Bean
  @ConditionalOnMissingBean
  public SchemaStringProvider schemaStringProvider() {
    return new ClasspathResourceSchemaStringProvider(props.getSchemaLocationPattern());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConfigurationProperties("graphql.tools.schema-parser-options")
  public SchemaParserOptions.Builder optionsBuilder(
      PerFieldObjectMapperProvider perFieldObjectMapperProvider
  ) {
    SchemaParserOptions.Builder optionsBuilder = SchemaParserOptions.newOptions();

    if (perFieldObjectMapperProvider != null) {
      optionsBuilder.objectMapperProvider(perFieldObjectMapperProvider);
    }
    optionsBuilder.introspectionEnabled(props.isIntrospectionEnabled());

    if (typeDefinitionFactories != null) {
      typeDefinitionFactories.forEach(optionsBuilder::typeDefinitionFactory);
    }

    return optionsBuilder;
  }

  @Bean
  @ConditionalOnBean({GraphQLResolver.class})
  @ConditionalOnMissingBean
  public SchemaParser schemaParser(
      List<GraphQLResolver<?>> resolvers,
      SchemaStringProvider schemaStringProvider,
      SchemaParserOptions.Builder optionsBuilder
  ) throws IOException {
    SchemaParserBuilder builder = dictionary != null ? new SchemaParserBuilder(dictionary) : new SchemaParserBuilder();

    List<String> schemaStrings = schemaStringProvider.schemaStrings();
    schemaStrings.forEach(builder::schemaString);

    if (scalars != null) {
      builder.scalars(scalars);
    }

    builder.options(optionsBuilder.build());

    if (directives != null) {
      directives.forEach(it -> builder.directive(it.getName(), it.getDirective()));
    }

    if (directiveWirings != null) {
      directiveWirings.forEach(builder::directiveWiring);
    }

    return builder
        .resolvers(resolvers)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(value = "graphql.tools.use-default-objectmapper", havingValue = "true", matchIfMissing = true)
  public PerFieldObjectMapperProvider perFieldObjectMapperProvider(ObjectMapper objectMapper) {
    objectMapper
        .registerModule(new Jdk8Module())
        .registerModule(new KotlinModule());
    return fieldDefinition -> objectMapper;
  }

  @Bean
  @ConditionalOnBean(SchemaParser.class)
  @ConditionalOnMissingBean({GraphQLSchema.class, GraphQLSchemaProvider.class})
  public GraphQLSchema graphQLSchema(SchemaParser schemaParser) {
    return schemaParser.makeExecutableSchema();
  }
}
