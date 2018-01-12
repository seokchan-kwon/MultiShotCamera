package com.example.seokchankwon.multishotcamera.activity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.seokchankwon.multishotcamera.GlobalConstant;
import com.example.seokchankwon.multishotcamera.R;
import com.example.seokchankwon.multishotcamera.util.FileUtil;
import com.example.seokchankwon.multishotcamera.util.GlobalApplication;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraUtils;
import com.otaliastudios.cameraview.CameraView;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by seokchan.kwon on 2018. 1. 8..
 */

public class CameraActivity extends AppCompatActivity {

    public static final String SAVED_CAPTURE_PATHS = "saved.capture_paths";
    public static final String EXTRA_LIMIT_CAPTURE_COUNT = "extra.limit_capture_count";

    public static final String REQUEST_EXTRA_CAPTURE_PATHS = "request_extra.capture_paths";

    public static final int REQUEST_CODE_CAMERA_PREVIEW_ACTIVITY = 1000;

    private int mLimitCaptureCount;

    private CameraView mCameraView;

    private Button btCameraRecord;

    private ImageView ivLatestCapture;

    private ImageButton ibtCapturesSend;
    private ImageButton ibtCameraFinish;

    private FrameLayout flCaptureContainer;

    private ArrayList<String> mCapturePaths;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initView();
        initCameraView();

        setupInstanceState(savedInstanceState);

        updateDisplayViews();

        // 촬영버튼 클릭
        btCameraRecord.setOnClickListener(v -> takePicture());

        // 찍은 사진 보기
        flCaptureContainer.setOnClickListener(v -> movingCapturePreviewActivity());

        // 찍은 사진 보내기
        ibtCapturesSend.setOnClickListener(v -> sendCaptureImage());

