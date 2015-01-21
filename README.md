# UI Executor for Vaadin

A framework for safely implementing background tasks in Vaadin including support for dynamic polling intervals or manual push. The framework makes it a little easier to write cancelable background tasks that safely access the UI for updates and at the same time makes it easier to unit test presenters/view-models that need to execute background tasks.

This is a work in progress and it has not been thoroughly tested. I'm interested in feedback and if there is interest I may package it up as a full add-on. Ideally this functionality would be rolled into Vaadin core but it makes sense to prove out the concepts independently first.

Refer to the JavaDoc for more details. All the classes and interfaces are fully documented.

## Example Usage

First, create an instance of a UIExecutor such as the StrategyUIExecutor and set the desired UI locator strategy and executor. This is normally done via your DI framework such as Spring.

Second, implement the UIRunnable interface to define your task. For example:

    public class LoadMyData implements UIRunnable {
    
      public void runInBackground() {
        // do some slow processing here, but don't use any UI components.
      }

      public void runInUI(Throwable ex) {
        // do all UI changes based on the work done previously.
      }
    }

Third, execute your task using the executor. For example:

    Future<Void> future = executor.executeAndAccess(new LoadMyData());

Fourth, if needed, you can monitor or cancel the task via the future:

    future.cancel(false);


