package edu.gatech.epl.mfsnap.statapp;

import android.content.Context;
import android.content.Intent;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by shrutip on 11/2/14.
 */
public class FileOperations {
    private static final String MFSNAP_TAG = "mfsnap-stat";

    public static void copyRawFile(Context context, int resourceId, File dst, String mode) throws IOException, InterruptedException {
        String absolutePath = dst.getAbsolutePath();
        InputStream in = context.getResources().openRawResource(resourceId);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        Runtime.getRuntime().exec("chmod " + mode + " " + absolutePath).waitFor();
    }

    public static void openFile(Context context, File url) throws IOException {
        File file=url;
        Uri uri = Uri.fromFile(file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        if(url.toString().contains(".txt")) {
            intent.setDataAndType(uri, "text/plain");
        } else if (url.toString().contains(".doc") || url.toString().contains(".docx")) {
            // Word document
            intent.setDataAndType(uri, "application/msword");
        } else if(url.toString().contains(".pdf")) {
            // PDF file
            intent.setDataAndType(uri, "application/pdf");
        } else if(url.toString().contains(".ppt") || url.toString().contains(".pptx")) {
            // Powerpoint file
            intent.setDataAndType(uri, "application/vnd.ms-powerpoint");
        } else if(url.toString().contains(".xls") || url.toString().contains(".xlsx")) {
            // Excel file
            intent.setDataAndType(uri, "application/vnd.ms-excel");
        } else if(url.toString().contains(".zip") || url.toString().contains(".rar")) {
            // WAV audio file
            intent.setDataAndType(uri, "application/x-wav");
        } else if(url.toString().contains(".rtf")) {
            // RTF file
            intent.setDataAndType(uri, "application/rtf");
        } else if(url.toString().contains(".wav") || url.toString().contains(".mp3")) {
            // WAV audio file
            intent.setDataAndType(uri, "audio/x-wav");
        } else if(url.toString().contains(".gif")) {
            // GIF file
            intent.setDataAndType(uri, "image/gif");
        } else if(url.toString().contains(".jpg") || url.toString().contains(".jpeg") || url.toString().contains(".png")) {
            // JPG file
            intent.setDataAndType(uri, "image/jpeg");
        } else if(url.toString().contains(".3gp") || url.toString().contains(".mpg") || url.toString().contains(".mpeg") || url.toString().contains(".mpe") || url.toString().contains(".mp4") || url.toString().contains(".avi")) {
            // Video files
            intent.setDataAndType(uri, "video/*");
        } else {
            //if you want you can also define the intent type for any other file

            //additionally use else clause below, to manage other unknown extensions
            //in this case, Android will show all applications installed on the device
            //so you can choose which application to use
            intent.setDataAndType(uri, "*/*");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static String hashFileName (String filePath) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String fileName = new File(filePath).getName();
        String dirPath = filePath.substring(0, filePath.lastIndexOf(fileName));

        String ext = "";
        if (fileName.lastIndexOf(".") != -1) {
            ext = fileName.substring(fileName.lastIndexOf("."));
            fileName = fileName.substring(0, fileName.lastIndexOf(ext));
        }

        String hashName = CryptoFunctions.computeHash(fileName);
        String hashPath = dirPath + hashName + ext;
        //Log.i(MFSNAP_TAG, "File: " + filePath + ", FileName: " + fileName + ", DirPath: " + dirPath + ", Hash: " + hashPath);
        return hashPath;
    }
    public static void getStatInfo (String command, BufferedWriter statLog, String filePath, Boolean hashFileName, boolean asRoot) throws IOException,
                                                                                        InterruptedException, NoSuchAlgorithmException {
        Process pStat;
        if (!(new File(filePath)).exists())
            return;

        // Because a file path may contain spaces, make the past as a single argument.
        String[] statCommand;
        if (asRoot) {
            statCommand = new String[]{"su", "-c", command, "-c", "'%a;%A;%b;%B;%d;%f;%F;%g;%G;%h;%i;%m;%n;%N;%o;%s;%t;%T;%u;%U;%w;%W;%x;%X;%y;%Y;%z;%Z'",
                    filePath.trim()};
        } else {
            statCommand = new String[]{command, "-c", "'%a;%A;%b;%B;%d;%f;%F;%g;%G;%h;%i;%m;%n;%N;%o;%s;%t;%T;%u;%U;%w;%W;%x;%X;%y;%Y;%z;%Z'",
                    filePath.trim()};
        }
        pStat = Runtime.getRuntime().exec(statCommand);

        BufferedReader readerStat = new BufferedReader(new InputStreamReader(pStat.getInputStream()));
        BufferedReader errStat = new BufferedReader(new InputStreamReader(pStat.getErrorStream()));

        String line;
        while ((line = readerStat.readLine())!= null) {
            if (hashFileName) {
                String[] tokens = line.split(";");
                if (!tokens[6].equals("directory")) {
                    tokens[12] = hashFileName(tokens[12]);

                    String begin = tokens[13].substring(0,1);
                    String end = tokens[13].substring(tokens[13].length()-1);
                    tokens[13] = begin + hashFileName(tokens[13].substring(1, tokens[13].length()-1)) + end;

                    line = TextUtils.join(";", tokens);
                }
            }
            statLog.write(line);
            // Log.v(MFSNAP_TAG, line);
            statLog.newLine();
        }
        while ((line = errStat.readLine())!= null) {
            Log.e(MFSNAP_TAG, "Staterr: " + line);
        }
        pStat.waitFor();
        errStat.close();
        readerStat.close();
    }

    public static class SendFileToServer extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String DESTINATION_IP = params[0];
            String filePath = params[1];
            String deviceId = params[2];
            int port = 4444;

            StringBuffer output = new StringBuffer();

            try {
                Socket sock = new Socket(DESTINATION_IP, port);

                File myFile = new File (filePath);
                byte [] myByteArray  = new byte [(int)myFile.length()];

                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

                dos.writeUTF(deviceId);
                dos.flush();

                bis.read(myByteArray,0,myByteArray.length);
                dos.write(myByteArray,0,myByteArray.length);

                dos.flush();
                sock.close();
                bis.close();
                dos.close();
            } catch (UnknownHostException e) {
                output.append("UnknownHostException: " + e + "\n");
            } catch (IOException e) {
                output.append("IOException: " + e + "\n");
            } catch (Exception e) {
                output.append("Encountered exception: " + e + "\n");
            }
            return output.toString();
        }
        @Override
        protected void onPostExecute(String params) {
            //Task you want to do on UIThread after completing Network operation
            //onPostExecute is called after doInBackground finishes its task.
        }
    }
}
