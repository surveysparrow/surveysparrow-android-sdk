package com.surveysparrow.ss_android_sdk;

/**
 * Implement OnSsResponseEventListener in your Activity to listen for survey response event.
 *
 */
public interface OnSsResponseEventListener {
    /**
     * Override this method to handle survey response event while using SsSurveyFragment.
     * @param data Survey response.
     * @see SsSurveyFragment
     */
    public void onSsResponseEvent(String data);
}
