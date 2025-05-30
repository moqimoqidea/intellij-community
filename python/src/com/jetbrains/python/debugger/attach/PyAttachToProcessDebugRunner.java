// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.debugger.PyRemoteDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;

import static com.intellij.openapi.options.advanced.AdvancedSettings.getInt;

public class PyAttachToProcessDebugRunner extends PyDebugRunner {
  private final Project myProject;
  private final int myPid;
  private final String mySdkPath;
  private final Sdk mySdk;


  public PyAttachToProcessDebugRunner(@NotNull Project project, int pid, @Nullable Sdk sdk) {
    myProject = project;
    myPid = pid;
    mySdk = sdk;
    if (mySdk != null) {
      mySdkPath = sdk.getHomePath();
    }
    else {
      mySdkPath = null;
    }
  }

  public XDebugSession launch() throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    return launchRemoteDebugServer();
  }

  private XDebugSession launchRemoteDebugServer() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException(PyBundle.message("debugger.attach.to.process.failed.to.find.free.socket.port"), e);
    }


    PyAttachToProcessCommandLineState state = PyAttachToProcessCommandLineState.create(myProject, mySdk, serverSocket.getLocalPort(), myPid);

    final ExecutionResult result = state.execute(state.getEnvironment().getExecutor(), this);

    //start remote debug server
    return XDebuggerManager.getInstance(myProject).
      startSessionAndShowTab(String.valueOf(myPid), null, new XDebugProcessStarter() {
        @Override
        public @NotNull XDebugProcess start(final @NotNull XDebugSession session) {
          PyRemoteDebugProcess pyDebugProcess =
            new PyRemoteDebugProcess(session, serverSocket, result.getExecutionConsole(),
                                     result.getProcessHandler(), "") {
              @Override
              protected void printConsoleInfo() {
              }

              @Override
              public int getConnectTimeout() {
                return getInt("python.debugger.attach.timeout");
              }

              @Override
              protected void detachDebuggedProcess() {
                handleStop();
              }

              @Override
              protected String getConnectionMessage() {
                return PyBundle.message("python.debugger.attaching.to.process.with.pid", myPid);
              }

              @Override
              protected String getConnectionTitle() {
                return PyBundle.message("python.debugger.attaching");
              }
            };
          pyDebugProcess.setPositionConverter(new PyLocalPositionConverter());


          createConsoleCommunicationAndSetupActions(myProject, result, pyDebugProcess, session);

          return pyDebugProcess;
        }
      });
  }
}
