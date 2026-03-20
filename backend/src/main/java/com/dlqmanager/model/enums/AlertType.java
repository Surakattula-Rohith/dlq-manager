package com.dlqmanager.model.enums;

public enum AlertType {
    /** Alert when current message count >= threshold */
    THRESHOLD,
    /** Alert when message count increases by >= threshold within the window */
    TIME_WINDOW
}
