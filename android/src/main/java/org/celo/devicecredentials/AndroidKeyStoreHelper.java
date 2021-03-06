package org.celo.devicecredentials;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.util.Log;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.nio.charset.Charset;
import java.security.*;
import java.security.cert.CertificateException;


public class AndroidKeyStoreHelper {
    private static final String TAG = "AndroidKeyStoreHelper";
    private static final String PIN_ENCRYPTED_FILENAME = "pin_encrypted.txt";
    private static final String PIN_IV_FILENAME = "pin_iv.txt";


    /**
     * @see #makeDeviceSecure(Context, String, String)
     */
    public static boolean isDeviceSecure(Context context) {
        @Nullable KeyguardManager keyguardManager = getKeyguardManager(context);
        if (keyguardManager == null) {
            Log.w(TAG, "Keyguard manager is null");
            return false;
        }
        return keyguardManager.isDeviceSecure();
    }

    public static void makeDeviceSecure(Activity activity,
                                        String description,
                                        String buttonLabel,
                                        int requestCodeForReturn,
                                        MakeDeviceSecureCallback callback) {
        Intent buttonActionIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
        showPrompt(activity, description, buttonLabel, buttonActionIntent, requestCodeForReturn,
                callback);
    }

    public static void authenticateUser(Activity activity, int requestCode) {
        @Nullable KeyguardManager keyguardManager = getKeyguardManager(activity);
        if (keyguardManager == null) {
            throw new RuntimeException("Unable to access Keyguard manager");
        }
        String title = null;
        String description = null;
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description);
        if (intent != null) {
            activity.startActivityForResult(intent, requestCode);
        }
    }

    private static KeyguardManager getKeyguardManager(Context context) {
        return (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after
     * the user has authenticated with device credentials within the last reauthenticationTimeoutInSecs seconds.
     */
    public static boolean createKey(String keyName, int reauthenticationTimeoutInSecs,
                                    boolean invalidateKeyByNewBiometricEnrollment) {
        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
            Log.e(TAG, "createKey/cannot access keystore");
            return false;
        }

        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    // Require that the user has unlocked in the last 30 seconds
                    .setUserAuthenticationValidityDurationSeconds(reauthenticationTimeoutInSecs)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                builder.setInvalidatedByBiometricEnrollment(invalidateKeyByNewBiometricEnrollment);
            }
            keyGenerator.init(builder.build());
            keyGenerator.generateKey();
            return true;
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed to create a symmetric key", e);
            return false;
        }
    }

    public static boolean deleteKey(String keyName) {
        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
            Log.e(TAG, "createKey/cannot access keystore");
            return false;
        }
        try {
            keyStore.deleteEntry(keyName);
            return true;
        } catch (KeyStoreException e) {
            Log.e(TAG, "deleteKey/Failed to delete key: " + keyName, e);
            return false;
        }
    }

    public static boolean storePin(Context context, String keyName, String pinValue)
            throws UserNotAuthenticatedException {
        byte[] encryptedData = encrypt(context, keyName, pinValue, PIN_IV_FILENAME);
        if (encryptedData == null) {
            Log.w(TAG, "Failed to encrypt data");
            return false;
        }
        boolean success = writeData(context, PIN_ENCRYPTED_FILENAME, encryptedData);
        if (!success) {
            Log.w(TAG, "Failed to save encrypted data");
            return false;
        }
        return true;
    }

    public static String retrievePin(Context context, String keyName) throws
            UserNotAuthenticatedException, UnrecoverableKeyException {
        byte[] encryptedData = readData(context, PIN_ENCRYPTED_FILENAME);

        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
            return null;
        }

        Key secretKey = getKey(keyStore, keyName);
        if (secretKey == null) {
            Log.e(TAG, "retrievePin/Failed to load key");
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            IvParameterSpec ivParams = new IvParameterSpec(readData(context, PIN_IV_FILENAME));
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
            return new String(cipher.doFinal(encryptedData));
        } catch (UserNotAuthenticatedException e) {
            Log.e(TAG, "retrievePin/User is not authenticated");
            throw e;
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException |
                NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "retrievePin/Error trying to decrypt the pin");
            return null;
        }
    }

    @Nullable
    private static KeyStore getKeyStore() {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to access keystore", e);
            return null;
        }

        try {
            keyStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to load keystore", e);
            return null;
        }
        return keyStore;
    }

    public static boolean keyExists(String keyName) {
        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
            Log.e(TAG, "Cannot access keystore, cannot create key");
            return false;
        }
        try {
            return getKey(keyStore, keyName) != null;
        } catch (UnrecoverableKeyException e) {
            // An unrecoveable key is same as a non-existent key.
            return false;
        }
    }

    @Nullable
    private static Key getKey(KeyStore keyStore, String keyName) throws UnrecoverableKeyException {
        try {
            return keyStore.getKey(keyName, null);
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to load key", e);
            return null;
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "Key is unrecoverable", e);
            throw e;
        }
    }

    private static byte[] encrypt(Context context, String keyName, String plainTextMessage, String ivFilename)
            throws UserNotAuthenticatedException {
        KeyStore keyStore = getKeyStore();
        if (keyStore == null) {
            return null;
        }

        Key secretKey;
        try {
            secretKey = getKey(keyStore, keyName);
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "key is unrecoverable, this is unusual at the time of encrypt");
            return null;
        }
        if (secretKey == null) {
            Log.e(TAG, "encrypt/fail to read key");
            return null;
        }

        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "Failed to create Cipher", e);
            return null;
        }

        byte[] encryptedData;
        // Try encrypting something, it will only work if the user authenticated within
        // the reauthenticationTimeoutInSecs timeout specified during key creation.
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            encryptedData = cipher.doFinal(plainTextMessage.getBytes(Charset.defaultCharset()));
        } catch (UserNotAuthenticatedException e) {
            Log.w(TAG, "encrypt/User not authenticated");
            throw e;
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            Log.w(TAG, "encrypt/Failed to encrypt data", e);
            return null;
        }

        if (!writeData(context, ivFilename, cipher.getIV())) {
            return null;
        }
        return encryptedData;

    }

    private static boolean writeData(Context context, String fileName, byte[] data) {
        FileOutputStream fos;
        try {
            fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to create file " + fileName, e);
            return false;
        }

        try {
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + fileName, e);
            return false;
        }

        return true;
    }

    private static byte[] readData(Context context, String fileName) {
        FileInputStream fis;
        try {
            fis = context.openFileInput(fileName);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Failed to read file " + fileName, e);
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            transfer(fis, baos);
            fis.close();
            return baos.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file " + fileName, e);
            return null;
        }
    }

    private static void transfer(FileInputStream fis, ByteArrayOutputStream baos) throws IOException {
        byte buf[] = new byte[1024];
        int numRead = fis.read(buf);
        while (numRead > 0) {
            baos.write(buf, 0, numRead);
            numRead = fis.read(buf);
        }
    }

    private static void showPrompt(final Activity activity,
                                   String message,
                                   String buttonLabel,
                                   final Intent buttonActionIntent,
                                   final int requestCodeForReturn,
                                   final MakeDeviceSecureCallback callback) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            Log.w(TAG, "Cannot show dialog, activity is finishing");
            return;
        }
        new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(buttonLabel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        activity.startActivityForResult(buttonActionIntent, requestCodeForReturn);
                        callback.onUserTransitionToSetupDeviceLock();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        callback.onUserCancelled();
                    }
                })
                .show();
    }

    interface MakeDeviceSecureCallback {
        void onUserCancelled();
        void onUserTransitionToSetupDeviceLock();
    }
}
