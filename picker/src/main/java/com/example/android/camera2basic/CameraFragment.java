package com.example.android.camera2basic;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.android.camera2basic.adapter.GalleryAdapter;
import com.example.android.camera2basic.anim.AnimFadeReveal;
import com.example.android.camera2basic.base.PickerFragment;
import com.example.android.camera2basic.fileio.FileHandler;
import com.example.android.camera2basic.fileio.FilePathUtil;
import com.example.android.camera2basic.image.ImageEffectFragment;
import com.example.android.camera2basic.picker.MediaPickerOpts;
import com.example.android.camera2basic.tasks.LoadGLImageTask;
import com.example.android.camera2basic.tasks.LoadImageTask;
import com.example.android.camera2basic.tasks.SaveGLImageTask;
import com.example.android.camera2basic.utils.BitmapUtils;

import java.util.ArrayList;
import java.util.Timer;

import static android.app.Activity.RESULT_OK;
import static com.example.android.camera2basic.R.drawable.camera_btn_mode_down;
import static com.example.android.camera2basic.R.drawable.camera_btn_mode_up;
import static com.example.android.camera2basic.R.drawable.shutter;
import static com.example.android.camera2basic.R.drawable.shutter2;


public class CameraFragment extends PickerFragment implements OnClickListener {

    public final String TAG = "CameraFragment";

    private static final int REQUEST_GET_CONTENT = 1001;

    public static CameraFragment newInstance(@NonNull MediaPickerOpts opts) {
        Bundle args = new Bundle();
        args.putParcelable(MediaPickerOpts.INTENT_OPTS, opts);
        CameraFragment fragment = new CameraFragment();
        fragment.setArguments(args);
        return fragment;
    }


    private CameraGLView mCameraView;
    private ImageView mCameraSwitcher;

    private GalleryAdapter galleryAdapter;


    private ImageButton iv_filter;
    private Handler uiThreadHandler;


    private ImageView mRecordButton;
    FrameLayout function_layout;
    ImageButton arrow_button;

