package org.mpilone.vaadin;

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.concurrent.*;

import com.vaadin.event.EventRouter;
import com.vaadin.ui.UI;

/**
 * <p>
 * A runnable that runs background work first and then handles synchronizing to
 * the UI when performing updates from the execution thread via
 * {@link UI#access(Runnable)} methods. The common use case is to create a
 * {@link UIRunnable} and wrap it in a {@link UIRunnableFuture} for execution
 * and monitoring via a single {@link Future} interface. The runnable future can
 * then be submitted to an {@link Executor} for background execution.
 * </p>
 * <p>
 * A {@link UIRunnableFuture} can be monitored for completion via
 * {@link #addCompleteListener(CompleteListener)}.
 * </p>
 * 
 * @author mpilone
 */
public class UIRunnableFuture implements UIRunnable, RunnableFuture<Void> {

  /**
   * The method to call on the {@link CompleteListener} when firing the complete
   * event.
   */
  private static final Method COMPLETE_METHOD;

  static {
    try {
      COMPLETE_METHOD =
        CompleteListener.class.getMethod("uiRunnableComplete",
          CompleteEvent.class);
    }
    catch (NoSuchMethodException ex) {
      throw new RuntimeException("Unable to find taskRunnableComplete method.",
        ex);
    }
  }

  /**
   * The mutex for swapping futures to ensure that the active future is safely
   * accessed during all delegated calls.
   */
  private final Object futureMutex = new Object();

  /**
   * The active future that is running/will be run. When the future is complete,
   * it must count down the {@link #doneLatch}.
   */
  private Future<Void> future;

  /**
   * The count down latch used to keep track of the number of futures that need
   * to execute before this task is considered complete. Calls to {@link #get()}
   * will block on this latch until it reaches 0.
   */
  private CountDownLatch doneLatch = new CountDownLatch(2);

  /**
   * The UI to synchronize with when updating from a background thread.
   */
  private UI ui;

  /**
   * Event router for sending completion events.
   */
  private EventRouter eventRouter;

  /**
   * The runnable to delegate to or null.
   */
  private UIRunnable runnable;

  /**
   * Constructs the runnable future which, when executed, will run the
   * background work, synchronize with the UI, then run the UI work.
   * 
   * @param ui
   *          the UI to synchronize work before updating any UI components
   */
  public UIRunnableFuture(UI ui) {
    this(null, ui);
  }

  /**
   * Constructs the runnable future which, when executed, will run the
   * background work, synchronize with the UI, then run the UI work. The
   * background and synchronized UI run methods will be delegated to the given
   * runnable if not null.
   * 
   * @param runnable
   *          the runnable to delegate to
   * @param ui
   *          the UI to synchronize work before updating any UI components
   */
  public UIRunnableFuture(UIRunnable runnable, UI ui) {

    if (ui == null) {
      throw new NullPointerException("No UI available for "
        + "background synchronization.");
    }

    this.ui = ui;
    this.runnable = runnable;

    eventRouter = new EventRouter();

    // We wrap the runnable in a future task to get a common API to which to
    // delegate. The task also handles all the tricky exception handling and
    // thread safe cancellation.
    this.future = new FutureTask<Void>(new RunInBackgroundRunnable(), null);
  }

  /**
   * Adds a listener to be notified when the runnable is complete. The listener
   * will always be notified in the UI lock. The complete event is fired if the
   * runnable is canceled or c if it completes successfully.
   * 
   * @param listener
   *          the listener to add
   */
  public void addCompleteListener(CompleteListener listener) {
    eventRouter.addListener(CompleteEvent.class, listener, COMPLETE_METHOD);
  }

  /**
   * Removes a previously registered listener to be notified when the runnable
   * is complete.
   * 
   * @param listener
   *          the listener to remove
   */
  public void removeCompleteListener(CompleteListener listener) {
    eventRouter.removeListener(CompleteEvent.class, listener, COMPLETE_METHOD);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Future#cancel(boolean)
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = false;

    synchronized (futureMutex) {
      result = future.cancel(mayInterruptIfRunning);
    }

    if (result) {
      fireComplete();
    }

    return result;
  }

  /**
   * A convenience method for {@link #cancel(boolean)} with a value of false.
   */
  public void cancel() {
    cancel(false);
  }

