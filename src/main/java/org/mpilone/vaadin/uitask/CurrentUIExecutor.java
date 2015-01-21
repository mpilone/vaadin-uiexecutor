package org.mpilone.vaadin.uitask;

import java.util.concurrent.Executor;

import com.vaadin.ui.UI;

/**
 * A UI executor that simply uses the current UI available via
 * {@link UI#getCurrent()} for synchronization/locking. Therefore tasks can only
 * be submitted to this executor from the UI thread. UIRunnables submitted will
 * be executed via a supplied {@link Executor} while access methods all delegate
 * directly to the current UI.
  * 
 * @author mpilone
 * @deprecated Use the {@link StrategyUIExecutor} instead
 */
@Deprecated
public class CurrentUIExecutor extends AbstractUIExecutor implements UIExecutor {

  /**
   * Prepares the given runnable future for execution on the given UI. The
   * default implementation is a no-op.
   * 
   * @param ui
   *          the current UI
   * @param runnableFuture
   *          the runnable future about to be executed
   */
  @Override
  protected void prepareForExecution(UI ui, UIRunnableFuture runnableFuture) {
    // no op
  }

  @Override
  protected UI getUI() {
    return UI.getCurrent();
  }
}
