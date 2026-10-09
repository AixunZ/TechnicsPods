package dev.technics.pods.hook;

import android.content.Context;
import android.view.View;

/** Compatibility entry point superseded by the verified artwork hook. */
final class Az100ImageResource {

    private Az100ImageResource() {}

    static void replaceDetailImages(Context hostContext, View root) {
        // Replaced by Az100ArtHook, which requires device identity and verified resource IDs.
    }
}
