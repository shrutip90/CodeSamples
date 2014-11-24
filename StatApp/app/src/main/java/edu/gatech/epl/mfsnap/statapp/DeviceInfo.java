package edu.gatech.epl.mfsnap.statapp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

/**
 * Created by shrutip on 11/3/14.
 */
public class DeviceInfo {
    public static String getDeviceId (Context context) {
        return Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }
    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public static String getAndroidVersion() {
        String release = Build.VERSION.RELEASE;
        int sdkVersion = Build.VERSION.SDK_INT;
        return sdkVersion + " (" + release +")";
    }

    public static String getKernelVersion() {
        return System.getProperty("os.version");
    }

    public static String getBuildNumber() {
        String mString = "CODENAME: " + Build.VERSION.CODENAME +
                        "\nPRODUCT: " + Build.PRODUCT +
                        "\nFINGERPRINT: " + Build.FINGERPRINT;
        return mString;
    }

    @SuppressWarnings("deprecation")
    public static long getInternalStorageSize() {
        StatFs statFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());

        long blockSize, totalSize;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            blockSize = statFs.getBlockSizeLong();
            totalSize = statFs.getBlockCountLong() * blockSize;
        } else {
            blockSize = (long)statFs.getBlockSize();
            totalSize = (long)statFs.getBlockCount() * blockSize;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            long availableSize = statFs.getAvailableBlocksLong() * blockSize;
            long freeSize = statFs.getFreeBlocksLong() * blockSize;
        }
        return totalSize;
    }

    @SuppressWarnings("deprecation")
    public static long getExternalStorageSize() {
        StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());

        long blockSize, totalSize;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            blockSize = statFs.getBlockSizeLong();
            totalSize = statFs.getBlockCountLong() * blockSize;
        } else {
            blockSize = statFs.getBlockSize();
            totalSize = statFs.getBlockCount() * blockSize;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            long availableSize = statFs.getAvailableBlocksLong() * blockSize;
            long freeSize = statFs.getFreeBlocksLong() * blockSize;
        }
        return totalSize;
    }

    public static String getStorageSize() {
        return "Internal Storage Size: " + getInternalStorageSize() +
                "\nExternal Storage Size: " + getExternalStorageSize();
    }

    public static boolean isDeviceRooted(Context context, String MFSNAP_TAG) {
        if (checkRootMethod1()) {
            Log.i(MFSNAP_TAG, "Device rooted: Method1\n");
            return true;
        } else if (checkRootMethod2()) {
            Log.i(MFSNAP_TAG, "Device rooted: Method2\n");
            return true;
        } else if (checkRootMethod3()) {
            Log.i(MFSNAP_TAG, "Device rooted: Method3\n");
            return true;
        } else if (checkRootMethod4(context)) {
            Log.i(MFSNAP_TAG, "Device rooted: Method4\n");
            return true;
        }
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4(context);
    }

    public static boolean checkRoot () {
        try {
            Process process = Runtime.getRuntime().exec("su");

            BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            while (output.ready()) {
                System.out.println("Out:" + output.readLine());
            }
            while (err.ready()) {
                System.out.println("Err:" + err.readLine());
            }
            process.waitFor();
            err.close();
            output.close();
            return ((process.exitValue() != 0) ? false : true);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    public static boolean checkRootMethod2() {
        try {
            File file = new File("/system/app/Superuser.apk");
            return file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkRootMethod3() {
        return executeCommand(SHELL_CMD.check_su_binary)!=null;
    }

    public static boolean checkRootMethod4(Context context) {
        return isPackageInstalled("eu.chainfire.supersu", context);
    }

    private static boolean isPackageInstalled(String packagename, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static enum SHELL_CMD {
        check_su_binary(new String[] { "/system/xbin/which", "su" });

        String[] command;

        SHELL_CMD(String[] command) {
            this.command = command;
        }
    }

    public static ArrayList<String> executeCommand(SHELL_CMD shellCmd) {
        String line = null;
        ArrayList<String> fullResponse = new ArrayList<String>();
        Process localProcess = null;
        try {
            localProcess = Runtime.getRuntime().exec(shellCmd.command);
        } catch (Exception e) {
            return null;
        }
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                localProcess.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(
                localProcess.getInputStream()));
        try {
            while ((line = in.readLine()) != null) {
                fullResponse.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fullResponse;
    }

    public static void writeMountOutput (BufferedWriter statLog) throws IOException, InterruptedException {
        Process pStat = Runtime.getRuntime().exec(new String[] {"mount"});

        BufferedReader output = new BufferedReader(new InputStreamReader(pStat.getInputStream()));
        BufferedReader err = new BufferedReader(new InputStreamReader(pStat.getErrorStream()));

        String line;
        while ((line = output.readLine())!= null) {
            statLog.write(line);
            // Log.v(MFSNAP_TAG, line);
            statLog.newLine();
        }
        while ((line = err.readLine())!= null) {
        }
        pStat.waitFor();
        err.close();
        output.close();
    }
    public static void writeDeviceInfo (BufferedWriter statLog, Context context) throws IOException, InterruptedException{
        statLog.write("Device ID: " + DeviceInfo.getDeviceId(context));
        statLog.newLine();
        statLog.write("Device Name: " + DeviceInfo.getDeviceName());
        statLog.newLine();
        statLog.write("Android SDK: " + DeviceInfo.getAndroidVersion());
        statLog.newLine();
        statLog.write("Kernel Version: " + DeviceInfo.getKernelVersion());
        statLog.newLine();
        statLog.write(DeviceInfo.getBuildNumber());
        statLog.newLine();
        statLog.write(DeviceInfo.getStorageSize());
        statLog.newLine();
        statLog.write("Mount output:");
        statLog.newLine();
        writeMountOutput(statLog);
        statLog.newLine();
    }
}
