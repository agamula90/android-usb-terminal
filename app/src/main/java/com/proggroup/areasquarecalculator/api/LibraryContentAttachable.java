package com.proggroup.areasquarecalculator.api;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

public interface LibraryContentAttachable extends ReportAttachable, OnProgressDismissable{

	FragmentManager getSupportFragmentManager();

	int getFragmentContainerId();

    //TODO can't use binding here
	DrawerLayout getDrawerLayout();

	int getToolbarId();

	int getLeftDrawerFragmentId();

	int getFolderDrawable();

	int getFileDrawable();

    int getButtonBackground();

	FrameLayout getFrameLayout();

    //TODO can't use binding here
	LinearLayout graphContainer();

    void onGraphAttached();

    void onGraphDetached();

	String toolbarTitle();

    void onBottomFragmentAttached();
}
