package edu.gatech.epl.mfsnap.statapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shrutip on 11/2/14.
 */
public class NetworkOperations {
    private Context context;
    private final int INTERNET_CONNECTION_TIMEOUT = 1500;
    private static final String IPv4_PATTERN =
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    public NetworkOperations(Context cxt) {
        this.context = cxt;
    }

    public static boolean validateIP (String ip){
        Pattern pattern = Pattern.compile(IPv4_PATTERN);
        Matcher matcher = pattern.matcher(ip);
        return matcher.matches();
    }

    public class HasActiveNetworkConnection extends AsyncTask<String, Void, Boolean> {
        public boolean isNetworkAvailable() {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (isNetworkAvailable()) {
                try {
                    HttpURLConnection urlc = (HttpURLConnection) (new URL("http://www.google.com").openConnection());
                    urlc.setRequestProperty("User-Agent", "Test");
                    urlc.setRequestProperty("Connection", "close");
                    urlc.setConnectTimeout(INTERNET_CONNECTION_TIMEOUT);
                    urlc.connect();
                    return (urlc.getResponseCode() == 200);
                } catch (IOException e) {
                    Log.e("ERROR", "Error checking internet connection", e);
                }
            } else {
                Log.d("ERROR", "No network available!");
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean params) {
            //Task you want to do on UIThread after completing Network operation
            //onPostExecute is called after doInBackground finishes its task.
        }
    }
}
