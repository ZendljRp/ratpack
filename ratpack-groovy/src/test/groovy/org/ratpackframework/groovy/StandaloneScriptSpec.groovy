package org.ratpackframework.groovy

import com.google.common.util.concurrent.AbstractIdleService
import org.ratpackframework.bootstrap.RatpackServer
import org.ratpackframework.bootstrap.internal.RatpackService
import org.ratpackframework.bootstrap.internal.ServiceBackedServer
import org.ratpackframework.groovy.internal.StandaloneScriptBacking
import org.ratpackframework.test.groovy.RatpackGroovyScriptAppSpec

class StandaloneScriptSpec extends RatpackGroovyScriptAppSpec {

  class ScriptBackedService extends AbstractIdleService implements RatpackService {
    RatpackServer server

    @Override
    protected void startUp() throws Exception {
      def shell = new GroovyShell(getClass().classLoader)
      def script = shell.parse(StandaloneScriptSpec.this.ratpackFile)

      StandaloneScriptBacking.captureNext(Closures.action {
        server = it
      })

      Thread.start {
        script.run()
      }

      def stopAt = System.currentTimeMillis() + 10000
      while (System.currentTimeMillis() < stopAt) {
        if (server != null) {
          break
        }
        sleep 100
      }

      if (!server) {
        throw new IllegalStateException("Server did not start")
      }

      server.startAndWait()
    }

    @Override
    protected void shutDown() throws Exception {
      server?.stopAndWait()
    }

    @Override
    int getBindPort() {
      server.bindPort
    }

    @Override
    String getBindHost() {
      server.bindHost
    }
  }

  @Override
  RatpackServer createServer() {
    new ServiceBackedServer(new ScriptBackedService())
  }

  def "can execute plain script and reload"() {
    when:
    app {
      script """
        System.setProperty("ratpack.port", "0")

        ratpack {
          handlers {
            get("") {
              response.send "foo"
            }
          }
        }
      """
    }

    then:
    getText() == "foo"

    when:
    script """
      ratpack {
        handlers {
          get("") {
            response.send "bar"
          }
        }
      }
    """

    then:
    getText() == "bar"
  }
}