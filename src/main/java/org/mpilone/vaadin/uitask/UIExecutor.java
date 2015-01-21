package org.mpilone.vaadin.uitask;

import java.util.concurrent.Future;

import com.vaadin.ui.UI;

/**
 * An executor capable of executing background tasks against a UI and
 * synchronizing those tasks for safe UI updates.
 * 
 * @author mpilone
 */
public interface UIExecutor {

  /**
   * Executes the work step of the given {@link UIRunnable} in a background
   * thread and then accesses the UI to safely execute the UI update step of the
   * runnable. The single future returned represents the entire background work
   * and UI update task, therefore canceling the future may prevent the
   * background work or the UI update depending on the state of the task at the
   * time.
   * 
   * @param runnable
   *          the UI runnable to execute and synchronize
   * @return a future representing the execution and access of the runnable
   */
  Future<Void> executeAndAccess(UIRunnable runnable);

  /**
   * Accesses the UI after obtaining the lock for safe updates to the UI. This
   * method returns immediately with the runnable being queued for later
   * execution.
   * 
   * @param runnable
   *          the runnable to execute in the UI lock
   * @return a future representing the runnable task
   * 
   * @see UI#access(Runnable)
   */
  Future<Void> access(Runnable runnable);

  /**
   * Accesses the UI after obtaining the lock for safe updates to the UI. This
   * method blocks until the runnable is executed.
   * 
   * @param runnable
   *          the runnable to execute in the UI lock
   * 
   * @see UI#accessSynchronously(Runnable)
   */
  void accessSynchronously(Runnable runnable);
}
