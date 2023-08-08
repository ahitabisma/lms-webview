@file:Suppress("DEPRECATION", "LocalVariableName", "UNUSED_ANONYMOUS_PARAMETER")

package com.example.lms

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.onesignal.OneSignal
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// OneSignal App ID
const val ONESIGNAL_APP_ID = "cc8d476f-f3d8-44d1-af24-97cf4c6eee4c"

class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    // Declare Variable
    private lateinit var webView : WebView
    private val url = "https://ahitabisma.github.io/lms/"
    private lateinit var refreshLayout: SwipeRefreshLayout
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val file_type = "*/*"
    private var cam_file_data: String? = null
    private var file_data: ValueCallback<Uri>? = null
    private var file_path: ValueCallback<Array<Uri?>?>? = null
    private val file_req_code = 1

    private var backPressedTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.LMS)
        webView.webViewClient = MyWebViewClient()
        webView.loadUrl(url)
        val javascriptInterface = MyJavascriptInterface(this)

        //Download File
        webView.setDownloadListener(DownloadListener { urlDownload, userAgent, contentDisposition, mimeType, length ->
            val request = DownloadManager.Request(Uri.parse(urlDownload))
            request.setMimeType(mimeType)
            val cookies = CookieManager.getInstance().getCookie(urlDownload)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading File ...")
            request.setTitle(URLUtil.guessFileName(urlDownload, contentDisposition, userAgent))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(
                    urlDownload, contentDisposition, mimeType
                )
            )
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)

            Toast.makeText(this, "Mengunduh File ...", Toast.LENGTH_SHORT).show()
        })


        // Add JavaScriptInterface to WebView
        webView.addJavascriptInterface(javascriptInterface, "AndroidInterface")

        // Declare Variable WebSetting
        val webSettings = webView.settings

        // Enable Javascript, CSS
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true

        // Enable Camera, Location, Import File
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.setGeolocationEnabled(true)

        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 0
        )

        webView.webChromeClient = object : WebChromeClient() {
            // Input File
            @SuppressLint("ObsoleteSdkInt", "QueryPermissionsNeeded")
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri?>?>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                return if (file_permission() && Build.VERSION.SDK_INT >= 21) {
                    file_path = filePathCallback
                    var takePictureIntent: Intent? = null
                    var takeVideoIntent: Intent? = null
                    var includeVideo = false
                    var includePhoto = false

                    paramCheck@ for (acceptTypes in fileChooserParams.acceptTypes) {
                        val splitTypes =
                            acceptTypes.split(", ?+".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        for (acceptType in splitTypes) {
                            when (acceptType) {
                                "*/*" -> {
                                    includePhoto = true
                                    includeVideo = true
                                    break@paramCheck
                                }
                                "image/*" -> includePhoto = true
                                "video/*" -> includeVideo = true
                            }
                        }
                    }
                    if (fileChooserParams.acceptTypes.isEmpty()) {
                        includePhoto = true
                        includeVideo = true
                    }
                    if (includePhoto) {
                        takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if (takePictureIntent.resolveActivity(this@MainActivity.packageManager) != null) {
                            var photoFile: File? = null
                            try {
                                photoFile = create_image()
                                takePictureIntent.putExtra("PhotoPath", cam_file_data)
                            } catch (ex: IOException) {
                                Log.e(ContentValues.TAG, "Image file creation failed", ex)
                            }
                            if (photoFile != null) {
                                cam_file_data = "file:" + photoFile.absolutePath
                                takePictureIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    Uri.fromFile(photoFile)
                                )
                            } else {
                                cam_file_data = null
                                takePictureIntent = null
                            }
                        }
                    }
                    if (includeVideo) {
                        takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        if (takeVideoIntent.resolveActivity(this@MainActivity.packageManager) != null) {
                            var videoFile: File? = null
                            try {
                                videoFile = create_video()
                            } catch (ex: IOException) {
                                Log.e(ContentValues.TAG, "Video file creation failed", ex)
                            }
                            if (videoFile != null) {
                                cam_file_data = "file:" + videoFile.absolutePath
                                takeVideoIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    Uri.fromFile(videoFile)
                                )
                            } else {
                                cam_file_data = null
                                takeVideoIntent = null
                            }
                        }
                    }
                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                    contentSelectionIntent.type = file_type
                    val intentArray: Array<Intent?> = if (takePictureIntent != null && takeVideoIntent != null) {
                        arrayOf(takePictureIntent, takeVideoIntent)
                    } else takePictureIntent?.let { arrayOf(it) }
                        ?: (takeVideoIntent?.let { arrayOf(it) } ?: arrayOfNulls(0))
                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "File chooser")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    startActivityForResult(chooserIntent, file_req_code)
                    true
                } else {
                    false
                }
            }

            // Location
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.toString().startsWith("https://")) {
                    request.grant(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                } else {
                    super.onPermissionRequest(request)
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            webView.loadUrl(url)
        }

        // Disable Zoom, Copy Paste
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        // webView.setOnLongClickListener { true }

        // Swipe Refresh
        refreshLayout = findViewById(R.id.swipefresh)
        refreshLayout.setOnRefreshListener(this)

        // OneSignal
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(backPressedTime + 2000 > System.currentTimeMillis()){
            super.onBackPressed()
        }else{
            Toast.makeText(applicationContext, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }

    // Pull to Refresh
    override fun onRefresh() {
        webView.reload()
        refreshLayout.isRefreshing = false
    }
    private inner class MyWebViewClient : WebViewClient(){
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return super.shouldOverrideUrlLoading(view, request)
        }
    }

    // Location
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    webView.loadUrl(url)
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    fun file_permission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
                1
            )
            false
        } else {
            true
        }
    }

    @Throws(IOException::class)
    private fun create_image(): File? {
        @SuppressLint("SimpleDateFormat") val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    @Throws(IOException::class)
    private fun create_video(): File? {
        @SuppressLint("SimpleDateFormat") val file_name =
            SimpleDateFormat("yyyy_mm_ss").format(Date())
        val new_name = "file_" + file_name + "_"
        val sd_directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(new_name, ".3gp", sd_directory)
    }

    @SuppressLint("ObsoleteSdkInt")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        val fileData = file_data
        if (Build.VERSION.SDK_INT >= 21) {
            var results: Array<Uri?>? = null

            if (resultCode == RESULT_CANCELED) {
                file_path!!.onReceiveValue(null)
                return
            }

            if (resultCode == RESULT_OK) {
                if (null == file_path) {
                    return
                }
                var clipData: ClipData?
                var stringData: String?
                try {
                    clipData = intent!!.clipData
                    stringData = intent.dataString
                } catch (e: Exception) {
                    clipData = null
                    stringData = null
                }
                if (clipData != null) {
                    val numSelectedFiles = clipData.itemCount
                    results = arrayOfNulls(numSelectedFiles)
                    for (i in 0 until clipData.itemCount) {
                        results[i] = clipData.getItemAt(i).uri
                    }
                } else {
                    try {
                        val cam_photo = intent!!.extras!!["data"] as Bitmap?
                        val bytes = ByteArrayOutputStream()
                        cam_photo!!.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                        stringData = MediaStore.Images.Media.insertImage(
                            this.contentResolver,
                            cam_photo,
                            null,
                            null
                        )
                    } catch (ignored: Exception) {
                    }
                    results = arrayOf(Uri.parse(stringData))
                }
            }
            file_path!!.onReceiveValue(results)
            file_path = null
        } else {
            if (requestCode == file_req_code) {
                if (null == fileData) return
                val result = if (intent == null || resultCode != RESULT_OK) null else intent.data
                fileData.onReceiveValue(result)
                file_data = null
            }
        }
    }
}