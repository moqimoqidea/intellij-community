// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.transport;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.AbstractCommand;
import com.jetbrains.python.debugger.pydev.ClientModeMultiProcessDebugger;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link DebuggerTransport} implementation that expects a debugging script to behave as a server. The main process of the debugging script
 * and all of the Python processes forked and created within it receives incoming connections on the <b>same port</b> using
 * {@code SO_REUSEPORT} server socket option on Linux (available since 3.9 core version), Mac OS, BSD platforms and {@code SO_REUSEADDR}
 * server socket option on Windows platforms (see {@code start_server(port)} method in <i>pydevd_comm.py</i>).
 * <p>
 * Each Python process within the debugging script requires <b>single connection</b> from the IDE. When a new Python process is created the
 * originator process sends {@link AbstractCommand#PROCESS_CREATED} message to the IDE. The new process binds server socket to the same
 * address and port as the originator process and starts listening for an incoming connection. The IDE tries to establish a new
 * connection with the script.
 * <p>
 * At the last point the following problem could arise. When several processes are created almost simultaneously and they become
 * bound to the single port the IDE could establish the connection to some of the processes twice or more times. The first connection is
 * accepted by the Python process but the others are not. Other connections would stay in <i>completed connection queue</i> until a timeout
 * for a response for the {@link AbstractCommand#RUN} arouse. To solve this problem {@link ClientModeMultiProcessDebugger} creates the pool
 * of connecting {@link RemoteDebugger}. Some of the debuggers will succeed connecting to the debugging process and later some of them will
 * succeed in handshaking. Connections of other would eventually die out on the timeout waiting for the connection or waiting for the
 * debugger script handshake response.
 *
 * @author Alexander Koshevoy
 * @see ClientModeMultiProcessDebugger
 */
public class ClientModeDebuggerTransport extends BaseDebuggerTransport {
  private static final Logger LOG = Logger.getInstance(ClientModeDebuggerTransport.class);

  /**
   * Connection timeout to the debugger script.
   */
  private static final int CONNECTION_TIMEOUT_IN_MILLIS = 5000;

  /**
   * Delay is long enough to connect to a Python script on a slow remote
   * machine with the slow connection.
   */
  private static final int HANDSHAKE_TIMEOUT_IN_MILLIS = 5000;

  private final @NotNull String myHost;
  private final int myPort;
  private final Duration myRetryTimeout;
  private final int myMaxRetries;

  private volatile @NotNull State myState = State.INIT;

  private @Nullable Socket mySocket;
  private volatile @Nullable DebuggerReader myDebuggerReader;

  public ClientModeDebuggerTransport(@NotNull RemoteDebugger debugger,
                                     @NotNull String host,
                                     int port,
                                     Duration retryTimeout,
                                     int maxRetries) {
    super(debugger);
    myHost = host;
    myPort = port;
    myRetryTimeout = retryTimeout;
    myMaxRetries = maxRetries;
  }

  @Override
  public void waitForConnect() throws IOException {
    if (myState != State.INIT) {
      throw new IllegalStateException(
        "Inappropriate state of Python debugger for connecting to Python debugger: " + myState + "; " + State.INIT + " is expected");
    }

    /*
    The server socket on the debugger side might not be open yet, so we need to retry the connection multiple times.
    Ideally, the debugger would print a message to stdout when the socket is ready, and we would connect after that.
    However, capturing stdout from the process at this point is not straightforward. Hence, we use this retry workaround.
    */
    var attempts = 0;
    boolean connected = false;
    do {
      try {
        Socket clientSocket = new Socket();
        clientSocket.setSoTimeout(HANDSHAKE_TIMEOUT_IN_MILLIS);
        clientSocket.connect(new InetSocketAddress(myHost, myPort), CONNECTION_TIMEOUT_IN_MILLIS);

        synchronized (mySocketObject) {
          mySocket = clientSocket;
          myState = State.CONNECTED;
        }

        DebuggerReader debuggerReader;
        try {
          myDebuggerReader = debuggerReader = new DebuggerReader(myDebugger, clientSocket.getInputStream());
        }
        catch (IOException e) {
          throw new IOException("Failed to create debugger reader", e);
        }

        try {
          myDebugger.handshake();

          debuggerReader.connectionApproved();
          connected = true;

          // After a successful connection turn back original timeout.
          clientSocket.setSoTimeout(0);
        }
        catch (PyDebuggerException e) {
          LOG.warn(String.format("[%d] Handshake failed", hashCode()));
        }
        finally {
          if (!connected) {
            debuggerReader.close();
          }
        }
      }
      catch (IOException e) {
        attempts++;
        try {
          Thread.sleep(myRetryTimeout.toMillis());
        }
        catch (InterruptedException ex) {
          LOG.warn("Connection to the debugger thread is interrupted during the retry delay", e);
          Thread.currentThread().interrupt();
        }
      }
    } while (attempts < myMaxRetries && !connected);

    if (!connected) {
      myState = State.DISCONNECTED;
      throw new IOException("Failed to connect to debugger script");
    }

    myState = State.APPROVED;
    LOG.debug(String.format("[%d] Connected to debugger script", hashCode()));
  }

  @Override
  protected boolean sendMessageImpl(byte[] packed) throws IOException {
    synchronized (mySocketObject) {
      if (mySocket == null || mySocket.isClosed()) {
        return false;
      }
      final OutputStream os = mySocket.getOutputStream();
      os.write(packed);
      os.flush();
      return true;
    }
  }

  @Override
  protected void onSocketException() {
    myDebugger.disconnect();
    if (myState == State.APPROVED) {
      myDebugger.fireCommunicationError();
    }
  }

  @Override
  public void close() {
    try {
      DebuggerReader debuggerReader = myDebuggerReader;
      if (debuggerReader != null) {
        debuggerReader.stop();
      }
    }
    finally {
      synchronized (mySocketObject) {
        if (mySocket != null) {
          try {
            mySocket.close();
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public boolean isConnecting() {
    return myState == State.CONNECTED;
  }

  @Override
  public boolean isConnected() {
    return myState == State.APPROVED;
  }

  @Override
  public void disconnect() {
    // TODO disconnect?
  }

  private enum State {
    /**
     * Before calling {@link #waitForConnect()}
     */
    INIT,
    /**
     * Socket connection to the debugger host:port address established and no messages has been received from the debugging script yet.
     * The connection might be ephemeral at this point (see {@link ClientModeDebuggerTransport}).
     */
    CONNECTED,
    /**
     * Socket connection to the debugger host:port address established and at least one message has been received from the debugging script.
     * This state means that a script is on the other end had accepted the connection.
     */
    APPROVED,
    /**
     * Debugger disconnected
     */
    DISCONNECTED
  }

  public static class DebuggerReader extends BaseDebuggerReader {
    /**
     * Indicates that the debugger connection has been approved within this {@link DebuggerReader}.
     */
    private final AtomicBoolean myConnectionApproved = new AtomicBoolean(false);

    public DebuggerReader(@NotNull RemoteDebugger debugger, @NotNull InputStream stream) {
      super(stream, StandardCharsets.UTF_8, debugger); //TODO: correct encoding?
      start(getClass().getName());
    }

    @Override
    protected void onExit() {
      if (myConnectionApproved.get()) {
        getDebugger().fireExitEvent();
      }
    }

    @Override
    protected void onCommunicationError() {
      if (myConnectionApproved.get()) {
        getDebugger().fireCommunicationError();
      }
    }

    public void connectionApproved() {
      myConnectionApproved.set(true);
    }
  }
}
