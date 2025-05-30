// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.maven.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryEvent;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.maven.server.SpyConstants.NEWLINE;

@Named("Intellij Idea Maven Embedded Event Spy")
@Singleton
public class IntellijMavenSpy extends AbstractEventSpy {
  private EventInfoPrinter myPrinter;

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Override
  public void init(Context context) throws Exception {
    try {
      doInit(context);
    }
    catch (Throwable ignore) {
    }

    if (myPrinter == null) {
      myPrinter = new EventInfoPrinter(s -> System.out.println(s));
      System.err.println("IntellijMavenSpy not initialized, build nodes could be displayed incorrectly");
    }
  }

  private void doInit(Context context)
    throws Throwable {
    myPrinter = new EventInfoPrinter(s -> System.out.println(s));
  }

  @Override
  public void onEvent(Object event) {
    try {
      if (event instanceof ExecutionEvent) {
        onExecutionEvent((ExecutionEvent)event);
      }
      else if (event instanceof RepositoryEvent) {
        onRepositoryEvent((RepositoryEvent)event);
      }
      else if (event instanceof DependencyResolutionRequest) {
        onDependencyResolutionRequest((DependencyResolutionRequest)event);
      }
      else if (event instanceof DependencyResolutionResult) {
        onDependencyResolutionResult((DependencyResolutionResult)event);
      }
    }
    catch (Throwable e) {
      collectAndPrintLastLinesForEA(e);
    }
  }

  private void onDependencyResolutionRequest(DependencyResolutionRequest event) {
    String projectId = event.getMavenProject() == null ? "unknown" : event.getMavenProject().getId();
    myPrinter.printMavenEventInfo("DependencyResolutionRequest", "id", projectId);
  }

  private void onDependencyResolutionResult(DependencyResolutionResult event) {
    List<Exception> errors = event.getCollectionErrors();
    StringBuilder result = new StringBuilder();
    for (Exception e : errors) {
      if (result.length() > 0) {
        result.append(NEWLINE);
      }
      result.append(e.getMessage());
    }
    myPrinter.printMavenEventInfo("DependencyResolutionResult", "error", result);
  }

  private void collectAndPrintLastLinesForEA(Throwable e) {
    //need to collect last 3 lines to send to EA
    int lines = Math.max(e.getStackTrace().length, 3);
    StringBuilder builder = new StringBuilder();
    builder.append(e.getMessage());
    for (int i = 0; i < lines; i++) {
      builder.append(e.getStackTrace()[i]).append("\n");
    }
    myPrinter.printMavenEventInfo("INTERR", "error", builder);
  }

  private void onRepositoryEvent(RepositoryEvent event) {
    RepositoryEvent.EventType type = event.getType();

    // optimization to prevent unnecessary CPU and memory pressure on many irrelevant events
    //   only these events are currently used (see MavenEventType)
    if (type != RepositoryEvent.EventType.ARTIFACT_DOWNLOADING && type != RepositoryEvent.EventType.ARTIFACT_RESOLVING) return;

    String errMessage = event.getException() == null ? "" : event.getException().getMessage();
    String path = event.getFile() == null ? "" : event.getFile().getPath();
    String artifactCoord = event.getArtifact() == null ? "" : event.getArtifact().toString();
    myPrinter.printMavenEventInfo(type, "path", path, "artifactCoord", artifactCoord, "error", errMessage);
  }

  private void onExecutionEvent(ExecutionEvent event) {
    MojoExecution mojoExec = event.getMojoExecution();
    String projectId = event.getProject() == null ? "unknown" : event.getProject().getId();
    if (mojoExec != null) {
      String errMessage = event.getException() == null ? "" : getErrorMessage(event.getException());
      myPrinter.printMavenEventInfo(event.getType(), "source", String.valueOf(mojoExec.getSource()), "goal", mojoExec.getGoal(), "id",
                                    projectId,
                          "error",
                          errMessage);
    }
    else {
      if (event.getType() == ExecutionEvent.Type.SessionStarted) {
        printSessionStartedEventAndReactorData(event, projectId);
      }
      else {
        myPrinter.printMavenEventInfo(event.getType(), "id", projectId);
      }
    }
  }

  private static String getErrorMessage(Exception exception) {
    String baseMessage = exception.getMessage();
    Throwable rootCause = ExceptionUtils.getRootCause(exception);
    String rootMessage = rootCause != null ? rootCause.getMessage() : StringUtils.EMPTY;
    return StringUtils.isNotEmpty(rootMessage) ? rootMessage : baseMessage;
  }

  private void printSessionStartedEventAndReactorData(ExecutionEvent event, String projectId) {
    MavenSession session = event.getSession();
    if (session != null) {
      List<MavenProject> projectsInReactor = session.getProjects();
      if (projectsInReactor == null) {
        projectsInReactor = new ArrayList<>();
      }
      StringBuilder builder = new StringBuilder();
      for (MavenProject project : projectsInReactor) {
        builder
          .append(project.getGroupId()).append(":")
          .append(project.getArtifactId()).append(":")
          .append(project.getVersion()).append("&&");
      }
      myPrinter.printMavenEventInfo(ExecutionEvent.Type.SessionStarted, "id", projectId, "projects", builder);
    }
    else {
      myPrinter.printMavenEventInfo(ExecutionEvent.Type.SessionStarted, "id", projectId, "projects", "");
    }
  }
}
