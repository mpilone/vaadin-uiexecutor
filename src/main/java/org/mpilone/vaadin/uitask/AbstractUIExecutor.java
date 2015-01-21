package org.mpilone.vaadin.uitask;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.vaadin.ui.UI;

/**
 * A UI executor that delegates the UI lookup to subclasses. UIRunnables
 * submitted will be executed via a supplied {@link Executor} while access
 * methods all delegate directly to the current UI.
 *
 * @author mpilone
 * @deprecated Use the {@link StrategyUIExecutor} instead
 */
@Deprecated
public abstract class AbstractUIExecutor implements UIExecutor {

  private Executor executor;

  /*
   * (non-Javadoc)
   * 
   * @see org.prss.contentdepot.vaadin.uitask.UITaskExecutor#execute(org.prss.
   * contentdepot.vaadin.uitask.UITask)
   */
  @Override
  public Future<Void> executeAndAccess(final UIRunnable runnable) {
    final UI ui = getUI();
    UIRunnableFuture runnableFuture = new UIRunnableFuture(runnable, ui);

    prepareForExecution(ui, runnableFuture);

    getOrCreateExecutor().execute(runnableFuture);

    return runnableFuture;
  }

  /**
   * Prepares the given runnable future for execution on the given UI.
   *
   * @param ui the current UI
   * @param runnableFuture the runnable future about to be executed
   */
  abstract protected void prepareForExecution(UI ui,
      UIRunnableFuture runnableFuture);

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.uitask.UIExecutor#access(java.lang.Runnable)
   */
  @Override
  public Future<Void> access(Runnable runnable) {
    return getUI().access(runnable);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.prss.contentdepot.vaadin.uitask.UIExecutor#accessSynchronously(java
   * .lang.Runnable)
   */
  @Override
  public void accessSynchronously(Runnable runnable) {
    getUI().accessSynchronously(runnable);
  }

  /**
   * Returns the current executor or creates a new one if one has not been
   * defined.
   *
   * @return the executor
   */
  protected Executor getOrCreateExecutor() {
    if (executor == null) {
      executor = Executors.newCachedThreadPool();
    }

    return executor;
  }

  /**
   * Sets the executor to use for all {@link UIRunnable} execution.
   *
   * @param executor the executor to use for all {@link UIRunnable}s
   */
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }

  /**
   * Returns the UI that should be accessed (and therefore locked) via
   * {@link UI#access(java.lang.Runnable)} or {@link UI#accessSynchronously(java.lang.Runnable)
   * } when tasks are * submitted.
   *
   * @return the UI to access
   */
  protected abstract UI getUI();
}
