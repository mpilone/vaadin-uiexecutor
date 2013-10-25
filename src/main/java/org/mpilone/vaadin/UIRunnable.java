package org.mpilone.vaadin;

import com.vaadin.server.VaadinSession;

/**
 * A two step runnable that can perform work in the background and then update
 * the UI when the UI lock has been obtained. This class is similar to
 * {@link Runnable} except that the work must be divided between unsafe/not
 * locked and safe/locked code.
 * 
 * @author mpilone
 */
public interface UIRunnable {

  /**
   * Called when a lock has been obtained on the UI's {@link VaadinSession} and
   * it is safe to apply UI updates. This method will only be called after
   * {@link #runInBackground()} is complete and the task hasn't been cancelled.
   * This method will be called even if {@link #runInBackground()} throws an
   * exception to give the task a chance to cleanup the UI.
   * 
   * @param ex
   *          the exception raised in {@link #runInBackground()} or null if no
   *          exception was raised
   */
  void runInUI(Throwable ex);

  /**
   * Called when the task is started. All time consuming work should be done in
   * this method. No UI updates are permitted in this method because it is not
   * synchronized to the UI's {@link VaadinSession}. Good implementations should
   * attempt to stop if the task is cancelled while processing.
   */
  void runInBackground();

}
