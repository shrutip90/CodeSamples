package edu.gatech.epl.mfsnap.statapp;
import java.io.File;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {
    private final String statLogFinalPath = "/sdcard/statLog.txt";
    private String DESTINATION_IP = "143.215.204.252";

	private Button btnGetStats;
    private ImageButton btnChangeIp;
    private ImageButton btnChangeHash;
    private TextView textViewIp;
    private TextView textViewHash;
    private ProgressBar progress;

    private Button btnViewData;
    private Button btnSendData;

	private TextView out;
    final Context context = this;
    ShellExecutor exe;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_main);
		
		btnGetStats = (Button)findViewById(R.id.btnGetStats);
        btnChangeIp = (ImageButton)findViewById(R.id.btnChangeIp);
        btnChangeHash = (ImageButton)findViewById(R.id.btnChangeHash);

        textViewIp = (TextView)findViewById(R.id.textViewIp);
        textViewHash = (TextView)findViewById(R.id.textViewHash);
        progress = (ProgressBar)findViewById(R.id.progress);

        btnViewData = (Button)findViewById(R.id.btnViewData);
        btnSendData = (Button)findViewById(R.id.btnSendData);

	    out = (TextView)findViewById(R.id.out);
	    exe = new ShellExecutor(context);

	    File statLib = new File(getFilesDir(), "stat");
		try {
			FileOperations.copyRawFile(context, R.raw.stat, statLib, "777");
		} catch (Exception e) {
            out.setText("Exception encountered while copying the stat library");
			e.printStackTrace();
		}
	    
		btnGetStats.setOnClickListener(new View.OnClickListener() {
	        @Override
	        public void onClick(View arg0) {
                progress.setVisibility(View.INVISIBLE);
                btnViewData.setVisibility(View.INVISIBLE);
                btnSendData.setVisibility(View.INVISIBLE);
                out.setText("");
                NetworkOperations netconn = new NetworkOperations(context);
                Boolean connState = Boolean.FALSE;
                try {
                    connState = (netconn.new HasActiveNetworkConnection()).execute("").get();
                } catch (Exception e) {
                    out.setText("Exception encountered while checking for Network connection");
                    e.printStackTrace();
                }
                if (connState == Boolean.FALSE) {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    alertDialogBuilder.setTitle("Please Verify Your Network Connection and Try Again");
                    alertDialogBuilder
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                } else {
                    setProgressBarIndeterminateVisibility(true);

                    exe.new getFileSystemStats(progress, out, btnViewData, btnSendData).execute(0);
                    setProgressBarIndeterminateVisibility(false);
                }
	      }
	    });
        btnChangeIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                LayoutInflater layoutInflater = LayoutInflater.from(context);
                View promptView = layoutInflater.inflate(R.layout.input_prompt, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                // set prompts.xml to be the layout file of the alertdialog builder
                alertDialogBuilder.setView(promptView);

                final EditText inputIp = (EditText) promptView.findViewById(R.id.userInputIp);

                // setup a dialog window
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // get user input and set it to result
                                if (NetworkOperations.validateIP(inputIp.getText().toString())) {
                                    DESTINATION_IP = inputIp.getText().toString();
                                    textViewIp.setText(inputIp.getText());
                                } else {
                                    AlertDialog.Builder errorAlertDialogBuilder = new AlertDialog.Builder(context);
                                    errorAlertDialogBuilder.setTitle("Incorrect IP address");
                                    errorAlertDialogBuilder
                                            .setCancelable(false)
                                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            });
                                    AlertDialog alertDialog = errorAlertDialogBuilder.create();
                                    alertDialog.show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,	int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alertD = alertDialogBuilder.create();
                alertD.show();
            }
        });
        btnChangeHash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                LayoutInflater layoutInflater = LayoutInflater.from(context);
                final View promptView = layoutInflater.inflate(R.layout.radio_prompt, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                // set prompts.xml to be the layout file of the alertdialog builder
                alertDialogBuilder.setView(promptView);

                final RadioGroup radioHashGroup = (RadioGroup) promptView.findViewById(R.id.radioHash);

                // setup a dialog window
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // get user input and set it to result
                                RadioButton radioHashButton = (RadioButton) promptView.findViewById(radioHashGroup.getCheckedRadioButtonId());
                                exe.setHashingScheme(radioHashButton.getText().toString());
                                textViewHash.setText(radioHashButton.getText());
                            }
                        })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,	int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alertD = alertDialogBuilder.create();
                alertD.show();
            }
        });
        btnViewData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    File myFile = new File(statLogFinalPath);
                    FileOperations.openFile(context, myFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        btnSendData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                System.out.println(new FileOperations.SendFileToServer().execute(DESTINATION_IP, statLogFinalPath, DeviceInfo.getDeviceId(context)));
                out.append("Finished transferring the stat file\n");
            }
        });
	}
		
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
