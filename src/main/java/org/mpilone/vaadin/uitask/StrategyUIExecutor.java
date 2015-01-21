package org.mpilone.vaadin.uitask;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.*;

import com.vaadin.shared.communication.PushMode;
import com.vaadin.ui.UI;

/**
 * A UI executor that delegates the UI lookup to a configured strategy.
 * UIRunnables submitted will be executed via a supplied {@link Executor} while
 * access methods all delegate directly to the current UI.
 *
 * @author mpilone
 */
public class StrategyUIExecutor implements UIExecutor {

  private Executor executor;
  private UILocator uiLocator;
  private UIPreparer executionPreparer;

  /**
   * Constructs the executor which will use a {@link CurrentUILocator} strategy
   * to locate the UI.
   */
  public StrategyUIExecutor() {
    this(new CurrentUILocator());
  }

  /**
   * Constructs the executor which will use the given strategy to locate the UI.
   *
   * @param uiLocator the UI locator to use when locating the UI for access
   * synchronization
   */
  public StrategyUIExecutor(UILocator uiLocator) {
    this(uiLocator, null, null);
  }

  /**
   * Constructs the executor.
   *
   * @param uiLocator the UI locator to use when locating the UI for access
   * synchronization
   * @param executionPreparer the preparer used to prepare a runnable for
   * execution or null for none
   * @param executor the executor to use for background task execution or null
   * for the default
   */
  public StrategyUIExecutor(UILocator uiLocator,
      UIPreparer executionPreparer, Executor executor) {
    this.executor = executor;
    this.uiLocator = uiLocator;
    this.executionPreparer = executionPreparer;
  }

  /**
   * Sets the UI locator to use when locating the UI for access synchronization.
   * Must not be null.
   *
   * @param uiLocator the UI locator
   */
  public void setUiLocator(UILocator uiLocator) {
    if (uiLocator == null) {
      throw new IllegalArgumentException("The uiLocator must not be null.");
    }

    this.uiLocator = uiLocator;
  }

  /**
   * Sets the execution prepare to use before executing a runnable.
   *
   * @param executionPreparer the execution preparer or null for none
   */
  public void setExecutionPreparer(UIPreparer executionPreparer) {
    this.executionPreparer = executionPreparer;
  }

  @Override
  public Future<Void> executeAndAccess(final UIRunnable runnable) {
    final UI ui = uiLocator.locateUI();
    UIRunnableFuture runnableFuture = new UIRunnableFuture(runnable, ui);

    if (executionPreparer != null) {
      executionPreparer.prepareUI(ui, runnableFuture);
    }

    getOrCreateExecutor().execute(runnableFuture);

    return runnableFuture;
  }

  @Override
  public Future<Void> access(Runnable runnable) {
    final UI ui = uiLocator.locateUI();
    UIRunnableFuture runnableFuture = new UIRunnableFuture(null, runnable, ui);

    if (executionPreparer != null) {
      executionPreparer.prepareUI(ui, runnableFuture);
    }

    return ui.access(runnableFuture);
  }

