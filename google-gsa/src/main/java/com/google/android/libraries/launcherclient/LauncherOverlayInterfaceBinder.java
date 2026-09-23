package com.google.android.libraries.launcherclient;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;

import com.google.android.binder.LauncherOverlayBinder;
import com.google.android.binder.ParcelUtils;


public abstract class LauncherOverlayInterfaceBinder extends LauncherOverlayBinder implements ILauncherOverlay {
    private static final String INTERFACE_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay";
    // Transaction codes are assigned by AIDL in declaration order, starting at 1.
    // They must match ILauncherOverlay.aidl in Launcher3 exactly.
    private static final int TRANSACTION_START_SCROLL = 1;
    private static final int TRANSACTION_ON_SCROLL = 2;
    private static final int TRANSACTION_END_SCROLL = 3;
    private static final int TRANSACTION_WINDOW_ATTACHED_LAYOUT = 4;
    private static final int TRANSACTION_WINDOW_DETACHED = 5;
    private static final int TRANSACTION_CLOSE_OVERLAY = 6;
    private static final int TRANSACTION_ON_PAUSE = 7;
    private static final int TRANSACTION_ON_RESUME = 8;
    private static final int TRANSACTION_OPEN_OVERLAY = 9;
    private static final int TRANSACTION_REQUEST_VOICE_DETECTION = 10;
    private static final int TRANSACTION_GET_VOICE_SEARCH_LANGUAGE = 11;
    private static final int TRANSACTION_IS_VOICE_DETECTION_RUNNING = 12;
    private static final int TRANSACTION_HAS_OVERLAY_CONTENT = 13;
    private static final int TRANSACTION_WINDOW_ATTACHED_BUNDLE = 14;
    private static final int TRANSACTION_UNUSED_METHOD = 15;
    private static final int TRANSACTION_SET_ACTIVITY_STATE = 16;
    private static final int TRANSACTION_START_SEARCH = 17;

    protected LauncherOverlayInterfaceBinder() {
        attachInterface(this, INTERFACE_DESCRIPTOR);
    }

    @Override
    public boolean onTransact(final int code, @NonNull final Parcel data, final Parcel reply, final int flags) throws RemoteException {
        if (super.onTransact(code, data, reply, flags)) return true;
        ILauncherOverlayCallback callback;
        IBinder binder;

        switch (code) {
            case TRANSACTION_START_SCROLL:
                startScroll();
                break;
            case TRANSACTION_ON_SCROLL:
                onScroll(data.readFloat());
                break;
            case TRANSACTION_END_SCROLL:
                endScroll();
                break;
            case TRANSACTION_WINDOW_ATTACHED_LAYOUT:
                LayoutParams params = ParcelUtils.readParcelable(data, LayoutParams.CREATOR);
                callback = getCallbackFromBinder(data.readStrongBinder());
                windowAttached(params, callback, data.readInt());
                break;
            case TRANSACTION_REQUEST_VOICE_DETECTION:
                requestVoiceDetection(ParcelUtils.readBoolean(data));
                break;
            case TRANSACTION_ON_PAUSE:
                onPause();
                break;
            case TRANSACTION_ON_RESUME:
                onResume();
                break;
            case TRANSACTION_OPEN_OVERLAY:
                openOverlay(data.readInt());
                break;
            case TRANSACTION_CLOSE_OVERLAY:
                closeOverlay(data.readInt());
                break;
            case TRANSACTION_WINDOW_DETACHED:
                windowDetached(ParcelUtils.readBoolean(data));
                break;
            case TRANSACTION_SET_ACTIVITY_STATE:
                setActivityState(data.readInt());
                break;
            case TRANSACTION_GET_VOICE_SEARCH_LANGUAGE:
                // Not supported; answer the synchronous call so the client does not block.
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeString(null);
                }
                break;
            case TRANSACTION_IS_VOICE_DETECTION_RUNNING:
                if (reply != null) {
                    reply.writeNoException();
                    ParcelUtils.writeBoolean(reply, false);
                }
                break;
            case TRANSACTION_HAS_OVERLAY_CONTENT:
                if (reply != null) {
                    reply.writeNoException();
                    ParcelUtils.writeBoolean(reply, true);
                }
                break;
            case TRANSACTION_WINDOW_ATTACHED_BUNDLE:
                Bundle bundle = ParcelUtils.readParcelable(data, Bundle.CREATOR);
                callback = getCallbackFromBinder(data.readStrongBinder());
                windowAttached(bundle, callback);
                break;
            case TRANSACTION_UNUSED_METHOD:
                break;
            case TRANSACTION_START_SEARCH:
                // Not supported; the client treats false as "handle it yourself".
                if (reply != null) {
                    reply.writeNoException();
                    ParcelUtils.writeBoolean(reply, false);
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private ILauncherOverlayCallback getCallbackFromBinder(final IBinder binder) {
        if (binder == null) return null;

        IInterface iface = binder.queryLocalInterface("com.google.android.libraries.launcherclient.ILauncherOverlayCallback");
        return (iface instanceof ILauncherOverlayCallback)
                ? (ILauncherOverlayCallback) iface
                : new LauncherOverlayCallback(binder);
    }
}
