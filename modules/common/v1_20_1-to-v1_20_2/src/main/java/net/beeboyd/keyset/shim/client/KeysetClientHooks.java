package net.beeboyd.keyset.shim.client;

import java.util.function.Predicate;

/** Shared client polling and deferred screen-open helpers. */
public final class KeysetClientHooks<C, S> {
  private S pendingParentScreen;
  private boolean openScreenRequested;

  public interface PressSource {
    boolean pollPress();
  }

  public interface ThrowingRunnable {
    void run() throws Exception;
  }

  public interface FailureHandler {
    void onFailure(Exception exception);
  }

  public interface ScreenGetter<C, S> {
    S getCurrentScreen(C client);
  }

  public interface ScreenSetter<C, S> {
    void setScreen(C client, S screen);
  }

  public interface ScreenFactory<S> {
    S create(S parentScreen);
  }

  public static void consumeAllPresses(PressSource pressSource, Runnable action) {
    if (pressSource == null || action == null) {
      return;
    }
    while (pressSource.pollPress()) {
      action.run();
    }
  }

  public static void consumeAllPresses(
      PressSource pressSource, ThrowingRunnable action, FailureHandler failureHandler) {
    if (pressSource == null || action == null) {
      return;
    }
    while (pressSource.pollPress()) {
      try {
        action.run();
      } catch (Exception exception) {
        if (failureHandler != null) {
          failureHandler.onFailure(exception);
        }
      }
    }
  }

  public void requestOpenScreen(S parentScreen, Predicate<S> isKeysetScreen) {
    if (isKeysetScreen != null && isKeysetScreen.test(parentScreen)) {
      return;
    }
    pendingParentScreen = parentScreen;
    openScreenRequested = true;
  }

  public void flushPendingOpen(
      C client,
      ScreenGetter<C, S> screenGetter,
      ScreenSetter<C, S> screenSetter,
      ScreenFactory<S> screenFactory,
      Predicate<S> isKeysetScreen) {
    if (!openScreenRequested
        || client == null
        || screenGetter == null
        || screenSetter == null
        || screenFactory == null) {
      return;
    }

    openScreenRequested = false;
    S currentScreen = screenGetter.getCurrentScreen(client);
    S parentScreen = pendingParentScreen;
    pendingParentScreen = null;
    if (isKeysetScreen != null
        && (isKeysetScreen.test(currentScreen) || isKeysetScreen.test(parentScreen))) {
      return;
    }
    screenSetter.setScreen(
        client, screenFactory.create(parentScreen != null ? parentScreen : currentScreen));
  }
}
