package com.cascade.domain.event;

/** Observer: implement to receive lifecycle events from a run. */
public interface RunEventListener {
    void onEvent(RunEvent event);
}