    private MediaPickerOpts opts;




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (opts == null) {
            //noinspection ConstantConditions
            opts = getArguments().getParcelable(MediaPickerOpts.INTENT_OPTS);
        }
        uiThreadHandler = new Handler();
    }


    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        final View rootView = inflater.inflate(R.layout.fragment_camera2_basic, container, false);

        mCameraView = rootView.findViewById(R.id.cameraView);
        mCameraSwitcher = rootView.findViewById(R.id.iv_switch_camera);

        iv_filter = rootView.findViewById(R.id.filter_button);
        mRecordButton = rootView.findViewById(R.id.picture);

        function_layout = rootView.findViewById(R.id.function_layout);
        arrow_button = rootView.findViewById(R.id.arrow_upward_button);

        mRecordButton.setOnClickListener(this);
        mCameraSwitcher.setOnClickListener(this);
        function_layout.setOnClickListener(this);
        arrow_button.setOnClickListener(this);
        iv_filter.setOnClickListener(this);
        rootView.findViewById(R.id.info).setOnClickListener(this);




        handleIntent();

        AnimFadeReveal.fadeIn(rootView);

        return rootView;
    }

    @SuppressWarnings("ConstantConditions")
    private void handleIntent() {
        mCameraView.init(opts.scaleType, opts.mediaType);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.HORIZONTAL);

        mCameraView.setPreviewEnabled(opts.showFilters());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume:");
        mCameraView.onResume();

        mCameraView.setCameraSwitcher(mCameraSwitcher);
        mCameraView.setFrag(this);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause:");
        mCameraView.onPause();

        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isRemoving() && galleryAdapter != null) {
            galleryAdapter.changeCursor(null);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || requestCode != REQUEST_GET_CONTENT || data.getData() == null) {
            return;
        }

        Uri fileUri = data.getData();
        String filePath = FilePathUtil.getRealPath(getContext(), fileUri, opts);

        if (filePath == null) return;

        Log.d(TAG, "selectedPath: " + filePath);

        if (galleryAdapter != null) {
            galleryAdapter.addSelected(filePath);
        }

        checkIfMediaSelectionCompleted();
    }

    @Override
    public void onClick(final View view) {
        final int id = view.getId();

        if (id == R.id.filter_button) {
            mCameraView.touched();
        } else if (id == R.id.iv_switch_camera) {
            mCameraView.toggleCamera();
        } else if (id == R.id.picture) {
            if (opts.mediaType == MediaType.IMAGE) {
                takePicture();
            } else {
            }
        }
        else if(id== R.id.arrow_upward_button){

                if(function_layout.getVisibility()==View.GONE) {
                    arrow_button.setImageResource(camera_btn_mode_down);
                    function_layout.setVisibility(View.VISIBLE);
                }
                else if(function_layout.getVisibility()==View.VISIBLE){
                    arrow_button.setImageResource(camera_btn_mode_up);
                    function_layout.setVisibility(View.GONE);
                }
        }

    }


    private void takePicture() {
        mRecordButton.setImageResource(shutter2);
        mRecordButton.setOnClickListener(null);
        mCameraView.queueEvent(() -> {
            GLDrawer2D drawer = mCameraView.getDrawer();
            int x = drawer.getStartX();
            int y = drawer.getStartY();
            int w = drawer.width();
            int h = drawer.height();

            int bitmapBuffer[] = BitmapUtils.readEGLBuffer(x, y, w, h);

            if (bitmapBuffer == null) return;

            if (opts.mediaType == MediaType.IMAGE && opts.cropEnabled) {
                ImageEffectFragment fragment = ImageEffectFragment.newInstance(opts);
                uiThreadHandler.post(() -> showFragment(fragment));
                new LoadGLImageTask(w, h, bitmapBuffer, fragment, opts).execute();
            } else {
                new SaveGLImageTask(w, h, bitmapBuffer, this, opts).execute();
            }
        });
    }

    public void onPictureSaved(String imagePath) {
        if (imagePath == null || !FileHandler.exists(imagePath)) {
            mRecordButton.setVisibility(View.VISIBLE);
            mRecordButton.setOnClickListener(this);
            return;
        }

        if (galleryAdapter != null) {
            galleryAdapter.addSelected(imagePath);
        }

        MediaScannerConnection.MediaScannerConnectionClient callBack = null;

        if (!checkIfMediaSelectionCompleted()) {
            mRecordButton.setVisibility(View.VISIBLE);
            mRecordButton.setOnClickListener(this);

            callBack = new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {

                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                    uiThreadHandler.post(() -> refreshAdapters());
                }
            };
        }

        //noinspection ConstantConditions
        scanFile(imagePath, "image/jpg", callBack);
    }


    private void refreshAdapters() {
        if (galleryAdapter != null) {
            loadGalleryAdapter();
        }
    }

    public void loadGalleryAdapter() {

        String[] projection;
        final String orderBy;
        Uri contentURI;

        if (opts.mediaType == MediaType.VIDEO) {
            projection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.MediaColumns.DATA,
            };

            orderBy = MediaStore.Video.Media.DATE_TAKEN;
            contentURI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        } else {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.MediaColumns.DATA,
            };

            orderBy = MediaStore.Images.Media.DATE_TAKEN;
            contentURI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        //noinspection ConstantConditions
        ContentResolver contentResolver = getContext().getContentResolver();

        Cursor cursor = contentResolver.query(contentURI,
                projection, null, null, orderBy + " DESC");

        if (cursor != null && cursor.moveToFirst()) {

            Log.d(TAG, "mediaCount: " + cursor.getCount());

            if (cursor.getCount() > 0) {
            }

            if (galleryAdapter == null) {
                galleryAdapter = new GalleryAdapter(cursor, opts.maxSelection, opts.mediaType, this);
            } else {
                galleryAdapter.changeCursor(cursor);
            }

        }
    }


    public boolean checkIfMediaSelectionCompleted() {
        int selectionCount = 0;

        if (galleryAdapter != null) {
            selectionCount = galleryAdapter.getSelectionCount();
        }

        mRecordButton.setImageResource(shutter);

        if (selectionCount == opts.maxSelection) {
            Log.d(TAG, "onMaxSelection");
            uiThreadHandler.post(this::onClickDone);
            return true;
        }

        return false;
    }

    @SuppressWarnings("ConstantConditions")
    private void onClickDone() {
        ArrayList<String> items = new ArrayList<>();

        if (galleryAdapter != null) {
            galleryAdapter.fill(items);
            galleryAdapter.clearSelection();
        }

            if (opts.mediaType == MediaType.IMAGE) {
            ImageEffectFragment fragment = ImageEffectFragment.newInstance(opts);
                showFragment(fragment);
                new LoadImageTask(items.remove(0), fragment).execute();

                return;
        }

        if (opts.mediaType == MediaType.IMAGE && opts.imgSize > 0) {
            String imagePath = items.remove(0);
            String newPath = BitmapUtils.createCroppedBitmap(imagePath, opts);
            if (!imagePath.equals(newPath)) {
                scanFile(newPath, "image/jpg", null);
            }
            items.add(0, newPath);
        }

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra(MediaPickerOpts.INTENT_RES, items);
        FragmentActivity activity = getActivity();
        activity.setResult(RESULT_OK, resultIntent);
        activity.supportFinishAfterTransition();
    }

    @SuppressWarnings("ConstantConditions")
    private void scanFile(String mediaPath, String mimeType, OnScanCompletedListener callback) {
        MediaScannerConnection.scanFile(getContext().getApplicationContext(), new String[]{
                mediaPath
        }, new String[]{
                mimeType
        }, callback);
    }

}