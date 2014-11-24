package edu.gatech.epl.mfsnap.statapp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ShellExecutor {
    public enum HashingScheme{
        NO_HASH("None"),
        HASH_FILE_NAMES("File Names"),
        HASH_ALL("Hash All");

        private String text;

        HashingScheme (String text) {
            this.text = text;
        }
        public String getText() {
            return this.text;
        }
        public static HashingScheme getEnum(String text) {
            if (text != null) {
                for (HashingScheme b : HashingScheme.values()) {
                    if (text.equalsIgnoreCase(b.text)) {
                        return b;
                    }
                }
            }
            throw new IllegalArgumentException ("No constant with text " + text + " found");
        }
    }
    private Context context;
    private final String MFSNAP_TAG = "mfsnap-stat";
    private HashingScheme hashingScheme = HashingScheme.HASH_FILE_NAMES;

    // TODO: It would be convenient if we can copy the logs directly from the sdcard.
    // Or it could be moved from the original folder to the sdcard. But I just used hard-coded the path now.
    // Make a separate log file for each attempt, otherwise the file will be overwritten.
    private final String statLogFinalPath = "/sdcard/statLog.txt";

	public ShellExecutor(Context context) {
		this.context=context;
	}
    public void setHashingScheme (String scheme) {
        this.hashingScheme = HashingScheme.getEnum(scheme);
    }
	
	public class getFileSystemStats extends AsyncTask<Integer, Integer, String> {
        private final ProgressBar progress;
        private TextView textViewOutput;
        private Button btnViewData;
        private Button btnSendData;

        public getFileSystemStats (final ProgressBar progress, TextView output, Button view, Button send) {
            this.progress = progress;
            this.textViewOutput = output;
            this.btnViewData = view;
            this.btnSendData = send;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Shows Progress Bar Dialog and then call doInBackground method
            progress.setProgress(0);
            progress.setVisibility(View.VISIBLE);
        }
        @Override
        protected void onProgressUpdate(Integer... progr) {
            super.onProgressUpdate(progr[0]);
            progress.setProgress(progr[0]);
        }
        @Override
        protected void onPostExecute(String result) {
            progress.setVisibility(View.INVISIBLE);
            progress.setProgress(0);
            btnViewData.setVisibility(View.VISIBLE);
            btnSendData.setVisibility(View.VISIBLE);
            Log.d("Output", result);
        }

        @Override
        protected String doInBackground(Integer... params) {
            File statLib = new File(context.getFilesDir(), "stat");

            String lsLineItem = null;
            String command = statLib.getAbsolutePath();

            StringBuffer outputMessage = new StringBuffer();
            Process pLs, pStat;

            String[] skipDirs = {"/proc", "/sys", "/acct"}; // There is no directory like /procxxx, only /proc/xxx.

            try {
                // TODO: Just ignore skipDirs without traversing subdirectories, and make a list.
                Log.i(MFSNAP_TAG, "Checking root permission..\n");
                boolean asRoot = false;
                //asRoot = DeviceInfo.checkRoot();
                Log.i(MFSNAP_TAG, "Device rooted: " + asRoot + "\n");
                asRoot = false;
                if (asRoot) {
                    pLs = Runtime.getRuntime().exec(new String[] {"su", "-c", "ls -a -R /"});
                } else {
                    pLs = Runtime.getRuntime().exec("ls -a -R /");
                }

                StreamGobbler errorGobbler = new StreamGobbler(pLs.getErrorStream(), "lsLogErr");
                StreamGobbler outputGobbler = new StreamGobbler(pLs.getInputStream(), "lsLog");

                errorGobbler.start();
                outputGobbler.start();

                int exitVal = pLs.waitFor();
                errorGobbler.join();
                outputGobbler.join();

                int totalLines = outputGobbler.getProcessedLines();
                Log.i(MFSNAP_TAG, "Device Id: " + DeviceInfo.getDeviceId(context) + "\n");
                Log.i(MFSNAP_TAG, "Completed file list collection with exit status: " + exitVal + "\n");

                File statLogFile = new File(statLogFinalPath);
                // File statLogFile = new File(context.getFilesDir(), "statLog");
                statLogFile.createNewFile();

                File lsLogFile = new File(context.getFilesDir(), "lsLog");

                BufferedWriter statLog = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(statLogFile)));
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(lsLogFile)));

                String dirPath = null;
                String filePath = null;
                boolean skipDir = false;
                Boolean hashNames = Boolean.FALSE;
                if (hashingScheme.equals(HashingScheme.HASH_FILE_NAMES)) {
                    hashNames = Boolean.TRUE;
                }
                // Get the device info and write it in the first few lines of the stat file
                DeviceInfo.writeDeviceInfo(statLog, context);

                /** The upcoming block code assumes that 'ls -R' output looks like the following.
                 1. The directory contains ":" at the end of the path name
                 2. After the directory, the files inside the directory will show.
                 3. There is a blank between each directory contents.

                 For example:
                 sdcard/Android/:
                 data

                 sdcard/Android/data:
                 .khs
                 .nomedia
                 */
                int i = 0;
                while ((lsLineItem = br.readLine()) != null) {
                    i++;
                    float percentage = ((float)i / (float)totalLines) * 100;
                    publishProgress(new Float(percentage).intValue());

                    if (lsLineItem.trim().length() == 0) continue;

                    if (lsLineItem.endsWith(":")) { // If the line has a directory name,
                        dirPath = lsLineItem.substring(0, lsLineItem.length() - 1);
                        dirPath = (new File(dirPath)).getCanonicalPath();

                        skipDir = false;
                        for (String sdir : skipDirs) {
                            if (dirPath.startsWith(sdir)) {
                                // TODO: Do we really need this? This should have finished while examining the parent directory.
                                if (dirPath.equals(sdir)) {
                                    FileOperations.getStatInfo(command, statLog, dirPath, Boolean.FALSE, asRoot);
                                }
                                //Log.i(MFSNAP_TAG, "SkipDirectory: " + dirPath);
                                skipDir = true;
                            }
                        }

                    } else if (!skipDir) {
                        filePath = dirPath + File.separator + lsLineItem;
                        filePath = (new File(filePath)).getCanonicalPath();
                        //Log.i(MFSNAP_TAG, "File: " + filePath);
                        FileOperations.getStatInfo(command, statLog, filePath, hashNames, asRoot);
                    }
                }
                br.close();
                statLog.close();
                Log.i(MFSNAP_TAG, "Completed stat collection\n");
            } catch (IOException e) {
                outputMessage.append("Encountered IOException: " + e + "\n");
                e.printStackTrace();
            } catch (Exception e) {
                outputMessage.append("Encountered exception: " + e + "\n");
                e.printStackTrace();
            }
            return outputMessage.toString();
        }
    }

    private class StreamGobbler extends Thread {
		InputStream is;
	    String outputFileName;
        int processedLines;
	    
	    StreamGobbler(InputStream is, String outputFileName) {
	        this.is = is;
	        this.outputFileName = outputFileName;
            this.processedLines = 0;
	    }
        public int getProcessedLines () {
            return this.processedLines;
        }
        public void setProcessedLines(int numLines) {
            this.processedLines = numLines;
        }
	    
	    public void run() {
	        try {
	        	BufferedWriter log = new BufferedWriter(new OutputStreamWriter(
	        			new FileOutputStream(new File(context.getFilesDir(), outputFileName))));
	            BufferedReader br = new BufferedReader(new InputStreamReader(is));
	            String line = null;
                int numLines = 0;
	            while ((line = br.readLine()) != null) {
                    numLines++;
	            	log.write(line);
	            	log.newLine();
	            }
	            log.close();
                setProcessedLines(numLines);
	        } catch (IOException ioe) {
	            ioe.printStackTrace();  
	        }
	    }
	}
}