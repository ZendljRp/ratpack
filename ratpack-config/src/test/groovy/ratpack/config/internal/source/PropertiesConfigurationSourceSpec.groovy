/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.config.internal.source

import groovy.transform.NotYetImplemented
import ratpack.config.internal.DefaultConfigurationDataSpec
import spock.lang.Specification
import spock.lang.Unroll

import static ratpack.server.ServerConfigBuilder.DEFAULT_PROP_PREFIX

class PropertiesConfigurationSourceSpec extends Specification {
  private static final SAMPLE_SYS_PROPS = [("user.name"): "jdoe", ("file.encoding"): "UTF-8", ("user.language"): "en"]
  def mapper = DefaultConfigurationDataSpec.newDefaultObjectMapper()

  @Unroll
  def "supports no prefix (#prefix)"() {
    def source = propsSource(prefix, port: "8080", threads: "10")

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    rootNode.path("port").asText() == "8080"
    rootNode.path("threads").asText() == "10"
    rootNode.size() == 2

    where:
    prefix << [null, ""]
  }

  @Unroll
  def "when prefix provided, only matched elements are included, minus prefix: #prefix"() {
    def source = propsSource(input, prefix)

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    rootNode.path("port").asText() == "8080"
    rootNode.path("threads").asText() == "10"
    rootNode.size() == 2

    where:
    prefix              | input
    DEFAULT_PROP_PREFIX | SAMPLE_SYS_PROPS + [(DEFAULT_PROP_PREFIX + "port"): "8080", (DEFAULT_PROP_PREFIX + "threads"): "10"]
    "app."              | SAMPLE_SYS_PROPS + ["app.port": "8080", "app.threads": "10"]
  }

  def "entries are broken into sub-objects based on dot delimiter"() {
    def source = propsSource("server.port": "8080", "server.threads": "10", "db.jdbcUrl": "jdbc:h2:mem:")

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    rootNode.path("server").path("port").asText() == "8080"
    rootNode.path("server").path("threads").asText() == "10"
    rootNode.path("db").path("jdbcUrl").asText() == "jdbc:h2:mem:"
    rootNode.size() == 2
  }

  @NotYetImplemented
  def "indexed elements are handled as arrays (values)"() {
    def source = propsSource('''
    |users[0]=alice
    |users[1]=bob
    |users[2]=chuck
    '''.stripMargin())

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    def users = rootNode.path("users")
    users.path(0).asText() == "alice"
    users.path(1).asText() == "bob"
    users.path(2).asText() == "chuck"
    rootNode.size() == 1
  }

  @NotYetImplemented
  def "indexed elements are handled as arrays (objects)"() {
    def source = propsSource('''
    |dbs[0].name=test
    |dbs[0].url=jdbc:mysql://test/test
    |dbs[1].name=prod
    |dbs[1].url=jdbc:mysql://prod/prod
    '''.stripMargin())

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    def dbConfigs = rootNode.path("dbConfigs")
    dbConfigs.path(0).path("name").asText() == "test"
    dbConfigs.path(0).path("url").asText() == "jdbc:mysql://test/test"
    dbConfigs.path(1).path("name").asText() == "prod"
    dbConfigs.path(1).path("url").asText() == "jdbc:mysql://prod/prod"
    dbConfigs.size() == 2
    rootNode.size() == 1
  }

  @NotYetImplemented
  def "out of order or interleaved arrays should be indexed properly"() {
    def source = propsSource('''
    |users[1]=bob
    |users[0]=alice
    |users[2]=chuck
    |dbs[0].name=test
    |dbs[1].name=prod
    |dbs[0].url=jdbc:mysql://test/test
    |dbs[1].url=jdbc:mysql://prod/prod
    '''.stripMargin())

    when:
    def rootNode = source.loadConfigurationData(mapper)

    then:
    def users = rootNode.path("users")
    users.path(0).asText() == "alice"
    users.path(1).asText() == "bob"
    users.path(2).asText() == "chuck"
    def dbConfigs = rootNode.path("dbConfigs")
    dbConfigs.path(0).path("name").asText() == "test"
    dbConfigs.path(0).path("url").asText() == "jdbc:mysql://test/test"
    dbConfigs.path(1).path("name").asText() == "prod"
    dbConfigs.path(1).path("url").asText() == "jdbc:mysql://prod/prod"
    dbConfigs.size() == 2
    rootNode.size() == 2
  }

  private static PropertiesConfigurationSource propsSource(String input, String prefix = null) {
    def props = new Properties()
    props.load(new StringReader(input))
    new PropertiesConfigurationSource(prefix, props)
  }

  private static PropertiesConfigurationSource propsSource(Map<String, String> input, String prefix = null) {
    def props = new Properties()
    props.putAll(input)
    new PropertiesConfigurationSource(prefix, props)
  }
}
