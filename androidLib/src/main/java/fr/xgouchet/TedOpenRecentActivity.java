package fr.xgouchet;

import static fr.xgouchet.texteditor.common.Constants.PREFERENCES_NAME;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;

import fr.xgouchet.R;

import java.io.File;
import java.util.ArrayList;

import fr.xgouchet.androidlib.ui.Toaster;
import fr.xgouchet.texteditor.common.RecentFiles;
import fr.xgouchet.texteditor.ui.adapter.PathListAdapter;

public class TedOpenRecentActivity extends Activity implements OnClickListener,
		OnItemClickListener {

	protected String mContextPath;

	/**
	 * the dialog's list view
	 */
	protected ListView mFilesList;

	/**
	 * The list adapter
	 */
	protected ListAdapter mListAdapter;

	/**
	 * the list of recent files
	 */
	protected ArrayList<String> mList;

	/**
	 * @see Activity#onCreate(Bundle)
	 */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Setup content view
		setContentView(R.layout.layout_open);

		// buttons
		findViewById(R.id.buttonCancel).setOnClickListener(this);

		// widgets
		mFilesList = (ListView) findViewById(android.R.id.list);
		mFilesList.setOnItemClickListener(this);
		mFilesList.setOnCreateContextMenuListener(this);
	}

	/**
	 * @see Activity#onResume()
	 */
	protected void onResume() {
		super.onResume();

		fillRecentFilesView();
	}

	/**
	 * @see Activity#onCreateContextMenu(ContextMenu,
	 * View, ContextMenuInfo)
	 */
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		mContextPath = (String) mFilesList.getAdapter().getItem(info.position);

		menu.setHeaderTitle(mContextPath);
		menu.add(Menu.NONE, 0, Menu.NONE, R.string.ui_delete);
	}

	/**
	 * @see Activity#onContextItemSelected(MenuItem)
	 */
	public boolean onContextItemSelected(MenuItem item) {

		RecentFiles.removePath(mContextPath);
		RecentFiles.saveRecentList(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE));

		fillRecentFilesView();
		Toaster.showToast(this, R.string.toast_recent_files_deleted, false);

		return true;
	}

	/**
	 * @see OnClickListener#onClick(View)
	 */
	public void onClick(View v) {
		if (v.getId() == R.id.buttonCancel) {
            setResult(RESULT_CANCELED);
            finish();
		}
	}

	/**
	 * @see OnItemClickListener#onItemClick(AdapterView,
	 * View, int, long)
	 */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		String path;

		path = mList.get(position);

		if (setOpenResult(new File(path))) {
			finish();
		} else {
			RecentFiles.removePath(path);
			RecentFiles.saveRecentList(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE));
			((PathListAdapter) mListAdapter).notifyDataSetChanged();
			Toaster.showToast(this, R.string.toast_recent_files_invalid, true);
		}

	}

	/**
	 * Fills the files list with the recent files
	 */
	protected void fillRecentFilesView() {
		mList = RecentFiles.getRecentFiles();

		if (mList.size() == 0) {
			setResult(RESULT_CANCELED);
			finish();
			return;
		}

		// create string list adapter
		mListAdapter = new PathListAdapter(this, mList);

		// set adpater
		mFilesList.setAdapter(mListAdapter);
	}

	/**
	 * Set the result of this activity to open a file
	 *
	 * @param file the file to return
	 * @return if the result was set correctly
	 */
	protected boolean setOpenResult(File file) {
		Intent result;

		if ((file == null) || (!file.isFile()) || (!file.canRead())) {
			return false;
		}

		result = new Intent();
		result.putExtra("path", file.getAbsolutePath());

		setResult(RESULT_OK, result);
		return true;
	}
}
