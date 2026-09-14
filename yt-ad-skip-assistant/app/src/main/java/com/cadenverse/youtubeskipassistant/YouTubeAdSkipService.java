package com.cadenverse.youtubeskipassistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public class YouTubeAdSkipService extends AccessibilityService {
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final int MAX_NODES = 1200;
    private static final int MAX_DEPTH = 24;

    private long lastScanAt = 0L;
    private long lastClickAt = 0L;
    private String lastClickSignature = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 80;
        info.packageNames = new String[]{YOUTUBE_PACKAGE};
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        if (!YOUTUBE_PACKAGE.contentEquals(event.getPackageName())) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastScanAt < 120L) {
            return;
        }
        lastScanAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        int[] markerCount = new int[]{0};
        boolean adMarkerPresent = containsAdMarker(root, 0, markerCount);

        int[] scanCount = new int[]{0};
        findAndClickAdControl(root, adMarkerPresent, 0, scanCount);
    }

    @Override
    public void onInterrupt() {
        // No persistent action to interrupt.
    }

    private boolean containsAdMarker(AccessibilityNodeInfo node, int depth, int[] count) {
        if (node == null || depth > MAX_DEPTH || count[0]++ > 350) {
            return false;
        }

        String label = combinedLabel(node);
        if (!label.isEmpty()) {
            if (label.equals("ad")
                    || label.equals("advertisement")
                    || label.equals("sponsored")
                    || label.contains("sponsored")
                    || label.contains("advertisement")
                    || label.contains("ad 1 of")
                    || label.contains("ad 2 of")
                    || label.contains("ads 1 of")
                    || label.contains("ads 2 of")
                    || label.contains("video will play after ad")) {
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (containsAdMarker(child, depth + 1, count)) {
                return true;
            }
        }
        return false;
    }

    private boolean findAndClickAdControl(
            AccessibilityNodeInfo node,
            boolean adMarkerPresent,
            int depth,
            int[] count) {

        if (node == null || depth > MAX_DEPTH || count[0]++ > MAX_NODES) {
            return false;
        }

        String label = combinedLabel(node);
        String viewId = safeLower(node.getViewIdResourceName());

        boolean idMatch = viewId.contains("skip_ad")
                || viewId.contains("ad_skip")
                || viewId.contains("skip_button")
                || viewId.contains("dismiss_ad")
                || viewId.contains("close_ad");

        boolean labelMatch = label.equals("skip ad")
                || label.equals("skip ads")
                || label.equals("skip advertisement")
                || label.startsWith("skip ad ")
                || label.startsWith("skip ads ")
                || label.startsWith("skip advertisement ")
                || label.equals("close ad")
                || label.equals("close ads")
                || label.equals("dismiss ad")
                || label.equals("dismiss ads")
                || (adMarkerPresent && label.equals("skip"));

        if (idMatch || labelMatch) {
            String signature = label + "|" + viewId;
            long now = SystemClock.uptimeMillis();
            if (!(signature.equals(lastClickSignature) && now - lastClickAt < 800L)) {
                if (clickNodeOrParent(node)) {
                    lastClickSignature = signature;
                    lastClickAt = now;
                    return true;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (findAndClickAdControl(child, adMarkerPresent, depth + 1, count)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 5 && current != null; i++) {
            if (current.isEnabled() && current.isClickable()) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private String combinedLabel(AccessibilityNodeInfo node) {
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? ""
                : node.getContentDescription().toString();

        String combined;
        if (!text.isEmpty() && !description.isEmpty()) {
            combined = text + " " + description;
        } else {
            combined = text.isEmpty() ? description : text;
        }
        return safeLower(combined).trim().replaceAll("\\s+", " ");
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
