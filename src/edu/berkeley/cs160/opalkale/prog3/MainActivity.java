package edu.berkeley.cs160.opalkale.prog3;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import edu.berkeley.cs160.opalkale.prog3.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.media.AudioTrack;
import android.net.Uri;
import android.widget.FrameLayout;
import be.hogent.tarsos.dsp.AudioEvent;
import be.hogent.tarsos.dsp.onsets.PercussionOnsetDetector;
import be.hogent.tarsos.dsp.onsets.OnsetHandler;

public class MainActivity extends Activity implements OnsetHandler {
	private Camera mCamera;
	private Preview mPreview;
	private byte[] buffer;
	private AudioRecord recorder;
	private boolean mIsRecording;
	private Button mListenButton;
	private Button mShare;
	private TextView mTextField;
	private PercussionOnsetDetector mPercussionOnsetDetector;
	private be.hogent.tarsos.dsp.AudioFormat tarsosFormat;
	private int clap;
	private Bitmap bitmap;
	static final int SAMPLE_RATE = 8000;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mCamera = getCameraInstance();
		mPreview = new Preview(this, mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

		// set recording boolean to false
		mIsRecording = false;
		clap = 0;
		mTextField = (TextView) findViewById(R.id.pitch);

		// button to initialize call back
		mListenButton = (Button) findViewById(R.id.listen);
		mListenButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mIsRecording) {
					mListenButton.setText("Listen");
					mIsRecording = false;
				} else {
					mListenButton.setText("Stop listening");
					mIsRecording = true;
					// STEP 5: start recording and detect clap
					listen();
					// END STEP 5
				}
			}
		});

		mShare = (Button) findViewById(R.id.share);

		// STEP 1: setup AudioRecord
		int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

		buffer = new byte[minBufferSize];

		recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				minBufferSize);
		// END STEP 1

		// STEP 2: create detector

		mPercussionOnsetDetector = new PercussionOnsetDetector(SAMPLE_RATE,
				(minBufferSize / 2), this, 24, 5);

		// END STEP 2

	}

	// STEP 3: handle Onset event

	@Override
	public void handleOnset(double time, double salience) {

		System.out.println(String.format("%.4f;%.4f", time, salience));
		clap += 1;

		// have we detected a pitch?
		if (clap == 2) {

			mIsRecording = false;

			// handlePitch will be run from a background thread
			// so we need to run it on the UI thread
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mListenButton.setText("Listen");

					new CountDownTimer(5000, 1000) {

						public void onTick(long millisUntilFinished) {
							mTextField.setText("" + millisUntilFinished / 1000);
						}

						public void onFinish() {
							mTextField.setText("done!");
							mCamera.takePicture(null, null, mPicture);
						}
					}.start();

				}
			});

		} else {

		}

	}

	// END STEP 3

	// STEP 4: Setup recording
	public void listen() {
		recorder.startRecording();

		tarsosFormat = new be.hogent.tarsos.dsp.AudioFormat(
				(float) SAMPLE_RATE, // sample rate
				16, // bit depth
				1, // channels
				true, // signed samples?
				false // big endian?
		);

		Thread listeningThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (mIsRecording) {
					int bufferReadResult = recorder.read(buffer, 0,
							buffer.length);
					AudioEvent audioEvent = new AudioEvent(tarsosFormat,
							bufferReadResult);
					audioEvent.setFloatBufferWithByteBuffer(buffer);
					mPercussionOnsetDetector.process(audioEvent);

				}
				recorder.stop();
				System.out.println("recorder stopped");
			}

		});

		listeningThread.start();

	}

	// END STEP 4

	public Camera getCameraInstance() {
		Camera camera = null;
		try {
			camera = Camera.open();
		} catch (Exception e) {
			// cannot get camera or does not exist
		}
		return camera;
	}

	PictureCallback mPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			final File pictureFile = getOutputMediaFile();

			// End Picture

			if (pictureFile == null) {
				return;
			}
			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				// bitmap= BitmapFactory.decodeByteArray(data, 0, data.length);
				// bitmap = Bitmap.createScaledBitmap(bitmap, 480, 480, false);
				mShare.setOnClickListener(new View.OnClickListener() {

					public void onClick(View v) {

						Intent shareIntent = new Intent(Intent.ACTION_SEND);
						shareIntent.setType("image/jpeg");
						shareIntent.putExtra(Intent.EXTRA_STREAM,
								Uri.fromFile(pictureFile));
						startActivity(Intent.createChooser(shareIntent,
								"Share Image"));
					}
				});
				fos.close();
			} catch (FileNotFoundException e) {

			} catch (IOException e) {
			}
		}
	};

	private static File getOutputMediaFile() {
		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"ClapperApp");
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("ClapperApp", "failed to create directory");
				return null;
			}
		}
		// Create a media file name
		// String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
		// .format(new Date());
		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ "myPic.jpg");

		return mediaFile;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);

		return true;
	}

}