  @Override
  public void accessSynchronously(Runnable runnable) {
    final UI ui = uiLocator.locateUI();
    UIRunnableFuture runnableFuture = new UIRunnableFuture(null, runnable, ui);

    if (executionPreparer != null) {
      executionPreparer.prepareUI(ui, runnableFuture);
    }

    ui.accessSynchronously(runnableFuture);
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
   * A strategy for locating the UI that will be accessed for all
   * synchronization/lock requests.
   */
  public interface UILocator {

    UI locateUI();
  }

  /**
   * A UI executor that simply uses the current UI available via
   * {@link UI#getCurrent()} for synchronization/locking. Therefore tasks can
   * only be submitted to this executor from the UI thread.
   */
  public static class CurrentUILocator implements UILocator {

    @Override
    public UI locateUI() {
      return UI.getCurrent();
    }
  }

  /**
   * A UI locator strategy that uses a single UI instance for
   * synchronization/locking. Therefore tasks can be submitted from any thread,
   * including background threads.
   *
   * @author mpilone
   */
  public static class StaticUILocator implements UILocator {

    private final UI ui;

    /**
     * Constructs the locator which will always return the given UI.
     *
     * @param ui the UI to return on locate requests
     */
    public StaticUILocator(UI ui) {
      this.ui = ui;
    }

    @Override
    public UI locateUI() {
      return ui;
    }
  }

  /**
   * A strategy for preparing a UI for a runnable future for execution. For
   * example, the strategy may want to modify the UI push configuration or
   * attach listeners to the runnable to execute work when the task completes.
   */
  public interface UIPreparer {

    /**
     * Prepares the given UI for the execution of the runnable future.
     *
     * @param ui the current UI
     * @param runnableFuture the runnable future about to be executed
     */
    void prepareUI(UI ui, UIRunnableFuture runnableFuture);
  }

  /**
   * A {@link UIExecutor} that dynamically updates the polling interval on the
   * UI to perform faster polling when a background task is e executing in an
   * attempt to improve responsiveness when waiting for a result while limiting
   * unnecessary polling when there are no expected server side changes. If the
   * UI is configured for push rather than polling, the polling interval will
   * not be changed but the {@link UI#push()} method will be called when a task
   * completes.
   *
   * @author mpilone
   */
  public static class DynamicPollingManualPushUIPreparer implements
      UIPreparer {

    /**
     * The default poll interval when background work is executing in a
     * runnable.
     */
    public static final int DEFAULT_WORK_POLL_INTERVAL = 800;

    /**
     * The default poll interval when no background work is executing.
     */
    public static final int DEFAULT_NORMAL_POLL_INTERVAL = 15000;

    /**
     * The logger for this class.
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final int workPollInterval;
    private final int normalPollInterval;

    /**
     * A map of UI connector ID to a pending task count for the UI.
     */
    private final Map<String, Integer> uiConnectorIdTaskCountMap;

    /**
     * Constructs the preparer using the {@link #DEFAULT_NORMAL_POLL_INTERVAL}
     * and {@link #DEFAULT_WORK_POLL_INTERVAL}.
     */
    public DynamicPollingManualPushUIPreparer() {
      this(DEFAULT_NORMAL_POLL_INTERVAL, DEFAULT_WORK_POLL_INTERVAL);
    }

    /**
     * Constructs the executor with the given poll intervals.
     *
     * @param normalPollInterval the poll interval when work is not pending and
     * therefore the client is not expecting a server generated change. This
     * interval is normally large (e.g. 15000 milliseconds) to limit wasted
     * network round trips.
     * @param workPollInterval the poll interval when work is pending and
     * therefore the client is expecting a server s generated change. This
     * interval is normally small (e.g. 800 milliseconds).
     */
    public DynamicPollingManualPushUIPreparer(int normalPollInterval,
        int workPollInterval) {
      this.normalPollInterval = normalPollInterval;
      this.workPollInterval = workPollInterval;
      this.uiConnectorIdTaskCountMap = new HashMap<>();
    }

    @Override
    public void prepareUI(UI ui, UIRunnableFuture runnableFuture) {

      // We handle the start now even though the runnable may be getting queued in
      // the executor. This is needed because we want any poll time changes to the
      // UI to happen in the first client response otherwise the client wouldn't
      // know that it should use the fast poll until the first slow poll got the
      // change to the fast poll.
      taskStart(ui);

      // Add a complete listener so we can slow the polling down if all background
      // work is done.
      runnableFuture.addCompleteListener(createCompleteListener());
    }

    /**
     * Constructs the complete listener to be notified when a submitted task
     * completes execution.
     *
     * @return the complete listener
     */
    private UIRunnableFuture.CompleteListener createCompleteListener() {
      return new UIRunnableFuture.CompleteListener() {
        @Override
        public void uiRunnableComplete(UIRunnableFuture.CompleteEvent evt) {

          UIRunnableFuture taskRunnable = (UIRunnableFuture) evt.getSource();
          UI ui = taskRunnable.getUi();

          taskFinish(ui);
        }
      };
    }

    /**
     * Called by the complete listener to adjust the polling interval or perform
     * a manual push.
     *
     * @param ui the UI to update or push
     */
    private synchronized void taskFinish(UI ui) {

      Integer pendingTasks = uiConnectorIdTaskCountMap.get(ui.getConnectorId());
      if (pendingTasks == null) {
        // This should never happen unless there is a bug.
        log.warn("Pending task completed but no pending task count was "
            + "found for the UI [{}].", ui.getConnectorId());
      }
      else {
        pendingTasks = pendingTasks - 1;

        if (pendingTasks == 0) {
          uiConnectorIdTaskCountMap.remove(ui.getConnectorId());
        }
        else {
          uiConnectorIdTaskCountMap.put(ui.getConnectorId(), pendingTasks);
        }
      }

      if (ui.getPushConfiguration().getPushMode() == PushMode.DISABLED
          && pendingTasks == 0 && ui.getPollInterval() != normalPollInterval) {

        // Decrease the poll interval because there are no more running tasks.
        ui.setPollInterval(normalPollInterval);
      }
      else if (ui.getPushConfiguration().getPushMode() == PushMode.MANUAL) {

        // Do a manual push because some background task just finished and
        // presumably made changes to the UI.
        ui.push();
      }

      log.debug("Background task finished. There are [{}] pending "
          + "tasks for UI [{}] with a poll interval of [{}].", pendingTasks,
          ui.getConnectorId(), ui.getPollInterval());
    }

    /**
     * Called when a task is submitted for execution to adjust the polling
     * interval.
     *
     * @param ui the UI to update
     */
    private synchronized void taskStart(UI ui) {

      if (ui.getPushConfiguration().getPushMode() == PushMode.DISABLED
          && ui.getPollInterval() != workPollInterval) {

        // Increase the poll interval because there are running tasks.
        ui.setPollInterval(workPollInterval);
      }

      int pendingTasks =
          uiConnectorIdTaskCountMap.containsKey(ui.getConnectorId()) ?
          uiConnectorIdTaskCountMap.get(ui.getConnectorId()) : 0;
      pendingTasks++;
      uiConnectorIdTaskCountMap.put(ui.getConnectorId(), pendingTasks);

      log.debug("Background task started. There are [{}] pending "
          + "tasks for UI [{}] with a poll interval of [{}].", pendingTasks,
          ui.getConnectorId(), ui.getPollInterval());
    }

  }
}
