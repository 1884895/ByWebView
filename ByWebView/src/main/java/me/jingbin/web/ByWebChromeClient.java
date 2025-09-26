package me.jingbin.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.provider.MediaStore;
import android.os.Environment;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by jingbin on 2019/07/27.
 * - 播放网络视频配置
 * - 上传图片(兼容)
 */
public class ByWebChromeClient extends WebChromeClient {

    private WeakReference<Activity> mActivityWeakReference;
    private ByWebView mByWebView;
    private ValueCallback<Uri> mUploadMessage;

    private OnChromeClientCallback onChromeClientCallback;
    private ValueCallback<Uri[]> mUploadMessageForAndroid5;
    private static final int RESULT_CODE_FILE_CHOOSER = 1;
    private static final int RESULT_CODE_FILE_CHOOSER_FOR_ANDROID_5 = 2;
    private static final String TAG = "ByWebChromeClient";
    private Uri mCameraImageUri;
    private String mCameraPhotoPath;

    private View mProgressVideo;
    private View mCustomView;
    private CustomViewCallback mCustomViewCallback;
    private ByFullscreenHolder videoFullView;
    private OnTitleProgressCallback onByWebChromeCallback;
    // 修复可能部分h5无故横屏问题
    private boolean isFixScreenLandscape = false;
    // 修复可能部分h5无故竖屏问题
    private boolean isFixScreenPortrait = false;

    ByWebChromeClient(Activity activity, ByWebView byWebView) {
        mActivityWeakReference = new WeakReference<Activity>(activity);
        this.mByWebView = byWebView;
    }

    void setOnByWebChromeCallback(OnTitleProgressCallback onByWebChromeCallback) {
        this.onByWebChromeCallback = onByWebChromeCallback;
    }

    public void setFixScreenLandscape(boolean fixScreenLandscape) {
        isFixScreenLandscape = fixScreenLandscape;
    }

    public void setFixScreenPortrait(boolean fixScreenPortrait) {
        isFixScreenPortrait = fixScreenPortrait;
    }

    /**
     * 播放网络视频时全屏会被调用的方法
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity != null && !mActivity.isFinishing()) {
            if (!isFixScreenLandscape) {
                if (onByWebChromeCallback == null || !onByWebChromeCallback.onHandleScreenOrientation(true)) {
                    // 为空或返回为true时，自己处理横竖屏。否则全屏时默认设置为横屏
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            }
            mByWebView.getWebView().setVisibility(View.INVISIBLE);

            // 如果一个视图已经存在，那么立刻终止并新建一个
            if (mCustomView != null) {
                callback.onCustomViewHidden();
                return;
            }

            if (videoFullView == null) {
                FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
                videoFullView = new ByFullscreenHolder(mActivity);
                decor.addView(videoFullView);
            }
            videoFullView.addView(view);

            mCustomView = view;
            mCustomViewCallback = callback;
            videoFullView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 视频播放退出全屏会被调用的
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onHideCustomView() {
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity != null && !mActivity.isFinishing()) {
            // 不是全屏播放状态
            if (mCustomView == null) {
                return;
            }
            // 还原到之前的屏幕状态
            if (!isFixScreenPortrait) {
                if (onByWebChromeCallback == null || !onByWebChromeCallback.onHandleScreenOrientation(false)) {
                    // 为空或返回为true时，自己处理横竖屏。否则默认设置为竖屏
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }

            mCustomView.setVisibility(View.GONE);
            if (videoFullView != null) {
                videoFullView.removeView(mCustomView);
                videoFullView.setVisibility(View.GONE);
            }
            mCustomView = null;
            mCustomViewCallback.onCustomViewHidden();
            mByWebView.getWebView().setVisibility(View.VISIBLE);
        }
    }

    /**
     * 视频加载时loading
     */
    @Override
    public View getVideoLoadingProgressView() {
        if (mProgressVideo == null) {
            mProgressVideo = LayoutInflater.from(mByWebView.getWebView().getContext()).inflate(R.layout.by_video_loading_progress, null);
        }
        return mProgressVideo;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        // 进度条
        if (mByWebView.getProgressBar() != null) {
            mByWebView.getProgressBar().setWebProgress(newProgress);
        }
        // 当显示错误页面时，进度达到100才显示网页
        if (mByWebView.getWebView() != null
                && mByWebView.getWebView().getVisibility() == View.INVISIBLE
                && (mByWebView.getErrorView() == null || mByWebView.getErrorView().getVisibility() == View.GONE)
                && newProgress == 100) {
            mByWebView.getWebView().setVisibility(View.VISIBLE);
        }
        if (onByWebChromeCallback != null) {
            onByWebChromeCallback.onProgressChanged(newProgress);
        }
    }