        // 카메라 종료
        ibtCameraFinish.setOnClickListener(v -> cancelCameraActivity());

    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraView.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraView.destroy();
    }

    @Override
    public void onBackPressed() {
        cancelCameraActivity();
    }

    private void setupInstanceState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mCapturePaths = savedInstanceState.getStringArrayList(SAVED_CAPTURE_PATHS);
            mLimitCaptureCount = savedInstanceState.getInt(EXTRA_LIMIT_CAPTURE_COUNT, 10);

        } else {
            mCapturePaths = new ArrayList<>();
            mLimitCaptureCount = getIntent().getIntExtra(EXTRA_LIMIT_CAPTURE_COUNT, 10);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_LIMIT_CAPTURE_COUNT, mLimitCaptureCount);
        outState.putStringArrayList(SAVED_CAPTURE_PATHS, mCapturePaths);
    }

    private void initView() {
        mCameraView = findViewById(R.id.fl_activity_camera_view);
        btCameraRecord = findViewById(R.id.bt_activity_camera_record);
        ivLatestCapture = findViewById(R.id.iv_activity_camera_latest_capture);
        ibtCapturesSend = findViewById(R.id.ibt_activity_camera_capture_send);
        ibtCameraFinish = findViewById(R.id.ibt_activity_camera_close);
        flCaptureContainer = findViewById(R.id.fl_activity_camera_capture_container);
    }

    private void initCameraView() {
        mCameraView.addCameraListener(new CameraListener() {

            @Override
            public void onPictureTaken(byte[] jpeg) {
                super.onPictureTaken(jpeg);
                byteToFile(jpeg);
            }

            @Override
            public void onCameraError(@NonNull CameraException exception) {
                super.onCameraError(exception);
                btCameraRecord.setEnabled(true);
            }

            @Override
            public void onOrientationChanged(int orientation) {
                super.onOrientationChanged(orientation);
                startRotateViewAnimation(orientation);
            }
        });
    }

    private void takePicture() {
        if (mCapturePaths.size() >= mLimitCaptureCount) {
            String toastMsg = getString(R.string.take_picture_max)
                    + " " + String.valueOf(mLimitCaptureCount)
                    + getString(R.string.take_picture_max_select);
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
            return;
        }
        mCameraView.capturePicture();
        setDisplayViewsEnable(false);
    }

    private void sendCaptureImage() {

        new Thread(() -> {
            for (String path : mCapturePaths) {
                updateGallery(path);
            }
        }).start();

        Intent intent = new Intent();
        intent.putStringArrayListExtra(REQUEST_EXTRA_CAPTURE_PATHS, mCapturePaths);
        setResult(RESULT_OK, intent);

        finish();
    }

    private void cancelCameraActivity() {
        new Thread(() -> {
            for (String path : mCapturePaths) {
                FileUtil.deleteFile(path);
            }
        }).start();

        finish();
    }

    private void byteToFile(byte[] jpeg) {
        CameraUtils.decodeBitmap(jpeg, bitmap ->
                new Thread(() -> {

                    Bitmap resizedBitmap = FileUtil.resizeBitmap(bitmap, 720);
                    String fileName = String.valueOf(System.currentTimeMillis()) + ".jpg";

                    File file = FileUtil.bitmapToFile(GlobalConstant.APP_IMAGE_DIR_PATH, fileName, resizedBitmap);
                    if (file == null) {
                        return;
                    }

                    String path = file.getAbsolutePath();
                    mCapturePaths.add(path);

                    if (!isFinishing()) {
                        updateDisplayViews();
                        setDisplayViewsEnable(true);
                    }

                }).start()
        );
    }

    private void movingCapturePreviewActivity() {
        if (mCapturePaths.size() > 0) {
            Intent intent = new Intent(this, CameraPreviewActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putStringArrayListExtra(CameraPreviewActivity.EXTRA_CAPTURE_PATHS, mCapturePaths);
            startActivityForResult(intent, REQUEST_CODE_CAMERA_PREVIEW_ACTIVITY);
        }
    }

    @WorkerThread
    private void updateDisplayViews() {
        int captureSize = mCapturePaths.size();

        runOnUiThread(() -> {
            if (captureSize <= 0) {

                if (flCaptureContainer.getVisibility() == View.VISIBLE) {
                    flCaptureContainer.setVisibility(View.GONE);
                }

            } else {
                String latestCapturePath = mCapturePaths.get(captureSize - 1);

                Glide.with(this)
                        .load(latestCapturePath)
                        .apply(new RequestOptions()
                                .circleCrop())
                        .into(ivLatestCapture);

                if (flCaptureContainer.getVisibility() != View.VISIBLE) {
                    flCaptureContainer.setVisibility(View.VISIBLE);
                }
            }
            btCameraRecord.setText(String.valueOf(captureSize));
        });
    }

    private void setDisplayViewsEnable(boolean isEnable) {
        runOnUiThread(() -> {
            if (ibtCameraFinish.isEnabled() != isEnable) {
                ibtCameraFinish.setEnabled(isEnable);
            }
            if (ibtCapturesSend.isEnabled() != isEnable) {
                ibtCapturesSend.setEnabled(isEnable);
            }
            if (btCameraRecord.isEnabled() != isEnable) {
                btCameraRecord.setEnabled(isEnable);
            }
            if (flCaptureContainer.isEnabled() != isEnable) {
                flCaptureContainer.setEnabled(isEnable);
            }
        });
    }

    private void startRotateViewAnimation(int degrees) {

        if (degrees == 270) {
            degrees = -90;
        }

        btCameraRecord.animate()
                .rotation(-degrees)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(500)
                .start();

        flCaptureContainer.animate()
                .rotation(-degrees)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(500)
                .start();

        ibtCapturesSend.animate()
                .rotation(-degrees)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(500)
                .start();

        ibtCameraFinish.animate()
                .rotation(-degrees)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(500)
                .start();

    }

    private void updateGallery(String path) {
        if (!TextUtils.isEmpty(path) && path.contains(GlobalConstant.APP_IMAGE_DIR_PATH)) {
            ContentValues values = new ContentValues();

            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.DATA, path);

            ContentResolver contentResolver = GlobalApplication.getContext().getContentResolver();
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_CAMERA_PREVIEW_ACTIVITY:
                ArrayList<String> newCapturePaths =
                        data.getStringArrayListExtra(CameraPreviewActivity.REQUEST_EXTRA_CAPTURE_PATHS);

                if (mCapturePaths.size() != newCapturePaths.size()) {
                    mCapturePaths.clear();
                    mCapturePaths.addAll(newCapturePaths);
                }

                updateDisplayViews();
                break;
        }
    }
}
