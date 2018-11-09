
package com.reactlibrary;


import android.app.Activity;
import android.content.Intent;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;


public class RNConfirmDeviceCredentialsModule extends ReactContextBaseJavaModule {

  private static final String TAG = "RNonfirmDeviceCredentialsModule";
  private static final String CONFIG_NODE_ERROR = "CONFIG_NODE_ERROR";
  private static final String START_NODE_ERROR = "START_NODE_ERROR";
  private static final String STOP_NODE_ERROR = "STOP_NODE_ERROR";
  private static final String NEW_ACCOUNT_ERROR = "NEW_ACCOUNT_ERROR";
  private static final String SET_ACCOUNT_ERROR = "SET_ACCOUNT_ERROR";
  private static final String GET_ACCOUNT_ERROR = "GET_ACCOUNT_ERROR";
  private static final String BALANCE_ACCOUNT_ERROR = "BALANCE_ACCOUNT_ERROR";
  private static final String BALANCE_AT_ERROR = "BALANCE_AT_ERROR";
  private static final String SYNC_PROGRESS_ERROR = "SYNC_PROGRESS_ERROR";
  private static final String SUBSCRIBE_NEW_HEAD_ERROR = "SUBSCRIBE_NEW_HEAD_ERROR";
  private static final String UPDATE_ACCOUNT_ERROR = "UPDATE_ACCOUNT_ERROR";
  private static final String DELETE_ACCOUNT_ERROR = "DELETE_ACCOUNT_ERROR";
  private static final String EXPORT_KEY_ERROR = "EXPORT_ACCOUNT_KEY_ERROR";
  private static final String IMPORT_KEY_ERROR = "IMPORT_ACCOUNT_KEY_ERROR";
  private static final String GET_ACCOUNTS_ERROR = "GET_ACCOUNTS_ERROR";
  private static final String GET_NONCE_ERROR = "GET_NONCE_ERROR";
  private static final String NEW_TRANSACTION_ERROR = "NEW_TRANSACTION_ERROR";
  private static final String SUGGEST_GAS_PRICE_ERROR = "SUGGEST_GAS_PRICE_ERROR";
  private static final String DEVICE_SECURE_ERROR = "DEVICE_SECURE_ERROR";
  private static final String MAKE_DEVICE_SECURE_ERROR = "MAKE_DEVICE_SECURE_ERROR";
  private static final String KEYSTORE_INIT_ERROR = "KEYSTORE_INIT_ERROR";
  private static final String USER_NOT_AUTHENTICATED_ERROR = "USER_NOT_AUTHENTICATED_ERROR";
  private static final String STORE_PIN_ERROR = "STORE_PIN_ERROR";
  private static final String RETRIEVE_PIN_ERROR = "RTETRIEVE_PIN_ERROR";

  private final ReactApplicationContext reactContext;

  public RNConfirmDeviceCredentialsModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "ConfirmDeviceCredentials";
  }

  @ReactMethod
    public void isDeviceSecure(Promise promise) {
        try {
            promise.resolve(AndroidKeyStoreHelper.isDeviceSecure(getReactApplicationContext()));
        } catch (Exception e) {
            promise.reject(DEVICE_SECURE_ERROR, e);
        }
    }

    @ReactMethod
    public void makeDeviceSecure(String message, String actionButtonLabel, final Promise promise) {
        final int requestCodeForSetPasswordAction = 3;

        AndroidKeyStoreHelper.MakeDeviceSecureCallback makeDeviceSecureCallback =
                new AndroidKeyStoreHelper.MakeDeviceSecureCallback() {
                    @Override
                    public void onUserCancelled() {
                        Log.d(TAG, "makeDeviceSecure/onUserCancelled");
                        promise.reject(MAKE_DEVICE_SECURE_ERROR, "User dismissed dialog");
                    }

                    @Override
                    public void onUserTransitionToSetupDeviceLock() {
                        ActivityEventListener activityEventListener = new ActivityEventListener() {
                            @Override
                            public void onActivityResult(Activity activity,
                                                         int requestcode,
                                                         int resultCode,
                                                         Intent intent) {
                                if (requestcode == requestCodeForSetPasswordAction) {
                                    if (AndroidKeyStoreHelper.isDeviceSecure(getReactApplicationContext())) {
                                        promise.resolve(true);
                                    } else if (resultCode == Activity.RESULT_OK) {
                                        // Retry since now the user is authenticated.
                                        Log.d(TAG, "makeDeviceSecure/onActivityResult/ok");
                                        promise.resolve(AndroidKeyStoreHelper.isDeviceSecure(
                                                getReactApplicationContext()));
                                    } else {
                                        // User decided to reject authentication.
                                        Log.d(TAG, "makeDeviceSecure/onActivityResult/user-canceled-setup/" + resultCode);
                                        promise.reject(MAKE_DEVICE_SECURE_ERROR, "User canceled setup");
                                    }
                                }
                                getReactApplicationContext().removeActivityEventListener(this);
                            }

                            @Override
                            public void onNewIntent(Intent intent) {
                                // Do nothing
                            }
                        };
                        getReactApplicationContext().addActivityEventListener(activityEventListener);
                    }
                };
        try {
            AndroidKeyStoreHelper.makeDeviceSecure(
                    getCurrentActivity(),
                    message,
                    actionButtonLabel,
                    requestCodeForSetPasswordAction,
                    makeDeviceSecureCallback);
        } catch (Exception e) {
            Log.d(TAG, "makeDeviceSecure/error", e);
            promise.reject(MAKE_DEVICE_SECURE_ERROR, e);
        }
    }

    /**
     * This can be called multiple times, it won't recreate the key if the key already exists.
     */
    @ReactMethod
    public void keystoreInit(String keyName,
            int reauthenticationTimeoutInSecs,
            boolean invalidateKeyByNewBiometricEnrollment,
            Promise promise) {
        try {
            if (!AndroidKeyStoreHelper.isDeviceSecure(getReactApplicationContext())) {
                promise.reject(DEVICE_SECURE_ERROR, new Exception("Device is not secure"));
                return;
            }
            if (!AndroidKeyStoreHelper.keyExists(keyName)) {
                Log.i(TAG, "keyStoreInit/key does not exist, creating it");
                createKey(keyName, reauthenticationTimeoutInSecs, invalidateKeyByNewBiometricEnrollment);
                promise.resolve(true);
            } else {
                Log.i(TAG, "keyStoreInit/key exists");
                promise.resolve(true);
            }
            Log.d("GethModule", "key created");
        } catch (Exception e) {
            promise.reject(KEYSTORE_INIT_ERROR, e);
        }
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after
     * the user has authenticated with device credentials within the last X seconds.
     */
    private void createKey(String keyName, int reauthenticationTimeoutInSecs,
            boolean invalidateKeyByNewBiometricEnrollment) {
        try {
            boolean result = AndroidKeyStoreHelper.createKey(keyName,
                    reauthenticationTimeoutInSecs, invalidateKeyByNewBiometricEnrollment);
            Log.i(TAG, "createKey/key creation result: " + result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create a symmetric key", e);
        }
    }

    @ReactMethod
    public void storePin(final String keyName, final String pinValue, final Promise promise) {
        try {
            boolean result = AndroidKeyStoreHelper.storePin(
                    getReactApplicationContext(),
                    keyName,
                    pinValue);
            promise.resolve(result);
        } catch (final UserNotAuthenticatedException e) {
            final int authenticateForEncryptionRequestCode = 1;
            final Runnable retryRunnable = new Runnable() {
                @Override
                public void run() {
                    storePin(keyName, pinValue, promise);
                }
            };
            performAuthentication(promise, e, authenticateForEncryptionRequestCode, retryRunnable);
        } catch (Exception e) {
            promise.reject(STORE_PIN_ERROR, e);
        }
    }

    private void performAuthentication(final Promise promise,
                                       final UserNotAuthenticatedException e,
                                       final int requestCode,
                                       final Runnable retryRunnable) {
        ActivityEventListener activityEventListener = new ActivityEventListener() {
            @Override
            public void onActivityResult(Activity activity,
                                         int requestcode,
                                         int resultCode,
                                         Intent intent) {
                if (requestcode == requestCode) {
                    if (resultCode == Activity.RESULT_OK) {
                        // Retry since now the user is authenticated.
                        retryRunnable.run();
                    } else {
                        // User decided to reject authentication.
                        promise.reject(USER_NOT_AUTHENTICATED_ERROR, e);
                    }
                    getReactApplicationContext().removeActivityEventListener(this);
                }
            }

            @Override
            public void onNewIntent(Intent intent) {
                // Do nothing
            }
        };
        getReactApplicationContext().addActivityEventListener(activityEventListener);
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            // Current activity is null, cannot authenticate.
            promise.reject(USER_NOT_AUTHENTICATED_ERROR, e);
        } else {
            AndroidKeyStoreHelper.authenticateUser(
                    currentActivity,
                    requestCode);
        }
    }

    @ReactMethod
    public void retrievePin(String keyName, Promise promise) {
        try {
            String result = AndroidKeyStoreHelper.retrievePin(getReactApplicationContext(),
                    keyName);
            promise.resolve(result);
        } catch (UserNotAuthenticatedException e) {
            promise.reject(USER_NOT_AUTHENTICATED_ERROR, e);
        } catch (Exception e) {
            promise.reject(RETRIEVE_PIN_ERROR, e);
        }
    }
}