    /**
     * 判断是否是全屏
     */
    boolean inCustomView() {
        return (mCustomView != null);
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        // 设置title
        if (onByWebChromeCallback != null) {
            if (mByWebView.getErrorView() != null && mByWebView.getErrorView().getVisibility() == View.VISIBLE) {
                onByWebChromeCallback.onReceivedTitle(TextUtils.isEmpty(mByWebView.getErrorTitle()) ? "网页无法打开" : mByWebView.getErrorTitle());
            } else {
                onByWebChromeCallback.onReceivedTitle(title);
            }
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        if (onByWebChromeCallback != null && onByWebChromeCallback.onJsAlert(view, url, message, result)) {
            return true;
        }
        Dialog alertDialog = new AlertDialog.Builder(view.getContext()).
                setMessage(message).
                setCancelable(false).
                setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.confirm();
                    }
                })
                .create();
        alertDialog.show();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        if (onByWebChromeCallback != null && onByWebChromeCallback.onJsConfirm(view, url, message, result)) {
            return true;
        }
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == Dialog.BUTTON_POSITIVE) {
                    result.confirm();
                } else {
                    result.cancel();
                }
            }
        };
        new AlertDialog.Builder(view.getContext())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, listener)
                .setNegativeButton(android.R.string.cancel, listener).show();
        return true;
    }

    @Override
    public boolean onJsPrompt(final WebView view, String url, final String message, final String defaultValue, final JsPromptResult result) {
        if (onByWebChromeCallback != null && onByWebChromeCallback.onJsPrompt(view, url, message, defaultValue, result)) {
            return true;
        }
        view.post(new Runnable() {
            @Override
            public void run() {
                final EditText editText = new EditText(view.getContext());
                editText.setText(defaultValue);
                if (defaultValue != null) {
                    editText.setSelection(defaultValue.length());
                }
                float dpi = view.getContext().getResources().getDisplayMetrics().density;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == Dialog.BUTTON_POSITIVE) {
                            result.confirm(editText.getText().toString());
                        } else {
                            result.cancel();
                        }
                    }
                };
                new AlertDialog.Builder(view.getContext())
                        .setTitle(message)
                        .setView(editText)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener)
                        .show();
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                int t = (int) (dpi * 16);
                layoutParams.setMargins(t, 0, t, 0);
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                editText.setLayoutParams(layoutParams);
                int padding = (int) (15 * dpi);
                editText.setPadding(padding - (int) (5 * dpi), padding, padding, padding);
            }
        });
        return true;
    }

    //扩展浏览器上传文件
    //3.0++版本
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        openFileChooserImpl(uploadMsg);
    }

    //3.0--版本
    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        openFileChooserImpl(uploadMsg);
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        openFileChooserImpl(uploadMsg);
    }

    // For Android > 5.0
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> uploadMsg, FileChooserParams fileChooserParams) {
        openFileChooserImplForAndroid5(uploadMsg);
        return true;
    }

    private void openFileChooserImpl(ValueCallback<Uri> uploadMsg) {
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity != null && !mActivity.isFinishing()) {
            mUploadMessage = uploadMsg;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            mActivity.startActivityForResult(Intent.createChooser(intent, "文件选择"), RESULT_CODE_FILE_CHOOSER);
        }
    }

    private void openFileChooserImplForAndroid5(ValueCallback<Uri[]> uploadMsg) {
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity != null && !mActivity.isFinishing()) {
            mUploadMessageForAndroid5 = uploadMsg;
            if (onChromeClientCallback != null && onChromeClientCallback.openFileChooserIntent() != null) {
                mActivity.startActivityForResult(onChromeClientCallback.openFileChooserIntent(), RESULT_CODE_FILE_CHOOSER_FOR_ANDROID_5);
            } else {
                List<Intent> intentList = new ArrayList<>();

                // 创建相机拍照Intent
                Intent takePictureIntent = null;
                File photoFile = null;

                try {
                    photoFile = createImageFile(mActivity);
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                        takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        // Android 7.0+ 需要使用 FileProvider
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                mCameraImageUri = FileProvider.getUriForFile(mActivity,
                                        mActivity.getPackageName() + ".fileprovider", photoFile);
                                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (IllegalArgumentException e) {
                                Log.e(TAG, "FileProvider配置错误，请检查AndroidManifest.xml: " + e.getMessage());
                                // 如果FileProvider未配置，降级使用Uri.fromFile
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                                    mCameraImageUri = Uri.fromFile(photoFile);
                                } else {
                                    // Android N及以上必须使用FileProvider
                                    takePictureIntent = null;
                                }
                            }
                        } else {
                            mCameraImageUri = Uri.fromFile(photoFile);
                        }

                        if (takePictureIntent != null && mCameraImageUri != null) {
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraImageUri);

                            // 解决部分手机拍照后无法获取数据问题
                            // Android 11+ 需要额外处理包可见性
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // Android 11及以上不使用resolveActivity，直接尝试添加
                                intentList.add(takePictureIntent);
                            } else if (takePictureIntent.resolveActivity(mActivity.getPackageManager()) != null) {
                                intentList.add(takePictureIntent);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "创建临时文件失败: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "相机初始化失败: " + e.getMessage());
                }

                // 创建图片选择Intent
                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("image/*");

                // Android 11+ 需要额外处理
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                }

                // 创建选择器Intent
                Intent chooserIntent = Intent.createChooser(contentSelectionIntent, "选择图片");
                if (!intentList.isEmpty()) {
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                            intentList.toArray(new Intent[0]));
                }

                try {
                    mActivity.startActivityForResult(chooserIntent, RESULT_CODE_FILE_CHOOSER_FOR_ANDROID_5);
                } catch (Exception e) {
                    Log.e(TAG, "启动文件选择器失败: " + e.getMessage());
                    mUploadMessageForAndroid5.onReceiveValue(null);
                    mUploadMessageForAndroid5 = null;
                }
            }
        }
    }

    private File createImageFile(Activity activity) throws IOException {
        // 创建图片文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File storageDir = null;

        // Android 10+ 使用应用专属目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上，使用应用专属目录，不需要存储权限
            storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        } else {
            // Android 10以下版本
            // 优先使用外部存储的应用专属目录
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            }

            // 如果外部存储不可用，使用内部存储
            if (storageDir == null) {
                storageDir = activity.getFilesDir();
            }
        }

        // 确保目录存在
        if (storageDir != null && !storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e(TAG, "创建图片目录失败");
            }
        }

        // 创建临时文件
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".jpg",        /* 后缀 */
                storageDir     /* 目录 */
        );

        return image;
    }

    /**
     * 5.0以下 上传图片成功后的回调
     */
    private void uploadMessage(Intent intent, int resultCode) {
        if (null == mUploadMessage) {
            return;
        }
        Uri result = intent == null || resultCode != Activity.RESULT_OK ? null : intent.getData();
        mUploadMessage.onReceiveValue(result);
        mUploadMessage = null;
    }

    /**
     * 5.0以上 上传图片成功后的回调
     */
    private void uploadMessageForAndroid5(Intent intent, int resultCode) {
        if (null == mUploadMessageForAndroid5) {
            return;
        }

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (intent != null && intent.getData() != null) {
                // 从图库选择的图片
                String dataString = intent.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            } else if (mCameraPhotoPath != null) {
                // 相机拍照返回
                // Android 12+ 需要特殊处理
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 使用保存的Uri
                    if (mCameraImageUri != null) {
                        results = new Uri[]{mCameraImageUri};
                    } else {
                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                } else if (mCameraImageUri != null) {
                    // Android 7.0-11 使用FileProvider的Uri
                    results = new Uri[]{mCameraImageUri};
                } else {
                    // Android 7.0以下
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                }
            }
        }

        // 回调结果
        if (results != null) {
            mUploadMessageForAndroid5.onReceiveValue(results);
        } else {
            mUploadMessageForAndroid5.onReceiveValue(new Uri[]{});
        }

        // 清理
        mUploadMessageForAndroid5 = null;
        mCameraImageUri = null;
        mCameraPhotoPath = null;
    }

    /**
     * 用于Activity的回调
     */
    public void handleFileChooser(int requestCode, int resultCode, Intent intent) {
        if (requestCode == RESULT_CODE_FILE_CHOOSER) {
            uploadMessage(intent, resultCode);
        } else if (requestCode == RESULT_CODE_FILE_CHOOSER_FOR_ANDROID_5) {
            uploadMessageForAndroid5(intent, resultCode);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        super.onPermissionRequest(request);
        // 部分页面可能崩溃
//        request.grant(request.getResources());
    }

    ByFullscreenHolder getVideoFullView() {
        return videoFullView;
    }

    @Nullable
    @Override
    public Bitmap getDefaultVideoPoster() {
        if (super.getDefaultVideoPoster() == null) {
            return BitmapFactory.decodeResource(mByWebView.getWebView().getResources(), R.drawable.by_icon_video);
        } else {
            return super.getDefaultVideoPoster();
        }
    }

    public void setOnChromeClientCallback(OnChromeClientCallback onChromeClientCallback) {
        this.onChromeClientCallback = onChromeClientCallback;
    }
}
