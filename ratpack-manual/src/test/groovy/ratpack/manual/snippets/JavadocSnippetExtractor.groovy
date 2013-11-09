/*
 * Copyright 2013 the original author or authors.
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

package ratpack.manual.snippets

import org.apache.commons.lang3.StringEscapeUtils
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import ratpack.manual.snippets.fixtures.SnippetFixture
import ratpack.util.Transformer

import java.util.regex.MatchResult
import java.util.regex.Pattern

class JavadocSnippetExtractor {

  private static Pattern javadocPattern =
    Pattern.compile(/(?ims)\/\*\*.*?\*\//)

  static List<TestCodeSnippet> extract(File root, String include, String cssClass, SnippetFixture fixture) {
    List<TestCodeSnippet> snippets = []

    def snippetTagPattern = Pattern.compile(/(?ims)<([a-z]+)\s+class\s*=\s*['"]$cssClass['"]\s*>.*?<\s*\/\s*\1>/)
    def filenames = new FileNameFinder().getFileNames(root.absolutePath, include)

    filenames.each { filename ->
      def file = new File(filename)
      addSnippets(snippets, file, snippetTagPattern, fixture)
    }

    snippets
  }

  private static void addSnippets(List<TestCodeSnippet> snippets, File file, Pattern snippetTagPattern, SnippetFixture snippetFixture) {
    def source = file.text
    String testName = calculateBaseName(file, source)
    Map<Integer, String> snippetsByLine = findSnippetsByLine(source, snippetTagPattern)

    snippetsByLine.each { lineNumber, snippetBlocks ->
      snippetBlocks.each { snippet ->
        snippets << createSnippet(testName, file, lineNumber, snippet, snippetFixture)
      }
    }
  }

  private static String calculateBaseName(File file, String source) {
    MatchResult packageNameMatch = source =~ "package ([^ ;]+)"
    packageNameMatch[0][1] + "." + (file.name[0..file.name.lastIndexOf(".") - 1])
  }

  private static List<String> findSnippetTags(String code, Pattern snippetTagPattern) {
    List<String> tags = []

    code.eachMatch(javadocPattern) { String javadoc ->
      tags.addAll(javadoc.findAll(snippetTagPattern))
    }

    tags
  }

  private static Map<Integer, String> findSnippetsByLine(String source, Pattern snippetTagPattern) {
    List<String> snippetTags = findSnippetTags(source, snippetTagPattern)
    Map lineNumberToAssertions = [:]

    int codeIndex = 0
    snippetTags.each { tag ->
      codeIndex = source.indexOf(tag, codeIndex)
      int lineNumber = source.substring(0, codeIndex).findAll("(?m)^").size()
      codeIndex += tag.size()

      String assertion = extractSnippetFromTag(tag)

      lineNumberToAssertions.get(lineNumber, []) << assertion
    }

    lineNumberToAssertions
  }

  private static String extractSnippetFromTag(String tag) {
    String tagInner = tag.substring(tag.indexOf(">") + 1, tag.lastIndexOf("<"))
    String html = tagInner.replaceAll("(?m)^\\s*\\*", "")
    String deliteral = html.replaceAll("\\{@literal (.+?)}", '$1')
    def snippet = StringEscapeUtils.unescapeHtml4(deliteral)
    snippet
  }

  private static TestCodeSnippet createSnippet(String sourceClassName, File sourceFile, int lineNumber, String snippet, SnippetFixture fixture) {
    new TestCodeSnippet(snippet, sourceClassName, sourceClassName + ":$lineNumber", fixture, new Transformer<Throwable, Throwable>() {
      @Override
      Throwable transform(Throwable t) {
        def errorLine = 0

        if (t instanceof MultipleCompilationErrorsException) {
          def compilationException = t as MultipleCompilationErrorsException
          def error = compilationException.errorCollector.getError(0)
          if (error instanceof SyntaxErrorMessage) {
            errorLine = error.cause.line
          }
        } else {
          def frame = t.getStackTrace().find { it.fileName == sourceClassName }
          if (frame) {
            errorLine = frame.lineNumber
          }
        }
        errorLine = errorLine - fixture.pre().split("\n").size()
        StackTraceElement[] stack = t.getStackTrace()
        List<StackTraceElement> newStack = new ArrayList<StackTraceElement>(stack.length + 1)
        newStack.add(new StackTraceElement(sourceClassName, "javadoc", sourceFile.name, lineNumber + errorLine))
        newStack.addAll(stack)
        t.setStackTrace(newStack as StackTraceElement[])

        t
      }
    })

  }

}