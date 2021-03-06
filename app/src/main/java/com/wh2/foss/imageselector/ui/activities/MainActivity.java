package com.wh2.foss.imageselector.ui.activities;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.View;

import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxTextView;
import com.wh2.foss.imageselector.R;
import com.wh2.foss.imageselector.databinding.ActivityMainBinding;
import com.wh2.foss.imageselector.databinding.DialogHostBinding;
import com.wh2.foss.imageselector.model.Config;
import com.wh2.foss.imageselector.model.Image;
import com.wh2.foss.imageselector.model.downloads.Progress;
import com.wh2.foss.imageselector.ui.adapters.ImagesAdapter;
import com.wh2.foss.imageselector.ui.viewmodels.ActivityViewModel;
import com.wh2.foss.imageselector.ui.viewmodels.ImageViewModel;
import com.wh2.foss.imageselector.utils.FilesHelper;

import java.util.ArrayList;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    ActivityViewModel activityViewModel;

    ActivityMainBinding activityMainBinding;
    DialogHostBinding dialogHostBinding;

    AlertDialog dialogHostView;

    ImagesAdapter adapter;
    CompositeDisposable subscriptions = new CompositeDisposable();
    private String dirPath;
    private String fileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        activityViewModel = new ActivityViewModel(this);
        activityMainBinding.setVm(activityViewModel);

        dialogHostBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_host, null, false);

        dirPath = new FilesHelper(this).getDirectoryPath();
        fileName = "foss_image.jpg";
    }

    public AlertDialog createDialog(View view, boolean cancelable) {

        // create a new dialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setView(view);
        builder.setCancelable(cancelable);

        // create and return the dialog
        return builder.create();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subscriptions.dispose();
    }

    private void loadData() {
        if (dialogHostView != null && dialogHostView.isShowing()) {
            dialogHostView.dismiss();
        }
        subscriptions.add(
                activityViewModel.getConfigurations().subscribe(
                        config -> {
                            setRecyclerView(config);
                            subscriptions.add(activityViewModel.getPictures().subscribe(
                                    this::updateRecyclerView,
                                    this::onError)
                            );
                        },
                        this::onError)
        );
    }

    private void setupSubscriptionsForHostAddress() {
        dialogHostView = createDialog(dialogHostBinding.getRoot(), false);
        subscriptions.add(
                RxView.clicks(dialogHostBinding.button)
                        .filter(o -> isValidHostAddress())
                        .subscribe(
                                o -> loadData(),
                                throwable -> {}));
        subscriptions.add(
                RxTextView.textChanges(dialogHostBinding.editText)
                        .subscribe(
                                o -> dialogHostBinding.textErrorMsg.setText(isValidHostAddress() ? getString(R.string.message_ok) : getString(R.string.message_invalid_host)),
                                throwable -> {}));
    }

    private boolean isValidHostAddress() {
        return dialogHostBinding != null && dialogHostBinding.editText.getText().toString().matches("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    }

    private void setRecyclerView(Config config) {
        activityMainBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new ImagesAdapter(new ArrayList<>(), config, this);
        subscriptions.add(adapter.imageSelected().subscribe(this::pictureSelected));
        subscriptions.add(adapter.imageIgnored().subscribe(this::pictureIgnored));
        activityMainBinding.recyclerView.setAdapter(adapter);
    }

    private void updateRecyclerView(Image image) {
        if (adapter != null) {
            adapter.addItem(image);
        }
    }

    private void pictureSelected(ImageViewModel imageViewModel) {
        Disposable downloadPicture = imageViewModel.performDownload(dirPath, fileName).subscribe(
                this::setupProgress,
                this::onError,
                this::onDownloadComplete,
                disposable -> showMessage(getString(R.string.message_download_started))
        );
        if (!downloadPicture.isDisposed()) {
            subscriptions.remove(downloadPicture);
        }
    }

    private void onError(Throwable throwable) {
        if (!TextUtils.isEmpty(throwable.getLocalizedMessage())) {
            Snackbar.make(activityMainBinding.getRoot(), throwable.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void setupProgress(Progress progress) {
        activityViewModel.setProgress(progress.getPercentDownloaded());
    }

    private void onDownloadComplete() {
        activityViewModel.progressShowing.set(false);
        showMessage(getString(R.string.message_download_complete));
    }

    private void pictureIgnored(ImageViewModel imageViewModel) {
        subscriptions.add(imageViewModel.ignorePicture().subscribe(() -> {
            loadData();
            showMessage(getString(R.string.message_picture_ignored));
        }, this::onError));
    }

    private void showMessage(String message) {
        Snackbar.make(activityMainBinding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}

