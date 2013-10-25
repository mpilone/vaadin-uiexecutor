package org.mpilone.vaadin;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.vaadin.ui.UI;

/**
 * A UI executor that simply uses the current UI available via
 * {@link UI#getCurrent()} for synchronization/locking. UIRunnables submitted
 * will be executed via a supplied {@link Executor} while access methods all
 * delegate directly to the current UI.
 * 
 * @author mpilone
 */
public class CurrentUIExecutor implements UIExecutor {

  private Executor executor;

  /*
   * (non-Javadoc)
   * 
   * @see org.mpilone.vaadin.UIExecutor#executeAndAccess(org.mpilone.vaadin.UIRunnable)
   */
  @Override
  public Future<Void> executeAndAccess(final UIRunnable runnable) {

    final UI ui = UI.getCurrent();
    UIRunnableFuture runnableFuture = new UIRunnableFuture(runnable, ui);

    prepareForExecution(ui, runnableFuture);

    getOrCreateExecutor().execute(runnableFuture);

    return runnableFuture;
  }

  /**
   * Prepares the given runnable future for execution on the given UI. The
   * default implementation is a no-op.
   * 
   * @param ui
   *          the current UI
   * @param runnableFuture
   *          the runnable future about to be executed
   */
  protected void prepareForExecution(UI ui, UIRunnableFuture runnableFuture) {
    // no op
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.mpilone.vaadin.UIExecutor#access(java.lang.Runnable)
   */
  @Override
  public Future<Void> access(Runnable runnable) {
    return UI.getCurrent().access(runnable);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.mpilone.vaadin.UIExecutor#accessSynchronously(java
   * .lang.Runnable)
   */
  @Override
  public void accessSynchronously(Runnable runnable) {
    UI.getCurrent().accessSynchronously(runnable);
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
   * @param executor
   *          the executor to use for all {@link UIRunnable}s
   */
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