  /**
   * Returns the UI runnable that this runnable future will execute. This may be
   * null if no runnable was given.
   * 
   * @return the runnable or null
   */
  public UIRunnable getRunnable() {
    return runnable;
  }

  /**
   * Returns the UI that this runnable future will synchronize with to perform
   * UI component updates.
   * 
   * @return the UI to access
   */
  public UI getUi() {
    return ui;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Future#get()
   */
  @Override
  public Void get() throws InterruptedException, ExecutionException {
    doneLatch.await();
    return future.get();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
   */
  @Override
  public Void get(long timeout, TimeUnit unit) throws InterruptedException,
    ExecutionException, TimeoutException {

    if (doneLatch.await(timeout, unit)) {
      return future.get(timeout, unit);
    }
    else {
      throw new TimeoutException();
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Future#isCancelled()
   */
  @Override
  public boolean isCancelled() {
    synchronized (futureMutex) {
      return future.isCancelled();
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.Future#isDone()
   */
  @Override
  public boolean isDone() {
    return doneLatch.getCount() == 0 && future.isDone();
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.util.concurrent.RunnableFuture#run()
   */
  @Override
  public void run() {

    // Run the initial future, the runInBackground runnable.
    ((FutureTask<Void>) future).run();

    synchronized (futureMutex) {
      Throwable exception = null;
      try {
        // Check if we got an exception during execution.
        future.get();
      }
      catch (Throwable ex) {
        exception = ex;
      }

      if (future.isCancelled() || ui == null || !ui.isAttached()) {
        // Cancelled during doWork or the UI is no longer attached so we'll skip
        // doUiUpdate and simply count down.
        doneLatch.countDown();
        fireComplete();
      }
      else {
        // Fire up runInUI runnable which gets us the second future of the
        // task.
        future = ui.access(new RunInUIRunnable(exception));
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.mpilone.vaadin.UIRunnable#runInUI(java.lang.Throwable)
   */
  @Override
  public void runInUI(Throwable ex) {
    if (runnable != null) {
      runnable.runInUI(ex);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.mpilone.vaadin.UIRunnable#runInBackground()
   */
  @Override
  public void runInBackground() {
    if (runnable != null) {
      runnable.runInBackground();
    }
  }

  /**
   * Fires the complete event after synchronizing with the UI to ensure that the
   * event is handled in the UI lock.
   */
  private void fireComplete() {
    ui.access(new Runnable() {
      @Override
      public void run() {
        eventRouter.fireEvent(new CompleteEvent(UIRunnableFuture.this));
      }
    });
  }

  /**
   * The event details for when the runnable is complete (either via cancel or
   * run completion).
   * 
   * @author mpilone
   */
  public static class CompleteEvent extends EventObject {
    /**
     * The serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs the event with the given source.
     * 
     * @param source
     *          the event source
     */
    public CompleteEvent(UIRunnableFuture source) {
      super(source);
    }
  }

  /**
   * A listener interested in being notified when the UI runnable is complete.
   * 
   * @author mpilone
   */
  public interface CompleteListener {
    /**
     * Called when the runnable is complete, either because it was cancelled or
     * because it finished execution.
     * 
     * @param evt
     *          the event details
     */
    void uiRunnableComplete(CompleteEvent evt);
  }

  /**
   * A simple runnable that executes the first of two parts of a
   * {@link UIRunnableFuture}. The {@link UIRunnableFuture#doWork()} method will
   * be called and the {@link UIRunnableFuture#doneLatch} will be decremented.
   * 
   * @author mpilone
   */
  private class RunInBackgroundRunnable implements Runnable {
    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
      try {
        runInBackground();
      }
      finally {
        doneLatch.countDown();
      }
    }
  }

  /**
   * A simple runnable that executes the second of two parts of a
   * {@link UIRunnableFuture}. The
   * {@link UIRunnableFuture#doUiUpdate(Throwable)} method will be called and
   * the {@link UIRunnableFuture#doneLatch} will be decremented.
   * 
   * @author mpilone
   */
  private class RunInUIRunnable implements Runnable {
    private Throwable exception;

    /**
     * Constructs the runnable.
     * 
     * @param exception
     *          the exception previously raised or null
     */
    public RunInUIRunnable(Throwable exception) {
      this.exception = exception;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
      try {
        runInUI(exception);
      }
      finally {
        doneLatch.countDown();
        fireComplete();
      }
    }
  }
}
